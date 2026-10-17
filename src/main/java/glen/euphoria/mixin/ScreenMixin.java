package glen.euphoria.mixin;

import glen.euphoria.gui.ClickGui;
import glen.euphoria.gui.newgui.NewClickGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow
    @Nullable
    protected MinecraftClient client;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void renderBackgroundInject(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (client == null) return;
        Screen currentScreen = client.currentScreen;
        if (currentScreen instanceof ClickGui) {
            ci.cancel();
            return;
        }
        if (currentScreen instanceof NewClickGUI && !glen.euphoria.module.modules.client.ClientSettingsModule.isGuiBlurEnabled()) {
            ci.cancel();
        }
    }
}
