package com.EyeOfHarmonyBuffer.client.transition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 维度转场后处理链（自研 GL，结构仿 EOHBPostChain；shader 逐行翻译自 Nostalgia radial_whiteout.fsh，
 * CC0-1.0，见 LICENSE-nostalgia.txt）。
 * <p>
 * 管线：主FBO深度blit拷贝 -> 白化球pass（C0）-> blit回主FBO。
 * uniform 语义对齐源库 WhiteoutSphereRenderer（BeaconAndTimer/ExtraData/CamPosData/SkyColor）。
 * 只渲染"自己"的转场：无转场进行时 render() 直接返回。shader 一律 GLSL 330 core 原生语法。
 */
public class TransitionPostChain {

    private static final String SHADER_PATH = "shaders/";
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;

    private static TransitionPostChain instance;

    public static TransitionPostChain getInstance() {
        if (instance == null) {
            instance = new TransitionPostChain();
        }
        return instance;
    }

    /** 完全销毁并置空（GL 上下文重建时调用）。 */
    public static void reset() {
        if (instance != null) {
            instance.destroy();
            instance = null;
        }
    }

    private int width = -1;
    private int height = -1;

    // 白化球 pass 输出 C0，输入 skyrip pass
    private GLProgram whiteoutProgram;
    private int whiteoutFramebuffer;
    private int whiteoutColorTexture;

    // 天空撕裂 v2 pass：输入 C0 + 深度 + rift 纹理 -> 输出 C1（地震裂），最终 blit 回主 FBO
    private GLProgram skyripProgram;
    private int skyripFramebuffer;
    private int skyripColorTexture;
    /** rift_data.png 纹理（r=裂缝边界距离，gb=碎片偏移，a=完整标记）。 */
    private static int riftTexture = 0;
    private static boolean riftTextureLoaded = false;
    /** end_portal.png（末地传送门发光贴图）+ end_sky.png（末地天空）。 */
    private static int portalTexture = 0;
    private static int skyTexture = 0;
    private static boolean portalTexLoaded = false;

    // 主 FBO 深度的拷贝（供 pass 采样）
    private int depthTexture;
    private int depthFramebuffer;

    private int quadVao;
    private int quadVbo;

    /** 矩阵读取/上传用缓冲。 */
    private final FloatBuffer matrixBuffer = floatBuffer(new float[16]);

    /** 创建失败后置位：不再重试（避免每帧失败风暴拖死渲染线程）。 */
    private boolean broken;

    /** 已打印过"渲染链已创建"日志（避免刷屏）。 */
    private boolean createdLogged;

    /** 已打印过"skyrip 渲染"日志（避免刷屏）。 */
    private boolean skyripCreatedLogged;

    /** 当前帧 partialTicks，供相机插值使用。 */
    private float partialTicks;

    private TransitionPostChain() {}

