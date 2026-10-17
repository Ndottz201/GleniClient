package glen.euphoria.gui.newgui.components;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.module.modules.client.NewClickGUIModule;
import glen.euphoria.profiles.ProfileManager;
import glen.euphoria.utils.render.GuiGlowHelper;
import glen.euphoria.utils.render.blur.BlurRenderer;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Modern, sleek NanoVG Config/Profile management panel matching the NewClickGUI design language.
 * Features fully rounded pill buttons, smooth scrolling, quick save/overwrite, loading, and deletion.
 */
public class ConfigPanel {
    public static final float CORNER_RADIUS = 14f;
    private static final int HEADER_HEIGHT = 28;
    private static final float ENTRY_HEIGHT = 26f;
    private static final float ENTRY_GAP = 4f;
    private static final float SCROLL_SPEED = 20f;
    private static final long DELETE_CONFIRM_TIMEOUT = 3000L;

    private static final Color SHELL_FILL_TOP = new Color(8, 14, 26, 248);
    private static final Color SHELL_FILL_BOTTOM = new Color(4, 8, 16, 252);
    private static final Color SHELL_INNER = new Color(255, 255, 255, 14);
    private static final Color TEXT_PRIMARY = new Color(245, 247, 252, 255);
    private static final Color TEXT_SECONDARY = new Color(148, 163, 184, 255);
    private static final Color TEXT_MUTED = new Color(92, 107, 130, 255);
    private static final Color ROW_FILL = new Color(12, 20, 36, 200);
    private static final Color ROW_FILL_ALT = new Color(13, 21, 38, 235);

    private final float width;
    private final float height = 240f;
    private float x;
    private float y;
    private boolean dragging = false;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean userDragged = false;
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    private String newConfigName = "";
    private boolean namingNewConfig = false;
    private String selectedConfig = null;
    private String activeProfile = "default";
    private String deleteConfirmConfig = null;
    private long deleteConfirmTime = 0L;
    private boolean closeRequested = false;

    private static float[] lastSavedPosition = null;
    private static Boolean lastDragState = null;

    private final List<ConfigEntry> configs = new ArrayList<>();

    public ConfigPanel(float x, float y, int width) {
        this.width = width > 0 ? width : 220f;

        if (lastSavedPosition != null) {
            this.x = lastSavedPosition[0];
            this.y = lastSavedPosition[1];
            this.userDragged = lastDragState != null && lastDragState;
        } else {
            this.x = x;
            this.y = y;
            this.userDragged = false;
        }

        refreshConfigs();
    }

    public boolean hasBeenDragged() {
        return userDragged;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }

    public void clearCloseRequest() {
        closeRequested = false;
    }

    public void saveState() {
        lastSavedPosition = new float[]{x, y};
        lastDragState = userDragged;
    }

    public static void clearSavedPosition() {
        lastSavedPosition = null;
        lastDragState = null;
    }

