package glen.euphoria.utils.render.font.fonts;

import com.google.common.base.Preconditions;
import glen.euphoria.utils.render.font.util.Colors;
import glen.euphoria.utils.render.font.util.RendererUtils;
import it.unimi.dsi.fastutil.chars.Char2IntArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;
import java.io.Closeable;
import java.util.List;

/**
 * A simple font renderer. Supports multiple fonts, passed in as {@link Font} instances.
 *
 * <p>Ported to 1.21.11: the glyph atlas and layout are unchanged, but the textured-quad
 * draw — which used the deleted {@code Tessellator}+{@code BufferRenderer}+{@code POSITION_TEXTURE_COLOR}
 * path — now emits into the vanilla text render layer ({@code RenderLayers.text(Identifier)})
 * through the immediate {@link VertexConsumerProvider}, which carries the same custom TTF
 * glyph textures through Mojang's new render pipeline.</p>
 */
public class FontRenderer implements Closeable {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Char2IntArrayMap colorCodes = new Char2IntArrayMap() {{
        put('0', 0x000000);
        put('1', 0x0000AA);
        put('2', 0x00AA00);
        put('3', 0x00AAAA);
        put('4', 0xAA0000);
        put('5', 0xAA00AA);
        put('6', 0xFFAA00);
        put('7', 0xAAAAAA);
        put('8', 0x555555);
        put('9', 0x5555FF);
        put('A', 0x55FF55);
        put('B', 0x55FFFF);
        put('C', 0xFF5555);
        put('D', 0xFF55FF);
        put('E', 0xFFFF55);
        put('F', 0xFFFFFF);
    }};
    private static final Object2ObjectArrayMap<Identifier, ObjectList<DrawEntry>> GLYPH_PAGE_CACHE = new Object2ObjectArrayMap<>();
    private final float originalSize;
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    private final Char2ObjectArrayMap<Glyph> allGlyphs = new Char2ObjectArrayMap<>();
    private final int charsPerPage;
    private final int padding;
    private int scaleMul = 0;
    private Font[] fonts;
    private int previousGameScale = -1;

    public FontRenderer(Font[] fonts, float sizePx, int charactersPerPage, int paddingBetweenCharacters) {
        Preconditions.checkArgument(fonts.length > 0, "fonts.length == 0");
        Preconditions.checkArgument(charactersPerPage > 4, "Unreasonable charactersPerPage count");
        Preconditions.checkArgument(paddingBetweenCharacters > 0, "paddingBetweenCharacters > 0");
        this.originalSize = sizePx;
        this.charsPerPage = charactersPerPage;
        this.padding = paddingBetweenCharacters;
        init(fonts, sizePx);
    }

    public FontRenderer(Font[] fonts, float sizePx) {
        this(fonts, sizePx, 256, 5);
    }

    private static int floorNearestMulN(int x, int n) {
        return n * (int) Math.floor((double) x / (double) n);
    }

