package glen.euphoria.module.modules.render;

import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.utils.notification.Notification;
import glen.euphoria.utils.notification.NotificationManager;
import glen.euphoria.utils.render.HudGlassmorphism;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;

public class Notifications extends Module {

    private static final int NOTIFICATION_WIDTH = 158;
    private static final int NOTIFICATION_HEIGHT = 32;
    private static final int NOTIFICATION_SPACING = 6;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 10;
    private static final float CORNER_RADIUS = 9f;
    private static final float ICON_SIZE = 18f;
    private static final float ICON_RADIUS = 5f;
    private static final float PADDING_X = 8f;
    private static final float TITLE_SIZE = 9.5f;
    private static final float MESSAGE_SIZE = 8.0f;

    private static final Color ENABLED_COLOR = new Color(52, 211, 153);
    private static final Color DISABLED_COLOR = new Color(248, 113, 113);
    private static final Color BUFF_EXPIRED_COLOR = new Color(251, 191, 36);

    public Notifications() {
        super("Notifications", "Shows on-screen client notifications", Category.RENDER);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!event.isContentPass()) return;
        if (mc.player == null || mc.world == null) return;

        List<Notification> notifications = NotificationManager.getInstance().getNotifications();
        if (notifications.isEmpty()) return;

        updateNotifications(notifications);
        renderNotifications(event.getWidth(), event.getHeight(), notifications);
    }

    private void updateNotifications(List<Notification> notifications) {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();

            if (notification.isExpired() && !notification.isRemoving()) {
                notification.setRemoving(true);
            }

            float targetProgress = notification.isRemoving() ? 0f : 1f;
            notification.setAnimationProgress(
                    MathHelper.lerp(0.18f, notification.getAnimationProgress(), targetProgress)
            );

            if (notification.isRemoving() && notification.getAnimationProgress() < 0.03f) {
                iterator.remove();
            }
        }
    }

    private void renderNotifications(int screenWidth, int screenHeight, List<Notification> notifications) {
        float yOffset = 0;

        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification notification = notifications.get(i);

            float x = screenWidth - NOTIFICATION_WIDTH - MARGIN_RIGHT;
            float y = screenHeight - MARGIN_BOTTOM - NOTIFICATION_HEIGHT - yOffset;

            float slideProgress = notification.getAnimationProgress();
            float ease = easeOutCubic(slideProgress);
            float slideOffset = (1f - ease) * (NOTIFICATION_WIDTH + 24);
            x += slideOffset;

            renderNotification(notification, x, y);

            yOffset += (NOTIFICATION_HEIGHT + NOTIFICATION_SPACING) * ease;
        }
    }

    private void renderNotification(Notification notification, float x, float y) {
        float alpha = notification.getAnimationProgress();
        if (alpha <= 0.02f) return;

        Color statusColor = switch (notification.getType()) {
            case MODULE_ENABLED -> ENABLED_COLOR;
            case MODULE_DISABLED -> DISABLED_COLOR;
            case BUFF_EXPIRED -> BUFF_EXPIRED_COLOR;
        };

        // 1. Unified Glassmorphism Card
        HudGlassmorphism.drawCard(x, y, NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT, CORNER_RADIUS, alpha, statusColor);

        // 2. Icon Badge
        float iconX = x + PADDING_X;
        float iconY = y + (NOTIFICATION_HEIGHT - ICON_SIZE) / 2f - 1f;

        Color badgeFill = new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(),
                HudGlassmorphism.clampColor((int) (38 * alpha)));
        Color badgeBorder = new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(),
                HudGlassmorphism.clampColor((int) (110 * alpha)));

        NanoVGRenderer.drawRoundedRect(iconX, iconY, ICON_SIZE, ICON_SIZE, ICON_RADIUS, badgeFill);
        NanoVGRenderer.drawRoundedRectOutline(iconX, iconY, ICON_SIZE, ICON_SIZE, ICON_RADIUS, 1f, badgeBorder);

        String symbol = switch (notification.getType()) {
            case MODULE_ENABLED -> "✓";
            case MODULE_DISABLED -> "✕";
            case BUFF_EXPIRED -> "!";
        };

        int fontBold = NanoVGRenderer.getBoldFontId();
        float symW = NanoVGRenderer.getTextWidthWithFont(symbol, 9.5f, fontBold);
        float symX = iconX + (ICON_SIZE - symW) / 2f;
        float symY = iconY + (ICON_SIZE - 9.5f) / 2f + 1f;
        Color symColor = new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(),
                HudGlassmorphism.clampColor((int) (240 * alpha)));
        NanoVGRenderer.drawTextWithFont(symbol, symX, symY, 9.5f, symColor, fontBold);

        // 3. Title & Message text
        float textX = iconX + ICON_SIZE + 7f;
        float titleY = y + 4.5f;

        int poppins = NanoVGRenderer.getPoppinsFontId();
        int regular = NanoVGRenderer.getRegularFontId();

        NanoVGRenderer.drawTextWithFont(
                notification.getTitle(), textX, titleY, TITLE_SIZE,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_PRIMARY, alpha), poppins
        );

        NanoVGRenderer.drawTextWithFont(
                notification.getMessage(), textX, titleY + 11.5f, MESSAGE_SIZE,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_SECONDARY, alpha), regular
        );

        // 4. Progress line at bottom
        float barMaxW = NOTIFICATION_WIDTH - PADDING_X * 2f;
        float lifeRem = MathHelper.clamp(1f - notification.getLifetimeProgress(), 0f, 1f);
        float barW = barMaxW * lifeRem;
        float barY = y + NOTIFICATION_HEIGHT - 3.5f;

        // Inset slot background
        NanoVGRenderer.drawRoundedRect(x + PADDING_X, barY, barMaxW, 1.5f, 1f,
                HudGlassmorphism.applyAlpha(HudGlassmorphism.ROW_FILL, alpha));

        // Active progress fill with gradient to accent color
        if (barW > 1f) {
            Color accent = HudGlassmorphism.getAccent();
            Color pStart = new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(),
                    HudGlassmorphism.clampColor((int) (180 * alpha)));
            Color pEnd = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                    HudGlassmorphism.clampColor((int) (180 * alpha)));
            NanoVGRenderer.drawRoundedRectGradient(x + PADDING_X, barY, barW, 1.5f, 1f, pStart, pEnd);
        }
    }

    private static float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1f - x, 3);
    }
}