    public void refreshConfigs() {
        configs.clear();
        ProfileManager profileManager = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
        if (profileManager != null) {
            File profileDir = profileManager.getProfileDir();
            if (profileDir != null && profileDir.exists() && profileDir.isDirectory()) {
                File[] files = profileDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File file : files) {
                        String name = file.getName().replace(".json", "");
                        configs.add(new ConfigEntry(name));
                    }
                }
            }
        }
    }

    public void render(int mouseX, int mouseY, float alpha) {
        render(mouseX, mouseY, alpha, 1f, 0, 0);
    }

    public void render(int mouseX, int mouseY, float alpha, float scale, int centerX, int centerY) {
        Color accent = NewClickGUIModule.getAccentColor();

        // Background shell with subtle gradient and inner border
        NanoVGRenderer.drawRoundedRectGradient(x, y, width, height, CORNER_RADIUS, applyAlpha(SHELL_FILL_TOP, alpha), applyAlpha(SHELL_FILL_BOTTOM, alpha));
        NanoVGRenderer.drawRoundedRect(x, y, width, height, CORNER_RADIUS,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (14 * alpha))));
        NanoVGRenderer.drawRoundedRectOutline(x, y, width, height, CORNER_RADIUS, 1f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (70 * alpha))));
        NanoVGRenderer.drawRoundedRectOutline(x + 1f, y + 1f, width - 2f, height - 2f, CORNER_RADIUS - 1f, 1f, applyAlpha(SHELL_INNER, alpha));

        // Header Title
        NanoVGRenderer.drawText("Profiles", x + 12f, y + 9f, 9.2f, applyAlpha(TEXT_PRIMARY, alpha));

        String countText = configs.size() + (configs.size() == 1 ? " config" : " configs");
        NanoVGRenderer.drawText(countText, x + 58f, y + 10.2f, 7.2f, applyAlpha(TEXT_MUTED, alpha));

        // Close button (pill)
        float closeSize = 16f;
        float closeX = x + width - closeSize - 8f;
        float closeY = y + 6f;
        boolean closeHover = isMouseOver(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        NanoVGRenderer.drawRoundedRect(closeX, closeY, closeSize, closeSize, closeSize / 2f,
                closeHover ? new Color(255, 255, 255, clampColor((int) (24 * alpha))) : new Color(255, 255, 255, clampColor((int) (8 * alpha))));
        float cxW = NanoVGRenderer.getTextWidth("x", 7.8f);
        NanoVGRenderer.drawText("x", closeX + (closeSize - cxW) / 2f, closeY + 4f, 7.8f,
                closeHover ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));

        // Header divider
        NanoVGRenderer.drawRect(x + 10f, y + HEADER_HEIGHT, width - 20f, 1f, new Color(255, 255, 255, clampColor((int) (12 * alpha))));

        // Create new profile section
        float inputSectionY = y + HEADER_HEIGHT + 6f;
        float inputH = 22f;
        float inputW = width - 66f;
        float inputX = x + 10f;
        float btnW = 42f;
        float btnX = x + width - btnW - 10f;

        // Input pill
        boolean inputHover = isMouseOver(mouseX, mouseY, inputX, inputSectionY, inputW, inputH);
        NanoVGRenderer.drawRoundedRect(inputX, inputSectionY, inputW, inputH, inputH / 2f, applyAlpha(ROW_FILL_ALT, alpha));
        Color inputBorder = namingNewConfig
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (160 * alpha)))
                : (inputHover ? new Color(255, 255, 255, clampColor((int) (28 * alpha))) : new Color(255, 255, 255, clampColor((int) (14 * alpha))));
        NanoVGRenderer.drawRoundedRectOutline(inputX, inputSectionY, inputW, inputH, inputH / 2f, 1f, inputBorder);

        String placeholder = namingNewConfig
                ? (newConfigName.isEmpty() ? "_" : newConfigName + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""))
                : (newConfigName.isEmpty() ? "New profile..." : newConfigName);
        Color textColor = namingNewConfig || !newConfigName.isEmpty() ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_MUTED, alpha);
        NanoVGRenderer.drawText(placeholder, inputX + 10f, inputSectionY + 6.5f, 7.6f, textColor);

        // Save / Create Button (pill)
        boolean btnHover = isMouseOver(mouseX, mouseY, btnX, inputSectionY, btnW, inputH);
        Color btnBg = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) ((btnHover ? 220 : 170) * alpha)));
        NanoVGRenderer.drawRoundedRect(btnX, inputSectionY, btnW, inputH, inputH / 2f, btnBg);
        float saveTw = NanoVGRenderer.getTextWidth("+ Save", 7.5f);
        NanoVGRenderer.drawText("+ Save", btnX + (btnW - saveTw) / 2f, inputSectionY + 6.5f, 7.5f, applyAlpha(TEXT_PRIMARY, alpha));

        // Scrollable Configs Area
        float listX = x + 10f;
        float listY = inputSectionY + inputH + 8f;
        float listW = width - 20f;
        float listH = height - (listY - y) - 8f;

        float totalListH = configs.isEmpty() ? 30f : configs.size() * (ENTRY_HEIGHT + ENTRY_GAP);
        float maxScroll = Math.max(0f, totalListH - listH);
        targetScrollOffset = Math.max(0f, Math.min(maxScroll, targetScrollOffset));
        scrollOffset += (targetScrollOffset - scrollOffset) * 0.25f;

        NanoVGRenderer.scissor(listX - 2f, listY - 2f, listW + 4f, listH + 4f);

        if (configs.isEmpty()) {
            String emptyMsg = "No profiles found";
            float emW = NanoVGRenderer.getTextWidth(emptyMsg, 7.8f);
            NanoVGRenderer.drawText(emptyMsg, listX + (listW - emW) / 2f, listY + 30f, 7.8f, applyAlpha(TEXT_MUTED, alpha));
        } else {
            float cy = listY - scrollOffset;
            for (ConfigEntry entry : configs) {
                renderConfigCard(entry, listX, cy, listW, mouseX, mouseY, alpha, accent);
                cy += ENTRY_HEIGHT + ENTRY_GAP;
            }
        }

        NanoVGRenderer.resetScissor();

        // Scrollbar
        if (maxScroll > 0.01f) {
            float sbH = Math.max(14f, (listH / totalListH) * listH);
            float sbY = listY + (scrollOffset / maxScroll) * (listH - sbH);
            NanoVGRenderer.drawRoundedRect(x + width - 4.5f, sbY, 2.5f, sbH, 1.25f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (130 * alpha))));
        }
    }

    private void renderConfigCard(ConfigEntry entry, float cx, float cy, float cw, int mouseX, int mouseY, float alpha, Color accent) {
        float ch = ENTRY_HEIGHT;
        float cr = ch / 2f; // fully rounded pill
        boolean cardHover = isMouseOver(mouseX, mouseY, cx, cy, cw, ch);
        boolean isActive = entry.name.equalsIgnoreCase(activeProfile);

        // Background pill
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

        // Active indicator dot
        if (isActive) {
            NanoVGRenderer.drawCircle(cx + 9f, cy + ch / 2f, 2.5f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (255 * alpha))));
        }

        float textX = cx + (isActive ? 16f : 10f);

        // Buttons on right: [Load], [Save], [Del]
        boolean isDeleting = entry.name.equals(deleteConfirmConfig);
        float delW = isDeleting ? 30f : 16f;
        float btnH = 16f;
        float btnR = btnH / 2f; // fully rounded pill
        float btnY = cy + (ch - btnH) / 2f;

        float delX = cx + cw - delW - 5f;
        float saveW = 26f;
        float saveX = delX - saveW - 3f;
        float loadW = 26f;
        float loadX = saveX - loadW - 3f;

        // Truncated name
        float maxNameW = loadX - textX - 4f;
        String name = trimToWidth(entry.name, maxNameW, 7.9f);
        NanoVGRenderer.drawText(name, textX, cy + 8.2f, 7.9f,
                isActive ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));

        // [Load] button pill
        boolean loadHover = isMouseOver(mouseX, mouseY, loadX, btnY, loadW, btnH);
        Color loadBg = loadHover
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (180 * alpha)))
                : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (85 * alpha)));
        NanoVGRenderer.drawRoundedRect(loadX, btnY, loadW, btnH, btnR, loadBg);
        float loadTw = NanoVGRenderer.getTextWidth("Load", 6.8f);
        NanoVGRenderer.drawText("Load", loadX + (loadW - loadTw) / 2f, btnY + 4.8f, 6.8f, applyAlpha(TEXT_PRIMARY, alpha));

        // [Save] button pill
        boolean saveHover = isMouseOver(mouseX, mouseY, saveX, btnY, saveW, btnH);
        Color saveBg = saveHover
                ? new Color(255, 255, 255, clampColor((int) (32 * alpha)))
                : new Color(255, 255, 255, clampColor((int) (14 * alpha)));
        NanoVGRenderer.drawRoundedRect(saveX, btnY, saveW, btnH, btnR, saveBg);
        float saveTw = NanoVGRenderer.getTextWidth("Save", 6.8f);
        NanoVGRenderer.drawText("Save", saveX + (saveW - saveTw) / 2f, btnY + 4.8f, 6.8f,
                saveHover ? applyAlpha(TEXT_PRIMARY, alpha) : applyAlpha(TEXT_SECONDARY, alpha));

        // [Del] / [Sure?] button pill
        boolean delHover = isMouseOver(mouseX, mouseY, delX, btnY, delW, btnH);
        Color delBg = isDeleting
                ? new Color(220, 60, 60, clampColor((int) (210 * alpha)))
                : (delHover ? new Color(200, 50, 50, clampColor((int) (110 * alpha))) : new Color(255, 255, 255, clampColor((int) (12 * alpha))));
        NanoVGRenderer.drawRoundedRect(delX, btnY, delW, btnH, btnR, delBg);
        String delLabel = isDeleting ? "Sure?" : "x";
        float delTw = NanoVGRenderer.getTextWidth(delLabel, 6.8f);
        NanoVGRenderer.drawText(delLabel, delX + (delW - delTw) / 2f, btnY + 4.8f, 6.8f,
                isDeleting ? applyAlpha(TEXT_PRIMARY, alpha) : (delHover ? new Color(255, 120, 120, clampColor((int) (255 * alpha))) : applyAlpha(TEXT_MUTED, alpha)));

        entry.bounds = new float[]{cx, cy, cw, ch};
        entry.loadBounds = new float[]{loadX, btnY, loadW, btnH};
        entry.saveBounds = new float[]{saveX, btnY, saveW, btnH};
        entry.delBounds = new float[]{delX, btnY, delW, btnH};
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY, x, y, width, height)) {
            return false;
        }

        // Close button
        float closeSize = 16f;
        float closeX = x + width - closeSize - 8f;
        float closeY = y + 6f;
        if (button == 0 && isMouseOver(mouseX, mouseY, closeX, closeY, closeSize, closeSize)) {
            closeRequested = true;
            return true;
        }

        // Header dragging
        if (button == 0 && isMouseOver(mouseX, mouseY, x, y, width - closeSize - 12f, HEADER_HEIGHT)) {
            dragging = true;
            dragOffsetX = (float) (mouseX - x);
            dragOffsetY = (float) (mouseY - y);
            return true;
        }

        // Input field focus
        float inputSectionY = y + HEADER_HEIGHT + 6f;
        float inputH = 22f;
        float inputW = width - 66f;
        float inputX = x + 10f;
        float btnW = 42f;
        float btnX = x + width - btnW - 10f;

        if (button == 0 && isMouseOver(mouseX, mouseY, inputX, inputSectionY, inputW, inputH)) {
            namingNewConfig = true;
            return true;
        }

        // Save / create new profile button
        if (button == 0 && isMouseOver(mouseX, mouseY, btnX, inputSectionY, btnW, inputH)) {
            String nameToSave = newConfigName.trim().isEmpty() ? "quick" : newConfigName.trim();
            ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
            if (pm != null) {
                pm.saveProfile(nameToSave, true);
                activeProfile = nameToSave;
                refreshConfigs();
            }
            newConfigName = "";
            namingNewConfig = false;
            return true;
        }

        // Config entries list
        float listX = x + 10f;
        float listY = inputSectionY + inputH + 8f;
        float listW = width - 20f;
        float listH = height - (listY - y) - 8f;

        if (isMouseOver(mouseX, mouseY, listX, listY, listW, listH)) {
            for (ConfigEntry entry : configs) {
                if (entry.bounds == null) continue;

                // Load button
                if (entry.loadBounds != null && button == 0 && isMouseOver(mouseX, mouseY, entry.loadBounds[0], entry.loadBounds[1], entry.loadBounds[2], entry.loadBounds[3])) {
                    ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                    if (pm != null) {
                        pm.loadProfile(entry.name);
                        activeProfile = entry.name;
                    }
                    return true;
                }

                // Save (overwrite) button
                if (entry.saveBounds != null && button == 0 && isMouseOver(mouseX, mouseY, entry.saveBounds[0], entry.saveBounds[1], entry.saveBounds[2], entry.saveBounds[3])) {
                    ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                    if (pm != null) {
                        pm.saveProfile(entry.name, true);
                        activeProfile = entry.name;
                        refreshConfigs();
                    }
                    return true;
                }

                // Delete button
                if (entry.delBounds != null && button == 0 && isMouseOver(mouseX, mouseY, entry.delBounds[0], entry.delBounds[1], entry.delBounds[2], entry.delBounds[3])) {
                    boolean alreadyConfirming = entry.name.equals(deleteConfirmConfig) && System.currentTimeMillis() - deleteConfirmTime < DELETE_CONFIRM_TIMEOUT;
                    if (alreadyConfirming) {
                        ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                        if (pm != null) {
                            File f = new File(pm.getProfileDir(), entry.name + ".json");
                            if (f.delete()) {
                                if (entry.name.equals(activeProfile)) activeProfile = "default";
                                deleteConfirmConfig = null;
                                refreshConfigs();
                            }
                        }
                    } else {
                        deleteConfirmConfig = entry.name;
                        deleteConfirmTime = System.currentTimeMillis();
                    }
                    return true;
                }

                // Clicking the card body loads/selects
                if (button == 0 && isMouseOver(mouseX, mouseY, entry.bounds[0], entry.bounds[1], entry.bounds[2], entry.bounds[3])) {
                    selectedConfig = entry.name;
                    ProfileManager pm = EuphoriaClient.INSTANCE != null ? EuphoriaClient.INSTANCE.getProfileManager() : null;
                    if (pm != null) {
                        pm.loadProfile(entry.name);
                        activeProfile = entry.name;
                    }
                    return true;
                }
            }
        }

        // Click outside text input unfocuses it
        namingNewConfig = false;
        return true;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            x = (float) (mouseX - dragOffsetX);
            y = (float) (mouseY - dragOffsetY);
            userDragged = true;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isMouseOver(mouseX, mouseY, x, y, width, height)) {
            targetScrollOffset = targetScrollOffset - (float) amount * SCROLL_SPEED;
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (namingNewConfig) {
                namingNewConfig = false;
                newConfigName = "";
                return true;
            }
            closeRequested = true;
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
                        refreshConfigs();
                    }
                    newConfigName = "";
                }
                namingNewConfig = false;
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
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

    public boolean isDragging() {
        return dragging;
    }

    public void update(float deltaTime) {
        if (deleteConfirmConfig != null && System.currentTimeMillis() - deleteConfirmTime >= DELETE_CONFIRM_TIMEOUT) {
            deleteConfirmConfig = null;
        }
    }

    public void renderGlow(DrawContext context, float alpha, float scale, int centerX, int centerY) {
        float transformedX = (x - centerX) * scale + centerX;
        float transformedY = (y - centerY) * scale + centerY;
        float transformedWidth = width * scale;
        float transformedHeight = height * scale;

        GuiGlowHelper.drawGuiGlow(context, transformedX, transformedY, transformedWidth, transformedHeight, CORNER_RADIUS * scale);
    }

    public void renderBlur(float alpha, float scale, int centerX, int centerY) {
        if (!ClientSettingsModule.isPanelBlurEnabled()) {
            return;
        }

        float transformedX = (x - centerX) * scale + centerX;
        float transformedY = (y - centerY) * scale + centerY;
        float transformedWidth = width * scale;
        float transformedHeight = height * scale;
        float blurRadius = ClientSettingsModule.getPanelBlurRadius();

        BlurRenderer.drawBlur(
                transformedX, transformedY,
                transformedWidth, transformedHeight,
                CORNER_RADIUS * scale,
                new Color(255, 255, 255, clampColor((int) (205 * alpha))),
                blurRadius
        );
    }

    private boolean isMouseOver(double mouseX, double mouseY, float targetX, float targetY, float targetW, float targetH) {
        return mouseX >= targetX && mouseX <= targetX + targetW && mouseY >= targetY && mouseY <= targetY + targetH;
    }

    private String trimToWidth(String text, float maxW, float size) {
        if (text == null) return "";
        if (NanoVGRenderer.getTextWidth(text, size) <= maxW) return text;
        while (text.length() > 1 && NanoVGRenderer.getTextWidth(text + "...", size) > maxW) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private Color applyAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampColor((int) (color.getAlpha() * alpha)));
    }

    private int clampColor(int val) {
        return Math.max(0, Math.min(255, val));
    }

    private static class ConfigEntry {
        final String name;
        float[] bounds;
        float[] loadBounds;
        float[] saveBounds;
        float[] delBounds;

        ConfigEntry(String name) {
            this.name = name;
        }
    }
}