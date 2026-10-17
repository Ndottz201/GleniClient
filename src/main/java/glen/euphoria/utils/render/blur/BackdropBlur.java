package glen.euphoria.utils.render.blur;

import glen.euphoria.utils.render.nanovg.MainFramebufferTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.nanovg.NanoVG.NVG_IMAGE_FLIPY;
import static org.lwjgl.nanovg.NanoVG.nvgDeleteImage;
import static org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle;

/**
 * Real frosted-glass backdrop blur for the ClickGUI.
 *
 * Samples Minecraft's main color attachment (the world behind the GUI) and produces a smooth,
 * FULL-resolution blurred image exposed as a NanoVG image, so the panel can draw it behind its
 * translucent fill (genuine glassmorphism, independent of Mojang's deleted shader pipeline).
 *
 * Pipeline (avoids both source aliasing and visible upscale blocks):
 *   1. clean bilinear downsample  src(full) -> half        (kills shimmer/aliasing)
 *   2. wide separable Gaussian     half -> half             (the actual frost strength, cheap)
 *   3. upsample                    half -> full             (bilinear)
 *   4. light separable Gaussian    full -> full             (removes the upscale steps -> smooth)
 * The result is a full-res texture NanoVG draws 1:1, so there are no visible large pixels.
 *
 * Must run on the render thread BEFORE the NanoVG frame is begun. Returns a NanoVG image handle, or
 * -1 if unavailable (shader failed, no GL handle, etc.) so callers fall back to a plain panel.
 */
public final class BackdropBlur {

    private static int program = 0;
    private static int uTexel = -1, uDir = -1, uRadius = -1;
    private static int vao = 0;

    // Half-res ping-pong (wide blur) and full-res ping-pong (upsample + smoothing).
    private static int fboA = 0, texA = 0, fboB = 0, texB = 0;   // half
    private static int fboC = 0, texC = 0, fboD = 0, texD = 0;   // full
    private static int halfW = 0, halfH = 0, fullW = 0, fullH = 0;
    private static long imageVg = 0L;
    private static int nvgImage = -1;

    private static boolean initialized = false;
    private static boolean failed = false;
    private static int diag = 0;

    private BackdropBlur() {}

    private static final String VERT =
            "#version 330 core\n" +
            "out vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));\n" +
            "  vUV = p;\n" +
            "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAG =
            "#version 330 core\n" +
            "in vec2 vUV;\n" +
            "out vec4 frag;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uTexel;\n" +
            "uniform vec2 uDir;\n" +
            "uniform float uRadius;\n" +
            "void main(){\n" +
            "  float w0 = 0.227027;\n" +
            "  float w[4] = float[](0.1945946, 0.1216216, 0.054054, 0.016216);\n" +
            "  float off[4] = float[](1.0, 2.0, 3.0, 4.0);\n" +
            "  vec3 sum = texture(uTex, vUV).rgb * w0;\n" +
            "  for (int i = 0; i < 4; i++){\n" +
            "    vec2 o = uDir * uTexel * off[i] * uRadius;\n" +
            "    sum += texture(uTex, vUV + o).rgb * w[i];\n" +
            "    sum += texture(uTex, vUV - o).rgb * w[i];\n" +
            "  }\n" +
            "  frag = vec4(sum, 1.0);\n" +
            "}\n";

    private static boolean init() {
        if (failed) return false;
        if (initialized) return true;
        try {
            int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
            int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new RuntimeException("link: " + GL20.glGetProgramInfoLog(program));
            }
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);

            uTexel = GL20.glGetUniformLocation(program, "uTexel");
            uDir = GL20.glGetUniformLocation(program, "uDir");
            uRadius = GL20.glGetUniformLocation(program, "uRadius");
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTex"), 0);
            GL20.glUseProgram(0);

