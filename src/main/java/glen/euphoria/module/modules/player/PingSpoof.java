package glen.euphoria.module.modules.player;

import glen.euphoria.event.impl.network.DisconnectEvent;
import glen.euphoria.event.impl.network.PacketEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.event.types.TransferOrder;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PingSpoof extends Module {
    private final NumberSetting msDelay = new NumberSetting("Ms", 1, 500, 60, 1);

    private final Queue<DelayedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final Set<Packet<?>> sending = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private ScheduledExecutorService executor;

    public PingSpoof() {
        super("Ping Spoof", "Simulates higher network latency", -1, Category.PLAYER);
        this.addSetting(msDelay);
    }

    @Override
    public void onEnable() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PingSpoof-Scheduler");
                t.setDaemon(true);
                return t;
            });
        }
    }

    @Override
    public void onDisable() {
        flushAllPackets();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @EventHandler
    private void onDisconnect(DisconnectEvent event) {
        packetQueue.clear();
        sending.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent event) {
        if (isNull()) return;

        if (event.getOrder() == TransferOrder.SEND) {
            Packet<?> packet = event.getPacket();
            if (sending.remove(packet)) {
                return;
            }

            if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket) {
                event.cancel();
                long delay = msDelay.getValueInt();
                long sendTime = System.currentTimeMillis() + delay;
                packetQueue.add(new DelayedPacket(packet, sendTime));

                if (executor != null && !executor.isShutdown()) {
                    executor.schedule(() -> {
                        if (mc != null) {
                            mc.execute(this::flushReadyPackets);
                        }
                    }, delay, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull()) return;
        flushReadyPackets();
    }

    private void flushReadyPackets() {
        if (packetQueue.isEmpty() || mc.getNetworkHandler() == null) return;
        var connection = mc.getNetworkHandler().getConnection();
        if (connection == null) return;

        long now = System.currentTimeMillis();
        while (!packetQueue.isEmpty()) {
            DelayedPacket head = packetQueue.peek();
            if (head == null || head.sendTime > now) {
                break;
            }
            packetQueue.poll();
            sending.add(head.packet);
            connection.send(head.packet);
        }
    }

    private void flushAllPackets() {
        if (packetQueue.isEmpty()) return;
        var connection = (mc != null && mc.getNetworkHandler() != null) ? mc.getNetworkHandler().getConnection() : null;
        while (!packetQueue.isEmpty()) {
            DelayedPacket dp = packetQueue.poll();
            if (dp != null && connection != null) {
                sending.add(dp.packet);
                connection.send(dp.packet);
            }
        }
    }

    private record DelayedPacket(Packet<?> packet, long sendTime) {}
}
