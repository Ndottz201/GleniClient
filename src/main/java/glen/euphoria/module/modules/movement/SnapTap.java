package glen.euphoria.module.modules.movement;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import meteordevelopment.orbit.EventHandler;

public final class SnapTap extends Module {

    public static long LEFT_STRAFE_LAST_PRESS_TIME = 0;
    public static long RIGHT_STRAFE_LAST_PRESS_TIME = 0;

    public static long FORWARD_STRAFE_LAST_PRESS_TIME = 0;
    public static long BACKWARD_STRAFE_LAST_PRESS_TIME = 0;


    public SnapTap() {
        super("Snap Tap", "Prioritizes the last pressed movement key like Wooting keyboards", -1, Category.MOVEMENT);
        this.addSettings();
    }
}
