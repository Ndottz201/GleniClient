package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.client.SelfDestruct;
import glen.euphoria.module.modules.render.FullBright;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private static void setBrightness(CallbackInfoReturnable<Float> ci) {
        if (SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) return;
        var module = EuphoriaClient.INSTANCE.getModuleManager().getModule(FullBright.class);
        if (module.isPresent() && module.get().isEnabled()) {
            ci.setReturnValue(1.0F);
        }
    }
}