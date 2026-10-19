package glen.euphoria.module.modules.combat;

import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.RangeSetting;

public final class Hitboxes extends Module {
    private static Hitboxes INSTANCE;
    
    public static final RangeSetting expansion = new RangeSetting("Expansion", 0, 2, 0.1, 0.3, 0.01);

    public Hitboxes() {
        super("Hitboxes", "Expands entity hitboxes for attacks", -1, Category.COMBAT);
        addSettings(expansion);
        INSTANCE = this;
    }

    public static Hitboxes getInstance() {
        return INSTANCE;
    }

    public static float getExpansion() {
        if (INSTANCE != null && INSTANCE.isEnabled()) {
            return (float) expansion.getMinValue();
        }
        return 0f;
    }
}
