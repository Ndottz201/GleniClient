package glen.euphoria.mixin;

import glen.euphoria.module.modules.combat.AutoInventoryTotem;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenInputMixin extends AbstractParentElement {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (AutoInventoryTotem.isInputLocked()) {
            return false;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (AutoInventoryTotem.isInputLocked()) {
            return false;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (AutoInventoryTotem.isInputLocked()) {
            return false;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (AutoInventoryTotem.isInputLocked()) {
            return false;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (AutoInventoryTotem.isInputLocked()) {
            return false;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
