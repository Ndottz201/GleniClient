package glen.euphoria.event.impl.render;

import glen.euphoria.event.types.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;

@Getter
@Setter
public class Render2DEvent implements Event {
    private DrawContext context;
    private int width;
    private int height;
    /**
     * True for the late pass rendered on top of the vanilla HUD (at Framebuffer.blitToScreen),
     * false for the in-HUD pass (at InGameHud.render TAIL). NanoVG-only HUD overlays render in the
     * content pass so they sit above the hotbar; DrawContext/world overlays render in the HUD pass.
     */
    private boolean contentPass;

    public Render2DEvent(DrawContext context, int width, int height) {
        this(context, width, height, false);
    }

    public Render2DEvent(DrawContext context, int width, int height, boolean contentPass) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.contentPass = contentPass;
    }

}
