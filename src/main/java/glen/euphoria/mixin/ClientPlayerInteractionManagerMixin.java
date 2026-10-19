package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.player.AttackEvent;
import glen.euphoria.module.modules.combat.AutoCrystal;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void attackEntityInject(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        AttackEvent event = new AttackEvent(target);
        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Prevents starting to break a block when holding crystal/obsidian.
     */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) return;
        EuphoriaClient.INSTANCE.getModuleManager().getModule(AutoCrystal.class).ifPresent(autoCrystal -> {
            if (autoCrystal.shouldCancelBlockBreaking()) {
                cir.setReturnValue(false);
            }
        });
    }

    /**
     * Prevents continuing to break a block (holding left click) when holding crystal/obsidian.
     */
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) return;
        EuphoriaClient.INSTANCE.getModuleManager().getModule(AutoCrystal.class).ifPresent(autoCrystal -> {
            if (autoCrystal.shouldCancelBlockBreaking()) {
                cir.setReturnValue(false);
            }
        });
    }
}