package glen.euphoria.module.modules.client;

import glen.euphoria.gui.newgui.NewClickGUI;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.ColorSetting;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class ClientSettingsModule extends Module {

    public static final ColorSetting guiColor = new ColorSetting("GUI Color", new Color(124, 77, 255, 255));

    public ClientSettingsModule() {
        super("Settings", "Client settings and GUI appearance", GLFW.GLFW_KEY_RIGHT_SHIFT, Category.CLIENT);
        this.addSettings(guiColor);
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen == null) {
            mc.setScreen(new NewClickGUI());
        }
        setEnabled(false);
    }

    @Override
    public int getKey() {
        int k = super.getKey();
        return (k == 0 || k == -1) ? GLFW.GLFW_KEY_RIGHT_SHIFT : k;
    }

    @Override
    public glen.euphoria.module.setting.KeybindSetting getKeybindSetting() {
        glen.euphoria.module.setting.KeybindSetting kb = super.getKeybindSetting();
        if (kb != null && (kb.getKeyCode() == 0 || kb.getKeyCode() == -1)) {
            kb.setKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT);
        }
        return kb;
    }

    public static Color getGuiColor() {
        return guiColor.getValue();
    }

    public static Color getAccentColor() {
        return guiColor.getValue();
    }

    public static String getFontStyle() {
        return "Inter.ttf";
    }

    public static boolean isScrollable() {
        return false;
    }

    public static float getGuiTransparency() {
        return 0f;
    }

    public static boolean isGuiBlurEnabled() {
        return true;
    }

    public static boolean isPanelBlurEnabled() {
        return true;
    }

    public static float getPanelBlurRadius() {
        return 12f;
    }

    public static boolean isModuleDescriptionsEnabled() {
        return true;
    }

    public static float getToggleWidth() {
        return 20f;
    }

    public static float getToggleHeight() {
        return 10f;
    }

    public static float getSliderHeight() {
        return 4f;
    }

    public static float getSliderHandleSize() {
        return 5f;
    }

    public static boolean isGuiGlowEnabled() {
        return false;
    }

    public static Color getGlowColor() {
        return guiColor.getValue();
    }

    public static float getGlowIntensity() {
        return 1f;
    }

    public static float getGlowThickness() {
        return 5f;
    }

    public static float getBloomRadius() {
        return 10f;
    }

    public static boolean isAutoFocusSearchEnabled() {
        return true;
    }

    public static boolean isSnowEffectEnabled() {
        return false;
    }

    @Deprecated
    public static int getGuiScale() {
        return 1;
    }

    public static float getGuiScaleFactor() {
        return 1.0f;
    }

    public static boolean isAutoSaveEnabled() {
        return false;
    }

    public static boolean getTitle() {
        return true;
    }
}