package glen.euphoria.mixin;

import glen.euphoria.module.modules.combat.AutoInventoryTotem;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    // ─── AutoInventoryTotem input blocking ───

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockKeysWhenLocked(net.minecraft.client.input.KeyInput input,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockClicksWhenLocked(net.minecraft.client.gui.Click click, boolean doubled,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockReleaseWhenLocked(net.minecraft.client.gui.Click click,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(true);
        }
    }
}