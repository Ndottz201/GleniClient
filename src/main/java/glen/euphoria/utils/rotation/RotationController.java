package glen.euphoria.utils.rotation;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class RotationController {
    private static Float yaw, pitch;
    private static int priority = -1;

    private RotationController() {}

    // modules call this; highest priority wins the tick
    public static void request(float y, float p, int prio) {
        if (prio >= priority) {
            yaw = y;
            pitch = p;
            priority = prio;
        }
    }

    // call ONCE at END_CLIENT_TICK, after all modules have requested
    public static void apply(ClientPlayerEntity player) {
        if (yaw == null || pitch == null || player == null) {
            priority = -1;
            yaw = null;
            pitch = null;
            return;
        }
        float gcd = RotationUtils.mouseGcd();
        player.setYaw(RotationUtils.snap(yaw, player.getYaw(), gcd));
        float snappedPitch = RotationUtils.snap(pitch, player.getPitch(), gcd);
        player.setPitch(MathHelper.clamp(snappedPitch, -89.9f, 89.9f));
        yaw = null;
        pitch = null;
        priority = -1;
    }

    public static void reset() {
        yaw = null;
        pitch = null;
        priority = -1;
    }
}
