package glen.euphoria.module.modules.render;

import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;

public class AspectRatio extends Module {

    private static AspectRatio INSTANCE;

    private static final NumberSetting aspectRatio = new NumberSetting("Ratio", 0.5, 3.0, 1.78, 0.01);

    public AspectRatio() {
        super("Aspect Ratio", "Changes the game's aspect ratio", Category.RENDER);
        this.addSettings(aspectRatio);
        INSTANCE = this;
    }

    public static AspectRatio getInstance() {
        return INSTANCE;
    }

    public static float getAspectRatio() {
        if (INSTANCE != null && INSTANCE.isEnabled()) {
            return (float) aspectRatio.getValue();
        }
        return -1;
    }

    public static boolean isAspectRatioEnabled() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }
}
