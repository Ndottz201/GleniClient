package glen.euphoria.module.modules.client;

import glen.euphoria.gui.newgui.NewClickGUI;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.ModeSetting;
import glen.euphoria.gui.theme.ThemeManager;
import org.lwjgl.glfw.GLFW;

public final class ClickGUIModule extends Module {
    public static final ModeSetting theme = new ModeSetting("Theme", ThemeManager.getDefaultName(),
            ThemeManager.getThemeNamesArray());

    public ClickGUIModule() {
        super("Click Gui", "Toggles the Euphoria GUI", GLFW.GLFW_KEY_RIGHT_SHIFT, Category.CLIENT);
        addSettings(theme);
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen == null) {
            mc.setScreen(new NewClickGUI());
        }
        setEnabled(false);
    }
}
