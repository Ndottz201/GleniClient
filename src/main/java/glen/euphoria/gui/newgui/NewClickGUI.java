package glen.euphoria.gui.newgui;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.gui.newgui.components.CategoryIcon;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.ModuleManager;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.module.setting.*;
import glen.euphoria.profiles.ProfileManager;
import glen.euphoria.utils.render.GuiGlowHelper;
import glen.euphoria.utils.render.blur.BackdropBlur;
import glen.euphoria.utils.render.blur.BlurRenderer;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NewClickGUI extends Screen {
    private static final float PANEL_WIDTH_FULL = 514f;
    private static final float PANEL_WIDTH_COMPACT = 380f;
    private static final float PANEL_HEIGHT = 296f;
    private static final float PANEL_RADIUS = 18f;

    private static final float DRAWER_WIDTH = 234f;
    private static final float DRAWER_RADIUS = 16f;
    private static final float DRAWER_GAP = 8f;

    private static final float SIDEBAR_WIDTH = 102f;
    private static final float HEADER_HEIGHT = 44f;
    private static final float PADDING = 10f;

    private static final float CATEGORY_HEIGHT = 24f;
    private static final float CATEGORY_GAP = 6f;

    private static final float MODULE_HEIGHT = 31f;
    private static final float MODULE_GAP = 6f;

    private static final float DRAWER_ROW_HEIGHT = 26f;
    private static final float DRAWER_ROW_GAP = 7f;

    private static final float PROFILE_BOX_HEIGHT = 60f;

    private static final Color SHELL_FILL_TOP = new Color(8, 14, 28, 138);
    private static final Color SHELL_FILL_BOTTOM = new Color(6, 11, 22, 128);
    private static final Color SHELL_TINT = new Color(18, 46, 96, 38);
    private static final Color SHELL_INNER = new Color(255, 255, 255, 11);
    private static final Color SHELL_DIVIDER = new Color(255, 255, 255, 10);

    private static final Color SEARCH_FILL = new Color(10, 18, 34, 170);
    private static final Color ROW_FILL = new Color(12, 20, 36, 154);
    private static final Color ROW_FILL_ALT = new Color(14, 24, 43, 176);
    private static final Color DRAWER_FILL_TOP = new Color(8, 15, 29, 164);
    private static final Color DRAWER_FILL_BOTTOM = new Color(6, 12, 24, 154);

    private static final Color TEXT_PRIMARY = new Color(242, 247, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(160, 180, 206, 255);
    private static final Color TEXT_MUTED = new Color(108, 128, 154, 255);

    private final Random random = new Random();
    private final List<MicroParticle> particles = new ArrayList<>();
    private final Map<String, Float> values = new HashMap<>();
    private final Map<String, Float> velocities = new HashMap<>();

    private float openProgress = 0f;
    private boolean closing = false;
    private final long openTimeMs = System.currentTimeMillis();
    private long lastFrameTime = System.currentTimeMillis();
    // GUI scale captured at open time so moving the slider doesn't resize the GUI mid-session.
    private float sessionGuiScale = 1.0f;

    private Category selectedCategory;
    private Module selectedModule;
    private Module drawerModule;
    private Module displayedDrawerModule;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private float categoryScroll = 0f;
    private float targetCategoryScroll = 0f;

    private float moduleScroll = 0f;
    private float targetModuleScroll = 0f;

    private float drawerScroll = 0f;
    private float targetDrawerScroll = 0f;

    private float categoryIndicatorY = 0f;

    private Setting draggingSlider = null;
    private RangeSetting draggingRange = null;
    private boolean draggingRangeMin = false;

    private KeybindSetting listeningKeybind = null;

    private NumberSetting typingNumber = null;
    private String typingNumberText = "";

    private RangeSetting typingRange = null;
    private boolean typingRangeMin = false;
    private String typingRangeText = "";

    private ModeSetting expandedMode = null;

    private DrawContext lastContext;

    public DrawContext getLastContext() {
        return lastContext;
    }

    // Config drawer related fields
    private static final float CONFIG_DRAWER_WIDTH = 220f;
    private boolean configPanelOpen = false;
    private float configScroll = 0f;
    private float targetConfigScroll = 0f;
    private String newConfigName = "";
    private boolean namingNewConfig = false;
    private String deleteConfirmConfig = null;
    private long deleteConfirmTime = 0L;
    private String activeProfile = "default";
    private final List<String> savedConfigs = new ArrayList<>();
    private float configBtnX = 0f, configBtnY = 0f;
    private float configBtnW = 20f, configBtnH = 16f;
    private int configIcon = -1;

    public NewClickGUI() {
        super(Text.literal("Euphoria"));
        values.put("open_progress", 0f);
        initParticles();
        initializeSelection();
    }

    @Override
    protected void init() {
        super.init();
        NanoVGRenderer.init();
        CategoryIcon.init();
        configIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/file.png");
        validateSelection();
        refreshSavedConfigs();
        lastFrameTime = System.currentTimeMillis();
        // Snapshot the GUI scale on open; slider changes apply next time the GUI is opened.
        sessionGuiScale = ClientSettingsModule.getGuiScaleFactor();
    }

    private void refreshSavedConfigs() {
        savedConfigs.clear();
        ProfileManager profileManager = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
        if (profileManager != null) {
            File profileDir = profileManager.getProfileDir();
            if (profileDir != null && profileDir.exists() && profileDir.isDirectory()) {
                File[] files = profileDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File file : files) {
                        savedConfigs.add(file.getName().replace(".json", ""));
                    }
                }
            }
        }
    }

    @Override
    public void tick() {
        validateSelection();
    }

    @Override
    public void close() {
        if (closing) {
            MinecraftClient mcInstance = client != null ? client : MinecraftClient.getInstance();
            mcInstance.setScreen(null);
            return;
        }
        closing = true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void initializeSelection() {
        List<Category> categories = getValidCategories();
        if (!categories.isEmpty()) {
            selectedCategory = categories.get(0);
            List<Module> modules = getFilteredModules(selectedCategory);
            if (!modules.isEmpty()) {
                selectedModule = modules.get(0);
            }
        }
    }

    private void validateSelection() {
        List<Category> categories = getValidCategories();
        if (categories.isEmpty()) {
            selectedCategory = null;
            selectedModule = null;
            drawerModule = null;
            return;
        }

        if (selectedCategory == null || !categories.contains(selectedCategory)) {
            selectedCategory = categories.get(0);
        }

        List<Module> modules = getFilteredModules(selectedCategory);
        if (modules.isEmpty()) {
            selectedModule = null;
        } else if (selectedModule == null || !modules.contains(selectedModule)) {
            selectedModule = modules.get(0);
        }

        if (drawerModule != null) {
            boolean valid = !searchQuery.isEmpty()
                    ? modules.contains(drawerModule)
                    : (drawerModule.getModuleCategory() == selectedCategory && modules.contains(drawerModule));
            if (!valid) {
                drawerModule = null;
                expandedMode = null;
                targetDrawerScroll = 0f;
                drawerScroll = 0f;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.lastContext = context;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        float openTarget = closing ? 0f : 1f;
        float stiffness = closing ? 0.16f : 0.08f;
        openProgress = euphoriaSpring("open_progress", openTarget, dt, stiffness, 0.92f);
        if (closing && openProgress <= 0.03f) {
            openProgress = 0f;
            MinecraftClient mcInstance = client != null ? client : MinecraftClient.getInstance();
            mcInstance.setScreen(null);
            return;
        }

        float alpha = easeOutCubic(openProgress);
        float scale = guiRenderScale();

        float panelWidthTarget = (drawerModule != null || configPanelOpen) ? PANEL_WIDTH_COMPACT : PANEL_WIDTH_FULL;
        float panelWidth = euphoriaSpring("panel_width", panelWidthTarget, dt, 0.08f, 0.90f);

        categoryScroll = euphoriaSpring("category_scroll", targetCategoryScroll, dt, 0.10f, 0.88f);
        moduleScroll = euphoriaSpring("module_scroll", targetModuleScroll, dt, 0.10f, 0.88f);
        drawerScroll = euphoriaSpring("drawer_scroll", targetDrawerScroll, dt, 0.10f, 0.88f);
        configScroll = euphoriaSpring("config_scroll", targetConfigScroll, dt, 0.10f, 0.88f);

        if (deleteConfirmConfig != null && System.currentTimeMillis() - deleteConfirmTime >= 3000L) {
            deleteConfirmConfig = null;
        }

        updateParticles(dt, mouseX, mouseY);
        updateCategoryIndicator(dt, panelWidth);

        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - PANEL_HEIGHT) / 2f;

        if (drawerModule != null) {
            displayedDrawerModule = drawerModule;
        }

        float drawerTarget = drawerModule != null ? 1f : 0f;
        float drawerAnim = euphoriaSpring("drawer_open", drawerTarget, dt, 0.10f, 0.88f);
        if (drawerAnim <= 0.02f && drawerModule == null) {
            displayedDrawerModule = null;
        }
        float drawerWidth = DRAWER_WIDTH * easeOutCubic(drawerAnim);
        float drawerX = panelX + panelWidth + DRAWER_GAP;
        float drawerY = panelY + 28f;
        float drawerHeight = PANEL_HEIGHT - 34f;

        float configTarget = configPanelOpen ? 1f : 0f;
        float configAnim = euphoriaSpring("config_open", configTarget, dt, 0.10f, 0.88f);
        float configWidth = CONFIG_DRAWER_WIDTH * easeOutCubic(configAnim);
        float configX = panelX - DRAWER_GAP - configWidth;
        float configY = panelY + 28f;
        float configHeight = PANEL_HEIGHT - 34f;

        int centerX = width / 2;
        int centerY = height / 2;

        int transformedMouseX = (int) ((mouseX - centerX) / scale + centerX);
        int transformedMouseY = (int) ((mouseY - centerY) / scale + centerY);

        // Real glassmorphism backdrop: blur MC's main color (the world behind the GUI) before the
        // NanoVG frame, then paint it behind the panel so the translucent shell reads as frosted glass.
        int blurImage = -1;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null) {
            blurImage = BackdropBlur.render(
                    NanoVGRenderer.getContext(),
                    mc.getWindow().getFramebufferWidth(),
                    mc.getWindow().getFramebufferHeight(),
                    Math.max(1.5f, ClientSettingsModule.getPanelBlurRadius() / 4f));
        }

        // Render at higher pixel density so text/shapes stay crisp (HD) when scaled down.
        float densityMul = Math.min(4f, 2f / Math.min(1f, sessionGuiScale));
        NanoVGRenderer.beginFrame(densityMul);

        renderGlassBackdrop(blurImage, panelX, panelY, panelWidth, drawerAnim, drawerX, drawerY, drawerWidth, drawerHeight,
                configAnim, configX, configY, configWidth, configHeight, scale, centerX, centerY, alpha);

        NanoVGRenderer.save();
        NanoVGRenderer.translate(centerX, centerY);
        NanoVGRenderer.scale(scale, scale);
        NanoVGRenderer.translate(-centerX, -centerY);

        renderShell(panelX, panelY, panelWidth, transformedMouseX, transformedMouseY, alpha, dt);
        if (drawerAnim > 0.02f && displayedDrawerModule != null) {
            renderDrawer(drawerX, drawerY, drawerWidth, drawerHeight, transformedMouseX, transformedMouseY, alpha, dt);
        }

        if (configAnim > 0.02f) {
            renderConfigDrawer(configX, configY, configWidth, configHeight, transformedMouseX, transformedMouseY, alpha, dt);
        }

        NanoVGRenderer.restore();

        NanoVGRenderer.endFrame();

        if (ClientSettingsModule.isGuiGlowEnabled()) {
            GuiGlowHelper.drawGuiGlow(
                    context,
                    (panelX - centerX) * scale + centerX,
                    (panelY - centerY) * scale + centerY,
                    panelWidth * scale,
                    PANEL_HEIGHT * scale,
                    PANEL_RADIUS * scale
            );

            if (drawerAnim > 0.10f) {
                GuiGlowHelper.drawGuiGlow(
                        context,
                        (drawerX - centerX) * scale + centerX,
                        (drawerY - centerY) * scale + centerY,
                        (drawerWidth - 2f) * scale,
                        drawerHeight * scale,
                        DRAWER_RADIUS * scale
                );
            }

            if (configAnim > 0.10f) {
                GuiGlowHelper.drawGuiGlow(
                        context,
                        (configX - centerX) * scale + centerX,
                        (configY - centerY) * scale + centerY,
                        (configWidth - 2f) * scale,
                        configHeight * scale,
                        DRAWER_RADIUS * scale
                );
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        float scale = guiRenderScale();
        int centerX = width / 2;
        int centerY = height / 2;
        int transformedMouseX = (int) ((mouseX - centerX) / scale + centerX);
        int transformedMouseY = (int) ((mouseY - centerY) / scale + centerY);

        // Listening for a keybind: capture mouse buttons too (item 3). Stored as -100-button to
        // match getKeyName()/KeyUtils. The click that STARTED listening set listeningKeybind on a
        // prior event, so this binds the next click.
        if (listeningKeybind != null) {
            listeningKeybind.setKeyCode(-100 - button);
            listeningKeybind = null;
            return true;
        }

        int mouseKey = -100 - button;
        if (isGuiToggleKey(mouseKey)) {
            close();
            return true;
        }

        float panelWidth = values.getOrDefault("panel_width", PANEL_WIDTH_FULL);
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - PANEL_HEIGHT) / 2f;

        float configTarget = configPanelOpen ? 1f : 0f;
        float configAnim = values.getOrDefault("config_open", configTarget);
        float configWidth = CONFIG_DRAWER_WIDTH * easeOutCubic(configAnim);
        float configX = panelX - DRAWER_GAP - configWidth;
        float configY = panelY + 28f;
        float configHeight = PANEL_HEIGHT - 34f;

        // Config button in profile card
        if (isHovering(configBtnX, configBtnY, configBtnW, configBtnH, transformedMouseX, transformedMouseY)) {
            configPanelOpen = !configPanelOpen;
            if (configPanelOpen) {
                refreshSavedConfigs();
                namingNewConfig = false;
                targetConfigScroll = 0f;
                configScroll = 0f;
            }
            return true;
        }

        // Config drawer click handling (if open and animated in)
        if (configPanelOpen && configAnim > 0.05f) {
            if (handleConfigDrawerClick(transformedMouseX, transformedMouseY, button, configX, configY, configWidth, configHeight)) {
                return true;
            }
        }

        if (handleSearchClick(transformedMouseX, transformedMouseY, button, panelX, panelY, panelWidth)) return true;
        if (handleCategoryClick(transformedMouseX, transformedMouseY, button, panelX, panelY)) return true;
        if (handleModuleClick(transformedMouseX, transformedMouseY, button, panelX, panelY, panelWidth)) return true;
        if (drawerModule != null && handleDrawerClick(transformedMouseX, transformedMouseY, button, panelX, panelY)) return true;

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        int button = click.button();
        if (button == 0) {
            draggingSlider = null;
            draggingRange = null;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        float scale = guiRenderScale();
        int centerX = width / 2;
        int centerY = height / 2;
        double transformedMouseX = (mouseX - centerX) / scale + centerX;

        if (button == 0) {
            if (draggingSlider instanceof NumberSetting numberSetting) {
                float panelWidth = values.getOrDefault("panel_width", PANEL_WIDTH_FULL);
                float panelX = (width - panelWidth) / 2f;
                float drawerX = panelX + panelWidth + DRAWER_GAP;
                float contentX = drawerX + 10f;
                float sliderX = contentX + 10f;
                float sliderW = DRAWER_WIDTH - 42f;
                updateNumberSettingFromMouse(numberSetting, transformedMouseX, sliderX, sliderW);
                return true;
            }

            if (draggingRange != null) {
                float panelWidth = values.getOrDefault("panel_width", PANEL_WIDTH_FULL);
                float panelX = (width - panelWidth) / 2f;
                float drawerX = panelX + panelWidth + DRAWER_GAP;
                float contentX = drawerX + 10f;
                float sliderX = contentX + 10f;
                float sliderW = DRAWER_WIDTH - 42f;
                updateRangeSettingFromMouse(draggingRange, transformedMouseX, sliderX, sliderW, draggingRangeMin);
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float scale = guiRenderScale();
        int centerX = width / 2;
        int centerY = height / 2;
        int transformedMouseX = (int) ((mouseX - centerX) / scale + centerX);
        int transformedMouseY = (int) ((mouseY - centerY) / scale + centerY);

        float panelWidth = values.getOrDefault("panel_width", PANEL_WIDTH_FULL);
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - PANEL_HEIGHT) / 2f;

        float configTarget = configPanelOpen ? 1f : 0f;
        float configAnim = values.getOrDefault("config_open", configTarget);
        if (configPanelOpen && configAnim > 0.05f) {
            float configWidth = CONFIG_DRAWER_WIDTH * easeOutCubic(configAnim);
            float configX = panelX - DRAWER_GAP - configWidth;
            float configY = panelY + 28f;
            float configHeight = PANEL_HEIGHT - 34f;
            if (isHovering(configX, configY, configWidth, configHeight, transformedMouseX, transformedMouseY)) {
                targetConfigScroll -= (float) verticalAmount * 20f;
                return true;
            }
        }

        float scrollAmount = (float) verticalAmount * 18f;

        float catX = panelX + 10f;
        float catY = panelY + HEADER_HEIGHT + 6f;
        float catW = SIDEBAR_WIDTH - 20f;
        float catH = PANEL_HEIGHT - HEADER_HEIGHT - PROFILE_BOX_HEIGHT - 12f;

        if (isHovering(catX, catY, catW, catH, transformedMouseX, transformedMouseY)) {
            float maxScroll = Math.max(0f, getValidCategories().size() * (CATEGORY_HEIGHT + CATEGORY_GAP) - catH);
            targetCategoryScroll = Math.max(0f, Math.min(maxScroll, targetCategoryScroll - scrollAmount));
            return true;
        }

        float modX = panelX + SIDEBAR_WIDTH + PADDING;
        float modY = panelY + HEADER_HEIGHT + 6f;
        float modW = panelWidth - SIDEBAR_WIDTH - PADDING * 2f;
        float modH = PANEL_HEIGHT - HEADER_HEIGHT - 16f;

        if (isHovering(modX, modY, modW, modH, transformedMouseX, transformedMouseY)) {
            List<Module> modules = getFilteredModules(selectedCategory);
            float totalHeight = 0f;
            for (Module module : modules) {
                totalHeight += moduleRowHeight(module) + MODULE_GAP;
            }
            float maxScroll = Math.max(0f, totalHeight - modH);
            targetModuleScroll = Math.max(0f, Math.min(maxScroll, targetModuleScroll - scrollAmount));
            return true;
        }

        if (drawerModule != null) {
            float drawerX = panelX + panelWidth + DRAWER_GAP;
            float contentX = drawerX + 10f;
            float contentY = panelY + 28f + 36f;
            float contentW = DRAWER_WIDTH - 22f;
            float contentH = PANEL_HEIGHT - 34f - 42f;

            if (isHovering(contentX, contentY, contentW, contentH, transformedMouseX, transformedMouseY)) {
                float totalHeight = calculateDrawerContentHeight();
                float maxScroll = Math.max(0f, totalHeight - contentH);
                targetDrawerScroll = Math.max(0f, Math.min(maxScroll, targetDrawerScroll - scrollAmount));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (configPanelOpen) {
            if (handleConfigDrawerKey(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (listeningKeybind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningKeybind.setKeyCode(0);
            } else {
                listeningKeybind.setKeyCode(keyCode);
            }
            listeningKeybind = null;
            return true;
        }

        if (typingNumber != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                typingNumber = null;
                typingNumberText = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                try {
                    double value = Double.parseDouble(typingNumberText);
                    typingNumber.setValue(value);
                } catch (NumberFormatException ignored) {}
                typingNumber = null;
                typingNumberText = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!typingNumberText.isEmpty()) {
                    typingNumberText = typingNumberText.substring(0, typingNumberText.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (typingRange != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                typingRange = null;
                typingRangeText = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                try {
                    double value = Double.parseDouble(typingRangeText);
                    if (typingRangeMin) typingRange.setMinValue(value);
                    else typingRange.setMaxValue(value);
                } catch (NumberFormatException ignored) {}
                typingRange = null;
                typingRangeText = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!typingRangeText.isEmpty()) {
                    typingRangeText = typingRangeText.substring(0, typingRangeText.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    resetModuleScroll();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (isGuiToggleKey(keyCode)) {
                searchFocused = false;
                close();
                return true;
            }
            return true;
        }

        if (isGuiToggleKey(keyCode)) {
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (configPanelOpen && namingNewConfig) {
            if (handleConfigDrawerChar(chr, modifiers)) {
                return true;
            }
        }

        if (typingNumber != null) {
            if (Character.isDigit(chr) || chr == '.' || chr == '-') {
                typingNumberText += chr;
            }
            return true;
        }

        if (typingRange != null) {
            if (Character.isDigit(chr) || chr == '.' || chr == '-') {
                typingRangeText += chr;
            }
            return true;
        }

        if (searchFocused) {
            if (Character.isLetterOrDigit(chr) || chr == ' ') {
                searchQuery += chr;
                resetModuleScroll();
            }
            return true;
        }

        return super.charTyped(input);
    }

    // ---------- Existing handlers (unchanged except minor additions) ----------
    private boolean handleSearchClick(int mouseX, int mouseY, int button, float panelX, float panelY, float panelWidth) {
        if (button != 0) return false;

        float searchW = 166f;
        float searchH = 24f;
        float searchX = panelX + panelWidth - searchW - 12f;
        float searchY = panelY + 9f;

        if (isHovering(searchX, searchY, searchW, searchH, mouseX, mouseY)) {
            searchFocused = true;
            springPulse("search_click");
            return true;
        } else {
            searchFocused = false;
        }

        return false;
    }

    private boolean handleCategoryClick(int mouseX, int mouseY, int button, float panelX, float panelY) {
        if (button != 0) return false;

        float listX = panelX + 10f;
        float listY = panelY + HEADER_HEIGHT + 6f;
        float listW = SIDEBAR_WIDTH - 20f;

        float cy = listY - categoryScroll;
        for (Category category : getValidCategories()) {
            if (isHovering(listX, cy, listW, CATEGORY_HEIGHT, mouseX, mouseY)) {
                if (selectedCategory != category || !searchQuery.isEmpty()) {
                    selectedCategory = category;
                    searchQuery = "";
                    searchFocused = false;
                    selectedModule = null;
                    drawerModule = null;
                    expandedMode = null;
                    resetModuleScroll();
                    springPulse("cat_click_" + category.name());
                }
                return true;
            }
            cy += CATEGORY_HEIGHT + CATEGORY_GAP;
        }

        return false;
    }

    private boolean handleConfigDrawerClick(int mouseX, int mouseY, int button, float x, float y, float width, float height) {
        if (!isHovering(x, y, width, height, mouseX, mouseY)) {
            return false;
        }

        // Input field focus
        float inputSectionY = y + 36f;
        float inputH = 22f;
        float inputW = width - 68f;
        float inputX = x + 10f;
        float btnW = 44f;
        float btnX = x + width - btnW - 10f;

        if (button == 0 && isHovering(inputX, inputSectionY, inputW, inputH, mouseX, mouseY)) {
            namingNewConfig = true;
            return true;
        }

        // Save / create new profile button
        if (button == 0 && isHovering(btnX, inputSectionY, btnW, inputH, mouseX, mouseY)) {
            String nameToSave = newConfigName.trim().isEmpty() ? "quick" : newConfigName.trim();
            ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
            if (pm != null) {
                pm.saveProfile(nameToSave, true);
                activeProfile = nameToSave;
                refreshSavedConfigs();
            }
            newConfigName = "";
            namingNewConfig = false;
            return true;
        }

        // Config entries list
        float listX = x + 10f;
        float listY = y + 70f;
        float listW = width - 22f;
        float listH = height - 76f;
        float entryH = 26f;
        float entryGap = 5f;

        if (isHovering(listX, listY, listW, listH, mouseX, mouseY)) {
            float cy = listY - configScroll;
            for (String configName : savedConfigs) {
                if (isHovering(listX, cy, listW, entryH, mouseX, mouseY)) {
                    boolean isDeleting = configName.equals(deleteConfirmConfig);
                    float delW = 34f;
                    float btnH = 16f;
                    float btnY = cy + (entryH - btnH) / 2f;
                    float delX = listX + listW - delW - 5f;
                    float saveW = 26f;
                    float saveX = delX - saveW - 3f;
                    float loadW = 26f;
                    float loadX = saveX - loadW - 3f;

                    // Load button
                    if (button == 0 && isHovering(loadX, btnY, loadW, btnH, mouseX, mouseY)) {
                        ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                        if (pm != null) {
                            pm.loadProfile(configName);
                            activeProfile = configName;
                        }
                        return true;
                    }

                    // Save (overwrite) button
                    if (button == 0 && isHovering(saveX, btnY, saveW, btnH, mouseX, mouseY)) {
                        ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                        if (pm != null) {
                            pm.saveProfile(configName, true);
                            activeProfile = configName;
                            refreshSavedConfigs();
                        }
                        return true;
                    }

                    // Delete button
                    if (button == 0 && isHovering(delX, btnY, delW, btnH, mouseX, mouseY)) {
                        boolean alreadyConfirming = configName.equals(deleteConfirmConfig) && System.currentTimeMillis() - deleteConfirmTime < 3000L;
                        if (alreadyConfirming) {
                            ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                            if (pm != null) {
                                File f = new File(pm.getProfileDir(), configName + ".json");
                                if (f.delete()) {
                                    if (configName.equals(activeProfile)) activeProfile = "default";
                                    deleteConfirmConfig = null;
                                    refreshSavedConfigs();
                                }
                            }
                        } else {
                            deleteConfirmConfig = configName;
                            deleteConfirmTime = System.currentTimeMillis();
                        }
                        return true;
                    }

                    // Clicking card body loads profile
                    if (button == 0) {
                        ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                        if (pm != null) {
                            pm.loadProfile(configName);
                            activeProfile = configName;
                        }
                        return true;
                    }
                }
                cy += entryH + entryGap;
            }
        }

        // Clicking elsewhere in drawer unfocuses input field
        namingNewConfig = false;
        return true;
    }

    private boolean handleConfigDrawerKey(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (namingNewConfig) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!newConfigName.isEmpty()) {
                    newConfigName = newConfigName.substring(0, newConfigName.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!newConfigName.trim().isEmpty()) {
                    ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                    if (pm != null) {
                        pm.saveProfile(newConfigName.trim(), true);
                        activeProfile = newConfigName.trim();
                        refreshSavedConfigs();
                    }
                    newConfigName = "";
                }
                namingNewConfig = false;
                return true;
            }
        }
        return false;
    }

    private boolean handleConfigDrawerChar(char chr, int modifiers) {
        if (namingNewConfig) {
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ' ') {
                if (newConfigName.length() < 24) {
                    newConfigName += chr;
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleModuleClick(int mouseX, int mouseY, int button, float panelX, float panelY, float panelWidth) {
        if (selectedCategory == null) return false;

        List<Module> modules = getFilteredModules(selectedCategory);

        float listX = panelX + SIDEBAR_WIDTH + PADDING;
        float listY = panelY + HEADER_HEIGHT + 6f;
        float listW = panelWidth - SIDEBAR_WIDTH - PADDING * 2f;

        float rowY = listY - moduleScroll;
        for (Module module : modules) {
            float rowH = moduleRowHeight(module);
            if (isHovering(listX, rowY, listW, rowH, mouseX, mouseY)) {
                if (button == 0) {
                    float toggleW = 28f;
                    float toggleX = listX + listW - toggleW - 10f;

                    if (module.getSettings().size() > 1 && mouseX >= listX + listW - 65f && mouseX < toggleX) {
                        if (drawerModule == module) {
                            drawerModule = null;
                            expandedMode = null;
                            targetDrawerScroll = 0f;
                        } else {
                            drawerModule = module;
                            selectedModule = module;
                            selectedCategory = module.getModuleCategory();
                            expandedMode = null;
                            targetDrawerScroll = 0f;
                        }
                        springPulse("drawer_toggle_" + module.getName());
                        return true;
                    }

                    module.toggle();
                    selectedModule = module;
                    selectedCategory = module.getModuleCategory();
                    springPulse("mod_toggle_" + module.getName());
                    springPulse("mod_select_" + module.getName());
                    return true;

                } else if (button == 1) {
                    if (module.getSettings().size() > 1) {
                        if (drawerModule == module) {
                            drawerModule = null;
                            expandedMode = null;
                            targetDrawerScroll = 0f;
                        } else {
                            drawerModule = module;
                            selectedModule = module;
                            selectedCategory = module.getModuleCategory();
                            expandedMode = null;
                            targetDrawerScroll = 0f;
                        }
                        springPulse("drawer_toggle_" + module.getName());
                        return true;
                    }
                }
            }
            rowY += rowH + MODULE_GAP;
        }

        return false;
    }

    private boolean handleDrawerClick(int mouseX, int mouseY, int button, float panelX, float panelY) {
        float panelWidth = values.getOrDefault("panel_width", PANEL_WIDTH_FULL);
        float drawerX = panelX + panelWidth + DRAWER_GAP;
        float contentX = drawerX + 10f;
        float contentY = panelY + 28f + 36f;
        float contentW = DRAWER_WIDTH - 22f;
        float contentH = PANEL_HEIGHT - 34f - 42f;

        if (!isHovering(contentX, contentY, contentW, contentH, mouseX, mouseY)) {
            if (expandedMode != null) {
                expandedMode = null;
                return true;
            }
            return false;
        }

        float sy = contentY - drawerScroll;
        for (Setting setting : drawerModule.getSettings()) {
            if (setting instanceof KeybindSetting keybindSetting && keybindSetting.isModuleKey()) {
                if (handleKeybindClick(keybindSetting, contentX, sy, contentW, mouseX, mouseY, button)) return true;
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
                continue;
            }

            if (setting instanceof BooleanSetting booleanSetting) {
                if (handleBooleanClick(booleanSetting, contentX, sy, contentW, mouseX, mouseY, button)) return true;
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            } else if (setting instanceof NumberSetting numberSetting) {
                if (handleNumberClick(numberSetting, contentX, sy, contentW, mouseX, mouseY, button)) return true;
                sy += DRAWER_ROW_HEIGHT + 12f;
            } else if (setting instanceof RangeSetting rangeSetting) {
                if (handleRangeClick(rangeSetting, contentX, sy, contentW, mouseX, mouseY, button)) return true;
                sy += DRAWER_ROW_HEIGHT + 12f;
            } else if (setting instanceof ModeSetting modeSetting) {
                float consumed = handleModeClick(modeSetting, contentX, sy, contentW, mouseX, mouseY, button);
                if (consumed > 0) return true;
                sy += calculateModeSettingHeight(modeSetting);
            } else if (setting instanceof ColorSetting colorSetting) {
                if (handleColorClick(colorSetting, contentX, sy, contentW, mouseX, mouseY, button)) return true;
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            } else if (setting instanceof KeybindSetting keybindSetting) {
                if (handleKeybindClick(keybindSetting, contentX, sy, contentW, mouseX, mouseY, button)) return true;
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            }
        }

        return false;
    }

    private boolean handleBooleanClick(BooleanSetting setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        if (button == 0 && isHovering(x, y, w, DRAWER_ROW_HEIGHT, mouseX, mouseY)) {
            setting.setValue(!setting.getValue());
            springPulse("bool_click_" + setting.getName());
            return true;
        }
        return false;
    }

    private boolean handleNumberClick(NumberSetting setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        float sx = x + 14f;
        float sw = w - 28f;
        float sy = y + 22f;
        float sh = Math.max(4f, ClientSettingsModule.getSliderHeight());

        if (button == 0 && isHovering(sx, sy, sw, sh, mouseX, mouseY)) {
            draggingSlider = setting;
            updateNumberSettingFromMouse(setting, mouseX, sx, sw);
            springPulse("num_drag_" + setting.getName());
            return true;
        }

        if (button == 1 && isHovering(x + w - 84f, y + 6f, 74f, 18f, mouseX, mouseY)) {
            typingNumber = setting;
            typingNumberText = "";
            springPulse("num_type_" + setting.getName());
            return true;
        }

        return false;
    }

    private boolean handleRangeClick(RangeSetting setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        float sx = x + 14f;
        float sw = w - 28f;
        float sy = y + 22f;
        float sh = Math.max(4f, ClientSettingsModule.getSliderHeight());

        if (isHovering(sx, sy, sw, sh, mouseX, mouseY)) {
            double range = setting.getMax() - setting.getMin();
            float minPct = (float) ((setting.getMinValue() - setting.getMin()) / range);
            float maxPct = (float) ((setting.getMaxValue() - setting.getMin()) / range);

            float minX = sx + sw * minPct;
            float maxX = sx + sw * maxPct;

            float hr = ClientSettingsModule.getSliderHandleSize();
            float distMin = Math.abs(mouseX - minX);
            float distMax = Math.abs(mouseX - maxX);

            if (distMin < hr + 4f || distMax < hr + 4f) {
                draggingRange = setting;
                draggingRangeMin = distMin < distMax;
                updateRangeSettingFromMouse(setting, mouseX, sx, sw, draggingRangeMin);
                springPulse("range_drag_" + setting.getName());
                return true;
            }
        }

        return false;
    }

    private float handleModeClick(ModeSetting setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        if (button != 0) return 0;

        float boxW = 118f;
        float boxH = 18f;
        float bx = x + w - boxW - 12f;
        float by = y + (DRAWER_ROW_HEIGHT - boxH) / 2f;

        if (isHovering(bx, by, boxW, boxH, mouseX, mouseY)) {
            if (expandedMode == setting) {
                expandedMode = null;
            } else {
                expandedMode = setting;
            }
            springPulse("mode_expand_" + setting.getName());
            return 1f;
        }

        float expandAnim = values.getOrDefault("mode_expand_" + setting.getName(), 0f);
        if (expandAnim > 0.5f && expandedMode == setting) {
            float dropdownY = y + DRAWER_ROW_HEIGHT + 4f;
            float oy = dropdownY + 2f;
            for (String option : setting.getModes()) {
                if (isHovering(bx, oy, boxW, 18f, mouseX, mouseY)) {
                    setting.setMode(option);
                    expandedMode = null;
                    springPulse("mode_select_" + setting.getName());
                    return 1f;
                }
                oy += 20f;
            }
        }

        return 0f;
    }

    private boolean handleColorClick(ColorSetting setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        float boxW = 24f;
        float boxH = 16f;
        float boxX = x + w - boxW - 12f;
        float boxY = y + (DRAWER_ROW_HEIGHT - boxH) / 2f;
        if (button == 0 && isHovering(boxX, boxY, boxW, boxH, mouseX, mouseY)) {
            cycleColor(setting);
            springPulse("color_click_" + setting.getName());
            return true;
        }
        return false;
    }

    private boolean handleKeybindClick(KeybindSetting setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        float modeW = 44f;
        float keyH = 18f;
        float keyY = y + (DRAWER_ROW_HEIGHT - keyH) / 2f;
        float modeX = x + w - modeW - 12f;

        float keyW = 56f;
        float keyX = modeX - keyW - 6f;

        if (button == 0 && isHovering(keyX, keyY, keyW, keyH, mouseX, mouseY)) {
            listeningKeybind = setting;
            springPulse("key_listen_" + setting.getName());
            return true;
        }

        if (button == 0 && isHovering(modeX, keyY, modeW, keyH, mouseX, mouseY)) {
            setting.setHoldMode(!setting.isHoldMode());
            springPulse("key_mode_" + setting.getName());
            return true;
        }

        return false;
    }

    private void resetModuleScroll() {
        targetModuleScroll = 0f;
        moduleScroll = 0f;
    }

    private float calculateDrawerContentHeight() {
        Module module = displayedDrawerModule != null ? displayedDrawerModule : drawerModule;
        if (module == null) return 0f;

        float total = 0f;
        for (Setting setting : module.getSettings()) {
            if (setting instanceof KeybindSetting keybindSetting && keybindSetting.isModuleKey()) {
                total += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
                continue;
            }

            if (setting instanceof BooleanSetting || setting instanceof ColorSetting || setting instanceof KeybindSetting) {
                total += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            } else if (setting instanceof NumberSetting || setting instanceof RangeSetting) {
                total += DRAWER_ROW_HEIGHT + 12f;
            } else if (setting instanceof ModeSetting modeSetting) {
                total += calculateModeSettingHeight(modeSetting);
            }
        }

        return total;
    }

    private float calculateModeSettingHeight(ModeSetting setting) {
        float base = DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
        float expandAnim = values.getOrDefault("mode_expand_" + setting.getName(), 0f);
        if (expandAnim > 0.01f) {
            float fullHeight = setting.getModes().size() * 20f + 4f;
            base += fullHeight * expandAnim;
        }
        return base;
    }

    /**
     * Draws the blurred backdrop (item 8) behind the panel/drawer in screen space, clipped to the
     * scaled panel rounded-rects. The blur image spans the full window; mapping it across
     * (0,0)-(width,height) keeps it aligned with the world behind, so the translucent shell on top
     * reads as frosted glass.
     */
    private void renderGlassBackdrop(int blurImage, float panelX, float panelY, float panelWidth,
                                     float drawerAnim, float drawerX, float drawerY, float drawerWidth, float drawerHeight,
                                     float configAnim, float configX, float configY, float configWidth, float configHeight,
                                     float scale, int centerX, int centerY, float alpha) {
        if (blurImage <= 0) return;

        float px = (panelX - centerX) * scale + centerX;
        float py = (panelY - centerY) * scale + centerY;
        NanoVGRenderer.drawImagePatternRounded(blurImage, px, py, panelWidth * scale, PANEL_HEIGHT * scale,
                PANEL_RADIUS * scale, 0f, 0f, width, height, alpha);

        if (drawerAnim > 0.02f && displayedDrawerModule != null) {
            float dx = (drawerX - centerX) * scale + centerX;
            float dy = (drawerY - centerY) * scale + centerY;
            NanoVGRenderer.drawImagePatternRounded(blurImage, dx, dy, (drawerWidth - 2f) * scale, drawerHeight * scale,
                    DRAWER_RADIUS * scale, 0f, 0f, width, height, alpha);
        }

        if (configAnim > 0.02f) {
            float cx = (configX - centerX) * scale + centerX;
            float cy = (configY - centerY) * scale + centerY;
            NanoVGRenderer.drawImagePatternRounded(blurImage, cx, cy, (configWidth - 2f) * scale, configHeight * scale,
                    DRAWER_RADIUS * scale, 0f, 0f, width, height, alpha);
        }
    }

    private void renderBlurPass(DrawContext context, float panelX, float panelY, float panelWidth,
                                float drawerAnim, float drawerX, float drawerY, float drawerWidth, float drawerHeight,
                                float configAnim, float configX, float configY, float configWidth, float configHeight,
                                float alpha) {
        // Disabled: real glassmorphism is handled by BackdropBlur + NanoVG without white halo wash.
    }

    private void renderShell(float x, float y, float panelWidth, int mouseX, int mouseY, float alpha, float dt) {
        Color accent = getAccent();

        NanoVGRenderer.drawRoundedRectGradient(x, y, panelWidth, PANEL_HEIGHT, PANEL_RADIUS, applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));
        NanoVGRenderer.drawRoundedRect(x, y, panelWidth, PANEL_HEIGHT, PANEL_RADIUS, applyAlpha(SHELL_TINT, alpha));

        NanoVGRenderer.drawRoundedRectOutline(
                x, y, panelWidth, PANEL_HEIGHT, PANEL_RADIUS, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (98 * alpha)))
        );
        NanoVGRenderer.drawRoundedRectOutline(
                x + 1f, y + 1f, panelWidth - 2f, PANEL_HEIGHT - 2f, PANEL_RADIUS - 1f, 1f,
                applyAlpha(SHELL_INNER, alpha)
        );

        renderPanelParticles(x, y, panelWidth, PANEL_HEIGHT, alpha);

        NanoVGRenderer.drawRect(x + SIDEBAR_WIDTH, y + 12f, 1f, PANEL_HEIGHT - 24f, applyAlpha(SHELL_DIVIDER, alpha));

        renderHeader(x, y, panelWidth, alpha, mouseX, mouseY, dt);
        renderSidebar(x, y, alpha, mouseX, mouseY, dt);
        renderModuleList(x, y, panelWidth, alpha, mouseX, mouseY, dt);
        renderProfileCard(x, y, alpha, mouseX, mouseY);
    }

    private void renderHeader(float x, float y, float panelWidth, float alpha, int mouseX, int mouseY, float dt) {
        String catName = !searchQuery.isEmpty() ? "Search Results" : (selectedCategory != null ? selectedCategory.getName() : "Modules");
        int count = getFilteredModules(selectedCategory).size();

        int logoImage = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/logo.png");
        if (logoImage != -1) {
            float logoSize = 32f;
            float logoX = x + (SIDEBAR_WIDTH - logoSize) / 2f;
            NanoVGRenderer.drawImage(logoImage, logoX, y + 4f, logoSize, logoSize, new Color(255, 255, 255, clampColor((int) (255 * alpha))));
        }

        float titleWidth = NanoVGRenderer.getTextWidth("Euphoria", 8.8f);
        NanoVGRenderer.drawText("Euphoria", x + (SIDEBAR_WIDTH - titleWidth) / 2f, y + 33f, 8.8f, applyAlpha(TEXT_PRIMARY, alpha));

        NanoVGRenderer.drawText(catName, x + SIDEBAR_WIDTH + 12f, y + 11.5f, 11.8f, applyAlpha(TEXT_PRIMARY, alpha));
        NanoVGRenderer.drawText(count + " modules", x + SIDEBAR_WIDTH + 12f, y + 24.8f, 7.0f, applyAlpha(TEXT_MUTED, alpha));

        float searchW = 166f;
        float searchH = 24f;
        float searchRadius = searchH / 2f;
        float searchX = x + panelWidth - searchW - 12f;
        float searchY = y + 9f;

        boolean hovered = isHovering(searchX, searchY, searchW, searchH, mouseX, mouseY);
        float searchAnim = euphoriaSpring("search_focus", searchFocused ? 1f : hovered ? 0.5f : 0f, dt, 0.08f, 0.90f);

        NanoVGRenderer.drawRoundedRect(searchX, searchY, searchW, searchH, searchRadius, applyAlpha(SEARCH_FILL, alpha));
        NanoVGRenderer.drawRoundedRectOutline(
                searchX, searchY, searchW, searchH, searchRadius, 1f,
                new Color(getAccent().getRed(), getAccent().getGreen(), getAccent().getBlue(), clampColor((int) ((72 + 86 * searchAnim) * alpha)))
        );

        float iconCx = searchX + 13f;
        float iconCy = searchY + 11.0f;
        NanoVGRenderer.drawCircle(iconCx, iconCy, 3.8f, applyAlpha(TEXT_MUTED, alpha));
        NanoVGRenderer.drawCircle(iconCx, iconCy, 2.6f, applyAlpha(SEARCH_FILL, alpha));
        NanoVGRenderer.drawLine(iconCx + 2.7f, iconCy + 2.7f, iconCx + 5.6f, iconCy + 5.6f, 1.1f, applyAlpha(TEXT_MUTED, alpha));

        String text = searchQuery.isEmpty() ? "Search modules..." : searchQuery;
        NanoVGRenderer.drawText(text, searchX + 24f, searchY + 8.0f, 8.2f,
                searchQuery.isEmpty() ? applyAlpha(TEXT_MUTED, alpha) : applyAlpha(TEXT_PRIMARY, alpha));

        if (searchFocused && System.currentTimeMillis() % 1000 < 500) {
            float cx = searchX + 24f + NanoVGRenderer.getTextWidth(searchQuery, 8.2f);
            if (cx < searchX + searchW - 10f) {
                NanoVGRenderer.drawRect(cx, searchY + 7.5f, 1f, 11.0f, applyAlpha(TEXT_PRIMARY, alpha));
            }
        }
    }

    private void renderSidebar(float x, float y, float alpha, int mouseX, int mouseY, float dt) {
        float listX = x + 10f;
        float listY = y + HEADER_HEIGHT + 6f;
        float listW = SIDEBAR_WIDTH - 20f;
        float listH = PANEL_HEIGHT - HEADER_HEIGHT - PROFILE_BOX_HEIGHT - 12f;

        NanoVGRenderer.scissor(listX - 2f, listY - 2f, listW + 4f, listH + 4f);

        Color accent = getAccent();
        float catRadius = CATEGORY_HEIGHT / 2f;

        NanoVGRenderer.drawRoundedRect(listX, categoryIndicatorY, listW, CATEGORY_HEIGHT, catRadius,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (72 * alpha))));
        NanoVGRenderer.drawRoundedRectOutline(listX, categoryIndicatorY, listW, CATEGORY_HEIGHT, catRadius, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (26 * alpha))));

        float cy = listY - categoryScroll;
        for (Category category : getValidCategories()) {
            boolean selected = category == selectedCategory;
            boolean hovered = isHovering(listX, cy, listW, CATEGORY_HEIGHT, mouseX, mouseY);
            float hoverAnim = euphoriaSpring("cat_hover_" + category.name(), hovered ? 1f : 0f, dt, 0.08f, 0.90f);
            float clickAnim = euphoriaSpring("cat_click_" + category.name(), 0f, dt, 0.16f, 0.84f);

            if (!selected && hoverAnim > 0.01f) {
                NanoVGRenderer.drawRoundedRect(listX, cy, listW, CATEGORY_HEIGHT, catRadius,
                        new Color(255, 255, 255, clampColor((int) (8 * hoverAnim * alpha))));
            }

            float scale = 1f - clickAnim * 0.02f;
            if (scale != 1f) {
                NanoVGRenderer.save();
                NanoVGRenderer.translate(listX + listW / 2f, cy + CATEGORY_HEIGHT / 2f);
                NanoVGRenderer.scale(scale, scale);
                NanoVGRenderer.translate(-(listX + listW / 2f), -(cy + CATEGORY_HEIGHT / 2f));
            }

            Color iconColor = selected
                    ? applyAlpha(TEXT_PRIMARY, alpha)
                    : mix(applyAlpha(TEXT_SECONDARY, alpha), applyAlpha(TEXT_PRIMARY, alpha), hoverAnim * 0.35f);

            float iconSize = CategoryIcon.getIconSize();
            float iconX = listX + 9f;
            float iconY = cy + (CATEGORY_HEIGHT - iconSize) / 2f;
            CategoryIcon.drawIcon(category, iconX, iconY, iconColor);

            NanoVGRenderer.drawText(category.getName(), listX + 26f, cy + 8.6f, 8.3f, iconColor);

            if (scale != 1f) {
                NanoVGRenderer.restore();
            }

            cy += CATEGORY_HEIGHT + CATEGORY_GAP;
        }

        NanoVGRenderer.resetScissor();
    }

    private void renderModuleList(float x, float y, float panelWidth, float alpha, int mouseX, int mouseY, float dt) {
        if (selectedCategory == null) return;

        List<Module> modules = getFilteredModules(selectedCategory);

        float listX = x + SIDEBAR_WIDTH + PADDING;
        float listY = y + HEADER_HEIGHT + 6f;
        float listW = panelWidth - SIDEBAR_WIDTH - PADDING * 2f;
        float listH = PANEL_HEIGHT - HEADER_HEIGHT - 16f;

        NanoVGRenderer.scissor(listX - 2f, listY - 2f, listW + 4f, listH + 4f);

        float rowY = listY - moduleScroll;
        for (Module module : modules) {
            float rowH = moduleRowHeight(module);
            float modRadius = rowH / 2f;
            boolean hovered = isHovering(listX, rowY, listW, rowH, mouseX, mouseY);
            boolean drawerOpen = module == drawerModule;

            float hoverAnim = euphoriaSpring("mod_hover_" + module.getName(), hovered ? 1f : 0f, dt, 0.08f, 0.90f);
            float enabledAnim = euphoriaSpring("mod_enabled_" + module.getName(), module.isEnabled() ? 1f : 0f, dt, 0.10f, 0.88f);
            float clickAnim = euphoriaSpring("mod_select_" + module.getName(), 0f, dt, 0.16f, 0.84f);
            float toggleAnim = euphoriaSpring("mod_toggle_" + module.getName(), 0f, dt, 0.16f, 0.84f);

            Color accent = getAccent();

            float scale = 1f - clickAnim * 0.015f - toggleAnim * 0.02f;
            if (scale != 1f) {
                NanoVGRenderer.save();
                NanoVGRenderer.translate(listX + listW / 2f, rowY + rowH / 2f);
                NanoVGRenderer.scale(scale, scale);
                NanoVGRenderer.translate(-(listX + listW / 2f), -(rowY + rowH / 2f));
            }

            NanoVGRenderer.drawRoundedRect(listX, rowY, listW, rowH, modRadius, applyAlpha(ROW_FILL, alpha));

            if (enabledAnim > 0.01f) {
                NanoVGRenderer.drawRoundedRect(listX, rowY, listW, rowH, modRadius,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (18 * enabledAnim * alpha))));
            }

            if (hoverAnim > 0.01f) {
                NanoVGRenderer.drawRoundedRect(listX, rowY, listW, rowH, modRadius,
                        new Color(255, 255, 255, clampColor((int) (10 * hoverAnim * alpha))));
            }

            if (hovered) {
                NanoVGRenderer.drawRoundedRectOutline(
                        listX, rowY, listW, rowH, modRadius, 1f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (28 * hoverAnim * alpha)))
                );
            }

            if (module.isEnabled()) {
                NanoVGRenderer.drawRoundedRect(listX + 10f, rowY + 8f, 2.5f, rowH - 16f, 1.25f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (214 * alpha))));
            }

            if (ClientSettingsModule.isModuleDescriptionsEnabled()) {
                NanoVGRenderer.drawText(module.getName(), listX + 17f, rowY + 7.2f, 8.9f,
                        module.isEnabled() ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));
                // Always one straight line — no description exceeds the row width, so don't wrap.
                NanoVGRenderer.drawText(module.getDescription(), listX + 17f, rowY + 17.1f, 7.1f, applyAlpha(TEXT_MUTED, alpha));
            } else {
                NanoVGRenderer.drawText(module.getName(), listX + 17f, rowY + (rowH - 8.9f) / 2f - 0.2f, 8.9f,
                        module.isEnabled() ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));
            }

            float toggleW = 28f;
            float toggleH = 16f;
            float toggleX = listX + listW - toggleW - 12f;
            float toggleY = rowY + (rowH - toggleH) / 2f;

            Color off = new Color(20, 30, 49, clampColor((int) (255 * alpha)));
            Color on = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha)));
            Color bg = mix(off, on, enabledAnim);

            NanoVGRenderer.drawRoundedRect(toggleX, toggleY, toggleW, toggleH, 8f, bg);

            float knob = 12f;
            float knobX = toggleX + 2f + (toggleW - knob - 4f) * enabledAnim;
            float knobY = toggleY + 2f;

            if (enabledAnim > 0.01f) {
                NanoVGRenderer.drawCircle(knobX + knob / 2f, knobY + knob / 2f, knob / 2f + 2.2f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (65 * enabledAnim * alpha))));
            }
            NanoVGRenderer.drawCircle(knobX + knob / 2f, knobY + knob / 2f, knob / 2f, new Color(245, 247, 255, clampColor((int) (255 * alpha))));

            if (module.getSettings().size() > 1) {
                float arrowAnim = euphoriaSpring("drawer_toggle_" + module.getName(), drawerOpen ? 1f : 0f, dt, 0.10f, 0.88f);
                String arrow = arrowAnim > 0.5f ? "v" : ">";
                Color arrowColor = mix(applyAlpha(TEXT_MUTED, alpha), applyAlpha(TEXT_PRIMARY, alpha * 0.7f), arrowAnim);
                NanoVGRenderer.drawText(arrow, listX + listW - 55f, rowY + 11.4f, 7.8f, arrowColor);
            }

            if (scale != 1f) {
                NanoVGRenderer.restore();
            }

            rowY += rowH + MODULE_GAP;
        }

        NanoVGRenderer.resetScissor();
    }

    private void renderDrawer(float x, float y, float width, float height, int mouseX, int mouseY, float alpha, float dt) {
        if (width < 4f) return;
        Module module = displayedDrawerModule != null ? displayedDrawerModule : drawerModule;
        if (module == null) return;

        Color accent = getAccent();

        NanoVGRenderer.drawRoundedRectGradient(x, y, width - 2f, height, DRAWER_RADIUS, applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));
        NanoVGRenderer.drawRoundedRect(x, y, width - 2f, height, DRAWER_RADIUS, applyAlpha(SHELL_TINT, alpha));
        NanoVGRenderer.drawRoundedRectOutline(
                x, y, width - 2f, height, DRAWER_RADIUS, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (86 * alpha)))
        );
        NanoVGRenderer.drawRoundedRectOutline(
                x + 1f, y + 1f, width - 4f, height - 2f, DRAWER_RADIUS - 1f, 1f,
                applyAlpha(SHELL_INNER, alpha)
        );

        NanoVGRenderer.scissor(x, y, width - 2f, height);

        NanoVGRenderer.drawText(module.getName(), x + 14f, y + 11f, 10.6f, applyAlpha(TEXT_PRIMARY, alpha));
        NanoVGRenderer.drawText(trimToWidth(module.getDescription(), width - 32f, 7.4f), x + 14f, y + 24.8f, 7.4f, applyAlpha(TEXT_MUTED, alpha));

        float contentX = x + 10f;
        float contentY = y + 36f;
        float contentW = Math.max(0f, width - 22f);
        float contentH = height - 42f;

        float maxDrawerScroll = Math.max(0f, calculateDrawerContentHeight() - contentH);
        targetDrawerScroll = Math.max(0f, Math.min(maxDrawerScroll, targetDrawerScroll));

        NanoVGRenderer.scissor(contentX - 2f, contentY - 2f, contentW + 4f, contentH + 4f);

        float sy = contentY - drawerScroll;
        for (Setting setting : module.getSettings()) {
            if (setting instanceof KeybindSetting keybindSetting && keybindSetting.isModuleKey()) {
                renderKeybindSettingRow(keybindSetting, contentX, sy, contentW, mouseX, mouseY, alpha, dt);
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
                continue;
            }

            if (setting instanceof BooleanSetting booleanSetting) {
                renderBooleanSettingRow(booleanSetting, contentX, sy, contentW, mouseX, mouseY, alpha, dt);
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            } else if (setting instanceof NumberSetting numberSetting) {
                renderNumberSettingRow(numberSetting, contentX, sy, contentW, mouseX, mouseY, alpha, dt);
                sy += DRAWER_ROW_HEIGHT + 12f;
            } else if (setting instanceof RangeSetting rangeSetting) {
                renderRangeSettingRow(rangeSetting, contentX, sy, contentW, mouseX, mouseY, alpha, dt);
                sy += DRAWER_ROW_HEIGHT + 12f;
            } else if (setting instanceof ModeSetting modeSetting) {
                sy += renderModeSettingRow(modeSetting, contentX, sy, contentW, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha, dt);
            } else if (setting instanceof ColorSetting colorSetting) {
                renderColorSettingRow(colorSetting, contentX, sy, contentW, mouseX, mouseY, alpha, dt);
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            } else if (setting instanceof KeybindSetting keybindSetting) {
                renderKeybindSettingRow(keybindSetting, contentX, sy, contentW, mouseX, mouseY, alpha, dt);
                sy += DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;
            }
        }

        NanoVGRenderer.resetScissor();
    }

    private void renderConfigDrawer(float x, float y, float width, float height, int mouseX, int mouseY, float alpha, float dt) {
        if (width < 4f) return;

        Color accent = getAccent();

        NanoVGRenderer.drawRoundedRectGradient(x, y, width - 2f, height, DRAWER_RADIUS, applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));
        NanoVGRenderer.drawRoundedRect(x, y, width - 2f, height, DRAWER_RADIUS, applyAlpha(SHELL_TINT, alpha));
        NanoVGRenderer.drawRoundedRectOutline(
                x, y, width - 2f, height, DRAWER_RADIUS, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (86 * alpha)))
        );
        NanoVGRenderer.drawRoundedRectOutline(
                x + 1f, y + 1f, width - 4f, height - 2f, DRAWER_RADIUS - 1f, 1f,
                applyAlpha(SHELL_INNER, alpha)
        );

        NanoVGRenderer.scissor(x, y, width - 2f, height);

        // Header
        NanoVGRenderer.drawText("Profiles", x + 14f, y + 11f, 10.6f, applyAlpha(TEXT_PRIMARY, alpha));
        String countStr = savedConfigs.size() + (savedConfigs.size() == 1 ? " config" : " configs");
        NanoVGRenderer.drawText(countStr, x + 14f, y + 24.8f, 7.4f, applyAlpha(TEXT_MUTED, alpha));

        // Create new profile section
        float inputSectionY = y + 36f;
        float inputH = 22f;
        float inputW = width - 68f;
        float inputX = x + 10f;
        float btnW = 44f;
        float btnX = x + width - btnW - 10f;
        float inputR = inputH / 2f;

        boolean inputHover = isHovering(inputX, inputSectionY, inputW, inputH, mouseX, mouseY);
        NanoVGRenderer.drawRoundedRect(inputX, inputSectionY, inputW, inputH, inputR, applyAlpha(ROW_FILL_ALT, alpha));
        Color inputBorder = namingNewConfig
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (160 * alpha)))
                : (inputHover ? new Color(255, 255, 255, clampColor((int) (28 * alpha))) : new Color(255, 255, 255, clampColor((int) (14 * alpha))));
        NanoVGRenderer.drawRoundedRectOutline(inputX, inputSectionY, inputW, inputH, inputR, 1f, inputBorder);

        String placeholder = namingNewConfig
                ? (newConfigName.isEmpty() ? "_" : newConfigName + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""))
                : (newConfigName.isEmpty() ? "New profile..." : newConfigName);
        Color textColor = namingNewConfig || !newConfigName.isEmpty() ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_MUTED, alpha);
        NanoVGRenderer.drawText(placeholder, inputX + 10f, inputSectionY + 7.2f, 7.6f, textColor);

        // Save / Create Button (pill)
        boolean btnHover = isHovering(btnX, inputSectionY, btnW, inputH, mouseX, mouseY);
        Color btnBg = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) ((btnHover ? 220 : 170) * alpha)));
        NanoVGRenderer.drawRoundedRect(btnX, inputSectionY, btnW, inputH, inputR, btnBg);
        float saveTw = NanoVGRenderer.getTextWidth("+ Save", 7.5f);
        NanoVGRenderer.drawText("+ Save", btnX + (btnW - saveTw) / 2f, inputSectionY + 7.2f, 7.5f, applyAlpha(TEXT_PRIMARY, alpha));

        // Divider
        NanoVGRenderer.drawRect(x + 10f, y + 64f, width - 22f, 1f, new Color(255, 255, 255, clampColor((int) (12 * alpha))));

        // Scrollable Configs Area
        float listX = x + 10f;
        float listY = y + 70f;
        float listW = width - 22f;
        float listH = height - 76f;

        float entryH = 26f;
        float entryGap = 5f;
        float totalListH = savedConfigs.isEmpty() ? 30f : savedConfigs.size() * (entryH + entryGap);
        float maxScroll = Math.max(0f, totalListH - listH);
        targetConfigScroll = Math.max(0f, Math.min(maxScroll, targetConfigScroll));

        NanoVGRenderer.scissor(listX - 2f, listY - 2f, listW + 4f, listH + 4f);

        if (savedConfigs.isEmpty()) {
            String emptyMsg = "No profiles saved";
            float emW = NanoVGRenderer.getTextWidth(emptyMsg, 7.8f);
            NanoVGRenderer.drawText(emptyMsg, listX + (listW - emW) / 2f, listY + 28f, 7.8f, applyAlpha(TEXT_MUTED, alpha));
        } else {
            float cy = listY - configScroll;
            for (String configName : savedConfigs) {
                if (cy + entryH >= listY - 10f && cy <= listY + listH + 10f) {
                    renderConfigEntry(configName, listX, cy, listW, entryH, mouseX, mouseY, alpha, accent);
                }
                cy += entryH + entryGap;
            }
        }

        NanoVGRenderer.resetScissor();

        // Scrollbar
        if (maxScroll > 0.01f) {
            float sbH = Math.max(14f, (listH / totalListH) * listH);
            float sbY = listY + (configScroll / maxScroll) * (listH - sbH);
            NanoVGRenderer.drawRoundedRect(x + width - 4.5f, sbY, 2.5f, sbH, 1.25f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (130 * alpha))));
        }

        NanoVGRenderer.resetScissor();
    }

    private void renderConfigEntry(String name, float cx, float cy, float cw, float ch, int mouseX, int mouseY, float alpha, Color accent) {
        float cr = ch / 2f;
        boolean cardHover = isHovering(cx, cy, cw, ch, mouseX, mouseY);
        boolean isActive = name.equalsIgnoreCase(activeProfile);

        NanoVGRenderer.drawRoundedRect(cx, cy, cw, ch, cr, applyAlpha(ROW_FILL, alpha));

        if (isActive) {
            NanoVGRenderer.drawRoundedRect(cx, cy, cw, ch, cr,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (26 * alpha))));
            NanoVGRenderer.drawRoundedRectOutline(cx, cy, cw, ch, cr, 1f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (90 * alpha))));
        } else if (cardHover) {
            NanoVGRenderer.drawRoundedRect(cx, cy, cw, ch, cr,
                    new Color(255, 255, 255, clampColor((int) (8 * alpha))));
        }

        if (isActive) {
            NanoVGRenderer.drawCircle(cx + 9f, cy + ch / 2f, 2.5f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha))));
        }

        float textX = cx + (isActive ? 16f : 10f);

        boolean isDeleting = name.equals(deleteConfirmConfig);
        float delW = 34f;
        float btnH = 16f;
        float btnR = btnH / 2f;
        float btnY = cy + (ch - btnH) / 2f;

        float delX = cx + cw - delW - 5f;
        float saveW = 26f;
        float saveX = delX - saveW - 3f;
        float loadW = 26f;
        float loadX = saveX - loadW - 3f;

        float maxNameW = loadX - textX - 4f;
        String trimmedName = trimToWidth(name, maxNameW, 7.8f);
        NanoVGRenderer.drawText(trimmedName, textX, cy + 9.0f, 7.8f,
                isActive ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));

        // [Load] button pill
        boolean loadHover = isHovering(loadX, btnY, loadW, btnH, mouseX, mouseY);
        Color loadBg = loadHover
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (180 * alpha)))
                : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (85 * alpha)));
        NanoVGRenderer.drawRoundedRect(loadX, btnY, loadW, btnH, btnR, loadBg);
        float loadTw = NanoVGRenderer.getTextWidth("Load", 6.8f);
        NanoVGRenderer.drawText("Load", loadX + (loadW - loadTw) / 2f, btnY + 5.0f, 6.8f, applyAlpha(TEXT_PRIMARY, alpha));

        // [Save] button pill
        boolean saveHover = isHovering(saveX, btnY, saveW, btnH, mouseX, mouseY);
        Color saveBg = saveHover
                ? new Color(255, 255, 255, clampColor((int) (32 * alpha)))
                : new Color(255, 255, 255, clampColor((int) (14 * alpha)));
        NanoVGRenderer.drawRoundedRect(saveX, btnY, saveW, btnH, btnR, saveBg);
        float saveTw = NanoVGRenderer.getTextWidth("Save", 6.8f);
        NanoVGRenderer.drawText("Save", saveX + (saveW - saveTw) / 2f, btnY + 5.0f, 6.8f,
                saveHover ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));

        // [Del] button pill
        boolean delHover = isHovering(delX, btnY, delW, btnH, mouseX, mouseY);
        Color delBg = isDeleting
                ? new Color(220, 60, 60, clampColor((int) (210 * alpha)))
                : (delHover ? new Color(200, 50, 50, clampColor((int) (110 * alpha))) : new Color(255, 255, 255, clampColor((int) (12 * alpha))));
        NanoVGRenderer.drawRoundedRect(delX, btnY, delW, btnH, btnR, delBg);
        String delLabel = isDeleting ? "Sure?" : "Delete";
        float delTw = NanoVGRenderer.getTextWidth(delLabel, 6.5f);
        NanoVGRenderer.drawText(delLabel, delX + (delW - delTw) / 2f, btnY + 5.0f, 6.5f,
                isDeleting ? applyAlpha(TEXT_PRIMARY, alpha) : (delHover ? new Color(255, 120, 120, clampColor((int) (255 * alpha))) : applyAlpha(TEXT_MUTED, alpha)));
    }

    private void renderBooleanSettingRow(BooleanSetting setting, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt) {
        Color accent = getAccent();
        float rowH = DRAWER_ROW_HEIGHT;
        float rowRadius = rowH / 2f;
        boolean hovered = isHovering(x, y, w, rowH, mouseX, mouseY);
        float hoverAnim = euphoriaSpring("bool_hover_" + setting.getName(), hovered ? 1f : 0f, dt, 0.08f, 0.90f);
        float toggleAnim = euphoriaSpring("bool_toggle_" + setting.getName(), setting.getValue() ? 1f : 0f, dt, 0.10f, 0.88f);
        float clickAnim = euphoriaSpring("bool_click_" + setting.getName(), 0f, dt, 0.16f, 0.84f);

        float scale = 1f - clickAnim * 0.015f;
        if (scale != 1f) {
            NanoVGRenderer.save();
            NanoVGRenderer.translate(x + w / 2f, y + rowH / 2f);
            NanoVGRenderer.scale(scale, scale);
            NanoVGRenderer.translate(-(x + w / 2f), -(y + rowH / 2f));
        }

        NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(12, 20, 36, clampColor((int) (182 * alpha))));
        if (hoverAnim > 0.01f) {
            NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(255, 255, 255, clampColor((int) (8 * hoverAnim * alpha))));
        }

        NanoVGRenderer.drawText(setting.getName(), x + 14f, y + 9f, 8.9f, applyAlpha(TEXT_PRIMARY, alpha));

        float toggleW = 28f;
        float toggleH = 16f;
        float tx = x + w - toggleW - 12f;
        float ty = y + (rowH - toggleH) / 2f;

        Color off = new Color(20, 30, 49, clampColor((int) (255 * alpha)));
        Color on = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha)));
        Color bg = mix(off, on, toggleAnim);

        NanoVGRenderer.drawRoundedRect(tx, ty, toggleW, toggleH, toggleH / 2f, bg);

        float knob = 12f;
        float kx = tx + 2f + (toggleW - knob - 4f) * toggleAnim;
        float ky = ty + 2f;

        if (toggleAnim > 0.01f) {
            NanoVGRenderer.drawCircle(kx + knob / 2f, ky + knob / 2f, knob / 2f + 2.2f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (65 * toggleAnim * alpha))));
        }
        NanoVGRenderer.drawCircle(kx + knob / 2f, ky + knob / 2f, knob / 2f, new Color(245, 247, 255, clampColor((int) (255 * alpha))));

        if (scale != 1f) {
            NanoVGRenderer.restore();
        }
    }

    private void renderNumberSettingRow(NumberSetting setting, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt) {
        Color accent = getAccent();
        float rowH = DRAWER_ROW_HEIGHT + 4f;
        float rowRadius = rowH / 2f;
        float dragAnim = euphoriaSpring("num_drag_" + setting.getName(), 0f, dt, 0.16f, 0.84f);

        NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(12, 20, 36, clampColor((int) (182 * alpha))));
        NanoVGRenderer.drawText(setting.getName(), x + 14f, y + 8.5f, 8.9f, applyAlpha(TEXT_PRIMARY, alpha));

        String valueText = typingNumber == setting ? typingNumberText + "_" : formatNumber(setting.getValue());
        float tw = NanoVGRenderer.getTextWidth(valueText, 8.3f);
        NanoVGRenderer.drawText(
                valueText,
                x + w - tw - 14f,
                y + 8.8f,
                8.3f,
                typingNumber == setting
                        ? applyAlpha(TEXT_PRIMARY, alpha)
                        : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha)))
        );

        float sx = x + 14f;
        float sw = w - 28f;
        float sy = y + 22f;
        float sh = Math.max(4f, ClientSettingsModule.getSliderHeight());

        NanoVGRenderer.drawRoundedRect(sx, sy, sw, sh, sh / 2f, new Color(18, 28, 46, clampColor((int) (255 * alpha))));

        double pct = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        float animPct = euphoriaSpring("num_slider_" + setting.getName(), (float) pct, dt, 0.09f, 0.89f);
        float fill = sw * animPct;

        NanoVGRenderer.drawRoundedRect(sx, sy, fill, sh, sh / 2f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha))));

        float hr = ClientSettingsModule.getSliderHandleSize() * (1f + dragAnim * 0.05f);
        float hx = sx + fill;
        float hy = sy + sh / 2f;

        NanoVGRenderer.drawCircle(hx, hy, hr, new Color(245, 247, 255, clampColor((int) (255 * alpha))));
    }

    private void renderRangeSettingRow(RangeSetting setting, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt) {
        Color accent = getAccent();
        float rowH = DRAWER_ROW_HEIGHT + 4f;
        float rowRadius = rowH / 2f;
        float dragAnim = euphoriaSpring("range_drag_" + setting.getName(), 0f, dt, 0.16f, 0.84f);

        NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(12, 20, 36, clampColor((int) (182 * alpha))));
        NanoVGRenderer.drawText(setting.getName(), x + 14f, y + 8.5f, 8.9f, applyAlpha(TEXT_PRIMARY, alpha));

        String valueText = typingRange == setting ? typingRangeText + "_" : formatNumber(setting.getMinValue()) + " - " + formatNumber(setting.getMaxValue());
        float tw = NanoVGRenderer.getTextWidth(valueText, 8.3f);
        NanoVGRenderer.drawText(
                valueText,
                x + w - tw - 14f,
                y + 8.8f,
                8.3f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha)))
        );

        float sx = x + 14f;
        float sw = w - 28f;
        float sy = y + 22f;
        float sh = Math.max(4f, ClientSettingsModule.getSliderHeight());

        NanoVGRenderer.drawRoundedRect(sx, sy, sw, sh, sh / 2f, new Color(18, 28, 46, clampColor((int) (255 * alpha))));

        double range = setting.getMax() - setting.getMin();
        float minPct = (float) ((setting.getMinValue() - setting.getMin()) / range);
        float maxPct = (float) ((setting.getMaxValue() - setting.getMin()) / range);

        float minAnim = euphoriaSpring("range_min_" + setting.getName(), minPct, dt, 0.09f, 0.89f);
        float maxAnim = euphoriaSpring("range_max_" + setting.getName(), maxPct, dt, 0.09f, 0.89f);

        float start = sx + sw * minAnim;
        float end = sx + sw * maxAnim;

        NanoVGRenderer.drawRoundedRect(start, sy, end - start, sh, sh / 2f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha))));

        float hr = ClientSettingsModule.getSliderHandleSize() * (1f + dragAnim * 0.05f);
        float cy = sy + sh / 2f;

        NanoVGRenderer.drawCircle(start, cy, hr, new Color(245, 247, 255, clampColor((int) (255 * alpha))));
        NanoVGRenderer.drawCircle(end, cy, hr, new Color(245, 247, 255, clampColor((int) (255 * alpha))));
    }

    private float renderModeSettingRow(ModeSetting setting, float x, float y, float w, float drawerContentX, float drawerContentY, float drawerContentW, float drawerContentH, int mouseX, int mouseY, float alpha, float dt) {
        Color accent = getAccent();
        float rowH = DRAWER_ROW_HEIGHT;
        float rowRadius = rowH / 2f;

        NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(12, 20, 36, clampColor((int) (182 * alpha))));
        NanoVGRenderer.drawText(setting.getName(), x + 14f, y + 9f, 8.9f, applyAlpha(TEXT_PRIMARY, alpha));

        float boxW = 118f;
        float boxH = 18f;
        float boxRadius = boxH / 2f;
        float bx = x + w - boxW - 12f;
        float by = y + (rowH - boxH) / 2f;

        boolean hovered = isHovering(bx, by, boxW, boxH, mouseX, mouseY);
        float hoverAnim = euphoriaSpring("mode_hover_" + setting.getName(), hovered ? 1f : 0f, dt, 0.08f, 0.90f);

        NanoVGRenderer.drawRoundedRect(bx, by, boxW, boxH, boxRadius, applyAlpha(ROW_FILL_ALT, alpha));
        if (hoverAnim > 0.01f) {
            NanoVGRenderer.drawRoundedRect(bx, by, boxW, boxH, boxRadius, new Color(255, 255, 255, clampColor((int) (6 * hoverAnim * alpha))));
        }
        NanoVGRenderer.drawRoundedRectOutline(
                bx, by, boxW, boxH, boxRadius, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) ((90 + 20 * hoverAnim) * alpha)))
        );

        String modeText = trimToWidth(setting.getMode(), boxW - 24f, 8.3f);
        NanoVGRenderer.drawText(modeText, bx + 10f, by + 5f, 8.3f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha))));

        float expandAnim = euphoriaSpring("mode_expand_" + setting.getName(), expandedMode == setting ? 1f : 0f, dt, 0.10f, 0.88f);
        String arrow = expandAnim > 0.5f ? "v" : ">";
        Color arrowColor = mix(applyAlpha(TEXT_SECONDARY, alpha), applyAlpha(TEXT_PRIMARY, alpha * 0.7f), expandAnim);
        NanoVGRenderer.drawText(arrow, bx + boxW - 14f, by + 5f, 8.3f, arrowColor);

        float total = DRAWER_ROW_HEIGHT + DRAWER_ROW_GAP;

        if (expandAnim > 0.01f) {
            float dropdownY = y + DRAWER_ROW_HEIGHT + 4f;
            float fullHeight = setting.getModes().size() * 20f + 4f;
            float currentHeight = fullHeight * easeOutCubic(expandAnim);

            float clipTop = Math.max(drawerContentY - 2f, dropdownY);
            float clipBottom = Math.min(drawerContentY + drawerContentH + 2f, dropdownY + currentHeight);

            if (clipBottom > clipTop) {
                NanoVGRenderer.drawRoundedRect(bx, dropdownY, boxW, currentHeight, 10f, new Color(8, 14, 28, clampColor((int) (238 * alpha))));
                NanoVGRenderer.drawRoundedRectOutline(
                        bx, dropdownY, boxW, currentHeight, 10f, 1f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (86 * expandAnim * alpha)))
                );

                NanoVGRenderer.scissor(bx, clipTop, boxW, clipBottom - clipTop);

                float oy = dropdownY + 2f;
                for (String option : setting.getModes()) {
                    boolean selected = option.equals(setting.getMode());
                    boolean optionHovered = isHovering(bx, oy, boxW, 18f, mouseX, mouseY);
                    float optionHoverAnim = euphoriaSpring("mode_option_" + setting.getName() + "_" + option, optionHovered && expandAnim > 0.8f ? 1f : 0f, dt, 0.08f, 0.90f);

                    float optH = 18f;
                    float optRadius = optH / 2f;

                    if (selected) {
                        NanoVGRenderer.drawRoundedRect(
                                bx + 2f, oy, boxW - 4f, optH, optRadius,
                                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (58 * alpha)))
                        );
                    } else if (optionHoverAnim > 0.01f) {
                        NanoVGRenderer.drawRoundedRect(
                                bx + 2f, oy, boxW - 4f, optH, optRadius,
                                new Color(255, 255, 255, clampColor((int) (8 * optionHoverAnim * alpha)))
                        );
                    }

                    NanoVGRenderer.drawText(trimToWidth(option, boxW - 16f, 8.3f), bx + 10f, oy + 5f, 8.3f,
                            selected ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));
                    oy += 20f;
                }
            }

            // Restore drawer content scissoring
            NanoVGRenderer.scissor(drawerContentX - 2f, drawerContentY - 2f, drawerContentW + 4f, drawerContentH + 4f);
            total += fullHeight * expandAnim;
        }

        return total;
    }

    private void renderColorSettingRow(ColorSetting setting, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt) {
        float rowH = DRAWER_ROW_HEIGHT;
        float rowRadius = rowH / 2f;
        float boxW = 24f;
        float boxH = 16f;
        float boxRadius = boxH / 2f;
        float boxX = x + w - boxW - 12f;
        float boxY = y + (rowH - boxH) / 2f;

        boolean hovered = isHovering(boxX, boxY, boxW, boxH, mouseX, mouseY);
        float hoverAnim = euphoriaSpring("color_hover_" + setting.getName(), hovered ? 1f : 0f, dt, 0.08f, 0.90f);
        float clickAnim = euphoriaSpring("color_click_" + setting.getName(), 0f, dt, 0.16f, 0.84f);

        NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(12, 20, 36, clampColor((int) (182 * alpha))));
        NanoVGRenderer.drawText(setting.getName(), x + 14f, y + 9f, 8.9f, applyAlpha(TEXT_PRIMARY, alpha));

        float boxScale = 1f + hoverAnim * 0.04f - clickAnim * 0.05f;

        if (boxScale != 1f) {
            NanoVGRenderer.save();
            NanoVGRenderer.translate(boxX + boxW / 2f, boxY + boxH / 2f);
            NanoVGRenderer.scale(boxScale, boxScale);
            NanoVGRenderer.translate(-(boxX + boxW / 2f), -(boxY + boxH / 2f));
        }

        Color c = setting.getValue();
        NanoVGRenderer.drawRoundedRect(boxX, boxY, boxW, boxH, boxRadius,
                new Color(c.getRed(), c.getGreen(), c.getBlue(), clampColor((int) (255 * alpha))));

        if (boxScale != 1f) {
            NanoVGRenderer.restore();
        }
    }

    private void renderKeybindSettingRow(KeybindSetting setting, float x, float y, float w, int mouseX, int mouseY, float alpha, float dt) {
        Color accent = getAccent();
        float rowH = DRAWER_ROW_HEIGHT;
        float rowRadius = rowH / 2f;

        NanoVGRenderer.drawRoundedRect(x, y, w, rowH, rowRadius, new Color(12, 20, 36, clampColor((int) (182 * alpha))));
        NanoVGRenderer.drawText(setting.getName(), x + 14f, y + 9f, 8.9f, applyAlpha(TEXT_PRIMARY, alpha));

        String keyText = listeningKeybind == setting ? "..." : getKeyName(setting.getKeyCode());

        float modeW = 44f;
        float keyH = 18f;
        float keyRadius = keyH / 2f;
        float keyY = y + (rowH - keyH) / 2f;
        float modeX = x + w - modeW - 12f;

        float keyW = 56f;
        float keyX = modeX - keyW - 6f;

        boolean keyHovered = isHovering(keyX, keyY, keyW, keyH, mouseX, mouseY);
        float keyHoverAnim = euphoriaSpring("key_hover_" + setting.getName(), keyHovered ? 1f : 0f, dt, 0.08f, 0.90f);

        NanoVGRenderer.drawRoundedRect(keyX, keyY, keyW, keyH, keyRadius, applyAlpha(ROW_FILL_ALT, alpha));
        if (keyHoverAnim > 0.01f) {
            NanoVGRenderer.drawRoundedRect(keyX, keyY, keyW, keyH, keyRadius, new Color(255, 255, 255, clampColor((int) (6 * keyHoverAnim * alpha))));
        }
        NanoVGRenderer.drawRoundedRectOutline(
                keyX, keyY, keyW, keyH, keyRadius, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) ((90 + 20 * keyHoverAnim) * alpha)))
        );

        float kw = NanoVGRenderer.getTextWidth(keyText, 8.2f);
        NanoVGRenderer.drawText(keyText, keyX + (keyW - kw) / 2f, keyY + 5f, 8.2f, applyAlpha(TEXT_PRIMARY, alpha));

        String modeText = setting.isHoldMode() ? "Hold" : "Tgl";

        boolean modeHovered = isHovering(modeX, keyY, modeW, keyH, mouseX, mouseY);
        float modeHoverAnim = euphoriaSpring("key_mode_hover_" + setting.getName(), modeHovered ? 1f : 0f, dt, 0.08f, 0.90f);

        NanoVGRenderer.drawRoundedRect(
                modeX, keyY, modeW, keyH, keyRadius,
                setting.isHoldMode()
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (64 * alpha)))
                        : applyAlpha(ROW_FILL_ALT, alpha)
        );
        if (modeHoverAnim > 0.01f && !setting.isHoldMode()) {
            NanoVGRenderer.drawRoundedRect(modeX, keyY, modeW, keyH, keyRadius, new Color(255, 255, 255, clampColor((int) (6 * modeHoverAnim * alpha))));
        }

        float mw = NanoVGRenderer.getTextWidth(modeText, 7.4f);
        NanoVGRenderer.drawText(modeText, modeX + (modeW - mw) / 2f, keyY + 5.1f, 7.4f, applyAlpha(TEXT_PRIMARY, alpha));
    }

    private void renderProfileCard(float x, float y, float alpha, int mouseX, int mouseY) {
        float cardX = x + 10f;
        float cardY = y + PANEL_HEIGHT - PROFILE_BOX_HEIGHT - 8f;
        float cardW = SIDEBAR_WIDTH - 20f;
        float cardH = PROFILE_BOX_HEIGHT;

        NanoVGRenderer.drawRoundedRect(cardX, cardY, cardW, cardH, 10f, new Color(12, 20, 36, clampColor((int) (170 * alpha))));
        NanoVGRenderer.drawRoundedRectOutline(cardX, cardY, cardW, cardH, 10f, 1f, new Color(255, 255, 255, clampColor((int) (10 * alpha))));

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            AbstractClientPlayerEntity player = mc.player;
            String name = player.getName().getString();
            String displayName = (name != null && name.length() > 10)
                    ? name.substring(0, 9) + "..."
                    : (name != null ? name : "");

            float faceX = cardX + 8f;
            float faceY = cardY + 8f;
            float faceSize = 18f;

            Color accent = getAccent();
            NanoVGRenderer.drawCircle(faceX + faceSize / 2f, faceY + faceSize / 2f, faceSize / 2f, new Color(60, 70, 85, clampColor((int) (255 * alpha))));
            NanoVGRenderer.drawCircle(faceX + faceSize / 2f, faceY + faceSize / 2f, faceSize / 2f, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (25 * alpha))));

            String firstLetter = getFirstLetter(name);
            float letterW = NanoVGRenderer.getTextWidth(firstLetter, 10f);
            float letterX = faceX + (faceSize - letterW) / 2f;
            float letterY = faceY + (faceSize / 2f) - 4.5f;
            NanoVGRenderer.drawText(firstLetter, letterX, letterY, 10f, applyAlpha(TEXT_PRIMARY, alpha));

            NanoVGRenderer.drawText(displayName, cardX + 32f, cardY + 10f, 8.4f, applyAlpha(TEXT_PRIMARY, alpha));
            NanoVGRenderer.drawText("1.21.11 Fabric", cardX + 32f, cardY + 24f, 6.7f, applyAlpha(TEXT_MUTED, alpha));

            // Config button — full-width bar pinned to the bottom of the card so the version
            // text above never overlaps it.
            configBtnW = cardW - 12f;
            configBtnH = 16f;
            configBtnX = cardX + 6f;
            configBtnY = cardY + cardH - configBtnH - 5f;

            boolean hover = isHovering(configBtnX, configBtnY, configBtnW, configBtnH, mouseX, mouseY);
            float hoverAnim = euphoriaSpring("config_btn_hover", hover ? 1f : 0f, 0.05f, 0.08f, 0.90f);

            Color accentBtn = getAccent();
            float btnRadius = configBtnH / 2f;
            Color btnFill = configPanelOpen
                    ? new Color(accentBtn.getRed(), accentBtn.getGreen(), accentBtn.getBlue(), clampColor((int) (64 * alpha)))
                    : new Color(20, 30, 50, clampColor((int) (180 * alpha)));
            NanoVGRenderer.drawRoundedRect(configBtnX, configBtnY, configBtnW, configBtnH, btnRadius, btnFill);
            if (hoverAnim > 0.01f && !configPanelOpen) {
                NanoVGRenderer.drawRoundedRect(configBtnX, configBtnY, configBtnW, configBtnH, btnRadius,
                        new Color(accentBtn.getRed(), accentBtn.getGreen(), accentBtn.getBlue(), clampColor((int) (30 * hoverAnim * alpha))));
            }
            NanoVGRenderer.drawRoundedRectOutline(configBtnX, configBtnY, configBtnW, configBtnH, btnRadius, 1f,
                    new Color(accentBtn.getRed(), accentBtn.getGreen(), accentBtn.getBlue(), clampColor((int) ((configPanelOpen ? 160 : 70 + 40 * hoverAnim) * alpha))));

            float iconSize = configBtnH - 6f;
            float labelX = configBtnX + 10f;
            if (configIcon != -1) {
                NanoVGRenderer.drawImage(configIcon, configBtnX + 8f, configBtnY + 3f, iconSize, iconSize,
                        applyAlpha(TEXT_PRIMARY, alpha));
                labelX = configBtnX + 8f + iconSize + 6f;
            }
            NanoVGRenderer.drawText("Configs", labelX, configBtnY + 4.2f, 7.6f, applyAlpha(TEXT_PRIMARY, alpha));
        }
    }

    // ---------- Helper methods (unchanged) ----------
    private boolean isGuiToggleKey(int key) {
        if (System.currentTimeMillis() - openTimeMs < 100L) return false;
        if (key == 0 || key == -1) return false;
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) {
            return key == GLFW.GLFW_KEY_RIGHT_SHIFT;
        }
        ModuleManager mm = EuphoriaClient.INSTANCE.getModuleManager();
        ClientSettingsModule settings = mm.getModule(ClientSettingsModule.class).orElse(null);
        if (settings != null) {
            return settings.getKey() == key;
        }
        return key == GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    private List<Category> getValidCategories() {
        List<Category> list = new ArrayList<>();
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) {
            return list;
        }
        for (Category category : Category.values()) {
            List<Module> modules = EuphoriaClient.INSTANCE.getModuleManager().getModulesByCategory(category);
            if (!modules.isEmpty()) list.add(category);
        }
        return list;
    }

    private List<Module> getFilteredModules(Category category) {
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) {
            return new ArrayList<>();
        }
        List<Module> modules;
        if (!searchQuery.isEmpty()) {
            modules = new ArrayList<>(EuphoriaClient.INSTANCE.getModuleManager().getModules());
        } else if (category != null) {
            modules = new ArrayList<>(EuphoriaClient.INSTANCE.getModuleManager().getModulesByCategory(category));
        } else {
            modules = new ArrayList<>();
        }
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        if (searchQuery.isEmpty()) return modules;

        String q = searchQuery.toLowerCase();
        modules.removeIf(module ->
                !module.getName().toLowerCase().contains(q) &&
                        !module.getDescription().toLowerCase().contains(q)
        );
        return modules;
    }

    private void updateNumberSettingFromMouse(NumberSetting setting, double mouseX, float sliderX, float sliderWidth) {
        double percentage = Math.max(0d, Math.min(1d, (mouseX - sliderX) / sliderWidth));
        double value = setting.getMin() + (setting.getMax() - setting.getMin()) * percentage;
        setting.setValue(value);
    }

    private void updateRangeSettingFromMouse(RangeSetting setting, double mouseX, float sliderX, float sliderWidth, boolean minHandle) {
        double percentage = Math.max(0d, Math.min(1d, (mouseX - sliderX) / sliderWidth));
        double value = setting.getMin() + (setting.getMax() - setting.getMin()) * percentage;
        if (minHandle) {
            setting.setMinValue(Math.min(value, setting.getMaxValue()));
        } else {
            setting.setMaxValue(Math.max(value, setting.getMinValue()));
        }
    }

    private float euphoriaSpring(String key, float target, float dt, float stiffness, float damping) {
        float current = values.getOrDefault(key, target);
        float vel = velocities.getOrDefault(key, 0f);

        // Critically-damped smooth-damp (Game Programming Gems 4): no overshoot/bounce, smooth
        // acceleration and deceleration — the "buttery" feel. Stiffer keys map to a shorter
        // smoothing time so clicks stay responsive while hovers/scrolls glide. (damping unused now.)
        float smoothTime = Math.max(0.045f, 0.10f * (0.08f / Math.max(0.0001f, stiffness)));
        float omega = 2f / smoothTime;
        float x = omega * dt;
        float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = current - target;
        float temp = (vel + omega * change) * dt;
        vel = (vel - omega * temp) * exp;
        float result = target + (change + temp) * exp;

        if (Math.abs(target - result) < 0.0005f && Math.abs(vel) < 0.0005f) {
            result = target;
            vel = 0f;
        }

        values.put(key, result);
        velocities.put(key, vel);
        return result;
    }

    private void springPulse(String key) {
        values.put(key, 1f);
        velocities.put(key, 0f);
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    /** Open-animation scale multiplied by the session GUI scale (item 6, captured at open). */
    private float guiRenderScale() {
        return sessionGuiScale * (0.92f + 0.08f * easeOutCubic(openProgress));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private Color applyAlpha(Color color, float alpha) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                clampColor((int) (color.getAlpha() * alpha))
        );
    }

    private Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(clampColor(r), clampColor(g), clampColor(bl), clampColor(al));
    }

    private Color getAccent() {
        return ClientSettingsModule.getAccentColor();
    }

    private String formatNumber(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }

    private String trimToWidth(String text, float maxWidth, float size) {
        if (text == null) return "";
        if (NanoVGRenderer.getTextWidth(text, size) <= maxWidth) return text;

        String out = text;
        while (out.length() > 3 && NanoVGRenderer.getTextWidth(out + "...", size) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    /** Module-list row height — fixed; descriptions render as a single straight line. */
    private float moduleRowHeight(Module module) {
        return MODULE_HEIGHT;
    }

    private boolean isHovering(double x, double y, double w, double h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void cycleColor(ColorSetting setting) {
        float[] hsb = setting.getHSB();
        float hue = hsb[0] + 0.08f;
        if (hue > 1f) hue -= 1f;
        setting.setFromHSB(hue, Math.max(0.75f, hsb[1]), Math.max(0.9f, hsb[2]));
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
            case 280 -> "CAPS";
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
            case 344 -> "RSHIFT";
            case 345 -> "RCTRL";
            case 346 -> "RALT";
            default -> {
                if (keyCode <= -100) {
                    int mouseButton = -100 - keyCode;
                    yield switch (mouseButton) {
                        case 0 -> "M1";
                        case 1 -> "M2";
                        case 2 -> "M3";
                        default -> "M" + (mouseButton + 1);
                    };
                }
                String glfwName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
                if (glfwName != null && !glfwName.isEmpty()) yield glfwName.toUpperCase();
                yield "KEY " + keyCode;
            }
        };
    }

    // ---------- Particle system ----------
    private void renderPanelParticles(float x, float y, float w, float h, float alpha) {
        NanoVGRenderer.scissor(x, y, w, h);

        for (MicroParticle particle : particles) {
            if (particle.renderX >= x && particle.renderX <= x + w && particle.renderY >= y && particle.renderY <= y + h) {
                switch (particle.shapeType) {
                    case 1 -> {
                        NanoVGRenderer.drawLine(particle.renderX - 1.2f, particle.renderY, particle.renderX + 1.2f, particle.renderY, 0.7f,
                                new Color(230, 240, 255, clampColor((int) (26 * particle.alpha * alpha))));
                        NanoVGRenderer.drawLine(particle.renderX, particle.renderY - 1.2f, particle.renderX, particle.renderY + 1.2f, 0.7f,
                                new Color(230, 240, 255, clampColor((int) (26 * particle.alpha * alpha))));
                    }
                    case 2 -> {
                        NanoVGRenderer.drawLine(particle.renderX - 1.3f, particle.renderY - 1.3f, particle.renderX + 1.3f, particle.renderY + 1.3f, 0.7f,
                                new Color(230, 240, 255, clampColor((int) (24 * particle.alpha * alpha))));
                        NanoVGRenderer.drawLine(particle.renderX - 1.3f, particle.renderY + 1.3f, particle.renderX + 1.3f, particle.renderY - 1.3f, 0.7f,
                                new Color(230, 240, 255, clampColor((int) (24 * particle.alpha * alpha))));
                    }
                    case 3 -> {
                        NanoVGRenderer.drawLine(particle.renderX - 1.5f, particle.renderY + 0.8f, particle.renderX, particle.renderY - 1.1f, 0.7f,
                                new Color(230, 240, 255, clampColor((int) (24 * particle.alpha * alpha))));
                        NanoVGRenderer.drawLine(particle.renderX, particle.renderY - 1.1f, particle.renderX + 1.5f, particle.renderY + 0.8f, 0.7f,
                                new Color(230, 240, 255, clampColor((int) (24 * particle.alpha * alpha))));
                    }
                    default -> {
                        NanoVGRenderer.drawCircle(
                                particle.renderX, particle.renderY, particle.size * 2f,
                                new Color(95, 150, 255, clampColor((int) (10 * particle.alpha * alpha)))
                        );
                        NanoVGRenderer.drawCircle(
                                particle.renderX, particle.renderY, particle.size,
                                new Color(226, 240, 255, clampColor((int) (42 * particle.alpha * alpha)))
                        );
                    }
                }
            }
        }

        NanoVGRenderer.resetScissor();
    }

    private void initParticles() {
        particles.clear();
        for (int i = 0; i < 101; i++) {
            float x = random.nextFloat() * PANEL_WIDTH_FULL;
            float y = random.nextFloat() * PANEL_HEIGHT;

            particles.add(new MicroParticle(
                    x,
                    y,
                    (random.nextFloat() - 0.5f) * 0.15f,
                    -(0.07f + random.nextFloat() * 0.20f),
                    0.34f + random.nextFloat() * 0.82f,
                    0.25f + random.nextFloat() * 0.72f,
                    0.45f + random.nextFloat() * 0.95f,
                    random.nextFloat() < 0.08f ? 1 + random.nextInt(3) : 0
            ));
        }
    }

    private void updateParticles(float dt, int mouseX, int mouseY) {
        float panelWidth = values.getOrDefault("panel_width", PANEL_WIDTH_FULL);
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - PANEL_HEIGHT) / 2f;
        float centerX = panelX + panelWidth / 2f;
        float centerY = panelY + PANEL_HEIGHT / 2f;

        float parallaxX = (mouseX - centerX) / PANEL_WIDTH_FULL * 6f;
        float parallaxY = (mouseY - centerY) / PANEL_HEIGHT * 6f;

        for (MicroParticle particle : particles) {
            particle.x += particle.dx * dt * 22f;
            particle.y += particle.dy * dt * 22f;

            if (particle.y < 0f) {
                particle.y = PANEL_HEIGHT;
                particle.x = random.nextFloat() * PANEL_WIDTH_FULL;
            }

            if (particle.x < 0f) particle.x = PANEL_WIDTH_FULL;
            if (particle.x > PANEL_WIDTH_FULL) particle.x = 0f;

            particle.renderX = panelX + particle.x + parallaxX * particle.depth;
            particle.renderY = panelY + particle.y + parallaxY * particle.depth;
        }
    }

    private void updateCategoryIndicator(float dt, float panelWidth) {
        if (selectedCategory == null) return;

        float panelY = (height - PANEL_HEIGHT) / 2f;
        float listY = panelY + HEADER_HEIGHT + 6f;

        float currentY = listY - categoryScroll;
        float targetY = currentY;

        for (Category category : getValidCategories()) {
            if (category == selectedCategory) {
                targetY = currentY;
                break;
            }
            currentY += CATEGORY_HEIGHT + CATEGORY_GAP;
        }

        categoryIndicatorY = euphoriaSpring("cat_indicator", targetY, dt, 0.10f, 0.88f);
    }

    private String getFirstLetter(String name) {
        if (name == null || name.isEmpty()) return "?";
        for (char c : name.toCharArray()) {
            if (Character.isLetter(c)) {
                return String.valueOf(Character.toUpperCase(c));
            }
        }
        return String.valueOf(name.charAt(0)).toUpperCase();
    }

    private static class MicroParticle {
        float x;
        float y;
        final float dx;
        final float dy;
        final float size;
        final float alpha;
        final float depth;
        final int shapeType;
        float renderX;
        float renderY;

        private MicroParticle(float x, float y, float dx, float dy, float size, float alpha, float depth, int shapeType) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.size = size;
            this.alpha = alpha;
            this.depth = depth;
            this.shapeType = shapeType;
            this.renderX = x;
            this.renderY = y;
        }
    }
}