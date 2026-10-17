package glen.euphoria.mixin;

import glen.euphoria.utils.render.nanovg.NVGRenderer;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {

    @Shadow
    private long handle;

    @Unique
    private boolean wasUnfocused = false;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", shift = At.Shift.BEFORE))
    private void enableDebugContext(CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onWindowInit(CallbackInfo ci) {
        // Set up focus callback to handle tab-out/tab-in
        GLFW.glfwSetWindowFocusCallback(handle, new GLFWWindowFocusCallback() {
            @Override
            public void invoke(long window, boolean focused) {
                if (focused && wasUnfocused) {
                    // Window regained focus - mark for GL state restoration
                    NVGRenderer.markContextDirty();
                }
                wasUnfocused = !focused;
            }
        });
    }
}