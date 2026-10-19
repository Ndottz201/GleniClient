package glen.euphoria.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    // 1.21.5+ has no persistent WorldRenderer.frustum field anymore; capture the live
    // culling frustum each frame so modules (ESP/Nametags) can keep doing visibility checks.
    @Inject(method = "applyFrustum", at = @At("HEAD"))
    private void euphoria$captureFrustum(net.minecraft.client.render.Frustum frustum, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        glen.euphoria.utils.render.W2SUtil.frustum = frustum;
    }
}