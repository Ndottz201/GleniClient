package glen.euphoria.module.modules.client;

import glen.euphoria.event.impl.network.PacketEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.utils.mc.ChatUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;

public class Debugger extends Module {
    public Debugger() {
        super("Debugger", "Debugs inv packets (dev purposes)", -1, Category.CLIENT);
    }

    @EventHandler
    public void onPacketSend(PacketEvent e) {
        if (isNull()) return;
        if (e.getPacket() == null) return;
        if (!(e.getPacket() instanceof ClickSlotC2SPacket packet))
            return;

        ChatUtil.addChatMessage("""
                ClickSlotPacket
                  syncId: %s
                  revision: %s
                  slot: %s
                  button: %s
                  actionType: %s
                  modifiedItems: %s
                  stack: %s
                """.formatted(
                packet.syncId(),
                packet.revision(),
                packet.slot(),
                packet.button(),
                packet.actionType(),
                packet.modifiedStacks(),
                packet.cursor()
        ));
    }
}
