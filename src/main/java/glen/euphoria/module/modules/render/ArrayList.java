package glen.euphoria.module.modules.render;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.gui.ClickGui;
import glen.euphoria.gui.newgui.NewClickGUI;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.ModeSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.render.DraggableComponent;
import glen.euphoria.utils.render.HudGlassmorphism;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

public class ArrayList extends Module {
    private static final float PADDING_X = 7f;
    private static final float PADDING_Y = 4f;
    private static final float MARGIN = 6f;

    private final ModeSetting position;
    private final NumberSetting fontSize;
    private final BooleanSetting rounded;
    private final NumberSetting barWidth;
    private final BooleanSetting textShadow;

    private DraggableComponent draggable;
    private String lastPositionMode = "";
    private Float loadedX = null;
    private Float loadedY = null;
    private Boolean loadedRightAligned = null;
    private Float loadedRightEdge = null;

    public ArrayList() {
        super("Array List", "Displays list of enabled modules", -1, Category.RENDER);

        this.position = new ModeSetting("Position", "Top Right", "Top Left", "Top Right");
        this.fontSize = new NumberSetting("Font Size", 8, 24, 10, 1);
        this.rounded = new BooleanSetting("Rounded", true);
        this.barWidth = new NumberSetting("Bar Width", 1, 5, 2, 0.5);
        this.textShadow = new BooleanSetting("Text Shadow", false);

        addSettings(position, fontSize, rounded, barWidth, textShadow);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!event.isContentPass()) return;

        if (!shouldRender()) return;

        boolean isGuiOpen = isGuiOpen();
        List<Module> sortedModules = getSortedEnabledModules();
        if (sortedModules.isEmpty()) {
            if (!isGuiOpen) return;
            sortedModules = java.util.Collections.singletonList(this);
        }

