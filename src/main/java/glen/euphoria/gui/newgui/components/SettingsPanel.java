package glen.euphoria.gui.newgui.components;

import glen.euphoria.gui.newgui.GuiConstants;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.*;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.module.modules.client.NewClickGUIModule;
import glen.euphoria.utils.render.GuiGlowHelper;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class SettingsPanel {
    private static final Color PANEL_BG = new Color(7, 13, 25, 255);
    private static final Color PANEL_BG_2 = new Color(10, 18, 32, 255);
    private static final Color HEADER_BG = new Color(9, 17, 31, 255);
    private static final Color BORDER_COLOR = new Color(32, 60, 112, 255);
    private static final int HEADER_HEIGHT = 24;
    private static final float CORNER_RADIUS = 10f;
    private static final int SETTING_HEIGHT = 18;
    private static final int NUMBER_SETTING_HEIGHT = 24;
    private static final int PADDING = GuiConstants.PADDING + 2;
    private static final float TOGGLE_ANIMATION_SPEED = 0.15f;
    private static final float SLIDER_ANIMATION_SPEED = 0.12f;

    private final Module module;
    private final int width = 176;
    private float targetX;
    private float currentX;
    private float y;
    private int height;
    private ModeSetting expandedDropdown = null;
    private ColorPicker colorPicker = null;
    private boolean visible = false;
    private float animationProgress = 0f;
    private Setting draggingSlider = null;
    private KeybindSetting listeningKeybind = null;
    private NumberSetting typingSlider = null;
    private String sliderInputText = "";
    private RangeSetting draggingRangeSlider = null;
    private boolean draggingMinHandle = false;
    private RangeSetting typingRangeSlider = null;
    private boolean typingRangeMin = false;
    private String rangeInputText = "";
    private int screenWidth = 0;
    private int screenHeight = 0;

    private final java.util.Map<Setting, Float> animatedValues = new java.util.HashMap<>();
    private final java.util.Map<Setting, Float> animatedHandlePositions = new java.util.HashMap<>();
    private final java.util.Map<Setting, Float> hoverAnimations = new java.util.HashMap<>();

    public SettingsPanel(Module module, float x, float y) {
        this.module = module;
        this.targetX = x;
        this.currentX = x - width;
        this.y = y;
        calculateHeight();
    }

    private static Color getAccentColor() {
        return NewClickGUIModule.getAccentColor();
    }

    public boolean isDraggingSlider() {
        return draggingSlider != null;
    }

    private void calculateHeight() {
        int totalHeight = HEADER_HEIGHT + PADDING * 2;
        for (Setting setting : module.getSettings()) {
            if (setting instanceof NumberSetting || setting instanceof RangeSetting) {
                totalHeight += NUMBER_SETTING_HEIGHT;
            } else {
                totalHeight += SETTING_HEIGHT;
            }
        }
        this.height = totalHeight;
    }

    public void show(float x, float y) {
        this.targetX = x;
        this.y = y;
        this.visible = true;
        clampToScreen();
    }

    public void setScreenDimensions(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        clampToScreen();
    }

    private void clampToScreen() {
        if (screenWidth > 0 && screenHeight > 0) {
            if (targetX + width > screenWidth) targetX = screenWidth - width - 10;
            if (targetX < 10) targetX = 10;
            if (y + height > screenHeight) y = screenHeight - height - 10;
            if (y < 10) y = 10;
        }
    }

    public void hide() {
        this.visible = false;
    }

    public void update(float delta) {
        if (visible) {
            animationProgress += delta * 8f;
            if (animationProgress > 1f) animationProgress = 1f;
        } else {
            animationProgress -= delta * 8f;
            if (animationProgress < 0f) animationProgress = 0f;
        }

        float easedProgress = 1f - (float) Math.pow(1f - animationProgress, 3);
        currentX = (targetX - width) + (width * easedProgress);

        if (colorPicker != null) {
            colorPicker.update(delta);
        }
    }

    public void render(int mouseX, int mouseY, float alpha) {
        if (animationProgress <= 0f) return;

        float transparency = ClientSettingsModule.getGuiTransparency();
        float renderAlpha = alpha * animationProgress * (1f - transparency);
        int panelAlpha = (int) (255 * renderAlpha);

        Color panelBg = new Color(PANEL_BG.getRed(), PANEL_BG.getGreen(), PANEL_BG.getBlue(), panelAlpha);
        Color panelBg2 = new Color(PANEL_BG_2.getRed(), PANEL_BG_2.getGreen(), PANEL_BG_2.getBlue(), panelAlpha);
        Color borderColor = new Color(BORDER_COLOR.getRed(), BORDER_COLOR.getGreen(), BORDER_COLOR.getBlue(), (int) (150 * renderAlpha));
        Color headerBg = new Color(HEADER_BG.getRed(), HEADER_BG.getGreen(), HEADER_BG.getBlue(), panelAlpha);
        Color accentColor = getAccentColor();
        Color separatorColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (90 * renderAlpha));
        Color innerBorder = new Color(255, 255, 255, (int) (14 * renderAlpha));

        NanoVGRenderer.drawRoundedRectGradient(currentX, y, width, height, CORNER_RADIUS, panelBg2, panelBg);
        NanoVGRenderer.drawRoundedRectOutline(currentX, y, width, height, CORNER_RADIUS, 1f, borderColor);
        NanoVGRenderer.drawRoundedRectOutline(currentX + 1f, y + 1f, width - 2f, height - 2f, CORNER_RADIUS - 1f, 1f, innerBorder);

        NanoVGRenderer.drawRoundedRect(currentX, y, width, HEADER_HEIGHT, CORNER_RADIUS, headerBg);
        NanoVGRenderer.drawRect(currentX, y + HEADER_HEIGHT, width, 1f, separatorColor);

        Color textColor = new Color(236, 242, 250, panelAlpha);
        Color glowColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (55 * renderAlpha));

        float fontSize = 11f;
        NanoVGRenderer.drawText(module.getName(), currentX + PADDING, y + 7f, fontSize + 0.3f, glowColor);
        NanoVGRenderer.drawText(module.getName(), currentX + PADDING, y + 7f, fontSize, textColor);

        float settingY = y + HEADER_HEIGHT + PADDING;
        for (Setting setting : module.getSettings()) {
            renderSetting(setting, settingY, mouseX, mouseY, renderAlpha, false);
            if (setting instanceof NumberSetting || setting instanceof RangeSetting) {
                settingY += NUMBER_SETTING_HEIGHT;
            } else {
                settingY += SETTING_HEIGHT;
            }
        }

        if (expandedDropdown != null) {
            settingY = y + HEADER_HEIGHT + PADDING;
            for (Setting setting : module.getSettings()) {
                if (setting == expandedDropdown) {
                    renderSetting(setting, settingY, mouseX, mouseY, renderAlpha, true);
                    break;
                }
                if (setting instanceof NumberSetting || setting instanceof RangeSetting) {
                    settingY += NUMBER_SETTING_HEIGHT;
                } else {
                    settingY += SETTING_HEIGHT;
                }
            }
        }

        if (colorPicker != null && colorPicker.isAnimating()) {
            colorPicker.render(mouseX, mouseY, renderAlpha);
        }
    }

    private void renderSetting(Setting setting, float settingY, int mouseX, int mouseY, float alpha, boolean renderDropdownOnly) {
        if (setting instanceof BooleanSetting) {
            renderBooleanSetting((BooleanSetting) setting, settingY, mouseX, mouseY, alpha);
        } else if (setting instanceof RangeSetting) {
            renderRangeSetting((RangeSetting) setting, settingY, mouseX, mouseY, alpha);
        } else if (setting instanceof NumberSetting) {
            renderNumberSetting((NumberSetting) setting, settingY, mouseX, mouseY, alpha);
        } else if (setting instanceof ModeSetting) {
            renderModeSetting((ModeSetting) setting, settingY, mouseX, mouseY, alpha, renderDropdownOnly);
        } else if (setting instanceof ColorSetting) {
            renderColorSetting((ColorSetting) setting, settingY, mouseX, mouseY, alpha);
        } else if (setting instanceof KeybindSetting) {
            renderKeybindSetting((KeybindSetting) setting, settingY, mouseX, mouseY, alpha);
        }
    }

    private void renderRangeSetting(RangeSetting setting, float settingY, int mouseX, int mouseY, float alpha) {
        float sliderY = settingY + 15f;
        float sliderWidth = width - PADDING * 2f;
        float sliderHeight = ClientSettingsModule.getSliderHeight();
        float sliderRadius = Math.max(3f, sliderHeight / 2f);

        int bgAlpha = (int) (255 * alpha);
        Color accentColor = getAccentColor();
        Color sliderBg = new Color(14, 22, 38, bgAlpha);
        Color sliderFill = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), bgAlpha);
        Color textColor = new Color(198, 210, 228, bgAlpha);
        Color valueColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), bgAlpha);

        NanoVGRenderer.drawRoundedRect(currentX + PADDING, sliderY, sliderWidth, sliderHeight, sliderRadius, sliderBg);

        double range = setting.getMax() - setting.getMin();
        double targetMinPercentage = range > 0 ? (setting.getMinValue() - setting.getMin()) / range : 0;
        double targetMaxPercentage = range > 0 ? (setting.getMaxValue() - setting.getMin()) / range : 1;

        float currentMinPercentage = animatedValues.getOrDefault(setting, (float) targetMinPercentage);
        currentMinPercentage += ((float) targetMinPercentage - currentMinPercentage) * SLIDER_ANIMATION_SPEED;
        animatedValues.put(setting, currentMinPercentage);

        float currentMaxPercentage = animatedHandlePositions.getOrDefault(setting, (float) targetMaxPercentage);
        currentMaxPercentage += ((float) targetMaxPercentage - currentMaxPercentage) * SLIDER_ANIMATION_SPEED;
        animatedHandlePositions.put(setting, currentMaxPercentage);

        float minX = sliderWidth * currentMinPercentage;
        float maxX = sliderWidth * currentMaxPercentage;
        float fillWidth = maxX - minX;

        if (fillWidth > 0) {
            NanoVGRenderer.drawRoundedRect(currentX + PADDING + minX, sliderY, fillWidth, sliderHeight, sliderRadius, sliderFill);
        }

        float handleRadius = ClientSettingsModule.getSliderHandleSize();
        Color handleGlow = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (80 * alpha));
        Color handleColor = new Color(245, 247, 255, bgAlpha);

        float minHandleX = currentX + PADDING + minX;
        float handleY = sliderY + sliderHeight / 2f;
        NanoVGRenderer.drawCircle(minHandleX, handleY, handleRadius + 1.8f, handleGlow);
        NanoVGRenderer.drawCircle(minHandleX, handleY, handleRadius, handleColor);

        float maxHandleX = currentX + PADDING + maxX;
        NanoVGRenderer.drawCircle(maxHandleX, handleY, handleRadius + 1.8f, handleGlow);
        NanoVGRenderer.drawCircle(maxHandleX, handleY, handleRadius, handleColor);

        String displayText = setting.getName();
        String valueText;
        if (typingRangeSlider == setting) {
            valueText = rangeInputText + "_";
            valueColor = new Color(255, 255, 255, bgAlpha);
        } else {
            valueText = formatNumber(setting.getMinValue()) + " - " + formatNumber(setting.getMaxValue());
        }

        float fontSize = 9f;
        float valueWidth = NanoVGRenderer.getTextWidth(valueText, fontSize);
        NanoVGRenderer.drawText(displayText, currentX + PADDING, settingY + 2f, fontSize, textColor);
        NanoVGRenderer.drawText(valueText, currentX + width - PADDING - valueWidth, settingY + 2f, fontSize, valueColor);
    }

    private void renderBooleanSetting(BooleanSetting setting, float settingY, int mouseX, int mouseY, float alpha) {
        float toggleWidth = ClientSettingsModule.getToggleWidth();
        float toggleHeight = ClientSettingsModule.getToggleHeight();
        float toggleX = currentX + width - PADDING - toggleWidth;
        float toggleY = settingY + 4f;

        boolean isHovered = mouseX >= toggleX && mouseX <= toggleX + toggleWidth &&
                mouseY >= toggleY && mouseY <= toggleY + toggleHeight;

        float targetHover = isHovered ? 1f : 0f;
        float currentHover = hoverAnimations.getOrDefault(setting, 0f);
        currentHover += (targetHover - currentHover) * 0.2f;
        hoverAnimations.put(setting, currentHover);

        int bgAlpha = (int) (255 * alpha);
        boolean isOn = setting.getValue();

        float targetProgress = isOn ? 1f : 0f;
        float currentProgress = animatedValues.getOrDefault(setting, targetProgress);
        currentProgress += (targetProgress - currentProgress) * TOGGLE_ANIMATION_SPEED;
        animatedValues.put(setting, currentProgress);

        Color accentColor = getAccentColor();
        Color offColor = new Color(17, 25, 42, bgAlpha);
        Color onColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), bgAlpha);
        Color toggleBg = mixColor(offColor, onColor, currentProgress);

        if (currentHover > 0.01f) {
            Color hoverOverlay = new Color(255, 255, 255, (int) (16 * currentHover * alpha));
            toggleBg = mixColor(toggleBg, hoverOverlay, currentHover * 0.35f);
        }

        NanoVGRenderer.drawRoundedRect(toggleX, toggleY, toggleWidth, toggleHeight, toggleHeight / 2f, toggleBg);

        float handleSize = toggleHeight - 4f;
        float handleMinX = toggleX + 2f;
        float handleMaxX = toggleX + toggleWidth - handleSize - 2f;
        float handleX = handleMinX + (handleMaxX - handleMinX) * currentProgress;
        float handleY = toggleY + 2f;

        Color handleColor = new Color(245, 247, 255, bgAlpha);
        float handleRadius = handleSize / 2f;

        if (currentHover > 0.01f || currentProgress > 0.01f) {
            Color handleGlow = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (90 * Math.max(currentHover, currentProgress) * alpha));
            NanoVGRenderer.drawCircle(handleX + handleRadius, handleY + handleRadius, handleRadius + 2f, handleGlow);
        }

        NanoVGRenderer.drawCircle(handleX + handleRadius, handleY + handleRadius, handleRadius, handleColor);

        Color textColor = new Color(198, 210, 228, bgAlpha);
        float fontSize = 9f;
        NanoVGRenderer.drawText(setting.getName(), currentX + PADDING, settingY + 4f, fontSize, textColor);
    }

    private void renderNumberSetting(NumberSetting setting, float settingY, int mouseX, int mouseY, float alpha) {
        float sliderY = settingY + 15f;
        float sliderWidth = width - PADDING * 2f;
        float sliderHeight = ClientSettingsModule.getSliderHeight();
        float sliderRadius = Math.max(3f, sliderHeight / 2f);

        int bgAlpha = (int) (255 * alpha);
        Color accentColor = getAccentColor();
        Color sliderBg = new Color(14, 22, 38, bgAlpha);
        Color sliderFill = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), bgAlpha);
        Color textColor = new Color(198, 210, 228, bgAlpha);
        Color valueColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), bgAlpha);

        NanoVGRenderer.drawRoundedRect(currentX + PADDING, sliderY, sliderWidth, sliderHeight, sliderRadius, sliderBg);

        double targetPercentage = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        float currentPercentage = animatedHandlePositions.getOrDefault(setting, (float) targetPercentage);
        currentPercentage += ((float) targetPercentage - currentPercentage) * SLIDER_ANIMATION_SPEED;
        animatedHandlePositions.put(setting, currentPercentage);

        float fillWidth = sliderWidth * currentPercentage;
        if (fillWidth > 0) {
            NanoVGRenderer.drawRoundedRect(currentX + PADDING, sliderY, fillWidth, sliderHeight, sliderRadius, sliderFill);
        }

        float handleRadius = ClientSettingsModule.getSliderHandleSize();
        float handleX = currentX + PADDING + fillWidth;
        float handleY = sliderY + sliderHeight / 2f;
        Color handleGlow = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (80 * alpha));
        NanoVGRenderer.drawCircle(handleX, handleY, handleRadius + 1.8f, handleGlow);
        Color handleColor = new Color(245, 247, 255, bgAlpha);
        NanoVGRenderer.drawCircle(handleX, handleY, handleRadius, handleColor);

        String displayText = setting.getName();
        String valueText;
        if (typingSlider == setting) {
            valueText = sliderInputText + "_";
            valueColor = new Color(255, 255, 255, bgAlpha);
        } else {
            valueText = formatNumber(setting.getValue());
        }

        float fontSize = 9f;
        float valueWidth = NanoVGRenderer.getTextWidth(valueText, fontSize);
        NanoVGRenderer.drawText(displayText, currentX + PADDING, settingY + 2f, fontSize, textColor);
        NanoVGRenderer.drawText(valueText, currentX + width - PADDING - valueWidth, settingY + 2f, fontSize, valueColor);
    }

    private void renderModeSetting(ModeSetting setting, float settingY, int mouseX, int mouseY, float alpha, boolean renderDropdownOnly) {
        String mode = setting.getMode();
        float fontSize = 9f;

        int bgAlpha = (int) (255 * alpha);
        Color accentColor = getAccentColor();
        Color dropdownBg = new Color(14, 22, 38, bgAlpha);
        Color dropdownBorder = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (120 * alpha));
        Color textColor = new Color(198, 210, 228, bgAlpha);
        Color modeText = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), bgAlpha);

        float dropdownWidth = 72f;
        float dropdownHeight = 16f;
        float dropdownX = currentX + width - PADDING - dropdownWidth;
        float dropdownY = settingY + 1f;

        if (!renderDropdownOnly) {
            NanoVGRenderer.drawRoundedRect(dropdownX, dropdownY, dropdownWidth, dropdownHeight, 5f, dropdownBg);
            NanoVGRenderer.drawRoundedRectOutline(dropdownX, dropdownY, dropdownWidth, dropdownHeight, 5f, 1f, dropdownBorder);

            String arrow = expandedDropdown == setting ? "v" : ">";
            float arrowWidth = NanoVGRenderer.getTextWidth(arrow, fontSize);
            float availableWidth = dropdownWidth - 8f - arrowWidth - 4f;

            String displayMode = mode;
            float modeWidth = NanoVGRenderer.getTextWidth(displayMode, fontSize);
            if (modeWidth > availableWidth) {
                while (modeWidth > availableWidth && displayMode.length() > 3) {
                    displayMode = displayMode.substring(0, displayMode.length() - 1);
                    modeWidth = NanoVGRenderer.getTextWidth(displayMode + "...", fontSize);
                }
                displayMode += "...";
            }

            NanoVGRenderer.drawText(displayMode, dropdownX + 5f, dropdownY + 3f, fontSize, modeText);
            NanoVGRenderer.drawText(arrow, dropdownX + dropdownWidth - arrowWidth - 5f, dropdownY + 3f, fontSize, textColor);

            NanoVGRenderer.drawText(setting.getName(), currentX + PADDING, settingY + 4f, fontSize, textColor);
        }

        if (expandedDropdown == setting && renderDropdownOnly) {
            float menuY = dropdownY + dropdownHeight + 3f;
            int optionCount = setting.getModes().size();
            float menuHeight = optionCount * 15f;

            Color menuBg = new Color(8, 15, 28, bgAlpha);
            Color hoverBg = new Color(20, 32, 56, bgAlpha);
            Color menuBorder = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (150 * alpha));

            NanoVGRenderer.drawRoundedRect(dropdownX, menuY, dropdownWidth, menuHeight, 6f, menuBg);
            NanoVGRenderer.drawRoundedRectOutline(dropdownX, menuY, dropdownWidth, menuHeight, 6f, 1.2f, menuBorder);

            float optionY = menuY;
            for (String option : setting.getModes()) {
                boolean isHovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                        mouseY >= optionY && mouseY <= optionY + 15f;
                boolean isSelected = option.equals(mode);

                if (isHovered) {
                    NanoVGRenderer.drawRect(dropdownX + 1f, optionY, dropdownWidth - 2f, 15f, hoverBg);
                }

                String displayOption = option;
                float optionWidth = NanoVGRenderer.getTextWidth(displayOption, fontSize);
                float maxOptionWidth = dropdownWidth - 10f;
                if (optionWidth > maxOptionWidth) {
                    while (optionWidth > maxOptionWidth && displayOption.length() > 3) {
                        displayOption = displayOption.substring(0, displayOption.length() - 1);
                        optionWidth = NanoVGRenderer.getTextWidth(displayOption + "...", fontSize);
                    }
                    displayOption += "...";
                }

                Color optionColor = isSelected ? modeText : textColor;
                NanoVGRenderer.drawText(displayOption, dropdownX + 5f, optionY + 3f, fontSize, optionColor);
                optionY += 15f;
            }
        }
    }

    private void renderColorSetting(ColorSetting setting, float settingY, int mouseX, int mouseY, float alpha) {
        int previewSize = 13;
        float previewX = currentX + width - PADDING - previewSize;
        float previewY = settingY + 2f;

        Color color = setting.getValue();
        int colorAlpha = (int) (color.getAlpha() * alpha);
        Color displayColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), colorAlpha);
        Color accentColor = getAccentColor();
        Color border = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (150 * alpha));
        Color textColor = new Color(198, 210, 228, (int) (255 * alpha));

        NanoVGRenderer.drawRoundedRect(previewX, previewY, previewSize, previewSize, 4f, displayColor);
        NanoVGRenderer.drawRoundedRectOutline(previewX, previewY, previewSize, previewSize, 4f, 1f, border);

        float fontSize = 9f;
        NanoVGRenderer.drawText(setting.getName(), currentX + PADDING, settingY + 4f, fontSize, textColor);
    }

    private void renderKeybindSetting(KeybindSetting setting, float settingY, int mouseX, int mouseY, float alpha) {
        int bgAlpha = (int) (255 * alpha);
        boolean isListening = listeningKeybind == setting;

        String keyText = isListening ? "..." : getKeyName(setting.getKeyCode());
        float fontSize = 9f;
        float keyWidth = NanoVGRenderer.getTextWidth(keyText, fontSize);

        float modeButtonWidth = 36f;
        float modeButtonHeight = 16f;
        float modeButtonX = currentX + width - PADDING - modeButtonWidth;
        float modeButtonY = settingY + 1f;

        float buttonWidth = Math.max(keyWidth + 14f, 46f);
        float buttonHeight = 16f;
        float buttonX = modeButtonX - buttonWidth - 4f;
        float buttonY = settingY + 1f;

        Color accentColor = getAccentColor();
        Color buttonBg = isListening
                ? new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (100 * alpha))
                : new Color(14, 22, 38, bgAlpha);
        Color buttonBorder = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                isListening ? (int) (200 * alpha) : (int) (110 * alpha));
        Color textColor = new Color(198, 210, 228, bgAlpha);
        Color keyColor = isListening
                ? new Color(245, 247, 255, bgAlpha)
                : new Color(198, 210, 228, bgAlpha);

        NanoVGRenderer.drawRoundedRect(buttonX, buttonY, buttonWidth, buttonHeight, 5f, buttonBg);
        NanoVGRenderer.drawRoundedRectOutline(buttonX, buttonY, buttonWidth, buttonHeight, 5f, 1f, buttonBorder);

        float textX = buttonX + (buttonWidth - keyWidth) / 2f;
        NanoVGRenderer.drawText(keyText, textX, buttonY + 3f, fontSize, keyColor);

        String modeText = setting.isHoldMode() ? "Hold" : "Tgl";
        Color modeBg = setting.isHoldMode()
                ? new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (85 * alpha))
                : new Color(14, 22, 38, bgAlpha);
        Color modeBorder = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (110 * alpha));
        Color modeTextColor = setting.isHoldMode()
                ? new Color(245, 247, 255, bgAlpha)
                : new Color(198, 210, 228, bgAlpha);

        NanoVGRenderer.drawRoundedRect(modeButtonX, modeButtonY, modeButtonWidth, modeButtonHeight, 5f, modeBg);
        NanoVGRenderer.drawRoundedRectOutline(modeButtonX, modeButtonY, modeButtonWidth, modeButtonHeight, 5f, 1f, modeBorder);

        float modeTextWidth = NanoVGRenderer.getTextWidth(modeText, fontSize);
        float modeTextX = modeButtonX + (modeButtonWidth - modeTextWidth) / 2f;
        NanoVGRenderer.drawText(modeText, modeTextX, modeButtonY + 3f, fontSize, modeTextColor);

        NanoVGRenderer.drawText(setting.getName(), currentX + PADDING, settingY + 4f, fontSize, textColor);
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.format("%d", (long) value);
        } else {
            return String.format("%.2f", value);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (animationProgress <= 0f) return false;

        if (mouseClickedForKeybind(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 1) {
            float settingY = y + HEADER_HEIGHT + PADDING;
            for (Setting setting : module.getSettings()) {
                int currentHeight = (setting instanceof NumberSetting || setting instanceof RangeSetting)
                        ? NUMBER_SETTING_HEIGHT : SETTING_HEIGHT;

                if (setting instanceof NumberSetting && mouseY >= settingY && mouseY <= settingY + currentHeight) {
                    if (mouseX >= currentX && mouseX <= currentX + width) {
                        typingSlider = (NumberSetting) setting;
                        sliderInputText = formatNumber(((NumberSetting) setting).getValue());
                        return true;
                    }
                } else if (setting instanceof RangeSetting && mouseY >= settingY && mouseY <= settingY + currentHeight) {
                    if (mouseX >= currentX && mouseX <= currentX + width) {
                        typingRangeSlider = (RangeSetting) setting;
                        float sliderWidth = width - PADDING * 2f;
                        double range = ((RangeSetting) setting).getMax() - ((RangeSetting) setting).getMin();
                        double minPercentage = range > 0 ? (((RangeSetting) setting).getMinValue() - ((RangeSetting) setting).getMin()) / range : 0;
                        double maxPercentage = range > 0 ? (((RangeSetting) setting).getMaxValue() - ((RangeSetting) setting).getMin()) / range : 1;
                        float minX = (float) (sliderWidth * minPercentage);
                        float maxX = (float) (sliderWidth * maxPercentage);
                        float midX = (minX + maxX) / 2f;
                        float relativeX = (float) (mouseX - (currentX + PADDING));

                        typingRangeMin = relativeX < midX;
                        rangeInputText = formatNumber(typingRangeMin ? ((RangeSetting) setting).getMinValue() : ((RangeSetting) setting).getMaxValue());
                        return true;
                    }
                }

                settingY += currentHeight;
            }
            return false;
        }

        if (button != 0) return false;

        if (colorPicker != null && colorPicker.isAnimating()) {
            boolean clickedInside = colorPicker.mouseClicked(mouseX, mouseY, button);
            if (!clickedInside) {
                colorPicker.hide();
            }
            return true;
        }

        if (expandedDropdown != null) {
            float settingY = y + HEADER_HEIGHT + PADDING;
            for (Setting setting : module.getSettings()) {
                if (setting == expandedDropdown) {
                    float dropdownWidth = 72f;
                    float dropdownX = currentX + width - PADDING - dropdownWidth;
                    float dropdownY = settingY + 1f;
                    float dropdownHeight = 16f;
                    float menuY = dropdownY + dropdownHeight + 3f;

                    int optionIndex = 0;
                    for (String option : expandedDropdown.getModes()) {
                        float optionY = menuY + (optionIndex * 15f);
                        if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                mouseY >= optionY && mouseY <= optionY + 15f) {
                            expandedDropdown.setMode(option);
                            expandedDropdown = null;
                            return true;
                        }
                        optionIndex++;
                    }

                    expandedDropdown = null;
                    return true;
                }

                int currentHeight = (setting instanceof NumberSetting || setting instanceof RangeSetting)
                        ? NUMBER_SETTING_HEIGHT : SETTING_HEIGHT;
                settingY += currentHeight;
            }
        }

        if (!isMouseOver(mouseX, mouseY)) return false;

        float settingY = y + HEADER_HEIGHT + PADDING;
        for (Setting setting : module.getSettings()) {
            int currentHeight = (setting instanceof NumberSetting || setting instanceof RangeSetting)
                    ? NUMBER_SETTING_HEIGHT : SETTING_HEIGHT;

            if (mouseY >= settingY && mouseY <= settingY + currentHeight) {
                if (setting instanceof BooleanSetting) {
                    ((BooleanSetting) setting).toggle();
                    return true;
                } else if (setting instanceof RangeSetting) {
                    RangeSetting rangeSetting = (RangeSetting) setting;
                    float sliderWidth = width - PADDING * 2f;
                    double range = rangeSetting.getMax() - rangeSetting.getMin();
                    double minPercentage = range > 0 ? (rangeSetting.getMinValue() - rangeSetting.getMin()) / range : 0;
                    double maxPercentage = range > 0 ? (rangeSetting.getMaxValue() - rangeSetting.getMin()) / range : 1;
                    float minHandleX = currentX + PADDING + (float) (sliderWidth * minPercentage);
                    float maxHandleX = currentX + PADDING + (float) (sliderWidth * maxPercentage);

                    float distToMin = Math.abs((float) mouseX - minHandleX);
                    float distToMax = Math.abs((float) mouseX - maxHandleX);

                    draggingRangeSlider = rangeSetting;
                    draggingMinHandle = distToMin < distToMax;
                    updateRangeSliderValue(rangeSetting, mouseX, draggingMinHandle);
                    return true;
                } else if (setting instanceof NumberSetting) {
                    draggingSlider = setting;
                    updateSliderValue((NumberSetting) setting, mouseX);
                    return true;
                } else if (setting instanceof KeybindSetting keybindSetting) {
                    float modeButtonWidth = 36f;
                    float modeButtonX = currentX + width - PADDING - modeButtonWidth;
                    float modeButtonY = settingY + 1f;
                    float modeButtonHeight = 16f;

                    if (mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                            mouseY >= modeButtonY && mouseY <= modeButtonY + modeButtonHeight) {
                        keybindSetting.toggleHoldMode();
                        return true;
                    }

                    listeningKeybind = (listeningKeybind == setting) ? null : keybindSetting;
                    return true;
                } else if (setting instanceof ModeSetting modeSetting) {
                    float dropdownWidth = 72f;
                    float dropdownX = currentX + width - PADDING - dropdownWidth;
                    float dropdownY = settingY + 1f;
                    float dropdownHeight = 16f;

                    if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                            mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                        expandedDropdown = (expandedDropdown == modeSetting) ? null : modeSetting;
                        return true;
                    }

                    if (expandedDropdown == modeSetting) {
                        float menuY = dropdownY + dropdownHeight + 3f;
                        int optionIndex = 0;
                        for (String option : modeSetting.getModes()) {
                            float optionY = menuY + (optionIndex * 15f);
                            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                    mouseY >= optionY && mouseY <= optionY + 15f) {
                                modeSetting.setMode(option);
                                expandedDropdown = null;
                                return true;
                            }
                            optionIndex++;
                        }
                    }
                } else if (setting instanceof ColorSetting) {
                    int previewSize = 13;
                    float previewX = currentX + width - PADDING - previewSize;
                    float previewY = settingY + 2f;

                    if (mouseX >= previewX && mouseX <= previewX + previewSize &&
                            mouseY >= previewY && mouseY <= previewY + previewSize) {
                        float pickerX = currentX + width + 6f;
                        float pickerY = settingY;

                        if (colorPicker != null && colorPicker.isVisible()) {
                            colorPicker.hide();
                            colorPicker = null;
                        } else {
                            colorPicker = new ColorPicker((ColorSetting) setting, pickerX, pickerY);
                            colorPicker.show(pickerX, pickerY, screenWidth, screenHeight);
                        }
                        return true;
                    }
                }
            }
            settingY += currentHeight;
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (colorPicker != null && colorPicker.isAnimating()) {
            return colorPicker.mouseDragged(mouseX, mouseY, button);
        }

        if (draggingRangeSlider != null && button == 0) {
            updateRangeSliderValue(draggingRangeSlider, mouseX, draggingMinHandle);
            return true;
        }

        if (draggingSlider instanceof NumberSetting && button == 0) {
            updateSliderValue((NumberSetting) draggingSlider, mouseX);
            return true;
        }

        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingSlider = null;
            draggingRangeSlider = null;
        }

        if (colorPicker != null) {
            colorPicker.mouseReleased(mouseX, mouseY, button);
        }
    }

    private void updateSliderValue(NumberSetting setting, double mouseX) {
        float sliderWidth = width - PADDING * 2f;
        float sliderX = currentX + PADDING;

        double percentage = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderWidth));
        double newValue = setting.getMin() + (setting.getMax() - setting.getMin()) * percentage;
        setting.setValue(newValue);
    }

    private void updateRangeSliderValue(RangeSetting setting, double mouseX, boolean updateMin) {
        float sliderWidth = width - PADDING * 2f;
        float sliderX = currentX + PADDING;

        double percentage = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderWidth));
        double range = setting.getMax() - setting.getMin();
        double newValue = range > 0 ? setting.getMin() + range * percentage : setting.getMin();

        if (updateMin) {
            setting.setMinValue(newValue);
        } else {
            setting.setMaxValue(newValue);
        }
    }

    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= currentX && mouseX <= currentX + width &&
                mouseY >= y && mouseY <= y + height;
    }

    public boolean isVisible() {
        return visible;
    }

    public Module getModule() {
        return module;
    }

    public boolean isAnimating() {
        return animationProgress > 0f;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public float getCurrentX() {
        return currentX;
    }

    public float getY() {
        return y;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typingRangeSlider != null) {
            if (keyCode == 256) {
                typingRangeSlider = null;
                rangeInputText = "";
                return true;
            } else if (keyCode == 257 || keyCode == 335) {
                try {
                    double value = Double.parseDouble(rangeInputText);
                    value = Math.max(typingRangeSlider.getMin(), Math.min(typingRangeSlider.getMax(), value));
                    if (typingRangeMin) {
                        typingRangeSlider.setMinValue(value);
                    } else {
                        typingRangeSlider.setMaxValue(value);
                    }
                } catch (NumberFormatException ignored) {
                }
                typingRangeSlider = null;
                rangeInputText = "";
                return true;
            } else if (keyCode == 259) {
                if (!rangeInputText.isEmpty()) {
                    rangeInputText = rangeInputText.substring(0, rangeInputText.length() - 1);
                }
                return true;
            }
            return false;
        }

        if (typingSlider != null) {
            if (keyCode == 256) {
                typingSlider = null;
                sliderInputText = "";
                return true;
            } else if (keyCode == 257 || keyCode == 335) {
                try {
                    double value = Double.parseDouble(sliderInputText);
                    value = Math.max(typingSlider.getMin(), Math.min(typingSlider.getMax(), value));
                    typingSlider.setValue(value);
                } catch (NumberFormatException ignored) {
                }
                typingSlider = null;
                sliderInputText = "";
                return true;
            } else if (keyCode == 259) {
                if (!sliderInputText.isEmpty()) {
                    sliderInputText = sliderInputText.substring(0, sliderInputText.length() - 1);
                }
                return true;
            }
            return false;
        }

        if (listeningKeybind != null) {
            if (keyCode == 256) {
                listeningKeybind.setKeyCode(0);
            } else {
                listeningKeybind.setKeyCode(keyCode);
            }
            listeningKeybind = null;
            return true;
        }

        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (typingRangeSlider != null) {
            if (Character.isDigit(chr) || chr == '.' || chr == '-') {
                if (chr == '.' && rangeInputText.contains(".")) return true;
                if (chr == '-' && !rangeInputText.isEmpty()) return true;
                rangeInputText += chr;
                return true;
            }
        }

        if (typingSlider != null) {
            if (Character.isDigit(chr) || chr == '.' || chr == '-') {
                if (chr == '.' && sliderInputText.contains(".")) return true;
                if (chr == '-' && !sliderInputText.isEmpty()) return true;
                sliderInputText += chr;
                return true;
            }
        }

        return false;
    }

    public boolean mouseClickedForKeybind(double mouseX, double mouseY, int button) {
        if (listeningKeybind != null) {
            int mouseKeyCode = -100 - button;
            listeningKeybind.setKeyCode(mouseKeyCode);
            listeningKeybind = null;
            return true;
        }
        return false;
    }

    public boolean isListeningForKey() {
        return listeningKeybind != null;
    }

    private String getKeyName(int keyCode) {
        if (keyCode == 0 || keyCode == -1) return "NONE";

        return switch (keyCode) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BACKSPACE";
            case 260 -> "INSERT";
            case 261 -> "DELETE";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 266 -> "PAGE UP";
            case 267 -> "PAGE DOWN";
            case 268 -> "HOME";
            case 269 -> "END";
            case 280 -> "CAPS LOCK";
            case 281 -> "SCROLL LOCK";
            case 282 -> "NUM LOCK";
            case 283 -> "PRINT SCREEN";
            case 284 -> "PAUSE";
            case 290 -> "F1";
            case 291 -> "F2";
            case 292 -> "F3";
            case 293 -> "F4";
            case 294 -> "F5";
            case 295 -> "F6";
            case 296 -> "F7";
            case 297 -> "F8";
            case 298 -> "F9";
            case 299 -> "F10";
            case 300 -> "F11";
            case 301 -> "F12";
            case 340 -> "LSHIFT";
            case 341 -> "LCTRL";
            case 342 -> "LALT";
            case 343 -> "LSUPER";
            case 344 -> "RSHIFT";
            case 345 -> "RCTRL";
            case 346 -> "RALT";
            case 347 -> "RSUPER";
            case 348 -> "MENU";
            default -> {
                if (keyCode >= 320 && keyCode <= 329) {
                    yield "NUMPAD " + (keyCode - 320);
                }
                if (keyCode <= -100) {
                    int mouseButton = -100 - keyCode;
                    yield switch (mouseButton) {
                        case 0 -> "MOUSE LEFT";
                        case 1 -> "MOUSE RIGHT";
                        case 2 -> "MOUSE MIDDLE";
                        default -> "MOUSE " + (mouseButton + 1);
                    };
                }
                if (keyCode >= 65 && keyCode <= 90) {
                    yield String.valueOf((char) keyCode);
                }
                if (keyCode >= 48 && keyCode <= 57) {
                    yield String.valueOf((char) keyCode);
                }
                yield "KEY " + keyCode;
            }
        };
    }

    public void renderGlow(DrawContext context, float alpha) {
        if (animationProgress <= 0f) return;
        GuiGlowHelper.drawGuiGlow(context, currentX, y, width, height, CORNER_RADIUS);
    }

    private Color mixColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int alpha = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(clampColor(r), clampColor(g), clampColor(bl), clampColor(alpha));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}