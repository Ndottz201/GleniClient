package glen.euphoria.utils.rotation;

import net.minecraft.client.MinecraftClient;

public final class RotationUtils {

    private RotationUtils() {}

    /**
     * Calculates Minecraft's mouse GCD based on current mouse sensitivity.
     */
    public static float mouseGcd() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return 0.005f;
        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        double gcd = f * f * f * 1.2; // (f^3 * 8.0) * 0.15 = 1.2 * f^3
        return (float) Math.max(gcd, 0.0001);
    }

    /**
     * Snaps an absolute rotation value to the nearest GCD multiple.
     */
    public static float snap(float target, float gcd) {
        if (gcd <= 0.00001f) return target;
        return Math.round(target / gcd) * gcd;
    }

    /**
     * Snaps rotation delta relative to current angle to preserve GCD multiple across ticks.
     */
    public static float snap(float target, float current, float gcd) {
        if (gcd <= 0.00001f) return target;
        float delta = target - current;
        return current + Math.round(delta / gcd) * gcd;
    }

    /**
     * Snaps yaw rotation delta relative to current angle with proper degree wrapping (-180 to 180).
     */
    public static float snapYaw(float target, float current, float gcd) {
        if (gcd <= 0.00001f) return target;
        float delta = net.minecraft.util.math.MathHelper.wrapDegrees(target - current);
        return current + Math.round(delta / gcd) * gcd;
    }
}