        renderModuleList(sortedModules, isGuiOpen);
    }

    private boolean isGuiOpen() {
        return mc.currentScreen instanceof NewClickGUI
                || mc.currentScreen instanceof ClickGui
                || mc.currentScreen instanceof ChatScreen;
    }

    private boolean isInventoryOpen() {
        return mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen;
    }

    private boolean shouldRender() {
        return mc.player != null
                && mc.world != null
                && (mc.currentScreen == null || isGuiOpen() || isInventoryOpen());
    }

    private List<Module> getSortedEnabledModules() {
        List<Module> enabledModules = EuphoriaClient.INSTANCE.getModuleManager().getEnabledModules();
        List<Module> sortedModules = new java.util.ArrayList<>(enabledModules);

        float currentFontSize = (float) fontSize.getValue();
        int fontId = NanoVGRenderer.getRegularFontId();
        sortedModules.sort(Comparator.comparingDouble(
                module -> -getModuleDisplayWidth(module, currentFontSize, fontId)
        ));

        return sortedModules;
    }

    private float getModuleDisplayWidth(Module module, float size, int fontId) {
        float width = NanoVGRenderer.getTextWidthWithFont(module.getName(), size, fontId);
        String suffix = module.getSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            width += NanoVGRenderer.getTextWidthWithFont(" " + suffix, size, fontId);
        }
        return width;
    }

    private void renderModuleList(List<Module> modules, boolean isGuiOpen) {
        float currentFontSize = (float) fontSize.getValue();
        int fontId = NanoVGRenderer.getRegularFontId();
        float currentBarWidth = (float) barWidth.getValue();
        float currentAlpha = 1.0f;
        boolean isRounded = rounded.getValue();
        boolean hasShadow = textShadow.getValue();
        Color accentColor = ClientSettingsModule.getAccentColor();
        int screenWidth = mc.getWindow().getScaledWidth();

        float maxBgWidth = 0f;
        float rowHeight = NanoVGRenderer.getTextHeight(currentFontSize) + PADDING_Y * 2;
        for (Module m : modules) {
            float w = getModuleDisplayWidth(m, currentFontSize, fontId) + PADDING_X * 2 + currentBarWidth;
            if (w > maxBgWidth) maxBgWidth = w;
        }
        float totalHeight = modules.size() * rowHeight;

        // Position setting sync
        String currentPos = position.getMode();
        if (!currentPos.equals(lastPositionMode)) {
            lastPositionMode = currentPos;
            if (draggable != null) {
                if (currentPos.contains("Right")) {
                    draggable.setX(screenWidth - maxBgWidth - MARGIN);
                    draggable.setY(MARGIN);
                } else {
                    draggable.setX(MARGIN);
                    draggable.setY(MARGIN);
                }
            }
        }

        if (draggable == null) {
            boolean startRight = currentPos.contains("Right");
            float startX;
            if (Boolean.TRUE.equals(loadedRightAligned) && loadedRightEdge != null) {
                startX = loadedRightEdge - maxBgWidth;
            } else if (loadedX != null) {
                startX = loadedX;
            } else {
                startX = startRight ? screenWidth - maxBgWidth - MARGIN : MARGIN;
            }
            float startY = loadedY != null ? loadedY : MARGIN;
            draggable = new DraggableComponent(startX, startY, maxBgWidth, totalHeight);
            lastPositionMode = currentPos;
        } else {
            boolean wasRightAligned = (draggable.getX() + draggable.getWidth() / 2f) > (screenWidth / 2f);
            if (wasRightAligned && !draggable.isDragging()) {
                float rightEdge = draggable.getX() + draggable.getWidth();
                float newX = rightEdge - maxBgWidth;
                newX = net.minecraft.util.math.MathHelper.clamp(newX, 0, screenWidth - maxBgWidth);
                draggable.setX(newX);
            }
            draggable.setWidth(maxBgWidth);
            draggable.setHeight(totalHeight);
        }

        if (isGuiOpen) {
            draggable.update();
        }

        boolean isRightAligned = (draggable.getX() + maxBgWidth / 2f) > (screenWidth / 2f);

        RenderContext context = new RenderContext(
                screenWidth, currentFontSize, currentBarWidth, currentAlpha,
                isRightAligned, isRounded, hasShadow, fontId, accentColor,
                draggable.getX(), draggable.getY(), maxBgWidth, rowHeight
        );

        List<ModuleEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            entries.add(new ModuleEntry(modules.get(i), i, context));
        }

        if (entries.isEmpty()) return;

        // 1. Draw one continuous smoothed platform background
        renderSmoothedPlatform(entries, context);

        // 2. Draw vertical accent side bar
        renderSideBar(entries, context);

        // 3. Draw module text for each entry
        for (ModuleEntry entry : entries) {
            renderText(entry, context);
        }

        // 4. Subtle outline when dragging in GUI
        if (isGuiOpen && draggable.isDragging()) {
            NanoVGRenderer.drawRoundedRectOutline(draggable.getX() - 2f, draggable.getY() - 2f,
                    draggable.getWidth() + 4f, draggable.getHeight() + 4f, 6f, 1.5f, accentColor);
        }
    }

    private void renderSmoothedPlatform(List<ModuleEntry> entries, RenderContext context) {
        if (context.alpha <= 0.01f || entries.isEmpty()) return;

        float topY = context.originY;
        float bottomY = context.originY + entries.size() * context.rowHeight;
        float h = context.rowHeight;

        float rightX = context.originX + context.maxBgWidth;
        float leftX = context.originX;

        float minX = leftX;
        float maxX = rightX;

        Runnable pathBuilder;
        if (context.isRightAligned) {
            pathBuilder = () -> {
                NanoVGRenderer.beginPath();
                float r = context.isRounded ? Math.min(5f, h / 2f) : 0f;

                NanoVGRenderer.moveTo(rightX, topY);

                float firstX = entries.get(0).x;
                if (r > 0f) {
                    NanoVGRenderer.arcTo(firstX, topY, firstX, topY + h, r);
                } else {
                    NanoVGRenderer.lineTo(firstX, topY);
                }

                for (int i = 0; i < entries.size(); i++) {
                    ModuleEntry curr = entries.get(i);
                    float currX = curr.x;
                    float rowBottom = curr.y + curr.bgHeight;

                    if (i < entries.size() - 1) {
                        ModuleEntry next = entries.get(i + 1);
                        float nextX = next.x;
                        float step = nextX - currX;

                        if (r > 0f && Math.abs(step) > 1.5f) {
                            float rStep = Math.min(r, Math.min(Math.abs(step) / 2f, h / 2f));
                            NanoVGRenderer.arcTo(currX, rowBottom, nextX, rowBottom, rStep);
                            NanoVGRenderer.arcTo(nextX, rowBottom, nextX, rowBottom + next.bgHeight, rStep);
                        } else {
                            NanoVGRenderer.lineTo(currX, rowBottom);
                            NanoVGRenderer.lineTo(nextX, rowBottom);
                        }
                    } else {
                        if (r > 0f) {
                            NanoVGRenderer.arcTo(currX, rowBottom, rightX, rowBottom, r);
                        } else {
                            NanoVGRenderer.lineTo(currX, rowBottom);
                        }
                        NanoVGRenderer.lineTo(rightX, rowBottom);
                    }
                }

                NanoVGRenderer.lineTo(rightX, topY);
                NanoVGRenderer.closePath();
            };
        } else {
            pathBuilder = () -> {
                NanoVGRenderer.beginPath();
                float r = context.isRounded ? Math.min(5f, h / 2f) : 0f;

                NanoVGRenderer.moveTo(leftX, topY);

                float firstRightX = entries.get(0).x + entries.get(0).bgWidth;
                if (r > 0f) {
                    NanoVGRenderer.arcTo(firstRightX, topY, firstRightX, topY + h, r);
                } else {
                    NanoVGRenderer.lineTo(firstRightX, topY);
                }

                for (int i = 0; i < entries.size(); i++) {
                    ModuleEntry curr = entries.get(i);
                    float currRightX = curr.x + curr.bgWidth;
                    float rowBottom = curr.y + curr.bgHeight;

                    if (i < entries.size() - 1) {
                        ModuleEntry next = entries.get(i + 1);
                        float nextRightX = next.x + next.bgWidth;
                        float step = currRightX - nextRightX;

                        if (r > 0f && Math.abs(step) > 1.5f) {
                            float rStep = Math.min(r, Math.min(Math.abs(step) / 2f, h / 2f));
                            NanoVGRenderer.arcTo(currRightX, rowBottom, nextRightX, rowBottom, rStep);
                            NanoVGRenderer.arcTo(nextRightX, rowBottom, nextRightX, rowBottom + next.bgHeight, rStep);
                        } else {
                            NanoVGRenderer.lineTo(currRightX, rowBottom);
                            NanoVGRenderer.lineTo(nextRightX, rowBottom);
                        }
                    } else {
                        if (r > 0f) {
                            NanoVGRenderer.arcTo(currRightX, rowBottom, leftX, rowBottom, r);
                        } else {
                            NanoVGRenderer.lineTo(currRightX, rowBottom);
                        }
                        NanoVGRenderer.lineTo(leftX, rowBottom);
                    }
                }

                NanoVGRenderer.lineTo(leftX, topY);
                NanoVGRenderer.closePath();
            };
        }

        HudGlassmorphism.drawPlatform(minX, topY, maxX, bottomY, context.alpha, pathBuilder);
    }

    private void renderSideBar(List<ModuleEntry> entries, RenderContext context) {
        if (context.barWidth < 0.5f || entries.isEmpty()) return;

        float topY = context.originY;
        float totalH = entries.size() * context.rowHeight;
        float barW = context.barWidth;

        if (context.isRightAligned) {
            float barX = context.originX + context.maxBgWidth - barW;
            NanoVGRenderer.drawRect(barX, topY, barW, totalH, context.accentColor);
        } else {
            float barX = context.originX;
            NanoVGRenderer.drawRect(barX, topY, barW, totalH, context.accentColor);
        }
    }

    private void renderText(ModuleEntry entry, RenderContext context) {
        float textX = context.isRightAligned
                ? entry.x + PADDING_X
                : entry.x + (context.barWidth > 0.5f ? context.barWidth : 0f) + PADDING_X;
        float textY = entry.y + (entry.bgHeight - entry.textHeight) / 2f + 1f;

        Color nameColor = HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_PRIMARY, context.alpha);
        Color shadowColor = new Color(0, 0, 0, HudGlassmorphism.clampColor((int) (125 * context.alpha)));

        if (context.hasShadow) {
            NanoVGRenderer.drawTextWithShadow(entry.name, textX, textY, context.fontSize, nameColor,
                    context.fontId, 1f, 1f, shadowColor);
        } else {
            NanoVGRenderer.drawTextWithFont(entry.name, textX, textY, context.fontSize, nameColor, context.fontId);
        }

        if (entry.suffix != null && !entry.suffix.isEmpty()) {
            float nameW = NanoVGRenderer.getTextWidthWithFont(entry.name, context.fontSize, context.fontId);
            float suffixX = textX + nameW;
            Color suffixColor = HudGlassmorphism.applyAlpha(HudGlassmorphism.TEXT_MUTED, context.alpha);
            String suffixStr = " " + entry.suffix;

            if (context.hasShadow) {
                NanoVGRenderer.drawTextWithShadow(suffixStr, suffixX, textY, context.fontSize, suffixColor,
                        context.fontId, 1f, 1f, shadowColor);
            } else {
                NanoVGRenderer.drawTextWithFont(suffixStr, suffixX, textY, context.fontSize, suffixColor, context.fontId);
            }
        }
    }

    private static class RenderContext {
        final int screenWidth;
        final float fontSize;
        final float barWidth;
        final float alpha;
        final boolean isRightAligned;
        final boolean isRounded;
        final boolean hasShadow;
        final int fontId;
        final Color accentColor;
        final float originX;
        final float originY;
        final float maxBgWidth;
        final float rowHeight;

        RenderContext(int screenWidth, float fontSize, float barWidth, float alpha,
                      boolean isRightAligned, boolean isRounded, boolean hasShadow,
                      int fontId, Color accentColor, float originX, float originY,
                      float maxBgWidth, float rowHeight) {
            this.screenWidth = screenWidth;
            this.fontSize = fontSize;
            this.barWidth = barWidth;
            this.alpha = alpha;
            this.isRightAligned = isRightAligned;
            this.isRounded = isRounded;
            this.hasShadow = hasShadow;
            this.fontId = fontId;
            this.accentColor = accentColor;
            this.originX = originX;
            this.originY = originY;
            this.maxBgWidth = maxBgWidth;
            this.rowHeight = rowHeight;
        }
    }

    private static class ModuleEntry {
        final String name;
        final String suffix;
        final float textWidth;
        final float textHeight;
        final float bgWidth;
        final float bgHeight;
        final float x;
        final float y;

        ModuleEntry(Module module, int index, RenderContext context) {
            this.name = module.getName();
            this.suffix = module.getSuffix();

            float nameW = NanoVGRenderer.getTextWidthWithFont(name, context.fontSize, context.fontId);
            float suffixW = (suffix != null && !suffix.isEmpty())
                    ? NanoVGRenderer.getTextWidthWithFont(" " + suffix, context.fontSize, context.fontId)
                    : 0f;

            this.textWidth = nameW + suffixW;
            this.textHeight = NanoVGRenderer.getTextHeight(context.fontSize);
            this.bgWidth = textWidth + PADDING_X * 2 + context.barWidth;
            this.bgHeight = context.rowHeight;
            this.y = context.originY + index * context.rowHeight;
            this.x = context.isRightAligned
                    ? context.originX + context.maxBgWidth - bgWidth
                    : context.originX;
        }
    }

    @Override
    public void saveConfig(com.google.gson.JsonObject json) {
        if (draggable != null) {
            com.google.gson.JsonObject pos = new com.google.gson.JsonObject();
            pos.addProperty("x", draggable.getX());
            pos.addProperty("y", draggable.getY());
            int screenWidth = mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 1000;
            boolean isRight = (draggable.getX() + draggable.getWidth() / 2f) > (screenWidth / 2f);
            pos.addProperty("rightAligned", isRight);
            pos.addProperty("rightEdge", draggable.getX() + draggable.getWidth());
            json.add("position", pos);
        }
    }

    @Override
    public void loadConfig(com.google.gson.JsonObject json) {
        if (json.has("position") && json.get("position").isJsonObject()) {
            com.google.gson.JsonObject pos = json.getAsJsonObject("position");
            if (pos.has("x") && pos.has("y")) {
                float x = pos.get("x").getAsFloat();
                float y = pos.get("y").getAsFloat();
                loadedX = x;
                loadedY = y;
                if (pos.has("rightAligned") && pos.get("rightAligned").getAsBoolean() && pos.has("rightEdge")) {
                    loadedRightAligned = true;
                    loadedRightEdge = pos.get("rightEdge").getAsFloat();
                } else {
                    loadedRightAligned = false;
                    loadedRightEdge = null;
                }
                if (draggable != null) {
                    if (Boolean.TRUE.equals(loadedRightAligned) && loadedRightEdge != null) {
                        draggable.setX(loadedRightEdge - draggable.getWidth());
                    } else {
                        draggable.setX(x);
                    }
                    draggable.setY(y);
                }
            }
        }
    }
}
