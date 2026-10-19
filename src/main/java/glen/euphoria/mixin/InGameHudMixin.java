package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.gui.newgui.NewClickGUI;
import glen.euphoria.utils.render.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At(value = "TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        RenderUtils.unscaledProjection();
        RenderUtils.scaledProjection();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null && glen.euphoria.module.modules.client.ClientSettingsModule.isPanelBlurEnabled()) {
            glen.euphoria.utils.render.blur.BackdropBlur.render(
                    glen.euphoria.utils.render.nanovg.NanoVGRenderer.getContext(),
                    mc.getWindow().getFramebufferWidth(),
                    mc.getWindow().getFramebufferHeight(),
                    Math.max(1.5f, glen.euphoria.module.modules.client.ClientSettingsModule.getPanelBlurRadius() / 4f));
        }

        // GL state is now owned by NanoVG (its own context) and the render pipeline;
        // the old RenderSystem.enable/disable* immediate calls were removed in 1.21.5.
        glen.euphoria.utils.render.nanovg.NanoVGRenderer.beginFrame();

        EuphoriaClient.INSTANCE.getEuphoriaEventBus()
                .post(new Render2DEvent(context, context.getScaledWindowWidth(), context.getScaledWindowHeight()));

        glen.euphoria.utils.render.nanovg.NanoVGRenderer.endFrame();
    }

    // Hide the vanilla scoreboard sidebar while the ClickGUI is open.
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true)
    private void euphoria$hideScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (MinecraftClient.getInstance().currentScreen instanceof NewClickGUI) {
            ci.cancel();
        }
    }

    // Hide the vanilla crosshair while the ClickGUI is open.
    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true)
    private void euphoria$hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (MinecraftClient.getInstance().currentScreen instanceof NewClickGUI) {
            ci.cancel();
        }
    }
}
