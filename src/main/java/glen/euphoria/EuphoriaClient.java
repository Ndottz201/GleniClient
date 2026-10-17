package glen.euphoria;

import glen.euphoria.module.ModuleManager;
import glen.euphoria.module.events.MouseModuleHandler;
import glen.euphoria.profiles.ProfileManager;
import glen.euphoria.utils.notification.NotificationManager;
import glen.euphoria.utils.render.font.FontManager;
import java.lang.invoke.MethodHandles;
import lombok.Getter;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public final class EuphoriaClient implements ClientModInitializer {

    public static final String CLIENT_VERSION = "v1.0";
    public static final boolean shouldUseMouseEvent = System.getProperty(
        "os.name"
    )
        .toLowerCase()
        .contains("windows");
    public static EuphoriaClient INSTANCE;
    public static MinecraftClient mc;
    public final IEventBus EuphoriaEventBus;
    public final ModuleManager moduleManager;
    public final FontManager fontManager;
    public final ProfileManager profileManager;
    public final MouseModuleHandler mouseModuleHandler;
    public final NotificationManager notificationManager;
    private final Logger logger = LoggerFactory.getLogger("Euphoria");

    public EuphoriaClient() {
        INSTANCE = this;
        mc = MinecraftClient.getInstance();
        EuphoriaEventBus = new EventBus();
        EuphoriaEventBus.registerLambdaFactory("glen.euphoria", (lookupInMethod, klass) ->
            (MethodHandles.Lookup) lookupInMethod.invoke(
                null,
                klass,
                MethodHandles.lookup()
            )
        );

        this.moduleManager = new ModuleManager();
        this.fontManager = new FontManager();
        this.profileManager = new ProfileManager();
        this.mouseModuleHandler = new MouseModuleHandler();
        this.notificationManager = NotificationManager.getInstance();

        EuphoriaEventBus.subscribe(mouseModuleHandler);
        EuphoriaEventBus.subscribe(notificationManager);
    }

    @Override
    public void onInitializeClient() {
        // Double initialization prevention, it's already initializing in the constructor
    }
}