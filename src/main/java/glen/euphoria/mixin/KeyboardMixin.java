package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.Module;
import glen.euphoria.utils.keybinding.KeyUtils;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onPress(long window, int action, net.minecraft.client.input.KeyInput input, CallbackInfo ci) {
        if (glen.euphoria.module.modules.client.SelfDestruct.isDestroyed()) return;
        int key = input.key();
        if (window == this.client.getWindow().getHandle()) {
            if (this.client.currentScreen == null) {
                for (Module module : EuphoriaClient.INSTANCE.moduleManager.getModules()) {
                    if (key == module.getKey()) {
                        if (module.getKeybindSetting().isHoldMode()) {
                            if (action == GLFW.GLFW_PRESS && !module.isEnabled()) {
                                module.setEnabled(true);
                            } else if (action == GLFW.GLFW_RELEASE && module.isEnabled()) {
                                module.setEnabled(false);
                            }
                        } else {
                            if (action == GLFW.GLFW_PRESS) {
                                module.toggle();
                                if (this.client.currentScreen != null) {
                                    ci.cancel();
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
