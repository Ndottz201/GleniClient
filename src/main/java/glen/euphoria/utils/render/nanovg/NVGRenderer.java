package glen.euphoria.utils.render.nanovg;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

public final class NVGRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final float COLOR_DIVISOR = 255f;

    private static final long VG = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);

    // TEMP diagnostics for the 1.21.5+ GUI-invisible issue. Logs the first few frames then stops.
    private static final org.slf4j.Logger EUPHORIA_NVG_LOG = org.slf4j.LoggerFactory.getLogger("EuphoriaNVG");
    private static int euphoriaDiagFrames = 0;
    private static int euphoriaDensityDiag = 0;

    public static final NVGPaint NVG_PAINT = NVGPaint.create();
    public static final NVGColor NVG_COLOR_1 = NVGColor.create();
    public static final NVGColor NVG_COLOR_2 = NVGColor.create();
    private static final NVGColor NVG_TRANSPARENT = NVGColor.create();

    private static boolean frameStarted = false;
    private static boolean initialized = false;
    private static boolean contextDirty = false;
    public static float globalAlpha = 1.0f;

    // FBO that was bound before NanoVG took over; restored in endFrame.
    private static int savedDrawFbo = 0;

    static {
        nvgRGBAf(0, 0, 0, 0, NVG_TRANSPARENT);
    }

    private NVGRenderer() {}

    /**
     * Called when window regains focus to restore GL state
     */
    public static void markContextDirty() {
        contextDirty = true;
    }

    private static void restoreContextIfNeeded() {
        if (contextDirty) {
            // Force viewport reset
            if (mc.getWindow() != null) {
                GL11.glViewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
            }
            
            // Reset GL state
            GLStateManager.setup();
            
            // Clear any stale frame state
            if (frameStarted) {
                try {
                    nvgEndFrame(VG);
                } catch (Exception ignored) {}
                frameStarted = false;
            }
            
            contextDirty = false;
        }
    }

    private static void ensureInitialized() {
        if (!initialized) {
            try {
                GLStateManager.setup();
                NanoVGFontManager.loadFonts();
                initialized = true;

                if (NanoVGFontManager.getRegularFont() <= 0) {
                    System.err.println("[NVGRenderer] Warning: Regular font failed to load!");
                }
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error during initialization: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static boolean beginFrame() {
        return beginFrame(1f);
    }

    /**
     * @param densityMultiplier extra device-pixel-ratio factor. Pass {@code 1/guiScale} when the
     *        caller renders scaled-down so glyphs/shapes are rasterized at full density (HD at any
     *        size) instead of being downscaled and looking pixelated.
     */
    public static boolean beginFrame(float densityMultiplier) {
        if (frameStarted) {
            return false;
        }

        ensureInitialized();
        restoreContextIfNeeded();

        final Window window = mc.getWindow();
        if (window == null) {
            return false;
        }

        final int fbWidth = window.getFramebufferWidth();
        final int fbHeight = window.getFramebufferHeight();

        // Safety check - don't begin frame if window is minimized or invalid
        if (fbWidth <= 0 || fbHeight <= 0) {
            return false;
        }

        final float scaleFactor = (float) window.getScaleFactor();

        if (euphoriaDiagFrames < 5) {
            euphoriaDiagFrames++;
            int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prog = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            int errBefore = GL11.glGetError();
            EUPHORIA_NVG_LOG.info("[EuphoriaNVG] beginFrame#{} VG={} fb={}x{} scale={} density={} drawFBO={} readFBO={} program={} glErrBefore={}",
                euphoriaDiagFrames, VG, fbWidth, fbHeight, scaleFactor, Math.max(1f, densityMultiplier), drawFbo, readFbo, prog, errBefore);
        }

        if (densityMultiplier > 1.01f && euphoriaDensityDiag < 3) {
            euphoriaDensityDiag++;
            EUPHORIA_NVG_LOG.info("[EuphoriaNVG] scaled frame: pixelRatio {} -> {} (density x{}) fb={}x{}",
                scaleFactor, scaleFactor * densityMultiplier, densityMultiplier, fbWidth, fbHeight);
        }

        GLStateManager.push();

        // Redirect NanoVG into MC's main render target so it survives the final present blit
        // (see MainFramebufferTarget). Falls back to the current FBO if the target can't be bound.
        savedDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        MainFramebufferTarget.bind(fbWidth, fbHeight);

        nvgBeginFrame(VG,
            fbWidth / scaleFactor,
            fbHeight / scaleFactor,
            scaleFactor * Math.max(1f, densityMultiplier));

        frameStarted = true;
        return true;
    }

    public static void endFrame() {
        if (!frameStarted) {
            return;
        }

        nvgEndFrame(VG);

        // Restore the framebuffer NanoVG inherited (typically FBO 0) before MC continues.
        MainFramebufferTarget.unbind(savedDrawFbo);

        if (euphoriaDiagFrames <= 5) {
            int err = GL11.glGetError();
            if (err != 0) EUPHORIA_NVG_LOG.info("[EuphoriaNVG] endFrame glError={}", err);
        }

        GLStateManager.pop();

        if (mc.getWindow() != null) {
            GL11.glViewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        }

        frameStarted = false;
    }

    public static void globalAlpha(float alpha) {
        globalAlpha = alpha;
        nvgGlobalAlpha(VG, alpha);
    }

    public static void drawRect(float x, float y, float width, float height, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRectVarying(float x, float y, float width, float height,
                                               float radiusTopLeft, float radiusTopRight,
                                               float radiusBottomRight, float radiusBottomLeft, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRectVarying(VG, x, y, width, height,
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRectOutline(float x, float y, float width, float height,
                                               float radius, float strokeWidth, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, strokeWidth);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRectGradient(float x, float y, float width, float height,
                                                float radius, Color color1, Color color2) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        nvgLinearGradient(VG, x, y, x + width, y, NVG_COLOR_1, NVG_COLOR_2, NVG_PAINT);

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRectOutlineVarying(float x, float y, float width, float height,
                                                     float radiusTopLeft, float radiusTopRight,
                                                     float radiusBottomRight, float radiusBottomLeft,
                                                     float strokeWidth, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, strokeWidth);
        nvgRoundedRectVarying(VG, x, y, width, height,
                radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRectGradientVarying(float x, float y, float width, float height,
                                                      float radiusTopLeft, float radiusTopRight,
                                                      float radiusBottomRight, float radiusBottomLeft,
                                                      Color color1, Color color2) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        nvgLinearGradient(VG, x, y, x, y + height, NVG_COLOR_1, NVG_COLOR_2, NVG_PAINT);

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRectVarying(VG, x, y, width, height,
                radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawCircle(float x, float y, float radius, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgCircle(VG, x, y, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float strokeWidth, Color color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, strokeWidth);
        nvgMoveTo(VG, x1, y1);
        nvgLineTo(VG, x2, y2);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void drawRoundedRectWithShadow(float x, float y, float width, float height,
                                                   float radius, Color color, Color shadowColor,
                                                   float shadowBlur, float shadowSpread) {
        applyColor(shadowColor, NVG_COLOR_1);
        nvgBoxGradient(VG, x, y, width, height, radius, shadowBlur, NVG_COLOR_1, NVG_TRANSPARENT, NVG_PAINT);

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRect(VG, x - shadowSpread, y - shadowSpread,
            width + shadowSpread * 2, height + shadowSpread * 2, radius);
        nvgFill(VG);
        nvgClosePath(VG);

        drawRoundedRect(x, y, width, height, radius, color);
    }

    public static void save() {
        nvgSave(VG);
    }

    public static void restore() {
        nvgRestore(VG);
    }

    /** Fills a rounded rect with an image, the image mapped across (patternX,patternY,patternW,patternH). */
    public static void drawImagePatternRounded(int image, float x, float y, float width, float height, float radius,
                                               float patternX, float patternY, float patternW, float patternH, float alpha) {
        nvgImagePattern(VG, patternX, patternY, patternW, patternH, 0f, image, alpha, NVG_PAINT);
        nvgBeginPath(VG);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFillPaint(VG, NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void drawImagePatternRoundedVarying(int image, float x, float y, float width, float height,
                                                      float radiusTopLeft, float radiusTopRight,
                                                      float radiusBottomRight, float radiusBottomLeft,
                                                      float patternX, float patternY, float patternW, float patternH, float alpha) {
        nvgImagePattern(VG, patternX, patternY, patternW, patternH, 0f, image, alpha, NVG_PAINT);
        nvgBeginPath(VG);
        nvgRoundedRectVarying(VG, x, y, width, height,
                radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFillPaint(VG, NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void translate(float x, float y) {
        nvgTranslate(VG, x, y);
    }

    public static void scale(float x, float y) {
        nvgScale(VG, x, y);
    }

    public static void rotate(float angle) {
        nvgRotate(VG, angle);
    }

    public static void scissor(float x, float y, float width, float height) {
        nvgScissor(VG, x, y, width, height);
    }

    public static void resetScissor() {
        nvgResetScissor(VG);
    }

    public static void beginPath() {
        nvgBeginPath(VG);
    }

    public static void closePath() {
        nvgClosePath(VG);
    }

    public static void moveTo(float x, float y) {
        nvgMoveTo(VG, x, y);
    }

    public static void lineTo(float x, float y) {
        nvgLineTo(VG, x, y);
    }

    public static void arcTo(float x1, float y1, float x2, float y2, float radius) {
        nvgArcTo(VG, x1, y1, x2, y2, radius);
    }

    public static void fillPattern(int image, float patternX, float patternY, float patternW, float patternH, float alpha) {
        nvgImagePattern(VG, patternX, patternY, patternW, patternH, 0f, image, alpha, NVG_PAINT);
        nvgFillPaint(VG, NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void fillGradient(float x1, float y1, float x2, float y2, Color color1, Color color2) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);
        nvgLinearGradient(VG, x1, y1, x2, y2, NVG_COLOR_1, NVG_COLOR_2, NVG_PAINT);
        nvgFillPaint(VG, NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void fillColor(Color color) {
        applyColor(color, NVG_COLOR_1);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void strokeColor(Color color, float width) {
        applyColor(color, NVG_COLOR_1);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, width);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void applyColor(Color color, NVGColor nvgColor) {
        nvgRGBAf(
            color.getRed() / COLOR_DIVISOR,
            color.getGreen() / COLOR_DIVISOR,
            color.getBlue() / COLOR_DIVISOR,
            color.getAlpha() / COLOR_DIVISOR,
            nvgColor
        );
    }

    public static long getContext() {
        return VG;
    }

    public static boolean isInFrame() {
        return frameStarted;
    }

    public static void cleanup() {
        MainFramebufferTarget.cleanup();
        if (VG != 0) {
            nvgDelete(VG);
        }
    }
}