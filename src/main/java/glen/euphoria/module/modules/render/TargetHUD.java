package glen.euphoria.module.modules.render;

import glen.euphoria.event.impl.player.AttackEvent;
import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.gui.ClickGui;
import glen.euphoria.gui.newgui.NewClickGUI;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.render.DraggableComponent;
import glen.euphoria.utils.render.HudGlassmorphism;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class TargetHUD extends Module {
    private static final int BASE_WIDTH = 136;
    private static final int BASE_HEIGHT = 44;
    private static final int PADDING = 7;
    private static final int HEAD_SIZE = 30;
    private static final float CORNER_RADIUS = 12f;
    private static final float HEALTH_BAR_HEIGHT = 6f;
    private static final float HEALTH_BAR_RADIUS = 3f;
    private static final float NAME_SIZE = 9.5f;
    private static final float HEALTH_TEXT_SIZE = 8.5f;
    private static final float ANIMATION_SPEED = 3.5f;
    private static final int PARTICLE_LIFETIME_MS = 1000;
    private static final float PARTICLE_GRAVITY = 200f;
    private static final long TARGET_TIMEOUT_MS = 5000L;

    private final BooleanSetting blur;
    private final NumberSetting blurRadius;
    private final BooleanSetting gradientHealth;
    private final BooleanSetting particles;
    private final NumberSetting particleCount;

    private final DraggableComponent draggable;
    private final Deque<Particle> particleQueue;
    
    private float animatedHealthPercent;
    private float lastHealthPercent;
    private int userIcon = -1;
    private final Map<Integer, Integer> skinImageCache = new HashMap<>();
    private float animationProgress = 0f;

    private PlayerEntity currentTarget = null;
    private PlayerEntity pendingTarget = null;
    private long lastHitTime = 0L;
    private long pendingHitTime = 0L;
    private boolean swapping = false;
    private RenderData currentData = null;

    public TargetHUD() {
        super("Target HUD", "Shows target information on screen", -1, Category.RENDER);
        
        this.blur = new BooleanSetting("Blur", true);
        this.blurRadius = new NumberSetting("Blur Radius", 1, 30, 12, 1);
        this.gradientHealth = new BooleanSetting("Gradient Health", true);
        this.particles = new BooleanSetting("Particles", true);
        this.particleCount = new NumberSetting("Particle Count", 5, 30, 15, 1);
        
        this.draggable = new DraggableComponent(20, 100, BASE_WIDTH, BASE_HEIGHT);
        this.particleQueue = new ArrayDeque<>();
        this.animatedHealthPercent = 1.0f;
        this.lastHealthPercent = 1.0f;
        
        addSettings(blur, blurRadius, gradientHealth, particles, particleCount);
    }

    @Override
    public void onDisable() {
        skinImageCache.clear();
        animationProgress = 0f;
        currentTarget = null;
        pendingTarget = null;
        currentData = null;
        swapping = false;
        particleQueue.clear();
    }

    private void loadUserIcon() {
        if (mc == null || mc.getWindow() == null) return;
        if (userIcon == -1) {
            userIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/user.png");
        }
    }

    private int getOrCreateSkinImage(Identifier texture) {
        if (texture == null) return -1;
        net.minecraft.client.texture.AbstractTexture tex = mc.getTextureManager().getTexture(texture);
        if (tex == null) return -1;
        com.mojang.blaze3d.textures.GpuTexture gpuTexture = tex.getGlTexture();
        if (!(gpuTexture instanceof net.minecraft.client.texture.GlTexture glTexture)) return -1;
        int glId = glTexture.getGlId();
        if (glId <= 0) return -1;

        // Force GL_NEAREST so Minecraft skin pixels remain sharp and not blurred
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return skinImageCache.computeIfAbsent(glId, id -> {
            long vg = NanoVGRenderer.getContext();
            if (vg == 0L) return -1;
            return org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle(
                    vg, id, 64, 64,
                    org.lwjgl.nanovg.NanoVGGL3.NVG_IMAGE_NODELETE | org.lwjgl.nanovg.NanoVG.NVG_IMAGE_NEAREST
            );
        });
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (!(event.getTarget() instanceof PlayerEntity player) || player == mc.player) {
            return;
        }

        long now = System.currentTimeMillis();
        if (currentTarget == null) {
            // New target - slide in immediately
            currentTarget = player;
            lastHitTime = now;
            currentData = new RenderData(player);
            swapping = false;
            pendingTarget = null;
        } else if (currentTarget == player) {
            // Hit same player - reset 5-second timer
            lastHitTime = now;
            currentData = new RenderData(player);
            if (swapping) {
                swapping = false;
                pendingTarget = null;
            }
        } else {
            // Swapping to a different target: animate current out, then bring in new
            pendingTarget = player;
            pendingHitTime = now;
            swapping = true;
        }

        if (particles.getValue()) {
            spawnParticles(player);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        boolean isClickGuiOpen = mc.currentScreen instanceof NewClickGUI
                || mc.currentScreen instanceof ClickGui;
        boolean isInChat = mc.currentScreen instanceof ChatScreen;

        if (isClickGuiOpen) {
            if (!event.isContentPass()) return;
        } else {
            if (event.isContentPass()) return;
        }

        if (!shouldRender()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Check 5-second timeout and entity validity
        if (currentTarget != null) {
            boolean timedOut = (now - lastHitTime) > TARGET_TIMEOUT_MS;
            boolean invalid = !currentTarget.isAlive() || currentTarget.isRemoved() || currentTarget.getEntityWorld() != mc.world;
            if (timedOut || invalid) {
                if (pendingTarget != null) {
                    swapping = true;
                } else {
                    currentTarget = null;
                }
            } else {
                currentData = new RenderData(currentTarget);
            }
        }

        // Handle target swap animation
        if (swapping) {
            if (animationProgress <= 0.03f) {
                // Previous target finished closing animation -> switch to pending target
                currentTarget = pendingTarget;
                lastHitTime = pendingHitTime;
                currentData = (pendingTarget != null)
                        ? new RenderData(pendingTarget)
                        : null;
                pendingTarget = null;
                swapping = false;
                animationProgress = 0f;
            }
        }

        PlayerEntity targetToRender = currentTarget;
        RenderData dataToRender = currentData;

        // Preview ONLY in ClickGUI when no active target (never when chat is open)
        if (targetToRender == null && isClickGuiOpen) {
            targetToRender = mc.player;
            dataToRender = new RenderData(mc.player);
        }

        boolean hasTarget = (currentTarget != null && !swapping) || (isClickGuiOpen && targetToRender != null);
        float targetAnim = hasTarget ? 1f : 0f;

        // Pop-in / pop-out animation speed
        float animSpeed = hasTarget ? 0.18f : 0.22f;
        animationProgress = MathHelper.lerp(animSpeed, animationProgress, targetAnim);

        if (animationProgress <= 0.01f && !hasTarget) {
            animationProgress = 0f;
            if (!swapping) {
                currentTarget = null;
                currentData = null;
            }
            return;
        }

        if (targetToRender == null && currentData != null) {
            dataToRender = currentData;
        }
        if (dataToRender == null) return;

        if (isClickGuiOpen || isInChat) {
            draggable.update();
        }

        renderHUD(event, dataToRender, targetToRender, hasTarget);
    }

    private boolean shouldRender() {
        return mc.player != null && mc.world != null;
    }

    private void renderHUD(Render2DEvent event, RenderData data, PlayerEntity target, boolean hasTarget) {
        float x = draggable.getX();
        float y = draggable.getY();
        
        draggable.setWidth(BASE_WIDTH);
        draggable.setHeight(BASE_HEIGHT);

        int screenWidth = mc.getWindow().getScaledWidth();
        boolean onLeft = (x + BASE_WIDTH / 2f) < (screenWidth / 2f);
        float sideOffset = onLeft ? -(x + BASE_WIDTH + 30f) : (screenWidth - x + 30f);

        float ease = hasTarget ? easeOutBack(animationProgress) : easeOutCubic(animationProgress);
        float slideX = draggable.isDragging() ? 0f : (sideOffset * (1f - ease));
        float scale = draggable.isDragging() ? 1f : (0.88f + 0.12f * Math.min(1.05f, ease));

        float baseAlpha = 1.0f;
        float alpha = baseAlpha * MathHelper.clamp(animationProgress * 1.35f, 0f, 1f);
        if (alpha <= 0.01f) return;

        float cx = x + BASE_WIDTH / 2f;
        float cy = y + BASE_HEIGHT / 2f;

        NanoVGRenderer.save();
        NanoVGRenderer.translate(slideX, 0);
        NanoVGRenderer.translate(cx, cy);
        NanoVGRenderer.scale(scale, scale);
        NanoVGRenderer.translate(-cx, -cy);

        // 1. Unified Glassmorphism Card
        HudGlassmorphism.drawCard(x, y, BASE_WIDTH, BASE_HEIGHT, CORNER_RADIUS, alpha);

        if (draggable.isDragging()) {
            Color accent = getAccentColor();
            NanoVGRenderer.drawRoundedRectOutline(x, y, BASE_WIDTH, BASE_HEIGHT, CORNER_RADIUS, 1.5f, accent);
        }

        // 2. Target Name, Health Value, and Health Bar
        renderNameAndHealthBar(data, x, y, alpha);
        
        // 3. Particles
        if (particles.getValue()) {
            renderParticles();
        }
        
        // 4. Player Head (Crisp pixels with rounded 6px corners)
        if (target != null) {
            renderPlayerHead(target, x, y, alpha);
        }

        NanoVGRenderer.restore();
    }

    private void renderNameAndHealthBar(RenderData data, float x, float y, float alpha) {
        float headX = x + PADDING;
        float barX = headX + HEAD_SIZE + 8f;
        float barY = y + PADDING + 3f;
        float barWidth = BASE_WIDTH - HEAD_SIZE - 24f;

        int poppins = NanoVGRenderer.getPoppinsFontId();
        int regular = NanoVGRenderer.getRegularFontId();

        // Target Name
        String name = (data.name != null && !data.name.isEmpty()) ? data.name : "Target";

        // Health text value
        String hpText = String.format("%.1f", data.rawHealth);
        float hpWidth = NanoVGRenderer.getTextWidthWithFont(hpText, HEALTH_TEXT_SIZE, regular);
        NanoVGRenderer.drawTextWithFont(hpText, barX + barWidth - hpWidth, barY + 1f, HEALTH_TEXT_SIZE,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_SECONDARY, alpha), regular);

        float maxNameWidth = Math.max(35f, barWidth - hpWidth - 4f);
        if (NanoVGRenderer.getTextWidthWithFont(name, NAME_SIZE, poppins) > maxNameWidth) {
            while (name.length() > 3 && NanoVGRenderer.getTextWidthWithFont(name + "...", NAME_SIZE, poppins) > maxNameWidth) {
                name = name.substring(0, name.length() - 1);
            }
            name += "...";
        }
        NanoVGRenderer.drawTextWithFont(name, barX, barY, NAME_SIZE,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_PRIMARY, alpha), poppins);

        // Health Bar
        renderHealthBar(data, barX, barY + 13f, barWidth, alpha);
    }

    private void renderHealthBar(RenderData data, float x, float y, float width, float alpha) {
        NanoVGRenderer.drawRoundedRect(x, y, width, HEALTH_BAR_HEIGHT, HEALTH_BAR_RADIUS,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.ROW_FILL, alpha));
        NanoVGRenderer.drawRoundedRectOutline(x, y, width, HEALTH_BAR_HEIGHT, HEALTH_BAR_RADIUS, 1f,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.SHELL_INNER, alpha));

        updateHealthAnimation(data.healthPercent);
        
        float healthBarWidth = width * animatedHealthPercent;
        if (healthBarWidth > 1f) {
            Color accentColor = getAccentColor();
            if (gradientHealth.getValue()) {
                Color brighterAccent = new Color(
                        Math.min(255, (int) (accentColor.getRed() * 1.25f)),
                        Math.min(255, (int) (accentColor.getGreen() * 1.20f) + 20),
                        Math.min(255, (int) (accentColor.getBlue() * 1.15f) + 30),
                        HudGlassmorphism.clampColor((int) (220 * alpha))
                );
                Color startAccent = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                        HudGlassmorphism.clampColor((int) (230 * alpha)));
                NanoVGRenderer.drawRoundedRectGradient(x, y, healthBarWidth, HEALTH_BAR_HEIGHT,
                        HEALTH_BAR_RADIUS, startAccent, brighterAccent);
            } else {
                Color fill = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                        HudGlassmorphism.clampColor((int) (230 * alpha)));
                NanoVGRenderer.drawRoundedRect(x, y, healthBarWidth, HEALTH_BAR_HEIGHT,
                        HEALTH_BAR_RADIUS, fill);
            }
        }
    }

    private void updateHealthAnimation(float targetHealthPercent) {
        float deltaTime = mc.getRenderTickCounter().getTickProgress(true) / 20f;

        if (Math.abs(targetHealthPercent - lastHealthPercent) > 0.001f) {
            lastHealthPercent = targetHealthPercent;
        }

        if (animatedHealthPercent > lastHealthPercent) {
            animatedHealthPercent = Math.max(lastHealthPercent, 
                                            animatedHealthPercent - ANIMATION_SPEED * deltaTime);
        } else if (animatedHealthPercent < lastHealthPercent) {
            animatedHealthPercent = Math.min(lastHealthPercent, 
                                            animatedHealthPercent + ANIMATION_SPEED * deltaTime);
        }
    }

    private void renderPlayerHead(PlayerEntity player, float hudX, float hudY, float alpha) {
        float headX = hudX + PADDING;
        float headY = hudY + (BASE_HEIGHT - HEAD_SIZE) / 2f;

        // Inset slot frame for head
        HudGlassmorphism.drawSlot(headX - 1f, headY - 1f, HEAD_SIZE + 2f, HEAD_SIZE + 2f, 6f, alpha);

        Identifier texture = resolveSkin(player);
        int skinImg = getOrCreateSkinImage(texture);

        if (skinImg > 0) {
            float patternW = 8.0f * HEAD_SIZE;
            float patternH = 8.0f * HEAD_SIZE;
            float patternX = headX - HEAD_SIZE;
            float patternY = headY - HEAD_SIZE;

            // 1. Base skin face layer (UV 8,8 to 16,16) with 6px rounded corners
            NanoVGRenderer.drawImagePatternRounded(
                    skinImg, headX, headY, HEAD_SIZE, HEAD_SIZE, 6f,
                    patternX, patternY, patternW, patternH, alpha
            );

            // 2. Hat / outer layer (UV 40,8 to 48,8) with 6px rounded corners
            float patternXHat = headX - 5.0f * HEAD_SIZE;
            NanoVGRenderer.drawImagePatternRounded(
                    skinImg, headX, headY, HEAD_SIZE, HEAD_SIZE, 6f,
                    patternXHat, patternY, patternW, patternH, alpha
            );
        } else {
            loadUserIcon();
            if (userIcon != -1) {
                float iconSize = 18f;
                float iconX = headX + (HEAD_SIZE - iconSize) / 2f;
                float iconY = headY + (HEAD_SIZE - iconSize) / 2f;
                NanoVGRenderer.drawImage(userIcon, iconX, iconY, iconSize, iconSize,
                        HudGlassmorphism.applyAlpha(getAccentColor(), alpha));
            }
        }
    }

    private static float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1f - x, 3);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.4f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(x - 1f, 3) + c1 * (float) Math.pow(x - 1f, 2);
    }

    private void spawnParticles(LivingEntity target) {
        float x = draggable.getX();
        float y = draggable.getY();
        float headX = x + PADDING;
        float barX = headX + HEAD_SIZE + 8f;
        float barY = y + PADDING + 3f;
        float barWidth = BASE_WIDTH - HEAD_SIZE - 24f;

        float maxHealth = Math.max(target.getMaxHealth(), 1f);
        float health = MathHelper.clamp(target.getHealth(), 0f, maxHealth);
        float healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);
        float healthBarWidth = barWidth * healthPercent;

        float spawnX = barX + healthBarWidth;
        float spawnY = barY + 13f + HEALTH_BAR_HEIGHT / 2f;

        Color accentColor = getAccentColor();
        int count = particleCount.getValueInt();
        long now = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            float angle = (float) (rnd.nextDouble() * Math.PI * 2);
            float speed = 40 + rnd.nextFloat() * 80;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            float size = 1.5f + rnd.nextFloat() * 2.5f;

            particleQueue.addLast(new Particle(spawnX, spawnY, vx, vy, size, accentColor, now));
        }
    }

    private void renderParticles() {
        long now = System.currentTimeMillis();
        float deltaTime = mc.getRenderTickCounter().getTickProgress(true) / 20f;

        Iterator<Particle> it = particleQueue.iterator();
        while (it.hasNext()) {
            Particle p = it.next();

            if (now - p.spawnTime > PARTICLE_LIFETIME_MS) {
                it.remove();
                continue;
            }

            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;
            p.vy += PARTICLE_GRAVITY * deltaTime;

            float life = (now - p.spawnTime) / (float) PARTICLE_LIFETIME_MS;
            int alpha = (int) ((1f - life) * p.color.getAlpha());

            if (alpha > 0) {
                Color particleColor = new Color(p.color.getRed(), p.color.getGreen(), 
                                               p.color.getBlue(), alpha);
                NanoVGRenderer.drawCircle(p.x, p.y, p.size, particleColor);
            }
        }
    }

    private Identifier resolveSkin(PlayerEntity player) {
        SkinTextures textures;
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            textures = clientPlayer.getSkin();
        } else {
            textures = DefaultSkinHelper.getSkinTextures(player.getUuid());
        }
        return textures.body().texturePath();
    }

    private Color getAccentColor() {
        return HudGlassmorphism.getAccent();
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        Color color;
        long spawnTime;

        Particle(float x, float y, float vx, float vy, float size, Color color, long spawnTime) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.color = color;
            this.spawnTime = spawnTime;
        }
    }

    private static class RenderData {
        final PlayerEntity target;
        final String name;
        final float healthPercent;
        final float rawHealth;

        RenderData(PlayerEntity target) {
            this.target = target;

            String rawName = null;
            if (target != null) {
                if (target.getGameProfile() != null && target.getGameProfile().name() != null && !target.getGameProfile().name().isEmpty()) {
                    rawName = target.getGameProfile().name();
                } else {
                    rawName = target.getName().getString();
                }
            }
            if (rawName == null || rawName.trim().isEmpty()) {
                rawName = "Target";
            }
            this.name = Formatting.strip(rawName);

            if (target != null) {
                float maxHealth = Math.max(target.getMaxHealth(), 1f);
                this.rawHealth = MathHelper.clamp(target.getHealth(), 0f, maxHealth);
                this.healthPercent = MathHelper.clamp(rawHealth / maxHealth, 0f, 1f);
            } else {
                this.rawHealth = 20.0f;
                this.healthPercent = 0.75f;
            }
        }
    }

    @Override
    public void saveConfig(com.google.gson.JsonObject json) {
        if (draggable != null) {
            com.google.gson.JsonObject pos = new com.google.gson.JsonObject();
            pos.addProperty("x", draggable.getX());
            pos.addProperty("y", draggable.getY());
            json.add("position", pos);
        }
    }

    @Override
    public void loadConfig(com.google.gson.JsonObject json) {
        if (json.has("position") && json.get("position").isJsonObject()) {
            com.google.gson.JsonObject pos = json.getAsJsonObject("position");
            if (pos.has("x") && pos.has("y")) {
                draggable.setX(pos.get("x").getAsFloat());
                draggable.setY(pos.get("y").getAsFloat());
            }
        }
    }
}
