package glen.euphoria.module.modules.player;

import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;

public class FastMine extends Module {

    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 1.5, 1.0, 0.1);

    public FastMine() {
        super("Fast Mine", "Breaks blocks faster than normal", -1, Category.PLAYER);
        addSettings(speed);
    }

    public float getSpeed() {
        return speed.getValueFloat();
    }
}
