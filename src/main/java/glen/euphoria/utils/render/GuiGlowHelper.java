package glen.euphoria.utils.render;

import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

/**
 * GUI glow/bloom helper.
 *
 * <p>The original {@code GlowRenderer} used a custom GLSL bloom shader bound through
 * raw GL (VAO + {@code RenderSystem.blendFuncSeparate}) — deleted by the 1.21.5 render
 * rewrite. Glow is now drawn through NanoVG's feathered box-shadow, which gives an
 * equivalent halo and is independent of Mojang's pipeline. All calls happen inside the
 * NanoVG frame the GUI screens already open.</p>
 */
public class GuiGlowHelper {

    private static void glow(float x, float y, float width, float height, float radius,
                             Color color, float intensity, float thickness, float bloomRadius) {
        if (color == null) return;
        int a = Math.max(0, Math.min(255, (int) (color.getAlpha() * Math.max(0.15f, intensity))));
        Color halo = new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
        Color transparentFill = new Color(0, 0, 0, 0);
        float blur = Math.max(1f, bloomRadius);
        float spread = Math.max(1f, thickness);
        NanoVGRenderer.drawRoundedRectWithShadow(x, y, width, height, radius, transparentFill, halo, blur, spread);
    }

    public static void drawGuiGlow(DrawContext context, float x, float y, float width, float height, float radius) {
        if (!ClientSettingsModule.isGuiGlowEnabled()) return;
        glow(x, y, width, height, radius, ClientSettingsModule.getGlowColor(),
                ClientSettingsModule.getGlowIntensity(), ClientSettingsModule.getGlowThickness(),
                ClientSettingsModule.getBloomRadius());
    }

    public static void drawGuiGlow(DrawContext context, float x, float y, float width, float height,
                                   float radius, Color customColor) {
        if (!ClientSettingsModule.isGuiGlowEnabled()) return;
        glow(x, y, width, height, radius, customColor,
                ClientSettingsModule.getGlowIntensity(), ClientSettingsModule.getGlowThickness(),
                ClientSettingsModule.getBloomRadius());
    }

    public static void drawCustomGlow(DrawContext context, float x, float y, float width, float height,
                                      float radius, Color color, float intensity,
                                      float thickness, float bloomRadius) {
        glow(x, y, width, height, radius, color, intensity, thickness, bloomRadius);
    }

    public static void drawPulsingGuiGlow(DrawContext context, float x, float y, float width, float height,
                                          float radius) {
        if (!ClientSettingsModule.isGuiGlowEnabled()) return;
        double time = System.currentTimeMillis() / 1000.0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.0);
        glow(x, y, width, height, radius, ClientSettingsModule.getGlowColor(),
                0.4f + pulse * 0.6f, ClientSettingsModule.getGlowThickness(),
                ClientSettingsModule.getBloomRadius());
    }

    public static void drawIntenseGuiGlow(DrawContext context, float x, float y, float width, float height,
                                          float radius) {
        if (!ClientSettingsModule.isGuiGlowEnabled()) return;
        glow(x, y, width, height, radius, ClientSettingsModule.getGlowColor(), 1.0f, 10f, 18f);
    }

    public static void drawSubtleGlow(DrawContext context, float x, float y, float width, float height,
                                      float radius, Color color) {
        glow(x, y, width, height, radius, color, 0.5f, 6.0f, 10.0f);
    }

    public static void drawPanelGlow(DrawContext context, float x, float y, float width, float height) {
        drawGuiGlow(context, x, y, width, height, 8.0f);
    }

    public static void drawButtonGlow(DrawContext context, float x, float y, float width, float height,
                                      boolean hovered) {
        if (hovered) {
            drawGuiGlow(context, x, y, width, height, 4.0f);
        } else {
            drawSubtleGlow(context, x, y, width, height, 4.0f, ClientSettingsModule.getGlowColor());
        }
    }
}
