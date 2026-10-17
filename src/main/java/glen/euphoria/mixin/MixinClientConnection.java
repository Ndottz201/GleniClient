package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.network.DisconnectEvent;
import glen.euphoria.event.impl.network.PacketEvent;
import glen.euphoria.event.types.TransferOrder;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V"), cancellable = true)
    private static void receivePacketEventInject(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        postPacketEvent(packet, TransferOrder.RECEIVE, ci);
    }

    @Unique
    private static void postPacketEvent(Packet<?> packet, TransferOrder order, CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE == null) return;
        PacketEvent eventPacket = new PacketEvent(packet, order);
        EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(eventPacket);
        if (eventPacket.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendInternal", at = @At("HEAD"), cancellable = true)
    private void sendPacketEventInject(Packet<?> packet, io.netty.channel.ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
        postPacketEvent(packet, TransferOrder.SEND, ci);
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"))
    private void handleDisconnectionInject(CallbackInfo ci) {
        if (EuphoriaClient.INSTANCE != null) {
            EuphoriaClient.INSTANCE.getEuphoriaEventBus().post(new DisconnectEvent());
        }
    }
}
