package glen.euphoria.utils.mc;

import net.minecraft.client.MinecraftClient;

/**
 * Global "one selected-slot change per client tick" guard.
 *
 * Any module that changes the selected hotbar slot must claim before doing so.
 * Prevents two modules from emitting stacked UpdateSelectedSlotC2SPackets in one
 * tick (which reads as a fast-switch / packet-dup to strict anticheats).
 *
 * Reset is tied to player.age, so it needs no event-ordering dependency.
 */
public final class SlotGuard {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static int lastTick = -1;
    private static boolean claimed = false;

    private SlotGuard() {}

    /** @return true if the caller may change the selected slot this tick. */
    public static boolean tryClaim() {
        int tick = currentTick();
        if (tick != lastTick) {
            lastTick = tick;
            claimed = false;
        }
        if (claimed) return false;
        claimed = true;
        return true;
    }

    /** Release an unused claim (if you claimed then decided not to swap). */
    public static void release() {
        claimed = false;
    }

    private static int currentTick() {
        if (mc.player != null) return mc.player.age;
        return (int) (System.currentTimeMillis() / 50L);
    }
}
