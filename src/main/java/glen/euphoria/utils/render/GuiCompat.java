package glen.euphoria.utils.render;

import net.minecraft.client.gui.DrawContext;

/**
 * Small GUI compatibility helpers for APIs removed in the 1.21.5+ render rewrite.
 */
public final class GuiCompat {
    private GuiCompat() {}

    /**
     * Replacement for the removed {@code DrawContext#drawBorder}: draws a 1px border
     * by filling the four edge strips.
     */
    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);                       // top
        context.fill(x, y + height - 1, x + width, y + height, color);     // bottom
        context.fill(x, y + 1, x + 1, y + height - 1, color);             // left
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color); // right
    }
}
