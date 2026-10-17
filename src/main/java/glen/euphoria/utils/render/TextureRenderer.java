package glen.euphoria.utils.render;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Textured-quad helper.
 *
 * <p>Ported to 1.21.11: the old {@code Tessellator} + {@code BufferRenderer} +
 * {@code POSITION_TEXTURE_COLOR} path (and the {@code GlStateManager} mask tricks)
 * were deleted in the render-pipeline rewrite. 2D textured quads now go through
 * {@link DrawContext#drawTexture} with the {@code GUI_TEXTURED} pipeline, which
 * respects the current 2D GUI matrix (so caller-applied translate/rotate still apply).</p>
 */
public final class TextureRenderer {
    private TextureRenderer() {
    }

    /** Draws a texture centred on the origin of the current GUI matrix, tinted by {@code color} (ARGB). */
    public static void drawCenteredQuad(DrawContext context, Identifier texture, float width, float height, int color) {
        int w = Math.round(width);
        int h = Math.round(height);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, -w / 2, -h / 2, 0f, 0f, w, h, w, h, color);
    }
}
