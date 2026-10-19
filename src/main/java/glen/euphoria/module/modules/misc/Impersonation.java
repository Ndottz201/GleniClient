package glen.euphoria.module.modules.misc;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.chat.ChatEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.utils.mc.ChatUtil;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class Impersonation extends Module {

    private static final Pattern MOJANG_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String UUID_DASHLESS = "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})";

    private static final Executor LOOKUP_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Euphoria-Impersonation-Lookup");
        thread.setDaemon(true);
        return thread;
    });

    // Mojang lookups are rate limited, so keep what we already fetched for this game session.
    private static final Map<String, Resolved> MOJANG_CACHE = new ConcurrentHashMap<>();
    private static HttpClient httpClient;

    private final BooleanSetting copySkin = new BooleanSetting("Copy Skin", true);
    private final BooleanSetting copyDisplayName = new BooleanSetting("Copy Display Name", true);
    private final BooleanSetting copyTabName = new BooleanSetting("Copy Tab Name", true);
    private final BooleanSetting copyTeam = new BooleanSetting("Copy Team/Rank", true);

    private ImpersonationData activeImpersonation = null;
    private InputState inputState = InputState.NONE;
    private String pendingTargetName = "";

    // Bumped whenever the module is (re)started or toggled, so a slow lookup that finishes late is ignored.
    private volatile int requestToken = 0;

    private enum InputState {
        NONE,
        WAITING_FOR_TARGET,
        WAITING_FOR_IMPERSONATE
    }

    /** A player identity we were able to resolve, either from the live server or from Mojang. */
    private record Resolved(UUID uuid, String name, Property textures) {
    }

    public Impersonation() {
        super("Impersonation", "Changes player appearances client-side", Category.MISC);
        addSettings(copySkin, copyDisplayName, copyTabName, copyTeam);
    }

    // ==================== Data Class ====================

    public static class ImpersonationData {
        public final UUID targetUUID;
        public final String targetName;
        public final String impersonateName;
        public UUID impersonateUUID;
        public GameProfile impersonateProfile;
        public Text newDisplayName;
        public Text newTabName;
        public Text fullNametag;
        public Text teamPrefix;
        public Text teamSuffix;

        /** True when the target's skin should be replaced (the Copy Skin setting was on and it could be prepared). */
        public boolean skinLoaded = false;
        /** Reads the impersonated player's skin; falls back to the default skin while it is still downloading. */
        public Supplier<SkinTextures> skinSupplier;
        /** Steve/Alex style skin of the impersonated player, used when they have no custom skin. */
        public SkinTextures defaultSkin;
        public boolean dataLoaded = false;

        public ImpersonationData(UUID targetUUID, String targetName, String impersonateName) {
            this.targetUUID = targetUUID;
            this.targetName = targetName;
            this.impersonateName = impersonateName;
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onEnable() {
        requestToken++;
        activeImpersonation = null;
        inputState = InputState.WAITING_FOR_TARGET;
        pendingTargetName = "";

        ChatUtil.chatMessage("§a§l[Impersonation] §fEnter the §eTARGET§f player name:");
        ChatUtil.chatMessage("§7(The player you want to disguise. Type 'cancel' to abort)");
    }

    @Override
    public void onDisable() {
        requestToken++;
        if (activeImpersonation != null) {
            ChatUtil.chatMessage("§e[Impersonation] §fImpersonation removed.");
        }
        activeImpersonation = null;
        inputState = InputState.NONE;
        pendingTargetName = "";
    }

    // ==================== Events ====================

    @EventHandler
    private void onChatMessage(ChatEvent event) {
        if (inputState == InputState.NONE) return;

        // Swallow the line first, so nothing below can ever let it through to the server.
        event.cancel();

        try {
            String message = event.getMessage() == null ? "" : event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel")) {
                inputState = InputState.NONE;
                ChatUtil.chatMessage("§c[Impersonation] §fCancelled.");
                mc.execute(() -> setEnabled(false));
                return;
            }

            if (inputState == InputState.WAITING_FOR_TARGET) {
                handleTargetInput(message);
            } else if (inputState == InputState.WAITING_FOR_IMPERSONATE) {
                handleImpersonateInput(message);
            }
        } catch (Throwable t) {
            reportError("Something went wrong", t);
        }
    }

    private void handleTargetInput(String targetName) {
        if (targetName.isEmpty()) {
            ChatUtil.chatMessage("§c[Impersonation] §fName cannot be empty.");
            return;
        }

        boolean online = isOnline(targetName);
        if (!online && !MOJANG_NAME.matcher(targetName).matches()) {
            ChatUtil.chatMessage("§c[Impersonation] §f'§e" + targetName + "§f' is not a valid player name!");
            return;
        }

        pendingTargetName = targetName;
        inputState = InputState.WAITING_FOR_IMPERSONATE;

        ChatUtil.chatMessage("§a[Impersonation] §fTarget: §e" + targetName + (online ? "" : " §7(offline - disguise shows once they join)"));
        promptForImpersonate();
    }

    private void handleImpersonateInput(String impersonateName) {
        if (impersonateName.isEmpty()) {
            ChatUtil.chatMessage("§c[Impersonation] §fName cannot be empty.");
            return;
        }

        if (impersonateName.equalsIgnoreCase(pendingTargetName)) {
            ChatUtil.chatMessage("§c[Impersonation] §fCannot be the same player!");
            return;
        }

        if (!isOnline(impersonateName) && !MOJANG_NAME.matcher(impersonateName).matches()) {
            ChatUtil.chatMessage("§c[Impersonation] §f'§b" + impersonateName + "§f' is not a valid player name!");
            return;
        }

        // Stop input mode, the lookup below runs in the background and chat is free again meanwhile.
        inputState = InputState.NONE;
        startApply(pendingTargetName, impersonateName);
    }

    private void promptForTarget() {
        pendingTargetName = "";
        inputState = InputState.WAITING_FOR_TARGET;
        ChatUtil.chatMessage("§a§l[Impersonation] §fEnter the §eTARGET§f player name:");
    }

    private void promptForImpersonate() {
        inputState = InputState.WAITING_FOR_IMPERSONATE;
        ChatUtil.chatMessage("§a§l[Impersonation] §fNow enter the §bIMPERSONATE§f player name:");
        ChatUtil.chatMessage("§7(Any player works, online or offline)");
    }

    // ==================== Core Logic ====================

    private void startApply(String targetName, String impersonateName) {
        if (mc.world == null || mc.getNetworkHandler() == null) {
            ChatUtil.chatMessage("§c[Impersonation] §fJoin a world or server first.");
            promptForTarget();
            return;
        }

        final int token = ++requestToken;

        // The target only needs an identity (UUID); the skin is only needed for the impersonated player.
        CompletableFuture<Resolved> targetFuture = resolve(targetName, false);
        CompletableFuture<Resolved> impersonateFuture = resolve(impersonateName, copySkin.getValue());

        if (!targetFuture.isDone() || !impersonateFuture.isDone()) {
            ChatUtil.chatMessage("§7[Impersonation] Looking up players on Mojang...");
        }

        CompletableFuture.allOf(targetFuture, impersonateFuture).whenComplete((ignored, error) ->
                mc.execute(() -> onResolved(token, targetFuture, impersonateFuture, error)));
    }

    private void onResolved(int token, CompletableFuture<Resolved> targetFuture,
                            CompletableFuture<Resolved> impersonateFuture, Throwable error) {
        if (token != requestToken || !isEnabled()) return;

        try {
            if (error != null) {
                ChatUtil.chatMessage("§c[Impersonation] §fLookup failed: §7" + rootMessage(error));
                inputState = InputState.WAITING_FOR_IMPERSONATE;
                ChatUtil.chatMessage("§7Enter the IMPERSONATE player name again, or type 'cancel'.");
                return;
            }

            Resolved target = targetFuture.join();
            Resolved impersonate = impersonateFuture.join();

            if (target == null) {
                ChatUtil.chatMessage("§c[Impersonation] §fTarget player not found!");
                promptForTarget();
                return;
            }
            if (impersonate == null) {
                ChatUtil.chatMessage("§c[Impersonation] §fThat player doesn't exist!");
                inputState = InputState.WAITING_FOR_IMPERSONATE;
                ChatUtil.chatMessage("§7Enter the IMPERSONATE player name again, or type 'cancel'.");
                return;
            }
            if (target.uuid().equals(impersonate.uuid())) {
                ChatUtil.chatMessage("§c[Impersonation] §fCannot be the same player!");
                inputState = InputState.WAITING_FOR_IMPERSONATE;
                return;
            }

            finishApply(target, impersonate);
        } catch (Throwable t) {
            reportError("Failed to apply impersonation", t);
            inputState = InputState.WAITING_FOR_IMPERSONATE;
        }
    }

    private void finishApply(Resolved target, Resolved impersonate) {
        ImpersonationData data = new ImpersonationData(target.uuid(), target.name(), impersonate.name());
        data.impersonateUUID = impersonate.uuid();

        if (copySkin.getValue()) {
            prepareSkin(data, impersonate);
        }
        prepareNames(data, impersonate);
        data.dataLoaded = true;

        activeImpersonation = data;

        boolean targetOnline = isOnline(target.name());
        ChatUtil.chatMessage("§a[Impersonation] §f'§e" + target.name() + "§f' " + (targetOnline ? "now appears as" : "will appear as")
                + " '§b" + impersonate.name() + "§f'" + (targetOnline ? "!" : " once they join!"));
        ChatUtil.chatMessage("§7Disable module to remove impersonation.");
    }

    private void prepareSkin(ImpersonationData data, Resolved impersonate) {
        data.skinLoaded = true;
        data.defaultSkin = DefaultSkinHelper.getSkinTextures(impersonate.uuid());

        if (impersonate.textures() == null) {
            // That account has no custom skin, so it really does look like Steve/Alex.
            return;
        }

        GameProfile profile = buildProfile(impersonate.uuid(), impersonate.name(), impersonate.textures());
        data.impersonateProfile = profile;

        if (!profile.properties().containsKey("textures") || mc.getSkinProvider() == null) {
            data.skinLoaded = false;
            ChatUtil.chatMessage("§e[Impersonation] §fCould not attach skin data on this version, copying names only.");
            return;
        }

        final var provider = mc.getSkinProvider();
        data.skinSupplier = () -> {
            try {
                return provider.supplySkinTextures(profile, false).get();
            } catch (Exception e) {
                return null;
            }
        };
    }

    private void prepareNames(ImpersonationData data, Resolved impersonate) {
        String name = impersonate.name();
        PlayerListEntry entry = findPlayerListEntry(name);
        PlayerEntity entity = findPlayerInWorld(name);

        // Team data (works for offline players too, as long as the server has them on a team)
        Text decorated = null;
        if (copyTeam.getValue() && mc.world != null && mc.world.getScoreboard() != null) {
            var team = mc.world.getScoreboard().getScoreHolderTeam(name);
            if (team != null) {
                data.teamPrefix = team.getPrefix().copy();
                data.teamSuffix = team.getSuffix().copy();
                decorated = team.decorateName(Text.literal(name));
            }
        }

        // Full nametag from entity
        if (entity != null && copyDisplayName.getValue()) {
            data.fullNametag = entity.getDisplayName().copy();
            data.newDisplayName = entity.getDisplayName().copy();
        } else if (entry != null && copyDisplayName.getValue()) {
            Text displayName = entry.getDisplayName();
            data.newDisplayName = displayName != null ? displayName.copy() : Text.literal(name);
            data.fullNametag = data.newDisplayName;
        }

        // Tab name
        if (copyTabName.getValue()) {
            Text tabName = entry != null ? entry.getDisplayName() : null;
            if (tabName != null) {
                data.newTabName = tabName.copy();
            } else {
                data.newTabName = decorated != null ? decorated.copy() : Text.literal(name);
            }
        }

        if (decorated != null) {
            data.fullNametag = decorated.copy();
            data.newDisplayName = decorated.copy();
        }

        if (data.fullNametag == null) {
            data.fullNametag = entity != null ? entity.getDisplayName().copy() : Text.literal(name);
        }
    }

    // ==================== Player Resolution ====================

    /**
     * Finds a player by name. Players on the server are used as-is; anyone else (offline players,
     * or players the server sent no skin data for) is looked up through Mojang in the background.
     * Must be called on the main thread. Completes with null when the player does not exist.
     */
    private CompletableFuture<Resolved> resolve(String name, boolean needTextures) {
        Resolved live = resolveLive(name);
        if (live != null && (!needTextures || live.textures() != null)) {
            return CompletableFuture.completedFuture(live);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchMojang(name, needTextures);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, LOOKUP_EXECUTOR).handle((remote, error) -> {
            if (live != null) {
                // Keep the identity the server gave us, only borrow the skin (a failed lookup is not fatal).
                if (remote != null && remote.textures() != null) {
                    return new Resolved(live.uuid(), live.name(), remote.textures());
                }
                return live;
            }
            if (error != null) {
                throw new CompletionException(error);
            }
            return remote;
        });
    }

    private Resolved resolveLive(String name) {
        PlayerListEntry entry = findPlayerListEntry(name);
        PlayerEntity entity = findPlayerInWorld(name);

        GameProfile profile = entry != null ? entry.getProfile() : entity != null ? entity.getGameProfile() : null;
        if (profile == null) return null;

        Property textures = null;
        Collection<Property> found = profile.properties().get("textures");
        if (!found.isEmpty()) {
            textures = found.iterator().next();
        }
        return new Resolved(profile.id(), profile.name(), textures);
    }

    private static Resolved fetchMojang(String name, boolean needTextures) throws IOException {
        if (!MOJANG_NAME.matcher(name).matches()) return null;

        String cacheKey = name.toLowerCase(Locale.ROOT);
        Resolved cached = MOJANG_CACHE.get(cacheKey);
        if (cached != null && (!needTextures || cached.textures() != null)) {
            return cached;
        }

        JsonObject idObject = getJson("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (idObject == null || !idObject.has("id") || !idObject.has("name")) return null;

        UUID uuid = parseUuid(idObject.get("id").getAsString());
        String exactName = idObject.get("name").getAsString();

        Property textures = null;
        if (needTextures) {
            JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + uuid.toString().replace("-", "") + "?unsigned=false");
            if (profile != null && profile.has("properties") && profile.get("properties").isJsonArray()) {
                for (JsonElement element : profile.getAsJsonArray("properties")) {
                    JsonObject property = element.getAsJsonObject();
                    if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) continue;
                    if (!property.has("value")) continue;

                    String signature = property.has("signature") && !property.get("signature").isJsonNull()
                            ? property.get("signature").getAsString() : null;
                    textures = new Property("textures", property.get("value").getAsString(), signature);
                    break;
                }
            }
        }

        Resolved resolved = new Resolved(uuid, exactName, textures);
        if (textures != null || cached == null) {
            MOJANG_CACHE.put(cacheKey, resolved);
        }
        return resolved;
    }

    private static JsonObject getJson(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "GleniClient")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Lookup was interrupted", e);
        }

        int status = response.statusCode();
        if (status == 204 || status == 404) return null;
        if (status == 429) throw new IOException("Mojang is rate limiting requests, try again in a minute");
        if (status != 200) throw new IOException("Mojang returned HTTP " + status);

        JsonElement parsed = JsonParser.parseString(response.body());
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    private static synchronized HttpClient http() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        }
        return httpClient;
    }

    private static UUID parseUuid(String raw) {
        String dashed = raw.contains("-") ? raw : raw.replaceFirst(UUID_DASHLESS, "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    /**
     * Builds a profile that carries the given skin. Newer authlib versions hand out immutable property maps,
     * so the maps are passed in through the constructors first and only mutated as a fallback for older versions.
     */
    private static GameProfile buildProfile(UUID id, String name, Property textures) {
        try {
            Multimap<String, Property> map = ImmutableMultimap.of("textures", textures);
            PropertyMap properties = PropertyMap.class.getConstructor(Multimap.class).newInstance(map);
            return GameProfile.class.getConstructor(UUID.class, String.class, PropertyMap.class)
                    .newInstance(id, name, properties);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall through to the older API
        }

        GameProfile profile = new GameProfile(id, name);
        try {
            profile.properties().put("textures", textures);
        } catch (UnsupportedOperationException ignored) {
            // the caller checks for the missing property
        }
        return profile;
    }

    // ==================== Public API for Mixins ====================

    public Text getOverriddenDisplayName(UUID playerUUID, Text original) {
        if (!isEnabled() || activeImpersonation == null || !activeImpersonation.dataLoaded) return original;
        if (!playerUUID.equals(activeImpersonation.targetUUID)) return original;
        if (!copyDisplayName.getValue()) return original;

        return activeImpersonation.fullNametag != null ? activeImpersonation.fullNametag : original;
    }

    public Text getOverriddenTabName(UUID playerUUID, Text original) {
        if (!isEnabled() || activeImpersonation == null || !activeImpersonation.dataLoaded) return original;
        if (!playerUUID.equals(activeImpersonation.targetUUID)) return original;
        if (!copyTabName.getValue()) return original;

        return activeImpersonation.newTabName != null ? activeImpersonation.newTabName : original;
    }

    public Text getOverriddenTeamPrefix(String playerName, Text original) {
        if (!isEnabled() || activeImpersonation == null || !activeImpersonation.dataLoaded) return original;
        if (!activeImpersonation.targetName.equalsIgnoreCase(playerName)) return original;
        if (!copyTeam.getValue()) return original;

        return activeImpersonation.teamPrefix != null ? activeImpersonation.teamPrefix : original;
    }

    public Text getOverriddenTeamSuffix(String playerName, Text original) {
        if (!isEnabled() || activeImpersonation == null || !activeImpersonation.dataLoaded) return original;
        if (!activeImpersonation.targetName.equalsIgnoreCase(playerName)) return original;
        if (!copyTeam.getValue()) return original;

        return activeImpersonation.teamSuffix != null ? activeImpersonation.teamSuffix : original;
    }

    public SkinTextures getOverriddenSkinTextures(UUID playerUUID, SkinTextures original) {
        ImpersonationData data = getImpersonationData(playerUUID);
        if (data == null || !data.dataLoaded || !data.skinLoaded) return original;
        if (!copySkin.getValue()) return original;

        if (data.skinSupplier == null) {
            return data.defaultSkin != null ? data.defaultSkin : original;
        }

        SkinTextures loaded = data.skinSupplier.get();
        // Until the download finishes the provider hands back the default skin; keep the real one meanwhile.
        if (loaded == null || loaded.equals(data.defaultSkin)) return original;
        return loaded;
    }

    public ImpersonationData getImpersonationData(UUID playerUUID) {
        if (!isEnabled() || activeImpersonation == null) return null;
        if (!playerUUID.equals(activeImpersonation.targetUUID)) return null;
        return activeImpersonation;
    }

    public Map<UUID, ImpersonationData> getActiveImpersonations() {
        Map<UUID, ImpersonationData> map = new HashMap<>();
        if (activeImpersonation != null) {
            map.put(activeImpersonation.targetUUID, activeImpersonation);
        }
        return map;
    }

    // ==================== Utility ====================

    private boolean isOnline(String name) {
        return findPlayerListEntry(name) != null || findPlayerInWorld(name) != null;
    }

    private void reportError(String what, Throwable t) {
        EuphoriaClient.INSTANCE.getLogger().error("[Impersonation] " + what, t);
        ChatUtil.chatMessage("§c[Impersonation] §f" + what + ": §7" + rootMessage(t));
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null ? message : root.getClass().getSimpleName();
    }

    private PlayerListEntry findPlayerListEntry(String name) {
        if (mc.getNetworkHandler() == null) return null;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    private PlayerEntity findPlayerInWorld(String name) {
        if (mc.world == null) return null;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
