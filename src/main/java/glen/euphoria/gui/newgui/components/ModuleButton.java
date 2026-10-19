package glen.euphoria.gui.newgui.components;

import glen.euphoria.gui.newgui.GuiConstants;
import glen.euphoria.module.Module;
import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;

import java.awt.*;

public class ModuleButton {
    private final Module module;
    private final float relativeX;
    private final float relativeY;
    private final int width;
    private final int height;

    private float searchAlpha = 1f;
    private float targetSearchAlpha = 1f;
    private boolean matchesSearch = true;
    private float hoverProgress = 0f;
    private String searchQuery = "";

    private float enableProgress = 0f;
    private float activationFlash = 0f;
    private boolean lastKnownState;

    public ModuleButton(Module module, float relativeX, float relativeY, int width, int height) {
        this.module = module;
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.width = width;
        this.height = height;
        this.lastKnownState = module.isEnabled();
        this.enableProgress = module.isEnabled() ? 1f : 0f;
    }

    public void updateSearch(String query, float deltaTime) {
        this.searchQuery = query;

        if (query.isEmpty()) {
            matchesSearch = true;
            targetSearchAlpha = 1f;
        } else {
            matchesSearch = module.getName().toLowerCase().contains(query);
            targetSearchAlpha = matchesSearch ? 1f : 0f;
        }

        float speed = 9f;
        if (searchAlpha < targetSearchAlpha) {
            searchAlpha += deltaTime * speed;
            if (searchAlpha > targetSearchAlpha) searchAlpha = targetSearchAlpha;
        } else if (searchAlpha > targetSearchAlpha) {
            searchAlpha -= deltaTime * speed;
            if (searchAlpha < targetSearchAlpha) searchAlpha = targetSearchAlpha;
        }
    }

    public void render(float panelX, float panelY, int mouseX, int mouseY, float alpha) {
        render(panelX, panelY, mouseX, mouseY, alpha, false);
    }

