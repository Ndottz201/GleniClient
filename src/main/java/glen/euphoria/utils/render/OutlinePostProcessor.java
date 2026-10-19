package glen.euphoria.utils.render;

import net.minecraft.client.gl.Framebuffer;

import java.awt.*;

/**
 * Entity-outline bloom post-processor.
 *
 * <p>The original implementation ran a bespoke GLSL bloom pass via raw GL (custom VAO/VBO,
 * a {@code SimpleFramebuffer} ping-pong and {@code GpuTexture} binds) — all of which the
 * 1.21.5 render-pipeline rewrite removed. Faithfully re-creating a full-screen post-effect
 * on the new {@code GpuDevice}/{@code RenderPass} system is a large standalone task, so this
 * is currently a no-op shim: it keeps the public API and compiles cleanly. The standard
 * {@code OutlineESP} (which drives vanilla entity glow through the {@code Entity.isGlowing}
 * mixin) is unaffected and still renders outlines.</p>
 *
 * <p>TODO (post-port polish): reimplement the bloom as a {@code RenderPipeline}-based
 * post-effect, or route {@code CustomOutlineESP} through the vanilla glow path.</p>
 */
public class OutlinePostProcessor {

    public static void init() {
        // no-op: custom GL bloom pipeline removed in 1.21.5
    }

    public static void process(Framebuffer outlineBuffer, Color color, float width, float intensity) {
        // no-op shim — see class docs
    }

    public static void process(Framebuffer outlineBuffer, Color color, float width, float intensity,
                               boolean showFill, float fillAlpha) {
        // no-op shim — see class docs
    }

    public static void cleanup() {
        // no-op
    }
}
