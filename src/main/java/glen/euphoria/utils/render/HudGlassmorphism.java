package glen.euphoria.utils.render;

import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.utils.render.blur.BackdropBlur;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.MinecraftClient;

import java.awt.Color;

/**
 * Shared glassmorphism aesthetic styling helper that mirrors NewClickGUI's exact visual ecosystem:
 * frosted backdrop blur, dark glass gradient fill, cyan/blue tint overlay, dual borders (outer accent + inner specular highlight).
 */
public final class HudGlassmorphism {

    public static final Color SHELL_FILL_TOP = new Color(8, 14, 28, 138);
    public static final Color SHELL_FILL_BOTTOM = new Color(6, 11, 22, 128);
    public static final Color SHELL_TINT = new Color(18, 46, 96, 38);
    public static final Color SHELL_INNER = new Color(255, 255, 255, 11);
    public static final Color SHELL_DIVIDER = new Color(255, 255, 255, 10);

    public static final Color ROW_FILL = new Color(12, 20, 36, 154);
    public static final Color ROW_FILL_ALT = new Color(14, 24, 43, 176);
    public static final Color SEARCH_FILL = new Color(10, 18, 34, 170);

    public static final Color TEXT_PRIMARY = new Color(242, 247, 255, 255);
    public static final Color TEXT_SECONDARY = new Color(160, 180, 206, 255);
    public static final Color TEXT_MUTED = new Color(108, 128, 154, 255);

    private HudGlassmorphism() {}

    public static Color getAccent() {
        return ClientSettingsModule.getAccentColor();
    }

    public static Color applyAlpha(Color c, float alpha) {
        if (c == null) return new Color(0, 0, 0, 0);
        int a = clampColor((int) (c.getAlpha() * Math.max(0f, Math.min(1f, alpha))));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    public static int clampColor(int val) {
        return Math.max(0, Math.min(255, val));
    }

    /**
     * Draws a frosted glass card matching NewClickGUI's exact visual style.
     */
    public static void drawCard(float x, float y, float width, float height, float radius, float alpha) {
        drawCard(x, y, width, height, radius, alpha, getAccent());
    }

    /**
     * Draws a frosted glass card with a custom accent color for the border.
     */
    public static void drawCard(float x, float y, float width, float height, float radius, float alpha, Color accentBorder) {
        if (alpha <= 0.01f || width <= 1f || height <= 1f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 1920;
        int screenH = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledHeight() : 1080;

        // 1. Real backdrop blur sampled from world (matches NewClickGUI backdrop exactly)
        int blurImg = BackdropBlur.getCurrentImage();
        if (blurImg > 0 && ClientSettingsModule.isPanelBlurEnabled()) {
            NanoVGRenderer.drawImagePatternRounded(blurImg, x, y, width, height, radius, 0f, 0f, screenW, screenH, alpha);
        }

        // 2. Frosted glass gradient (matches NewClickGUI SHELL_FILL_TOP -> SHELL_FILL_BOTTOM)
        NanoVGRenderer.drawRoundedRectGradient(x, y, width, height, radius,
                applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));

        // 3. Subtle blue/cyan glass tint (matches NewClickGUI SHELL_TINT)
        NanoVGRenderer.drawRoundedRect(x, y, width, height, radius,
                applyAlpha(SHELL_TINT, alpha));

        // 4. Outer border with accent color (matches NewClickGUI)
        Color border = accentBorder != null ? accentBorder : getAccent();
        NanoVGRenderer.drawRoundedRectOutline(
                x, y, width, height, radius, 1f,
                new Color(border.getRed(), border.getGreen(), border.getBlue(), clampColor((int) (98 * alpha)))
        );

        // 5. Inner specular highlight border (matches NewClickGUI)
        if (radius >= 1f && width > 2f && height > 2f) {
            NanoVGRenderer.drawRoundedRectOutline(
                    x + 1f, y + 1f, width - 2f, height - 2f, Math.max(0f, radius - 1f), 1f,
                    applyAlpha(SHELL_INNER, alpha)
            );
        }

        // 6. GUI Bloom/Glow ONLY if explicitly enabled in settings
        if (ClientSettingsModule.isGuiGlowEnabled()) {
            GuiGlowHelper.drawGuiGlow(null, x, y, width, height, radius);
        }
    }

    /**
     * Draws a frosted glass card with varying corner radii.
     */
    public static void drawCardVarying(float x, float y, float width, float height,
                                       float rtl, float rtr, float rbr, float rbl, float alpha) {
        if (alpha <= 0.01f || width <= 1f || height <= 1f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 1920;
        int screenH = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledHeight() : 1080;

        // 1. Real backdrop blur
        int blurImg = BackdropBlur.getCurrentImage();
        if (blurImg > 0 && ClientSettingsModule.isPanelBlurEnabled()) {
            NanoVGRenderer.drawImagePatternRoundedVarying(blurImg, x, y, width, height, rtl, rtr, rbr, rbl, 0f, 0f, screenW, screenH, alpha);
        }

        // 2. Frosted glass gradient
        NanoVGRenderer.drawRoundedRectGradientVarying(x, y, width, height, rtl, rtr, rbr, rbl,
                applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));

        // 3. Subtle blue glass tint
        NanoVGRenderer.drawRoundedRectVarying(x, y, width, height, rtl, rtr, rbr, rbl,
                applyAlpha(SHELL_TINT, alpha));

        // 4. Outer accent border
        Color accent = getAccent();
        NanoVGRenderer.drawRoundedRectOutlineVarying(x, y, width, height, rtl, rtr, rbr, rbl, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (98 * alpha))));