            vao = GL30.glGenVertexArrays();
            fboA = GL30.glGenFramebuffers();
            fboB = GL30.glGenFramebuffers();
            fboC = GL30.glGenFramebuffers();
            fboD = GL30.glGenFramebuffers();
            initialized = true;
            return true;
        } catch (Throwable t) {
            System.err.println("[EuphoriaBlur] disabled — " + t);
            failed = true;
            return false;
        }
    }

    private static int compile(int type, String src) {
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("compile: " + GL20.glGetShaderInfoLog(sh));
        }
        return sh;
    }

    private static int makeTex(int w, int h) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        return id;
    }

    private static void attach(int fbo, int tex) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
    }

    private static void ensureTextures(long vg, int hw, int hh, int fw, int fh) {
        if (texA != 0 && halfW == hw && halfH == hh && fullW == fw && fullH == fh) return;

        if (texA != 0) GL11.glDeleteTextures(texA);
        if (texB != 0) GL11.glDeleteTextures(texB);
        if (texC != 0) GL11.glDeleteTextures(texC);
        if (texD != 0) GL11.glDeleteTextures(texD);
        if (nvgImage > 0 && imageVg != 0L) nvgDeleteImage(imageVg, nvgImage);

        halfW = hw; halfH = hh; fullW = fw; fullH = fh;
        texA = makeTex(hw, hh);
        texB = makeTex(hw, hh);
        texC = makeTex(fw, fh);
        texD = makeTex(fw, fh);
        attach(fboA, texA);
        attach(fboB, texB);
        attach(fboC, texC);
        attach(fboD, texD);

        imageVg = vg;
        nvgImage = nvglCreateImageFromHandle(vg, texC, fw, fh, NVG_IMAGE_FLIPY);
        System.out.println("[EuphoriaBlur] textures half=" + hw + "x" + hh + " full=" + fw + "x" + fh
                + " nvgImage=" + nvgImage);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void blit(int targetFbo, int srcTex, float dirX, float dirY) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        GL20.glUniform2f(uDir, dirX, dirY);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /**
     * Blurs the current main color attachment and returns a smooth full-res NanoVG image, or -1.
     * @param radius half-res Gaussian radius (controls frost strength)
     */
    public static int render(long vg, int fbWidth, int fbHeight, float radius) {
        if (!init()) { if (diag < 3) { diag++; System.out.println("[EuphoriaBlur] init failed"); } return -1; }
        if (fbWidth <= 0 || fbHeight <= 0) return -1;

        int src = MainFramebufferTarget.currentMainColorTexId();
        if (src <= 0) { if (diag < 3) { diag++; System.out.println("[EuphoriaBlur] no src tex (" + src + ")"); } return -1; }

        int hw = Math.max(1, fbWidth / 2);
        int hh = Math.max(1, fbHeight / 2);

        // Save GL state we touch.
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevSampler = GL11.glGetInteger(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING);
        org.lwjgl.opengl.GL33.glBindSampler(0, 0);
        java.nio.ByteBuffer prevMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, prevMask);
        GL11.glColorMask(true, true, true, true);

        try {
            ensureTextures(vg, hw, hh, fbWidth, fbHeight);
            if (nvgImage <= 0) { if (diag < 3) { diag++; System.out.println("[EuphoriaBlur] nvg image creation failed (" + nvgImage + ")"); } return -1; }

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);

            // --- Half-res: clean downsample + wide Gaussian (the frost strength) ---
            GL11.glViewport(0, 0, hw, hh);
            GL20.glUniform2f(uTexel, 1f / hw, 1f / hh);
            GL20.glUniform1f(uRadius, 0f);
            blit(fboB, src, 1f, 0f);                 // downsample src -> B
            GL20.glUniform1f(uRadius, Math.max(0.5f, radius));
            blit(fboA, texB, 1f, 0f);                // H: B -> A
            blit(fboB, texA, 0f, 1f);                // V: A -> B

            // --- Upsample to full res, then a light Gaussian to erase the upscale steps ---
            GL11.glViewport(0, 0, fbWidth, fbHeight);
            GL20.glUniform2f(uTexel, 1f / hw, 1f / hh);   // still sampling the half-res B
            GL20.glUniform1f(uRadius, 0f);
            blit(fboC, texB, 1f, 0f);                // upsample B(half) -> C(full)
            GL20.glUniform2f(uTexel, 1f / fbWidth, 1f / fbHeight);
            GL20.glUniform1f(uRadius, 2f);
            blit(fboD, texC, 1f, 0f);                // H: C -> D
            blit(fboC, texD, 0f, 1f);                // V: D -> C  (final, full res, smooth)

            if (diag < 3) {
                diag++;
                System.out.println("[EuphoriaBlur] rendered src=" + src + " full=" + fbWidth + "x" + fbHeight
                        + " img=" + nvgImage + " radius=" + radius + " glErr=" + GL11.glGetError());
            }
            return nvgImage;
        } catch (Throwable t) {
            System.err.println("[EuphoriaBlur] render failed — " + t);
            failed = true;
            return -1;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
            GL11.glColorMask(prevMask.get(0) != 0, prevMask.get(1) != 0, prevMask.get(2) != 0, prevMask.get(3) != 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            org.lwjgl.opengl.GL33.glBindSampler(0, prevSampler);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);
            GL13.glActiveTexture(prevActive);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}
