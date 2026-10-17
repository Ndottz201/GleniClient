package glen.euphoria.mixin;

import glen.euphoria.module.modules.render.CustomOutlineESP;
import glen.euphoria.utils.render.OutlinePostProcessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private Framebuffer entityOutlineFramebuffer;

    // 1.21.5+ has no persistent WorldRenderer.frustum field anymore; capture the live
    // culling frustum each frame so modules (ESP/Nametags) can keep doing visibility checks.
    @Inject(method = "applyFrustum", at = @At("HEAD"))
    private void euphoria$captureFrustum(net.minecraft.client.render.Frustum frustum, CallbackInfo ci) {
        glen.euphoria.utils.render.W2SUtil.frustum = frustum;
    }

    @Inject(method = "drawEntityOutlinesFramebuffer", at = @At("HEAD"), cancellable = true)
    private void onDrawEntityOutlines(CallbackInfo ci) {
        CustomOutlineESP outlineESP = CustomOutlineESP.getInstance();
        if (outlineESP == null || !outlineESP.isEnabled()) return;
        if (entityOutlineFramebuffer == null) return;

        outlineESP.updateTargets();

        Color color = outlineESP.getTargetEntities().stream()
                .findFirst()
                .map(outlineESP::getColorForEntity)
                .orElse(new Color(255, 50, 50));

        OutlinePostProcessor.process(
                entityOutlineFramebuffer,
                color,
                outlineESP.getOutlineWidth(),
                outlineESP.getIntensity(),
                outlineESP.shouldShowFill(),
                outlineESP.getFillAlpha()
        );

        ci.cancel();
    }
}