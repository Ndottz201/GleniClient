package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the "content" HUD pass (NanoVG overlays such as ArrayList/Watermark/Notifications) right
 * before the main framebuffer is presented, so they sit ON TOP of the vanilla HUD.
 *
 * In the 1.21.5+ pipeline the vanilla HUD is composited into the main framebuffer after
 * InGameHud.render returns (DrawContext has no manual flush), so anything NanoVG draws at the
 * InGameHud hook lands underneath. blitToScreen runs after that composite and before the present,
 * with the world+HUD already in the main color attachment — the right moment to draw on top.
 * DrawContext-based and world overlays stay on the InGameHud pass (see InGameHudMixin) where a live
 * DrawContext exists; they gate on {@link Render2DEvent#isContentPass()} so nothing double-renders.
 */
@Mixin(Framebuffer.class)
public class FramebufferMixin {

    @Inject(method = "blitToScreen", at = @At("HEAD"))
    private void euphoria$renderHudOnTop(CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        // Only the main render target, not other framebuffers that also blit.
        if (mc.getFramebuffer() != (Object) this) return;
        if (EuphoriaClient.INSTANCE == null || mc.getWindow() == null) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        if (glen.euphoria.module.modules.client.ClientSettingsModule.isPanelBlurEnabled()) {
            glen.euphoria.utils.render.blur.BackdropBlur.render(
                    NanoVGRenderer.getContext(),
                    mc.getWindow().getFramebufferWidth(),
                    mc.getWindow().getFramebufferHeight(),
                    Math.max(1.5f, glen.euphoria.module.modules.client.ClientSettingsModule.getPanelBlurRadius() / 4f));
        }

        NanoVGRenderer.beginFrame();
        try {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new Render2DEvent(null, w, h, true));
        } finally {
            NanoVGRenderer.endFrame();
        }
    }
}