    public static String stripControlCodes(String text) {
        char[] chars = text.toCharArray();
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '§') {
                i++;
                continue;
            }
            f.append(c);
        }
        return f.toString();
    }

    private void sizeCheck() {
        int gs = RendererUtils.getGuiScale();
        if (gs != this.previousGameScale) {
            close();
            init(this.fonts, this.originalSize);
        }
    }

    private void init(Font[] fonts, float sizePx) {
        this.previousGameScale = RendererUtils.getGuiScale();
        this.scaleMul = this.previousGameScale;
        this.fonts = new Font[fonts.length];
        for (int i = 0; i < fonts.length; i++) {
            this.fonts[i] = fonts[i].deriveFont(sizePx * this.scaleMul);
        }
    }

    private GlyphMap generateMap(char from, char to) {
        GlyphMap gm = new GlyphMap(from, to, this.fonts, RendererUtils.randomIdentifier(), padding);
        maps.add(gm);
        return gm;
    }

    private Glyph locateGlyph0(char glyph) {
        for (GlyphMap map : maps) {
            if (map.contains(glyph)) {
                return map.getGlyph(glyph);
            }
        }
        int base = floorNearestMulN(glyph, charsPerPage);
        GlyphMap glyphMap = generateMap((char) base, (char) (base + charsPerPage));
        return glyphMap.getGlyph(glyph);
    }

    private Glyph locateGlyph1(char glyph) {
        return allGlyphs.computeIfAbsent(glyph, this::locateGlyph0);
    }

    /**
     * Draws a string.
     *
     * @param stack The MatrixStack
     * @param s     The string to draw
     * @param x     X coordinate to draw at
     * @param y     Y coordinate to draw at
     * @param color Text colour
     */
    public void drawString(MatrixStack stack, String s, float x, float y, Color color) {
        float r = (float) color.getRed() / 255;
        float g = (float) color.getGreen() / 255;
        float b = (float) color.getBlue() / 255;
        float a = (float) color.getAlpha() / 255;
        sizeCheck();
        float r2 = r, g2 = g, b2 = b;
        stack.push();
        stack.translate(x, y, 0);
        stack.scale(1f / this.scaleMul, 1f / this.scaleMul, 1f);

        Matrix4f mat = stack.peek().getPositionMatrix();
        char[] chars = s.toCharArray();
        float xOffset = 0;
        float yOffset = 0;
        boolean inSel = false;
        int lineStart = 0;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inSel) {
                inSel = false;
                char c1 = Character.toUpperCase(c);
                if (colorCodes.containsKey(c1)) {
                    int ii = colorCodes.get(c1);
                    int[] col = Colors.RGBIntToRGB(ii);
                    r2 = col[0] / 255f;
                    g2 = col[1] / 255f;
                    b2 = col[2] / 255f;
                } else if (c1 == 'R') {
                    r2 = r;
                    g2 = g;
                    b2 = b;
                }
                continue;
            }
            if (c == '§') {
                inSel = true;
                continue;
            } else if (c == '\n') {
                yOffset += getStringHeight(s.substring(lineStart, i)) * scaleMul;
                xOffset = 0;
                lineStart = i + 1;
                continue;
            }
            Glyph glyph = locateGlyph1(c);
            if (glyph.value() != ' ') {
                Identifier i1 = glyph.owner().bindToTexture;
                DrawEntry entry = new DrawEntry(xOffset, yOffset, r2, g2, b2, glyph);
                GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
            }
            xOffset += glyph.width();
        }

        VertexConsumerProvider.Immediate immediate =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        for (Identifier identifier : GLYPH_PAGE_CACHE.keySet()) {
            RenderLayer layer = RenderLayers.text(identifier);
            VertexConsumer vc = immediate.getBuffer(layer);
            List<DrawEntry> objects = GLYPH_PAGE_CACHE.get(identifier);

            for (DrawEntry object : objects) {
                float xo = object.atX;
                float yo = object.atY;
                float cr = object.r;
                float cg = object.g;
                float cb = object.b;
                Glyph glyph = object.toDraw;
                GlyphMap owner = glyph.owner();
                float w = glyph.width();
                float h = glyph.height();
                float u1 = (float) glyph.u() / owner.width;
                float v1 = (float) glyph.v() / owner.height;
                float u2 = (float) (glyph.u() + glyph.width()) / owner.width;
                float v2 = (float) (glyph.v() + glyph.height()) / owner.height;

                vc.vertex(mat, xo + 0, yo + h, 0).texture(u1, v2).color(cr, cg, cb, a).light(FULL_BRIGHT);
                vc.vertex(mat, xo + w, yo + h, 0).texture(u2, v2).color(cr, cg, cb, a).light(FULL_BRIGHT);
                vc.vertex(mat, xo + w, yo + 0, 0).texture(u2, v1).color(cr, cg, cb, a).light(FULL_BRIGHT);
                vc.vertex(mat, xo + 0, yo + 0, 0).texture(u1, v1).color(cr, cg, cb, a).light(FULL_BRIGHT);
            }
            immediate.draw(layer);
        }

        stack.pop();
        GLYPH_PAGE_CACHE.clear();
    }

    public void drawCenteredString(MatrixStack stack, String s, float x, float y, Color color) {
        drawString(stack, s, x - getStringWidth(s) / 2f, y, color);
    }

    public float getStringWidth(String text) {
        char[] c = stripControlCodes(text).toCharArray();
        float currentLine = 0;
        float maxPreviousLines = 0;
        for (char c1 : c) {
            if (c1 == '\n') {
                maxPreviousLines = Math.max(currentLine, maxPreviousLines);
                currentLine = 0;
                continue;
            }
            Glyph glyph = locateGlyph1(c1);
            currentLine += glyph.width() / (float) this.scaleMul;
        }
        return Math.max(currentLine, maxPreviousLines);
    }

    public float getStringHeight(String text) {
        char[] c = stripControlCodes(text).toCharArray();
        if (c.length == 0) {
            c = new char[]{' '};
        }
        float currentLine = 0;
        float previous = 0;
        for (char c1 : c) {
            if (c1 == '\n') {
                if (currentLine == 0) {
                    currentLine = locateGlyph1(' ').height() / (float) this.scaleMul;
                }
                previous += currentLine;
                currentLine = 0;
                continue;
            }
            Glyph glyph = locateGlyph1(c1);
            currentLine = Math.max(glyph.height() / (float) this.scaleMul, currentLine);
        }
        return currentLine + previous;
    }

    @Override
    public void close() {
        for (GlyphMap map : maps) {
            map.destroy();
        }
        maps.clear();
        allGlyphs.clear();
    }

    record DrawEntry(float atX, float atY, float r, float g, float b, Glyph toDraw) {

        @Override
        public @NotNull String toString() {
            return "DrawEntry[" +
                    "atX=" + atX + ", " +
                    "atY=" + atY + ", " +
                    "r=" + r + ", " +
                    "g=" + g + ", " +
                    "b=" + b + ", " +
                    "toDraw=" + toDraw + ']';
        }

    }
}
