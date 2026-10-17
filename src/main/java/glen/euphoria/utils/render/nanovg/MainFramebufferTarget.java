package glen.euphoria.utils.render.nanovg;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * 1.21.5+ render-pipeline bridge for NanoVG.
 *
 * In the new blaze3d GpuDevice pipeline Minecraft renders the whole frame into its own
 * main render target (a non-zero FBO / {@link GpuTexture} color attachment) and only blits
 * that to the window's default framebuffer (FBO 0) at the very end of the frame. NanoVG draws
 * with raw OpenGL into whatever FBO is currently bound — which at the InGameHud render hook is
 * FBO 0. MC's final present blit then paints its main target *over* FBO 0, erasing everything
 * NanoVG drew, so the client GUI is invisible.
 *
 * Fix: bind MC's main color attachment as NanoVG's render target (wrapped in our own FBO with a
 * stencil buffer, which NanoVG's stencil-strokes/concave-fills require) so NanoVG composites into
 * the same texture MC presents, and survives the blit.
 *
 * The GL handle of the color attachment is not exposed by Yarn-mapped API in 1.21.11 (the OpenGL
 * backend classes are unmapped), so it is recovered reflectively from the runtime GlTexture and
 * validated with {@code glIsTexture} — which makes the lookup unambiguous regardless of field name.
 */
public final class MainFramebufferTarget {

    private static int fbo = 0;
    private static int depthStencilRbo = 0;
    private static int rboWidth = 0;
    private static int rboHeight = 0;

    private static Field cachedIdField;

    // Diagnostics for the first few frames so the fix can be confirmed from the log.
    private static int diagFrames = 0;
    public static int lastResolvedTexId = -1;
    public static boolean lastBindOk = false;

    private MainFramebufferTarget() {}

    /**
     * Binds MC's main color attachment (via our own FBO + stencil) as the current draw target.
     * @return true if NanoVG should render here; false to fall back to the previously bound FBO.
     */
    public static boolean bind(int fbWidth, int fbHeight) {
        lastBindOk = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) {
            return false;
        }

        GpuTexture color = mc.getFramebuffer().getColorAttachment();
        if (color == null) {
            return false;
        }

        int texId = resolveColorTexId(color);
        lastResolvedTexId = texId;
        if (texId <= 0) {
            return false;
        }

        if (fbo == 0) {
            fbo = GL30.glGenFramebuffers();
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texId, 0);

        if (depthStencilRbo == 0) {
            depthStencilRbo = GL30.glGenRenderbuffers();
        }
        if (rboWidth != fbWidth || rboHeight != fbHeight) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRbo);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, fbWidth, fbHeight);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            rboWidth = fbWidth;
            rboHeight = fbHeight;
        }
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, depthStencilRbo);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            if (diagFrames < 5) {
                diagFrames++;
                System.out.println("[EuphoriaNVG] main-target FBO incomplete status=0x"
                        + Integer.toHexString(status) + " texId=" + texId + " -> falling back to FBO 0");
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return false;
        }

        GL11.glViewport(0, 0, fbWidth, fbHeight);

        if (diagFrames < 5) {
            diagFrames++;
            System.out.println("[EuphoriaNVG] NanoVG now targeting MC main color texId=" + texId
                    + " via fbo=" + fbo + " (" + fbWidth + "x" + fbHeight + ")");
        }

        lastBindOk = true;
        return true;
    }

    /** Restores the framebuffer binding that was active before {@link #bind}. */
    public static void unbind(int previousFboBinding) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFboBinding);
    }

    /** GL texture name of MC's current main color attachment, or -1. Used by the backdrop blur. */
    public static int currentMainColorTexId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) return -1;
        GpuTexture color = mc.getFramebuffer().getColorAttachment();
        return color == null ? -1 : resolveColorTexId(color);
    }

    /**
     * Recovers the OpenGL texture name backing a {@link GpuTexture}. The base class exposes its
     * dimensions/usage through mapped getters; the GL id lives on the unmapped GlTexture subclass.
     * We scan the int fields of the runtime class hierarchy, skip the known dimension/usage values,
     * and confirm the candidate with {@code glIsTexture} so the match is unambiguous.
     */
    private static int resolveColorTexId(GpuTexture tex) {
        try {
            if (cachedIdField != null) {
                int v = cachedIdField.getInt(tex);
                if (v > 0 && GL11.glIsTexture(v)) {
                    return v;
                }
                cachedIdField = null; // texture was recreated with a different layout; re-resolve
            }

            Set<Integer> known = new HashSet<>();
            known.add(tex.getWidth(0));
            known.add(tex.getHeight(0));
            known.add(tex.getDepthOrLayers());
            known.add(tex.getMipLevels());
            known.add(tex.usage());

            for (Class<?> c = tex.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    int v = f.getInt(tex);
                    if (v > 0 && !known.contains(v) && GL11.glIsTexture(v)) {
                        cachedIdField = f;
                        return v;
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[EuphoriaNVG] failed to resolve main color texture id: " + t);
        }
        return -1;
    }

    public static void cleanup() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (depthStencilRbo != 0) {
            GL30.glDeleteRenderbuffers(depthStencilRbo);
            depthStencilRbo = 0;
        }
        rboWidth = 0;
        rboHeight = 0;
        cachedIdField = null;
    }
}