    public void render(float panelX, float panelY, int mouseX, int mouseY, float alpha, boolean isLastVisible) {
        if (searchAlpha <= 0.01f) return;

        float combinedAlpha = alpha * searchAlpha;
        float x = panelX + relativeX;
        float y = panelY + relativeY;

        boolean hovered = isMouseOver(x, y, width, height, mouseX, mouseY);
        float deltaTime = 0.016f;

        updateHover(hovered, deltaTime);
        updateEnableAnimation(deltaTime);

        Color accent = ClientSettingsModule.getAccentColor();
        float cornerRadius = isLastVisible ? 10f : 0f;

        Color idleOverlay = new Color(255, 255, 255, clampColor((int) (5 * combinedAlpha)));
        Color hoverOverlay = new Color(83, 130, 220, clampColor((int) (28 * hoverProgress * combinedAlpha)));
        Color enabledFill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (165 * enableProgress * combinedAlpha)));
        Color enabledGlow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (70 * enableProgress * combinedAlpha)));

        if (hoverProgress > 0.01f || enableProgress > 0.01f) {
            drawRectOrRounded(x, y, width, height, idleOverlay, isLastVisible, cornerRadius);
        }

        if (enableProgress > 0.01f) {
            drawRectOrRounded(x, y, width, height, enabledFill, isLastVisible, cornerRadius);
            drawRectOrRounded(x, y, width, width > 0 ? 1f : 0f, new Color(255, 255, 255, clampColor((int) (18 * combinedAlpha))), isLastVisible, cornerRadius);
            drawPremiumSweep(x, y, width, height, combinedAlpha * enableProgress, isLastVisible, cornerRadius);
            drawRectOrRounded(x, y, width, height, enabledGlow, isLastVisible, cornerRadius);
        }

        if (hoverProgress > 0.01f) {
            drawRectOrRounded(x, y, width, height, hoverOverlay, isLastVisible, cornerRadius);
        }

        if (activationFlash > 0.01f) {
            int flashAlpha = clampColor((int) (52 * easeOutCubic(activationFlash) * combinedAlpha));
            drawRectOrRounded(
                    x, y, width, height,
                    new Color(255, 255, 255, flashAlpha),
                    isLastVisible, cornerRadius
            );
        }

        if (matchesSearch && !searchQuery.isEmpty() && searchAlpha > 0.9f) {
            NanoVGRenderer.drawRoundedRect(
                    x + 2f, y + 3f, 2f, height - 6f, 1f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampColor((int) (210 * combinedAlpha)))
            );
        }

        Color textIdle = new Color(162, 178, 205, clampColor((int) (255 * combinedAlpha)));
        Color textEnabled = new Color(242, 247, 255, clampColor((int) (255 * combinedAlpha)));
        Color textColor = mixColor(textIdle, textEnabled, Math.max(enableProgress, hoverProgress * 0.55f));

        float fontSize = 9.5f;
        float textX = Math.round(x + GuiConstants.PADDING + 2f);
        float textY = Math.round(y + (height - fontSize) / 2f);

        NanoVGRenderer.drawText(module.getName(), textX, textY, fontSize, textColor);

        if (module.getKey() != 0 && module.getKey() != -1) {
            String keyName = getKeyDisplayName(module.getKey());
            if (keyName != null && !keyName.isEmpty()) {
                keyName = "[" + keyName + "]";
                float kbFontSize = 7.2f;
                float kbWidth = NanoVGRenderer.getTextWidth(keyName, kbFontSize);
                float kbX = Math.round(x + width - kbWidth - GuiConstants.PADDING - 2f);
                float kbY = Math.round(y + (height - kbFontSize) / 2f);

                Color kbColor = new Color(
                        textColor.getRed(),
                        textColor.getGreen(),
                        textColor.getBlue(),
                        clampColor((int) (textColor.getAlpha() * 0.65f))
                );

                NanoVGRenderer.drawText(keyName, kbX, kbY, kbFontSize, kbColor);
            }
        }
    }

    private void updateHover(boolean hovered, float deltaTime) {
        float target = hovered ? 1f : 0f;
        float speed = hovered ? 7f : 9f;

        if (hoverProgress < target) {
            hoverProgress += deltaTime * speed;
            if (hoverProgress > target) hoverProgress = target;
        } else if (hoverProgress > target) {
            hoverProgress -= deltaTime * speed;
            if (hoverProgress < target) hoverProgress = target;
        }
    }

    private void updateEnableAnimation(float deltaTime) {
        boolean currentState = module.isEnabled();
        if (currentState != lastKnownState) {
            lastKnownState = currentState;
            activationFlash = 1f;
        }

        float target = currentState ? 1f : 0f;
        float speed = currentState ? 5.5f : 8f;

        if (enableProgress < target) {
            enableProgress += deltaTime * speed;
            if (enableProgress > target) enableProgress = target;
        } else if (enableProgress > target) {
            enableProgress -= deltaTime * speed;
            if (enableProgress < target) enableProgress = target;
        }

        if (activationFlash > 0f) {
            activationFlash -= deltaTime * 4f;
            if (activationFlash < 0f) activationFlash = 0f;
        }
    }

    private void drawPremiumSweep(float x, float y, float width, float height, float alpha, boolean isLast, float cornerRadius) {
        long time = System.currentTimeMillis();
        float phase = ((time % 2400L) / 2400f);
        phase = easeInOutSine(phase);

        float bandWidth = width * 0.28f;
        float totalTravel = width + bandWidth * 2f;
        float bandCenter = x - bandWidth + totalTravel * phase;

        int segments = 30;
        float segmentWidth = (bandWidth * 2f) / segments;

        for (int i = 0; i < segments; i++) {
            float segX = bandCenter - bandWidth + segmentWidth * i;
            float segCenter = segX + segmentWidth / 2f;
            float dist = Math.abs(segCenter - bandCenter) / bandWidth;
            dist = Math.min(1f, dist);

            float intensity = (float) Math.exp(-(dist * 2.4f) * (dist * 2.4f));
            int segAlpha = clampColor((int) (65 * intensity * alpha));
            if (segAlpha <= 0) continue;

            float drawX = Math.max(x, segX);
            float drawEnd = Math.min(x + width, segX + segmentWidth + 0.5f);
            float drawWidth = drawEnd - drawX;
            if (drawWidth <= 0f) continue;

            drawRectOrRounded(
                    drawX, y, drawWidth, height,
                    new Color(255, 255, 255, segAlpha),
                    isLast, cornerRadius
            );
        }
    }

    private void drawRectOrRounded(float x, float y, float w, float h, Color color, boolean isLast, float radius) {
        if (w <= 0f || h <= 0f) return;

        if (isLast) {
            NanoVGRenderer.drawRoundedRectVarying(x, y, w, h, 0f, 0f, radius, radius, color);
        } else {
            NanoVGRenderer.drawRect(x, y, w, h, color);
        }
    }

    public boolean mouseClicked(float panelX, float panelY, double mouseX, double mouseY, int button) {
        if (searchAlpha <= 0.01f) return false;

        float x = panelX + relativeX;
        float y = panelY + relativeY;

        if (isMouseOver(x, y, width, height, mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
                return true;
            } else {
                return button == 1;
            }
        }

        return false;
    }

    public Module getModule() {
        return module;
    }

    public float getAbsoluteY(float panelY) {
        return panelY + relativeY;
    }

    public float getSearchAlpha() {
        return searchAlpha;
    }

    private boolean isMouseOver(float x, float y, float w, float h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private Color mixColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(clampColor(r), clampColor(g), clampColor(bl), clampColor(al));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private float easeInOutSine(float t) {
        return -(float) (Math.cos(Math.PI * t) - 1) / 2f;
    }

    private String getKeyDisplayName(int key) {
        if (key == 0 || key == -1) return null;

        if (key <= -100) {
            int b = -100 - key;
            return switch (b) {
                case 0 -> "M1";
                case 1 -> "M2";
                case 2 -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                default -> "M" + (b + 1);
            };
        }

        if (key >= 0 && key <= 7) {
            return switch (key) {
                case 0 -> "M1";
                case 1 -> "M2";
                case 2 -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                case 5 -> "M6";
                case 6 -> "M7";
                case 7 -> "M8";
                default -> "M" + (key + 1);
            };
        }

        String glfwName = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
        if (glfwName != null && !glfwName.isEmpty()) return glfwName.toUpperCase();

        return switch (key) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BKSP";
            case 260 -> "INS";
            case 261 -> "DEL";
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
            default -> null;
        };
    }
}