    /** 渲染一帧转场特效。在 RenderWorldLastEvent 中调用；失败时静默降级（不影响游戏渲染）。 */
    public void render(Framebuffer main, float partialTicks) {
        if (broken || main == null) {
            return;
        }
        this.partialTicks = partialTicks;
        if (main.framebufferWidth != width || main.framebufferHeight != height || whiteoutProgram == null) {
            resize(main.framebufferWidth, main.framebufferHeight);
            if (whiteoutProgram == null) {
                return;
            }
        }

        // 捕获当前 投影 与 模型视图 矩阵。此刻处于 RenderWorldLastEvent：
        // 世界透视与相机矩阵仍在 GL 栈上（天空盒已渲染完毕，其状态已自行恢复）。
        float[] invProjection = captureInverseProjection();
        float[] modelView = captureModelView();

        // 主 FBO 深度拷贝（renderbuffer 不可直接采样）
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.framebufferObject);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthFramebuffer);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        // 全屏后处理不需要深度测试/深度写入/混合
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);

        // pass 1：白化球（输入：主 FBO 颜色 + 深度 -> 离屏 C0）
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, whiteoutFramebuffer);
        GL11.glViewport(0, 0, width, height);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.framebufferTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        whiteoutProgram.use();
        GL20.glUniform1i(whiteoutProgram.uniform("DiffuseSampler"), 0);
        GL20.glUniform1i(whiteoutProgram.uniform("DepthSampler"), 1);
        setWhiteoutUniforms(whiteoutProgram, invProjection, modelView);
        drawFullscreenQuad();
        GL20.glUseProgram(0);

        // pass 2：天空撕裂 v2（输入：C0 白化输出 + 深度 + rift 纹理 -> 离屏 C1）
        if (TransitionClientState.isSkyRipActive() && skyripProgram != null && getRiftTexture() != 0) {
            if (!skyripCreatedLogged) {
                skyripCreatedLogged = true;
                EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Sky rip v2 pass rendering");
            }
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, skyripFramebuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, whiteoutColorTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, getRiftTexture());
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, getPortalTexture());
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, getSkyTexture());
            skyripProgram.use();
            GL20.glUniform1i(skyripProgram.uniform("DiffuseSampler"), 0);
            GL20.glUniform1i(skyripProgram.uniform("DepthSampler"), 1);
            GL20.glUniform1i(skyripProgram.uniform("RiftSampler"), 2);
            GL20.glUniform1i(skyripProgram.uniform("PortalSampler"), 3);
            GL20.glUniform1i(skyripProgram.uniform("SkySampler"), 4);
            setSkyRipUniforms(skyripProgram, invProjection, modelView);
            drawFullscreenQuad();
            GL20.glUseProgram(0);
        } else {
            // skyrip 未激活：C1 直接复制 C0（保持链路完整）
            copyTexture(whiteoutFramebuffer, skyripFramebuffer, whiteoutColorTexture, skyripColorTexture);
        }

        // blit：skyrip 输出 -> 主 FBO
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, skyripFramebuffer);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // 恢复状态，供手与 HUD 渲染继续
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject);
        GL11.glViewport(0, 0, main.framebufferWidth, main.framebufferHeight);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // ================= uniforms（对齐源库 WhiteoutSphereRenderer） =================

    private void setWhiteoutUniforms(GLProgram program, float[] inverseProjection, float[] modelView) {
        // BeaconAndTimer = (center+0.5, transitionTimeSeconds)
        GL20.glUniform4f(program.uniform("BeaconAndTimer"),
            centerX(), centerY(), centerZ(), TransitionClientState.transitionTimeSeconds());
        // ExtraData = (whiteoutAlpha, inNewDimension?1:0, whiteRadius, alphaRadius)
        GL20.glUniform4f(program.uniform("ExtraData"),
            TransitionClientState.whiteoutAlpha(),
            TransitionClientState.isInNewDimension() ? 1.0F : 0.0F,
            TransitionClientState.whiteRadius(),
            TransitionClientState.alphaRadius());
        // CamPosData = (camPos, 云高 192 未使用)
        GL20.glUniform4f(program.uniform("CamPosData"),
            (float) camX(), (float) camY(), (float) camZ(), 192.0F);
        // SkyColor = 源库 cosTime 公式：clamp(cos((timeOfDay-0.25)*2π)*2+0.5, 0, 1) 乘 (0.47, 0.66, 1.0)
        Minecraft mc = Minecraft.getMinecraft();
        long dayTime = mc.theWorld != null ? mc.theWorld.getWorldTime() : 0;
        float timeOfDay = (dayTime % 24000L) / 24000.0F;
        float cosTime = (float) Math.cos((timeOfDay - 0.25F) * Math.PI * 2.0F) * 2.0F + 0.5F;
        cosTime = Math.max(0.0F, Math.min(cosTime, 1.0F));
        GL20.glUniform4f(program.uniform("SkyColor"),
            0.47F * cosTime, 0.66F * cosTime, 1.0F * cosTime, 1.0F);
        GL20.glUniform1f(program.uniform("uCoverWhite"), TransitionClientState.coverWhite());
        uploadMatrix(program, "InverseTransformMatrix", inverseProjection);
        uploadMatrix(program, "ModelViewMat", modelView);
    }

    private void setSkyRipUniforms(GLProgram program, float[] inverseProjection, float[] modelView) {
        // BeaconAndTimer = (撕裂中心+0.5, 撕裂时间秒)
        GL20.glUniform4f(program.uniform("BeaconAndTimer"),
            TransitionClientState.getCenterX() + 0.5F,
            TransitionClientState.getCenterY() + 0.5F,
            TransitionClientState.getCenterZ() + 0.5F,
            TransitionClientState.skyRipTimeSeconds());
        GL20.glUniform1f(program.uniform("CrackPlaneY"),
            (float) com.EyeOfHarmonyBuffer.Config.MainConfig.DimensionTransitionSkyRipCrackPlaneY);
        GL20.glUniform1f(program.uniform("uSkyRipActive"), 1.0F);
        uploadMatrix(program, "InverseTransformMatrix", inverseProjection);
        uploadMatrix(program, "ModelViewMat", modelView);
    }

    /** end_portal.png（末地传送门发光贴图），懒加载。 */
    private static int getPortalTexture() {
        if (portalTexture != 0 && !GL11.glIsTexture(portalTexture)) {
            portalTexture = 0;
            portalTexLoaded = false;
        }
        if (portalTexture == 0 && !portalTexLoaded) {
            portalTexture = loadVanillaTexture("textures/entity/end_portal.png", "end_portal");
            portalTexLoaded = true;
        }
        return portalTexture;
    }

    /** end_sky.png（末地天空），懒加载。 */
    private static int getSkyTexture() {
        if (skyTexture != 0 && !GL11.glIsTexture(skyTexture)) {
            skyTexture = 0;
            portalTexLoaded = false;
        }
        if (skyTexture == 0 && !portalTexLoaded) {
            skyTexture = loadVanillaTexture("textures/environment/end_sky.png", "end_sky");
        }
        return skyTexture;
    }

    /** 从原版资源加载 RGBA 纹理为 GL 纹理对象。 */
    private static int loadVanillaTexture(String path, String name) {
        try {
            ResourceLocation loc = new ResourceLocation(path);
            IResource res = Minecraft.getMinecraft().getResourceManager().getResource(loc);
            if (res == null) {
                EyeOfHarmonyBuffer.LOGGER.error("[EOHB] {} texture missing: {}", name, path);
                return 0;
            }
            java.awt.image.BufferedImage img;
            try {
                img = javax.imageio.ImageIO.read(res.getInputStream());
            } finally {
                res.getInputStream().close();
            }
            if (img == null) {
                return 0;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    buf.put((byte) ((argb >> 16) & 0xFF));
                    buf.put((byte) ((argb >> 8) & 0xFF));
                    buf.put((byte) (argb & 0xFF));
                    buf.put((byte) ((argb >> 24) & 0xFF));
                }
            }
            buf.flip();
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            // 原版末地传送门是 REPEAT 平铺（纹理坐标超出 [0,1] 重复，不是边缘钳制）
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            EyeOfHarmonyBuffer.LOGGER.info("[EOHB] {} texture loaded ({}x{})", name, w, h);
            return tex;
        } catch (Throwable t) {
            EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Failed to load {} texture", name, t);
            return 0;
        }
    }

    /** rift_data.png 纹理（懒加载，仿黑洞程序噪声纹理模式）。 */
    private static int getRiftTexture() {
        if (riftTexture != 0 && !GL11.glIsTexture(riftTexture)) {
            riftTexture = 0;
            riftTextureLoaded = false;
        }
        if (riftTexture == 0 && !riftTextureLoaded) {
            try {
                ResourceLocation loc = new ResourceLocation(EyeOfHarmonyBuffer.MODID,
                    "textures/environment/rift_data.png");
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                    Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream());
                if (img == null) {
                    EyeOfHarmonyBuffer.LOGGER.error("[EOHB] rift_data.png missing or unreadable");
                    riftTextureLoaded = true;
                    return 0;
                }
                int w = img.getWidth();
                int h = img.getHeight();
                java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(w * h * 4);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = img.getRGB(x, y);
                        buf.put((byte) ((argb >> 16) & 0xFF));
                        buf.put((byte) ((argb >> 8) & 0xFF));
                        buf.put((byte) (argb & 0xFF));
                        buf.put((byte) ((argb >> 24) & 0xFF));
                    }
                }
                buf.flip();
                int tex = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                riftTexture = tex;
                EyeOfHarmonyBuffer.LOGGER.info("[EOHB] rift_data.png loaded ({}x{})", w, h);
            } catch (Throwable t) {
                EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Failed to load rift_data.png", t);
            }
            riftTextureLoaded = true;
        }
        return riftTexture;
    }

    /** 把 srcFBO 的颜色纹理复制到 dstFBO（skyrip 未激活时保持链路）。 */
    private static void copyTexture(int srcFbo, int dstFbo, int srcTex, int dstTex) {
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(0, 0, srcTexW, srcTexH, 0, 0, srcTexW, srcTexH,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static int srcTexW = -1;
    private static int srcTexH = -1;

    private void uploadMatrix(GLProgram program, String name, float[] matrix) {
        // ModelViewMat 语义为纯旋转：置零平移列（平移由 CamPosData 承担）
        if ("ModelViewMat".equals(name)) {
            matrix[12] = 0.0F;
            matrix[13] = 0.0F;
            matrix[14] = 0.0F;
        }
        matrixBuffer.clear();
        matrixBuffer.put(matrix);
        matrixBuffer.flip();
        GL20.glUniformMatrix4(program.uniform(name), false, matrixBuffer);
    }

    // ================= 相机/中心辅助 =================

    private float centerX() {
        return TransitionClientState.getCenterX() + 0.5F;
    }

    private float centerY() {
        return TransitionClientState.getCenterY() + 0.5F;
    }

    private float centerZ() {
        return TransitionClientState.getCenterZ() + 0.5F;
    }

    private double camX() {
        EntityClientPlayerMP p = Minecraft.getMinecraft().thePlayer;
        return p == null ? 0 : p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks;
    }

    private double camY() {
        EntityClientPlayerMP p = Minecraft.getMinecraft().thePlayer;
        return p == null ? 0 : p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;
    }

    private double camZ() {
        EntityClientPlayerMP p = Minecraft.getMinecraft().thePlayer;
        return p == null ? 0 : p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks;
    }

    // ================= GL 资源 =================

    /** 读取 GL 投影矩阵并求逆（列主序）。 */
    private float[] captureInverseProjection() {
        float[] proj = readMatrix(GL11.GL_PROJECTION_MATRIX);
        float[] inv = new float[16];
        invert4x4(proj, inv);
        return inv;
    }

    /** 读取 GL 模型视图矩阵（1.7.10 下为纯旋转 + 微小偏移，无相机平移）。 */
    private float[] captureModelView() {
        return readMatrix(GL11.GL_MODELVIEW_MATRIX);
    }

    private float[] readMatrix(int pname) {
        float[] out = new float[16];
        matrixBuffer.clear();
        GL11.glGetFloat(pname, matrixBuffer);
        matrixBuffer.rewind();
        matrixBuffer.get(out);
        return out;
    }

    /** 列主序 4x4 伴随矩阵求逆（gl-matrix mat4.invert 同款公式）。 */
    private static void invert4x4(float[] m, float[] out) {
        float a00 = m[0],  a01 = m[1],  a02 = m[2],  a03 = m[3];
        float a10 = m[4],  a11 = m[5],  a12 = m[6],  a13 = m[7];
        float a20 = m[8],  a21 = m[9],  a22 = m[10], a23 = m[11];
        float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];

        float b00 = a00 * a11 - a01 * a10;
        float b01 = a00 * a12 - a02 * a10;
        float b02 = a00 * a13 - a03 * a10;
        float b03 = a01 * a12 - a02 * a11;
        float b04 = a01 * a13 - a03 * a11;
        float b05 = a02 * a13 - a03 * a12;
        float b06 = a20 * a31 - a21 * a30;
        float b07 = a20 * a32 - a22 * a30;
        float b08 = a20 * a33 - a23 * a30;
        float b09 = a21 * a32 - a22 * a31;
        float b10 = a21 * a33 - a23 * a31;
        float b11 = a22 * a33 - a23 * a32;

        float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (det == 0.0F) {
            System.arraycopy(m, 0, out, 0, 16);
            return;
        }
        det = 1.0F / det;

        out[0]  = (a11 * b11 - a12 * b10 + a13 * b09) * det;
        out[1]  = (a02 * b10 - a01 * b11 - a03 * b09) * det;
        out[2]  = (a31 * b05 - a32 * b04 + a33 * b03) * det;
        out[3]  = (a22 * b04 - a21 * b05 - a23 * b03) * det;
        out[4]  = (a12 * b08 - a10 * b11 - a13 * b07) * det;
        out[5]  = (a00 * b11 - a02 * b08 + a03 * b07) * det;
        out[6]  = (a32 * b02 - a30 * b05 - a33 * b01) * det;
        out[7]  = (a20 * b05 - a22 * b02 + a23 * b01) * det;
        out[8]  = (a10 * b10 - a11 * b08 + a13 * b06) * det;
        out[9]  = (a01 * b08 - a00 * b10 - a03 * b06) * det;
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * det;
        out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * det;
        out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * det;
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * det;
        out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * det;
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * det;
    }

    private void resize(int w, int h) {
        destroy();
        if (w <= 0 || h <= 0) {
            return;
        }
        this.width = w;
        this.height = h;
        try {
            // 深度纹理 + 专用 FBO（主 FBO 深度的拷贝目标）
            depthTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            depthFramebuffer = GL30.glGenFramebuffers();
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthTexture, 0);
            // 无颜色 attachment 的 FBO 必须将 draw buffer 设为 GL_NONE，否则视为不完整
            GL20.glDrawBuffers(GL11.GL_NONE);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("depth framebuffer incomplete");
            }

            // C0：白化球 pass 输出
            whiteoutColorTexture = createColorTexture(w, h);
            whiteoutFramebuffer = createFramebuffer(whiteoutColorTexture, "whiteout framebuffer incomplete");

            // C1：天空撕裂 v2 pass 输出（无转场撕裂时由 copyTexture 保持链路）
            skyripColorTexture = createColorTexture(w, h);
            skyripFramebuffer = createFramebuffer(skyripColorTexture, "skyrip framebuffer incomplete");
            srcTexW = w;
            srcTexH = h;
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            // 全屏 quad VAO/VBO（Position 2 分量，[0,1]^2）
            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            FloatBuffer quad = floatBuffer(new float[] { 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F });
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quad, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0L);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            whiteoutProgram = new GLProgram(loadShader("fullscreen.vsh"), loadShader("transition_whiteout.fsh"));
            skyripProgram = new GLProgram(loadShader("fullscreen.vsh"), loadShader("transition_skyrip_v2.fsh"));
            EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Transition post chain created OK ({}x{}), skyrip v2 linked", w, h);
        } catch (Throwable t) {
            EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Failed to create transition post chain, disabled permanently", t);
            broken = true;
            destroy();
        }
    }

    private static int createColorTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int createFramebuffer(int colorTexture, String error) {
        int fbo = GL30.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, colorTexture, 0);
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(error);
        }
        return fbo;
    }

    private static FloatBuffer floatBuffer(float[] data) {
        FloatBuffer buf = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buf.put(data).flip();
        return buf;
    }

    private void drawFullscreenQuad() {
        GL30.glBindVertexArray(quadVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
    }

    private static String loadShader(String name) throws IOException {
        ResourceLocation location = new ResourceLocation(EyeOfHarmonyBuffer.MODID, SHADER_PATH + name);
        IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
        InputStream in = resource.getInputStream();
        try {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /** 自研 GLSL program 封装（标准 GL20 API，同 EOHBPostChain）。 */
    private static final class GLProgram {
        private final int id;

        GLProgram(String vertexSource, String fragmentSource) {
            int vs = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
            int fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            id = GL20.glCreateProgram();
            GL20.glAttachShader(id, vs);
            GL20.glAttachShader(id, fs);
            GL20.glBindAttribLocation(id, 0, "Position");
            GL20.glLinkProgram(id);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("EOHB transition program link failed: "
                    + GL20.glGetProgramInfoLog(id, 4096));
            }
        }

        private static int compileShader(int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetShaderInfoLog(shader, 8192);
                GL20.glDeleteShader(shader);
                throw new IllegalStateException("EOHB transition shader compile failed (type " + type + "): " + log);
            }
            return shader;
        }

        void use() {
            GL20.glUseProgram(id);
        }

        int uniform(String name) {
            return GL20.glGetUniformLocation(id, name);
        }

        void destroy() {
            GL20.glDeleteProgram(id);
        }
    }

    public void destroy() {
        if (whiteoutProgram != null) {
            whiteoutProgram.destroy();
            whiteoutProgram = null;
        }
        if (skyripProgram != null) {
            skyripProgram.destroy();
            skyripProgram = null;
        }
        if (whiteoutFramebuffer != 0) {
            GL30.glDeleteFramebuffers(whiteoutFramebuffer);
            whiteoutFramebuffer = 0;
        }
        if (skyripFramebuffer != 0) {
            GL30.glDeleteFramebuffers(skyripFramebuffer);
            skyripFramebuffer = 0;
        }
        if (skyripColorTexture != 0) {
            GL11.glDeleteTextures(skyripColorTexture);
            skyripColorTexture = 0;
        }
        if (depthFramebuffer != 0) {
            GL30.glDeleteFramebuffers(depthFramebuffer);
            depthFramebuffer = 0;
        }
        if (whiteoutColorTexture != 0) {
            GL11.glDeleteTextures(whiteoutColorTexture);
            whiteoutColorTexture = 0;
        }
        if (depthTexture != 0) {
            GL11.glDeleteTextures(depthTexture);
            depthTexture = 0;
        }
        if (quadVao != 0) {
            GL30.glDeleteVertexArrays(quadVao);
            quadVao = 0;
        }
        if (quadVbo != 0) {
            GL15.glDeleteBuffers(quadVbo);
            quadVbo = 0;
        }
        broken = false;
    }
}
