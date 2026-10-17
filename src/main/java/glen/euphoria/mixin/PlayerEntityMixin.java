package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.misc.Impersonation;
import glen.euphoria.module.modules.movement.KeepSprint;
import glen.euphoria.module.modules.player.FastMine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void modifyBlockBreakingSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        if (EuphoriaClient.INSTANCE == null) return;
        if (EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        Optional<FastMine> optionalModule = EuphoriaClient.INSTANCE.getModuleManager().getModule(FastMine.class);
        if (optionalModule.isEmpty()) return;

        FastMine fastMine = optionalModule.get();
        if (!fastMine.isEnabled()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player != MinecraftClient.getInstance().player) return;

        // Server break time is authoritative; cap multiplier to ≤1.5 to prevent tripping FastBreak
        float vanillaSpeed = cir.getReturnValue();
        float multiplier = Math.min(1.5f, Math.max(1.0f, fastMine.getSpeed()));
        cir.setReturnValue(vanillaSpeed * multiplier);
    }

    // In 1.21.5+ the sprint-attack velocity reset + setSprinting(false) was extracted
    // out of attack() into knockbackTarget(Entity, float, Vec3d). Cancel that method at
    // the point it dampens the player's own velocity so the sprint is preserved.
    @Inject(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"), cancellable = true)
    private void attackInject(Entity target, float f, net.minecraft.util.math.Vec3d vec3d, CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE == null) return;
        if (EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        Optional<KeepSprint> keep = EuphoriaClient.INSTANCE.getModuleManager().getModule(KeepSprint.class);
        if (keep.isEmpty()) return;
        
        KeepSprint keepSprint = keep.get();
        if (keepSprint.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        if (EuphoriaClient.INSTANCE == null) return;
        if (EuphoriaClient.INSTANCE.getModuleManager() == null) return;

        try {
            Optional<Impersonation> optional = EuphoriaClient.INSTANCE.getModuleManager().getModule(Impersonation.class);
            if (optional.isEmpty()) return;
            
            Impersonation module = optional.get();
            if (!module.isEnabled()) return;

            PlayerEntity self = (PlayerEntity) (Object) this;
            UUID uuid = self.getUuid();

            Text overridden = module.getOverriddenDisplayName(uuid, cir.getReturnValue());
            if (overridden != cir.getReturnValue()) {
                cir.setReturnValue(overridden);
            }
        } catch (Exception ignored) {}
    }
}