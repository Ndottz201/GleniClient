package glen.euphoria.module.modules.render;

import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.ColorSetting;
import glen.euphoria.module.setting.ModeSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.module.setting.StringSetting;
import glen.euphoria.utils.render.DraggableComponent;
import glen.euphoria.utils.render.HudGlassmorphism;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import glen.euphoria.gui.ClickGui;
import glen.euphoria.gui.newgui.NewClickGUI;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Watermark extends Module {
    private static final float PADDING_X = 10f;
    private static final float PADDING_Y = 5f;
    private static final float TEXT_SIZE = 11f;
    private static final float ICON_SIZE = 12f;
    private static final float LOGO_SIZE = 15.5f;
    private static final float BOX_GAP = 6f;
    private static final float ICON_TEXT_GAP = 5f;
    private static final float ROW_GAP = 6f;
    private static final float CORNER_RADIUS = 7f;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StringSetting text;
    private final ModeSetting colorMode;
    private final ColorSetting customColor;

    private final BooleanSetting showTitle;
    private final BooleanSetting showUsername;
    private final BooleanSetting showFps;
    private final BooleanSetting showTime;
    private final BooleanSetting showCoords;
    private final BooleanSetting showPing;

    private final DraggableComponent titleDrag = new DraggableComponent(0, 0, 0, 0);
    private final DraggableComponent userDrag = new DraggableComponent(0, 0, 0, 0);
    private final DraggableComponent fpsDrag = new DraggableComponent(0, 0, 0, 0);
    private final DraggableComponent timeDrag = new DraggableComponent(0, 0, 0, 0);
    private final DraggableComponent coordsDrag = new DraggableComponent(0, 0, 0, 0);
    private final DraggableComponent pingDrag = new DraggableComponent(0, 0, 0, 0);
    private boolean initializedPositions = false;

    private int logoIcon = -1;
    private int fpsIcon = -1;
    private int userIcon = -1;
    private int clockIcon = -1;
    private int pingIcon = -1;

    public Watermark() {
        super("Hud Elements", "Displays client HUD elements on screen", -1, Category.RENDER);

        this.text = new StringSetting("Text", "Euphoria");
        this.colorMode = new ModeSetting("Color Mode", "Theme", "Theme", "Custom");
        this.customColor = new ColorSetting("Custom Color", new Color(255, 255, 255));

        this.showTitle = new BooleanSetting("Show Title", true);
        this.showUsername = new BooleanSetting("Show Username", true);
        this.showFps = new BooleanSetting("Show FPS", true);
        this.showTime = new BooleanSetting("Show Time", true);
        this.showCoords = new BooleanSetting("Show Coords", true);
        this.showPing = new BooleanSetting("Show Ping", true);

        addSettings(text, colorMode, customColor,
                showTitle, showUsername, showFps, showTime, showCoords, showPing);
    }

    @Override
    public void onEnable() {
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!event.isContentPass()) return;
        if (!shouldRender()) return;

        loadIcons();

        boolean isClickGuiOpen = mc.currentScreen instanceof NewClickGUI || mc.currentScreen instanceof ClickGui;
        boolean isInChat = mc.currentScreen instanceof ChatScreen;
        boolean isInventoryOpen = mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen;

        if (mc.currentScreen != null && !isClickGuiOpen && !isInChat && !isInventoryOpen) {
            return;
        }

        render(isClickGuiOpen || isInChat);
    }

    private boolean shouldRender() {
        return mc.player != null && mc.world != null;
    }

    private void loadIcons() {
        if (mc == null || mc.getWindow() == null) return;
        if (logoIcon == -1) {
            logoIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/logo.png");
        }
        if (fpsIcon == -1) {
            fpsIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/fps.png");
        }
        if (userIcon == -1) {
            userIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/user.png");
        }
        if (clockIcon == -1) {
            clockIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/clock.png");
        }
        if (pingIcon == -1) {
            pingIcon = NanoVGRenderer.loadImage("assets/euphoria/textures/icons/ping.png");
        }
    }

    private void render(boolean allowDragging) {
        WatermarkData data = new WatermarkData();
        List<Box> boxes = createBoxes(data);
        if (boxes.isEmpty()) return;

        float boxHeight = TEXT_SIZE + PADDING_Y * 2;
        initializePositions(boxes, boxHeight);

        // Update draggable dimensions and handle mouse dragging when GUI is open
        for (Box box : boxes) {
            box.draggable.setWidth(box.width);
            box.draggable.setHeight(boxHeight);
            if (allowDragging) {
                box.draggable.update();
            }
        }

        renderBoxes(boxes, allowDragging);
    }

    private void initializePositions(List<Box> boxes, float boxHeight) {
        if (initializedPositions) return;
        if (mc == null || mc.getWindow() == null) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        if (screenWidth <= 0) return;

        int topRowCount = countTopRowBoxes();

        float row1Width = 0f;
        int r1End = Math.min(topRowCount, boxes.size());
        for (int i = 0; i < r1End; i++) {
            row1Width += boxes.get(i).width;
            if (i < r1End - 1) row1Width += BOX_GAP;
        }

        float row2Width = 0f;
        for (int i = topRowCount; i < boxes.size(); i++) {
            row2Width += boxes.get(i).width;
            if (i < boxes.size() - 1) row2Width += BOX_GAP;
        }

        float startX1 = Math.max(10f, (screenWidth - row1Width) / 2f);
        float startX2 = Math.max(10f, (screenWidth - row2Width) / 2f);
        float x = startX1;
        float y = 10f;

        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            box.draggable.setX(x);
            box.draggable.setY(y);
            box.draggable.setWidth(box.width);
            box.draggable.setHeight(boxHeight);

            x += box.width + BOX_GAP;

            if (i == topRowCount - 1 && i < boxes.size() - 1) {
                x = startX2;
                y += boxHeight + ROW_GAP;
            }
        }

        initializedPositions = true;
    }

    private List<Box> createBoxes(WatermarkData data) {
        List<Box> boxes = new ArrayList<>();
        Color accentColor = getAccentColor();
        int fontTitle = NanoVGRenderer.getPoppinsFontId();
        int fontRegular = NanoVGRenderer.getRegularFontId();

        if (showTitle.getValue()) {
            boxes.add(new Box(data.title, logoIcon, accentColor, true, false, fontTitle, titleDrag));
        }
        if (showUsername.getValue()) {
            boxes.add(new Box(data.username, userIcon, HudGlassmorphism.TEXT_PRIMARY, false, true, fontRegular, userDrag));
        }
        if (showFps.getValue()) {
            boxes.add(new Box(data.fpsText, fpsIcon, HudGlassmorphism.TEXT_PRIMARY, false, false, fontRegular, fpsDrag));
        }
        if (showTime.getValue()) {
            boxes.add(new Box(data.time, clockIcon, HudGlassmorphism.TEXT_PRIMARY, false, false, fontRegular, timeDrag));
        }
        if (showCoords.getValue()) {
            boxes.add(new Box(data.coords, -1, HudGlassmorphism.TEXT_PRIMARY, false, false, fontRegular, coordsDrag));
        }
        if (showPing.getValue()) {
            boxes.add(new Box(data.pingText, pingIcon, HudGlassmorphism.TEXT_PRIMARY, false, false, fontRegular, pingDrag));
        }

        return boxes;
    }

    private void renderBoxes(List<Box> boxes, boolean allowDragging) {
        float boxHeight = TEXT_SIZE + PADDING_Y * 2;
        for (Box box : boxes) {
            renderBox(box, box.draggable.getX(), box.draggable.getY(), boxHeight, allowDragging);
        }
    }

    private int countTopRowBoxes() {
        int count = 0;
        if (showTitle.getValue()) count++;
        if (showUsername.getValue()) count++;
        if (showFps.getValue()) count++;
        if (showTime.getValue()) count++;
        return count;
    }

    private void renderBox(Box box, float x, float y, float boxHeight, boolean allowDragging) {
        float alpha = 1.0f;

        Color accent = getAccentColor();
        Color cardBorder = box.isTitle ? accent : null;
        float boxRadius = boxHeight / 2f;

        // 1. Frosted glass card with blur, dual borders, and glow (pill shape)
        HudGlassmorphism.drawCard(x, y, box.width, boxHeight, boxRadius, alpha, cardBorder);

        if (allowDragging && box.draggable.isDragging()) {
            NanoVGRenderer.drawRoundedRectOutline(x, y, box.width, boxHeight, boxRadius, 1.2f, accent);
        }

        float contentX = x + PADDING_X;
        float textY = y + (boxHeight - TEXT_SIZE) / 2f + (box.isUsername ? 0f : 1f);
        float iconSize = box.isTitle ? LOGO_SIZE : ICON_SIZE;
        float iconY = y + (boxHeight - iconSize) / 2f;

        // 2. Icon rendering
        if (box.iconId != -1) {
            Color iconTint = box.isTitle
                    ? HudGlassmorphism.applyAlpha(Color.WHITE, alpha)
                    : HudGlassmorphism.applyAlpha(accent, alpha);
            NanoVGRenderer.drawImage(box.iconId, contentX, iconY, iconSize, iconSize, iconTint);
            contentX += iconSize + ICON_TEXT_GAP;
        }

        // 3. Text rendering
        if (box.isTitle) {
            NanoVGRenderer.drawTextWithFont(box.text, contentX, textY, TEXT_SIZE,
                    HudGlassmorphism.applyAlpha(box.color, alpha), box.fontId);
        } else if (box.label != null) {
            // Value in primary icy white, label (Fps/Ping) in muted blue
            NanoVGRenderer.drawTextWithFont(box.value, contentX, textY, TEXT_SIZE,
                    HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_PRIMARY, alpha), box.fontId);
            float valW = NanoVGRenderer.getTextWidthWithFont(box.value, TEXT_SIZE, box.fontId);
            NanoVGRenderer.drawTextWithFont(box.label, contentX + valW, textY, TEXT_SIZE,
                    HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_MUTED, alpha), box.fontId);
        } else {
            NanoVGRenderer.drawTextWithFont(box.text, contentX, textY, TEXT_SIZE,
                    HudGlassmorphism.applyAlpha(box.color, alpha), box.fontId);
        }
    }

    private Color getAccentColor() {
        return switch (colorMode.getMode()) {
            case "Custom" -> customColor.getValue();
            default -> ClientSettingsModule.getAccentColor();
        };
    }

    private class WatermarkData {
        final String title;
        final String username;
        final String time;
        final String fpsText;
        final String coords;
        final String pingText;

        WatermarkData() {
            this.title = (text != null && text.getValue() != null) ? text.getValue() : "Euphoria";
            this.username = (mc.player != null) ? mc.player.getName().getString() : "Player";
            this.time = LocalTime.now().format(TIME_FORMAT);

            int fps = 0;
            try {
                fps = mc.getCurrentFps();
            } catch (Throwable ignored) {}
            this.fpsText = fps + " Fps";

            this.coords = (mc.player != null)
                    ? String.format("%d %d %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ())
                    : "0 0 0";

            int ping = 0;
            try {
                if (mc.player != null && mc.player.networkHandler != null) {
                    var entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
                    if (entry != null) {
                        ping = entry.getLatency();
                    }
                }
            } catch (Throwable ignored) {}
            this.pingText = ping + " Ping";
        }
    }

    private class Box {
        final String text;
        final String value;
        final String label;
        final int iconId;
        final Color color;
        final float width;
        final boolean isTitle;
        final boolean isUsername;
        final int fontId;
        final DraggableComponent draggable;

        Box(String text, int iconId, Color color, boolean isTitle, boolean isUsername, int fontId, DraggableComponent draggable) {
            this.text = text;
            this.iconId = iconId;
            this.color = color;
            this.isTitle = isTitle;
            this.isUsername = isUsername;
            this.fontId = fontId;
            this.draggable = draggable;

            if (!isTitle && text.endsWith(" Fps")) {
                this.value = text.substring(0, text.length() - 4);
                this.label = " Fps";
            } else if (!isTitle && text.endsWith(" Ping")) {
                this.value = text.substring(0, text.length() - 5);
                this.label = " Ping";
            } else {
                this.value = text;
                this.label = null;
            }

            float contentWidth = 0f;
            float iconW = isTitle ? LOGO_SIZE : ICON_SIZE;
            if (iconId != -1) {
                contentWidth += iconW + ICON_TEXT_GAP;
            }

            if (label != null) {
                contentWidth += NanoVGRenderer.getTextWidthWithFont(value, TEXT_SIZE, fontId)
                        + NanoVGRenderer.getTextWidthWithFont(label, TEXT_SIZE, fontId);
            } else {
                contentWidth += NanoVGRenderer.getTextWidthWithFont(text, TEXT_SIZE, fontId);
            }

            this.width = contentWidth + PADDING_X * 2;
        }
    }

    @Override
    public void saveConfig(com.google.gson.JsonObject json) {
        com.google.gson.JsonObject pos = new com.google.gson.JsonObject();
        pos.addProperty("titleX", titleDrag.getX());
        pos.addProperty("titleY", titleDrag.getY());
        pos.addProperty("userX", userDrag.getX());
        pos.addProperty("userY", userDrag.getY());
        pos.addProperty("fpsX", fpsDrag.getX());
        pos.addProperty("fpsY", fpsDrag.getY());
        pos.addProperty("timeX", timeDrag.getX());
        pos.addProperty("timeY", timeDrag.getY());
        pos.addProperty("coordsX", coordsDrag.getX());
        pos.addProperty("coordsY", coordsDrag.getY());
        pos.addProperty("pingX", pingDrag.getX());
        pos.addProperty("pingY", pingDrag.getY());
        json.add("positions", pos);
    }

    @Override
    public void loadConfig(com.google.gson.JsonObject json) {
        if (json.has("positions") && json.get("positions").isJsonObject()) {
            com.google.gson.JsonObject pos = json.getAsJsonObject("positions");
            if (pos.has("titleX") && pos.has("titleY")) {
                titleDrag.setX(pos.get("titleX").getAsFloat());
                titleDrag.setY(pos.get("titleY").getAsFloat());
            }
            if (pos.has("userX") && pos.has("userY")) {
                userDrag.setX(pos.get("userX").getAsFloat());
                userDrag.setY(pos.get("userY").getAsFloat());
            }
            if (pos.has("fpsX") && pos.has("fpsY")) {
                fpsDrag.setX(pos.get("fpsX").getAsFloat());
                fpsDrag.setY(pos.get("fpsY").getAsFloat());
            }
            if (pos.has("timeX") && pos.has("timeY")) {
                timeDrag.setX(pos.get("timeX").getAsFloat());
                timeDrag.setY(pos.get("timeY").getAsFloat());
            }
            if (pos.has("coordsX") && pos.has("coordsY")) {
                coordsDrag.setX(pos.get("coordsX").getAsFloat());
                coordsDrag.setY(pos.get("coordsY").getAsFloat());
            }
            if (pos.has("pingX") && pos.has("pingY")) {
                pingDrag.setX(pos.get("pingX").getAsFloat());
                pingDrag.setY(pos.get("pingY").getAsFloat());
            }
            initializedPositions = true;
        }
    }
}