        // 5. Inner specular highlight
        NanoVGRenderer.drawRoundedRectOutlineVarying(x + 1f, y + 1f, width - 2f, height - 2f,
                Math.max(0f, rtl - 1f), Math.max(0f, rtr - 1f), Math.max(0f, rbr - 1f), Math.max(0f, rbl - 1f),
                1f, applyAlpha(SHELL_INNER, alpha));
    }

    /**
     * Draws an inset slot / sub-card (for item slots, avatar frames, badge tags).
     */
    public static void drawSlot(float x, float y, float width, float height, float radius, float alpha) {
        NanoVGRenderer.drawRoundedRect(x, y, width, height, radius, applyAlpha(ROW_FILL, alpha));
        NanoVGRenderer.drawRoundedRectOutline(x, y, width, height, radius, 1f, applyAlpha(SHELL_INNER, alpha));
    }

    /**
     * Draws a continuous custom-path frosted glass platform (e.g. for connected smoothed ArrayList).
     */
    public static void drawPlatform(float minX, float minY, float maxX, float maxY, float alpha, Runnable pathBuilder) {
        if (alpha <= 0.01f || maxX <= minX || maxY <= minY) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 1920;
        int screenH = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledHeight() : 1080;

        // 1. Real backdrop blur
        int blurImg = BackdropBlur.getCurrentImage();
        if (blurImg > 0 && ClientSettingsModule.isPanelBlurEnabled()) {
            pathBuilder.run();
            NanoVGRenderer.fillPattern(blurImg, 0f, 0f, screenW, screenH, alpha);
        }

        // 2. Frosted glass gradient
        pathBuilder.run();
        NanoVGRenderer.fillGradient(minX, minY, minX, maxY,
                applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));

        // 3. Subtle blue glass tint
        pathBuilder.run();
        NanoVGRenderer.fillColor(applyAlpha(SHELL_TINT, alpha));

        // 4. Outer border
        Color accent = getAccent();
        pathBuilder.run();
        NanoVGRenderer.strokeColor(
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (98 * alpha))),
                1f
        );

        // 5. Specular highlight inner stroke
        pathBuilder.run();
        NanoVGRenderer.strokeColor(applyAlpha(SHELL_INNER, alpha), 1f);
    }
}
