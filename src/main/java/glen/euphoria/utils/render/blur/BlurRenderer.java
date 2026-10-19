package glen.euphoria.utils.render.blur;

import glen.euphoria.utils.render.nanovg.NanoVGRenderer;

import java.awt.Color;

/**
 * Frosted-panel renderer.
 *
 * <p>The original implementation sampled Minecraft's framebuffer through a custom
 * GLSL blur shader bound via {@code ShaderProgramKey} + {@code BufferRenderer} —
 * an API Mojang deleted in the 1.21.5 render-pipeline rewrite. The client already
 * ships a fully integrated NanoVG renderer (independent of that pipeline), so the
 * panels are now drawn as translucent rounded rectangles through NanoVG, preserving
 * the frosted-glass look. All calls must occur inside an active NanoVG frame, which
 * is exactly where the old blur calls already happened.</p>
 */
public final class BlurRenderer {

    private BlurRenderer() {}

    public static void drawBlur(float x, float y, float width, float height,
                                float cornerRadius, Color color, float blurRadius) {
        // Soft outer halo approximates the diffuse glow of the old framebuffer blur.
        if (blurRadius > 0.5f) {
            Color halo = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    Math.max(0, color.getAlpha() / 3));
            NanoVGRenderer.drawRoundedRectWithShadow(x, y, width, height, cornerRadius,
                    color, halo, blurRadius, blurRadius * 0.5f);
        } else {
            NanoVGRenderer.drawRoundedRect(x, y, width, height, cornerRadius, color);
        }
    }

    public static void drawBlurWithCustomRadius(float x, float y, float width, float height,
                                                float[] cornerRadii, Color color, float blurRadius) {
        NanoVGRenderer.drawRoundedRectVarying(x, y, width, height,
                cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3], color);
    }

    public static void drawStyledRect(float x, float y, float width, float height,
                                      float cornerRadius, Color backgroundColor) {
        drawBlur(x, y, width, height, cornerRadius, backgroundColor, 12);
    }

    public static void drawStyledRectEx(float x, float y, float width, float height,
                                        float[] cornerRadii, Color backgroundColor, float blurRadius) {
        drawBlurWithCustomRadius(x, y, width, height, cornerRadii, backgroundColor, blurRadius);
    }

    public static void drawSimpleBlur(float x, float y, float width, float height) {
        drawBlur(x, y, width, height, 0, new Color(255, 255, 255, 60), 10);
    }

    public static void drawRoundedBlur(float x, float y, float width, float height, float cornerRadius) {
        drawBlur(x, y, width, height, cornerRadius, new Color(255, 255, 255, 60), 12);
    }
}
