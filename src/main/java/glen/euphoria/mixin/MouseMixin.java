package glen.euphoria.mixin;
import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.input.MouseClickEvent;
import glen.euphoria.module.Module;
import glen.euphoria.module.modules.combat.AutoInventoryTotem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, net.minecraft.client.input.MouseInput mouseInput, int action, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        int button = mouseInput.button();
        int mods = mouseInput.modifiers();
        // Block ALL mouse clicks while AutoInventoryTotem has the inventory open
        if (AutoInventoryTotem.isInputLocked()) {
            ci.cancel();
            return;
        }

        if (EuphoriaClient.INSTANCE == null) return;
        if (window != client.getWindow().getHandle()) return;
        if (client.currentScreen != null) return;

        // Activate module keybinds bound to mouse buttons (stored as -100-button). KeyboardMixin
        // only covers keyboard keys, so toggle/hold for mouse binds is handled here.
        int mouseKey = -100 - button;
        for (Module module : EuphoriaClient.INSTANCE.moduleManager.getModules()) {
            if (module.getKeybindSetting() == null || mouseKey != module.getKey()) continue;
            if (module.getKeybindSetting().isHoldMode()) {
                if (action == GLFW.GLFW_PRESS && !module.isEnabled()) {
                    module.setEnabled(true);
                } else if (action == GLFW.GLFW_RELEASE && module.isEnabled()) {
                    module.setEnabled(false);
                }
            } else if (action == GLFW.GLFW_PRESS) {
                module.toggle();
            }
        }

        MouseClickEvent event = new MouseClickEvent(button, action, mods);
        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        // Block real mouse movement while AutoInventoryTotem has the inventory open or right after closing
        if (AutoInventoryTotem.isInputLocked() || (System.currentTimeMillis() - AutoInventoryTotem.getLastUnlockTime() < 100L)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockMouseUpdate(double timeDelta, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        if (AutoInventoryTotem.isInputLocked() || client.currentScreen != null || (System.currentTimeMillis() - AutoInventoryTotem.getLastUnlockTime() < 100L)) {
            ci.cancel();
        }
    }
}