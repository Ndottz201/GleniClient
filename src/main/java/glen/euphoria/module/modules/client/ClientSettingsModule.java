package glen.euphoria.module.modules.client;

import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.*;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class ClientSettingsModule extends Module {
    public static final ColorSetting accentColor = new ColorSetting("Accent Color", new Color(124, 77, 255, 255));
    public static final ModeSetting fontStyle = new ModeSetting("Font", "Inter.ttf", "Inter.ttf", "jetbrainsmono.ttf",
            "poppins-medium.ttf", "Monaco.ttf");
    public static final BooleanSetting scrollableCategories = new BooleanSetting("Scrollable Categories", false);
    public static final NumberSetting guiScale = new NumberSetting("GUI Scale", 0.5, 2.0, 1.0, 0.05);
    public static final NumberSetting guiTransparency = new NumberSetting("GUI Transparency", 0, 100, 0, 1);
    public static final BooleanSetting guiBlur = new BooleanSetting("GUI Blur", true);
    public static final BooleanSetting panelBlur = new BooleanSetting("Panel Blur", false);
    public static final NumberSetting panelBlurRadius = new NumberSetting("Panel Blur Radius", 5, 30, 12, 1);
    public static final BooleanSetting moduleDescriptions = new BooleanSetting("Module Descriptions", true);
    
    public static final NumberSetting toggleWidth = new NumberSetting("Toggle Width", 10, 40, 20, 1);
    public static final NumberSetting toggleHeight = new NumberSetting("Toggle Height", 6, 20, 10, 1);
    public static final NumberSetting sliderHeight = new NumberSetting("Slider Height", 2, 10, 4, 0.5);
    public static final NumberSetting sliderHandleSize = new NumberSetting("Slider Handle Size", 3, 8, 5, 0.5);
    
    public static final BooleanSetting guiGlow = new BooleanSetting("GUI Glow", false);
    public static final ColorSetting glowColor = new ColorSetting("Glow Color", new Color(124, 77, 255, 255));
    public static final NumberSetting glowIntensity = new NumberSetting("Glow Intensity", 0, 2, 1, 0.1);
    public static final NumberSetting glowThickness = new NumberSetting("Glow Thickness", 2, 10, 5, 1);
    public static final NumberSetting bloomRadius = new NumberSetting("Bloom Radius", 5, 15, 10, 1);
    
    public static final BooleanSetting autoFocusSearch = new BooleanSetting("Auto Focus Search", true);
    public static final BooleanSetting snowEffect = new BooleanSetting("Snow Effect", false);
    public static final BooleanSetting autoSaveSettings = new BooleanSetting("Auto Save Settings", true);


    public ClientSettingsModule() {
        super("ClientSettings", "Customize GUI appearance", 0, Category.CLIENT);
        this.addSettings(accentColor, fontStyle, scrollableCategories, guiScale, guiTransparency, guiBlur, panelBlur, panelBlurRadius, moduleDescriptions,
                toggleWidth, toggleHeight, sliderHeight, sliderHandleSize,
                guiGlow, glowColor, glowIntensity, glowThickness, bloomRadius,
                autoFocusSearch, snowEffect, autoSaveSettings);
        this.setEnabled(true);
    }

    public static Color getAccentColor() {
        return accentColor.getValue();
    }

    public static String getFontStyle() {
        return fontStyle.getMode();
    }

    public static boolean isScrollable() {
        return scrollableCategories.getValue();
    }

    public static float getGuiTransparency() {
        return guiTransparency.getValueFloat() / 100f;
    }

    public static boolean isGuiBlurEnabled() {
        return guiBlur.getValue();
    }

    public static boolean isPanelBlurEnabled() {
        return panelBlur.getValue();
    }

    public static float getPanelBlurRadius() {
        return panelBlurRadius.getValueFloat();
    }

    public static boolean isModuleDescriptionsEnabled() {
        return moduleDescriptions.getValue();
    }
    
    public static float getToggleWidth() {
        return toggleWidth.getValueFloat();
    }
    
    public static float getToggleHeight() {
        return toggleHeight.getValueFloat();
    }
    
    public static float getSliderHeight() {
        return sliderHeight.getValueFloat();
    }
    
    public static float getSliderHandleSize() {
        return sliderHandleSize.getValueFloat();
    }
    
    public static boolean isGuiGlowEnabled() {
        return guiGlow.getValue();
    }
    
    public static Color getGlowColor() {
        return glowColor.getValue();
    }
    
    public static float getGlowIntensity() {
        return glowIntensity.getValueFloat();
    }
    
    public static float getGlowThickness() {
        return glowThickness.getValueFloat();
    }
    
    public static float getBloomRadius() {
        return bloomRadius.getValueFloat();
    }
    
    public static boolean isAutoFocusSearchEnabled() {
        return autoFocusSearch.getValue();
    }
    
    public static boolean isSnowEffectEnabled() {
        return snowEffect.getValue();
    }
    
    /**
     * Returns the raw GUI scale setting value (0, 1, 2, or 3).
     * @deprecated Use {@link #getGuiScaleFactor()} instead to get the actual scaling multiplier.
     */
    @Deprecated
    public static int getGuiScale() {
        return (int) guiScale.getValue();
    }
    
    /**
     * Returns the GUI scale multiplier (0.5–2.0, default 1.0). Applied directly to the
     * ClickGUI render transform so it scales uniformly. Lower it (e.g. ~0.67) on 720p so the
     * panel matches its 1080p-default footprint; on 1080p the same value shrinks it further.
     * @return scale factor as float
     */
    public static float getGuiScaleFactor() {
        return guiScale.getValueFloat();
    }
    
    public static boolean isAutoSaveEnabled() {
        return autoSaveSettings.getValue();
    }
}