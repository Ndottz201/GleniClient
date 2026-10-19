package glen.euphoria.gui.newgui.components;

import glen.euphoria.gui.newgui.GuiConstants;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.utils.render.GuiGlowHelper;
import glen.euphoria.utils.render.blur.BlurRenderer;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryPanel {
    private final Category category;
    private float x;
    private float y;
    private final int width;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();

    private static final int HEADER_HEIGHT = 24;
    private static final int MODULE_HEIGHT = 18;
    private static final float CORNER_RADIUS = 10f;

    private boolean dragging = false;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean userDragged = false;

    private SettingsPanel settingsPanel = null;
    private float settingsPanelSourceY = 0f;

    private String searchQuery = "";
    private boolean collapsed = false;
    private float collapseProgress = 0f;

    private float scrollOffset = 0f;
    private static final int MAX_VISIBLE_MODULES = 9;
    private static final int SCROLLBAR_WIDTH = 3;

    private Module hoveredModule = null;

    private static Module lastOpenSettingsModule = null;
    private static final java.util.Map<Category, float[]> lastPanelPositions = new java.util.HashMap<>();
    private static final java.util.Map<Category, Boolean> lastPanelDragStates = new java.util.HashMap<>();

    public CategoryPanel(Category category, float x, float y, int width) {
        this.category = category;
        this.width = width;

        float[] savedPos = lastPanelPositions.get(category);
        Boolean wasDragged = lastPanelDragStates.get(category);

        if (savedPos != null) {
            this.x = savedPos[0];
            this.y = savedPos[1];
            this.userDragged = wasDragged != null && wasDragged;
        } else {
            this.x = x;
            this.y = y;
            this.userDragged = false;
        }

        initModules();
        restoreSettingsPanel();
    }

    private void initModules() {
        List<Module> modules = glen.euphoria.EuphoriaClient.INSTANCE.getModuleManager().getModulesByCategory(category);
        for (Module module : modules) {
            moduleButtons.add(new ModuleButton(module, 0, 0, width, MODULE_HEIGHT));
        }
    }

    private void restoreSettingsPanel() {
        if (lastOpenSettingsModule != null && lastOpenSettingsModule.getModuleCategory() == category) {
            for (int i = 0; i < moduleButtons.size(); i++) {
                ModuleButton button = moduleButtons.get(i);
                if (button.getModule() == lastOpenSettingsModule && button.getModule().getSettings().size() > 1) {
                    float moduleY = y + HEADER_HEIGHT + (i * MODULE_HEIGHT);
                    float settingsX = x + width + 10;
                    settingsPanelSourceY = moduleY;
                    settingsPanel = new SettingsPanel(button.getModule(), settingsX, moduleY);
                    settingsPanel.show(settingsX, moduleY);
                    break;
                }
            }
        }
    }

    public void update(float deltaTime) {
        if (settingsPanel != null) {
            settingsPanel.update(deltaTime);
        }

        for (ModuleButton button : moduleButtons) {
            button.updateSearch(searchQuery, deltaTime);
        }

        if (collapsed) {
            collapseProgress += deltaTime * 10f;
            if (collapseProgress > 1f) collapseProgress = 1f;
        } else {
            collapseProgress -= deltaTime * 10f;
            if (collapseProgress < 0f) collapseProgress = 0f;
        }
    }

    public void render(int mouseX, int mouseY, float alpha, float scale, int centerX, int centerY) {
        int visibleCount = 0;
        for (ModuleButton button : moduleButtons) {
            if (button.getSearchAlpha() > 0.01f) visibleCount++;
        }

        boolean scrollable = ClientSettingsModule.isScrollable();
        float expandProgress = 1f - collapseProgress;

        int displayedModules = scrollable ? Math.min(visibleCount, MAX_VISIBLE_MODULES) : visibleCount;
        int moduleAreaHeight = (int) ((displayedModules * MODULE_HEIGHT) * expandProgress);
        int totalHeight = HEADER_HEIGHT + moduleAreaHeight;

        float transparency = ClientSettingsModule.getGuiTransparency();
        int panelAlpha = clampColor((int) (255 * alpha * (1f - transparency)));

        Color panelBg = new Color(7, 13, 25, panelAlpha);
        Color panelBg2 = new Color(10, 18, 32, panelAlpha);
        Color headerBg = new Color(9, 17, 31, panelAlpha);
        Color border = new Color(32, 60, 112, clampColor((int) (120 * alpha)));
        Color softBorder = new Color(255, 255, 255, clampColor((int) (14 * alpha)));

        NanoVGRenderer.drawRoundedRectGradient(x, y, width, totalHeight, CORNER_RADIUS, panelBg2, panelBg);
        NanoVGRenderer.drawRoundedRectOutline(x, y, width, totalHeight, CORNER_RADIUS, 1f, border);
        NanoVGRenderer.drawRoundedRectOutline(x + 1f, y + 1f, width - 2f, totalHeight - 2f, CORNER_RADIUS - 1f, 1f, softBorder);

        NanoVGRenderer.drawRoundedRect(x, y, width, HEADER_HEIGHT, CORNER_RADIUS, headerBg);
        NanoVGRenderer.drawRect(x, y + HEADER_HEIGHT - CORNER_RADIUS, width, CORNER_RADIUS, headerBg);

        Color accent = ClientSettingsModule.getAccentColor();
        Color iconColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha)));
        Color textColor = new Color(235, 241, 250, clampColor((int) (255 * alpha)));
        Color subGlow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (65 * alpha)));

        String categoryName = category.getName();
        float fontSize = 10f;
        float iconSize = CategoryIcon.getIconSize();
        float iconX = Math.round(x + GuiConstants.PADDING + 1f);
        float iconY = Math.round(y + (HEADER_HEIGHT - iconSize) / 2f);
        float textX = Math.round(x + GuiConstants.PADDING + iconSize + 6f);
        float textY = Math.round(y + 7f);

        CategoryIcon.drawIcon(category, iconX, iconY, iconColor);
        NanoVGRenderer.drawText(categoryName, textX, textY, fontSize + 0.5f, subGlow);
        NanoVGRenderer.drawText(categoryName, textX, textY, fontSize, textColor);

        float dotX = x + width - 10f;
        float dotY = y + HEADER_HEIGHT / 2f;
        NanoVGRenderer.drawCircle(dotX, dotY, 2.5f, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (180 * alpha))));

        if (expandProgress > 0.01f) {
            NanoVGRenderer.scissor(x, y + HEADER_HEIGHT, width, moduleAreaHeight);

            float moduleY = y + HEADER_HEIGHT - scrollOffset;
            float moduleAlpha = alpha * expandProgress;

            Module newHoveredModule = null;

            int visibleIndex = 0;
            int totalVisible = 0;
            for (ModuleButton button : moduleButtons) {
                if (button.getSearchAlpha() > 0.01f) totalVisible++;
            }

            for (ModuleButton button : moduleButtons) {
                if (button.getSearchAlpha() > 0.01f) {
                    visibleIndex++;
                    boolean isLastVisible = visibleIndex == totalVisible;
                    button.render(x, moduleY, mouseX, mouseY, moduleAlpha, isLastVisible);

                    if (mouseX >= x && mouseX <= x + width && mouseY >= moduleY && mouseY <= moduleY + MODULE_HEIGHT) {
                        newHoveredModule = button.getModule();
                    }

                    moduleY += MODULE_HEIGHT;
                }
            }

            hoveredModule = newHoveredModule;
            NanoVGRenderer.resetScissor();

            if (scrollable && visibleCount > MAX_VISIBLE_MODULES) {
                float scrollbarHeight = ((float) displayedModules / visibleCount) * moduleAreaHeight;
                float scrollbarY = y + HEADER_HEIGHT + (scrollOffset / (visibleCount * MODULE_HEIGHT)) * moduleAreaHeight;

                Color scrollbarColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (155 * alpha)));
                NanoVGRenderer.drawRoundedRect(x + width - SCROLLBAR_WIDTH - 3f, scrollbarY, SCROLLBAR_WIDTH, scrollbarHeight, 2f, scrollbarColor);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverHeader(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                x = Math.round(x);
                y = Math.round(y);
                dragOffsetX = (float) (mouseX - x);
                dragOffsetY = (float) (mouseY - y);
                return true;
            } else if (button == 1) {
                collapsed = !collapsed;
                if (collapsed && settingsPanel != null) {
                    settingsPanel.hide();
                }
                return true;
            }
        }

        if (settingsPanel != null && settingsPanel.isAnimating()) {
            if (settingsPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (collapsed) return false;

        boolean scrollable = ClientSettingsModule.isScrollable();
        float moduleY = y + HEADER_HEIGHT - (scrollable ? scrollOffset : 0f);

        for (ModuleButton moduleButton : moduleButtons) {
            if (moduleButton.getSearchAlpha() > 0.01f) {
                if (moduleButton.mouseClicked(x, moduleY, mouseX, mouseY, button)) {
                    if (button == 1 && moduleButton.getModule().getSettings().size() > 1) {
                        float settingsX = x + width + 10f;
                        float settingsY = moduleY;
                        settingsPanelSourceY = moduleY;

                        if (settingsPanel != null && settingsPanel.isVisible() && settingsPanel.getModule() == moduleButton.getModule()) {
                            settingsPanel.hide();
                        } else {
                            settingsPanel = new SettingsPanel(moduleButton.getModule(), settingsX, settingsY);
                            settingsPanel.show(settingsX, settingsY);
                            lastOpenSettingsModule = moduleButton.getModule();
                        }
                    }
                    return true;
                }
                moduleY += MODULE_HEIGHT;
            }
        }

        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        if (settingsPanel != null) settingsPanel.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (settingsPanel != null && settingsPanel.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }

        if (dragging) {
            float oldY = y;
            x = Math.round((float) (mouseX - dragOffsetX));
            y = Math.round((float) (mouseY - dragOffsetY));
            userDragged = true;

            if (settingsPanel != null && settingsPanel.isVisible()) {
                float deltaYMove = y - oldY;
                float newSettingsX = x + width + 10f;
                settingsPanelSourceY += deltaYMove;
                settingsPanel.show(newSettingsX, settingsPanelSourceY);
            }

            return true;
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (settingsPanel != null && settingsPanel.isAnimating()) {
            return settingsPanel.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!ClientSettingsModule.isScrollable() || collapsed || !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int visibleCount = 0;
        for (ModuleButton button : moduleButtons) {
            if (button.getSearchAlpha() > 0.01f) visibleCount++;
        }

        if (visibleCount <= MAX_VISIBLE_MODULES) return false;

        float maxScroll = (visibleCount - MAX_VISIBLE_MODULES) * MODULE_HEIGHT;
        scrollOffset -= (float) amount * 10f;

        if (scrollOffset < 0f) scrollOffset = 0f;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        return true;
    }

    public void renderSettingsPanel(int mouseX, int mouseY, float alpha, int screenWidth, int screenHeight) {
        if (settingsPanel != null && settingsPanel.isAnimating()) {
            settingsPanel.setScreenDimensions(screenWidth, screenHeight);
            settingsPanel.render(mouseX, mouseY, alpha);
        }
    }

    public void renderGlow(DrawContext context, float alpha, float scale, int centerX, int centerY) {
        int visibleCount = 0;
        for (ModuleButton button : moduleButtons) {
            if (button.getSearchAlpha() > 0.01f) visibleCount++;
        }

        boolean scrollable = ClientSettingsModule.isScrollable();
        float expandProgress = 1f - collapseProgress;
        int displayedModules = scrollable ? Math.min(visibleCount, MAX_VISIBLE_MODULES) : visibleCount;
        int moduleAreaHeight = (int) ((displayedModules * MODULE_HEIGHT) * expandProgress);
        int totalHeight = HEADER_HEIGHT + moduleAreaHeight;

        float transformedX = (x - centerX) * scale + centerX;
        float transformedY = (y - centerY) * scale + centerY;
        float transformedWidth = width * scale;
        float transformedHeight = totalHeight * scale;

        GuiGlowHelper.drawGuiGlow(context, transformedX, transformedY, transformedWidth, transformedHeight, CORNER_RADIUS * scale);
    }

    public void renderBlur(float alpha, float scale, int centerX, int centerY) {
        if (!ClientSettingsModule.isPanelBlurEnabled()) return;

        int visibleCount = 0;
        for (ModuleButton button : moduleButtons) {
            if (button.getSearchAlpha() > 0.01f) visibleCount++;
        }

        boolean scrollable = ClientSettingsModule.isScrollable();
        float expandProgress = 1f - collapseProgress;
        int displayedModules = scrollable ? Math.min(visibleCount, MAX_VISIBLE_MODULES) : visibleCount;
        int moduleAreaHeight = (int) ((displayedModules * MODULE_HEIGHT) * expandProgress);
        int totalHeight = HEADER_HEIGHT + moduleAreaHeight;

        float transformedX = (x - centerX) * scale + centerX;
        float transformedY = (y - centerY) * scale + centerY;
        float transformedWidth = width * scale;
        float transformedHeight = totalHeight * scale;

        BlurRenderer.drawBlur(
                transformedX, transformedY,
                transformedWidth, transformedHeight,
                CORNER_RADIUS * scale,
                new Color(255, 255, 255, (int) (255 * alpha)),
                ClientSettingsModule.getPanelBlurRadius()
        );
    }

    public void renderSettingsPanelGlow(DrawContext context, float alpha) {
        if (settingsPanel != null && settingsPanel.isAnimating()) {
            settingsPanel.renderGlow(context, alpha);
        }
    }

    public String getTooltipText() {
        if (hoveredModule != null && ClientSettingsModule.isModuleDescriptionsEnabled()) {
            return hoveredModule.getDescription();
        }
        return null;
    }

    public int getTooltipX(int mouseX) {
        return mouseX + 10;
    }

    public int getTooltipY(int mouseY) {
        return mouseY + 10;
    }

    public SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    public boolean hasActiveSettingsPanel() {
        return settingsPanel != null && settingsPanel.isAnimating();
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean hasBeenDragged() {
        return userDragged;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query.toLowerCase();
    }

    public void saveState() {
        lastPanelPositions.put(category, new float[]{x, y});
        lastPanelDragStates.put(category, userDragged);
        if (settingsPanel != null && settingsPanel.isVisible()) {
            lastOpenSettingsModule = settingsPanel.getModule();
        } else if (lastOpenSettingsModule != null && lastOpenSettingsModule.getModuleCategory() == category) {
            lastOpenSettingsModule = null;
        }
    }

    public static void clearLastOpenSettings() {
        lastOpenSettingsModule = null;
    }

    public static void clearAllSavedPositions() {
        lastPanelPositions.clear();
        lastPanelDragStates.clear();
    }

    private boolean isMouseOver(double mouseX, double mouseY) {
        int visibleCount = 0;
        for (ModuleButton button : moduleButtons) {
            if (button.getSearchAlpha() > 0.01f) visibleCount++;
        }
        int totalHeight = HEADER_HEIGHT + (visibleCount * MODULE_HEIGHT);
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + totalHeight;
    }

    private boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}