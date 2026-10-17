package glen.euphoria.mixin;

import glen.euphoria.module.modules.combat.AutoInventoryTotem;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseInputMixin {

    /**
     * Block all mouse movement while input is locked.
     */
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void onMouseMove(long window, double x, double y, CallbackInfo ci) {
        if (AutoInventoryTotem.isInputLocked() || (System.currentTimeMillis() - AutoInventoryTotem.getLastUnlockTime() < 100L)) {
            ci.cancel();
        }
    }

    /**
     * Block all mouse button clicks while input is locked.
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, net.minecraft.client.input.MouseInput mouseInput, int action, CallbackInfo ci) {
        if (AutoInventoryTotem.isInputLocked()) {
            ci.cancel();
        }
    }

    /**
     * Block mouse scroll while input is locked.
     */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (AutoInventoryTotem.isInputLocked()) {
            ci.cancel();
        }
    }
}