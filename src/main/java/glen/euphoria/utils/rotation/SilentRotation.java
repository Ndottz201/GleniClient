package glen.euphoria.utils.rotation;

/**
 * Manages silent rotations for combat modules.
 * Silent rotations are applied exclusively inside ClientPlayerEntity.sendMovementPackets()
 * so that outgoing packets reflect the required look angle while the client camera
 * remains completely unaffected on-screen.
 */
public final class SilentRotation {
    private static Float targetPitch = null;
    private static Float targetYaw = null;
    private static Runnable postAction = null;
    private static boolean active = false;

    private SilentRotation() {}

    /**
     * Request a silent rotation to be applied during the next sendMovementPackets().
     *
     * @param pitch Target pitch angle (-90 to 90)
     * @param yaw   Target yaw angle
     * @param onSent Optional callback executed immediately after the movement packet is sent
     */
    public static synchronized void set(float pitch, float yaw, Runnable onSent) {
        targetPitch = pitch;
        targetYaw = yaw;
        postAction = onSent;
        active = true;
    }

    public static synchronized void set(float pitch, float yaw) {
        set(pitch, yaw, null);
    }

    public static synchronized boolean isActive() {
        return active && targetPitch != null;
    }

    public static synchronized Float getPitch() {
        return targetPitch;
    }

    public static synchronized Float getYaw() {
        return targetYaw;
    }

    public static synchronized Runnable getPostAction() {
        return postAction;
    }

    public static synchronized void clear() {
        targetPitch = null;
        targetYaw = null;
        postAction = null;
        active = false;
    }
}
