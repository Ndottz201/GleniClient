package glen.euphoria.event.impl.network;

import glen.euphoria.event.types.CancellableEvent;
import glen.euphoria.event.types.TransferOrder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.packet.Packet;


@Getter
@Setter
public class PacketEvent extends CancellableEvent {
    private final TransferOrder order;
    private Packet packet;

    public PacketEvent(Packet packet, TransferOrder order) {
        this.packet = packet;
        this.order = order;
    }

}