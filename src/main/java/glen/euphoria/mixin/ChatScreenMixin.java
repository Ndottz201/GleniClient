package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.chat.ChatEvent;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        ChatEvent event = new ChatEvent(chatText);
        try {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(event);
        } catch (Throwable t) {
            // A broken ChatEvent handler must not crash the game, and a handler that already
            // cancelled the event before failing must still keep the line off the server.
            EuphoriaClient.INSTANCE.getLogger().error("ChatEvent handler failed", t);
        }

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}