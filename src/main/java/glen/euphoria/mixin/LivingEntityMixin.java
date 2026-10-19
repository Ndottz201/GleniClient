package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.render.SwingSpeed;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    public void getHandSwingDurationInject(CallbackInfoReturnable<Integer> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.mc == null || EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        var optionalModule = EuphoriaClient.INSTANCE.getModuleManager().getModule(SwingSpeed.class);
        if (optionalModule.isPresent()) {
            SwingSpeed module = optionalModule.get();
            if (module.isEnabled()) {
                cir.setReturnValue(module.getSwingSpeed());
            }
        }
    }
}
