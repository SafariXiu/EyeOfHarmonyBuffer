package com.EyeOfHarmonyBuffer.client.orbitalrailgun.post;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.client.orbitalrailgun.RailgunClientState;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 轨道炮后处理链（自研版，不依赖 Angelica 内部类）。
 *
 * <p>只用标准 GL20/GL30 API：自编译 GLSL program、自建 FBO/纹理/全屏 quad。
 * 运行在 Angelica 提供的 GL 3.3 core 上下文里（GL 调用由 Angelica 的 GLSM
 * 自动重定向/追踪），但代码本身不引用任何 Angelica 内部类，避免内部 API
 * 语义/版本漂移风险。</p>
 *
 * <p>管线（对齐 Forge 移植版 railgun.json 的 strike→chromatic→gui 顺序）：
 * <pre>
 *   主 FBO 颜色 --strike(800步SDF光线步进)--> C0 --色差--> C1 --GUI瞄准覆盖--> C2 --blit--> 主 FBO
 *   主 FBO 深度 --blit--> 深度纹理（1.7.10 主 FBO 深度是 renderbuffer/纹理，不可直接采样，需拷贝）
 * </pre>
 * strike pass 分辨率可由配置 OrbitalRailgunStrikePassScale 缩放（默认 1.0 全分辨率）。
 * </p>
 *
 * <p>shader 一律使用 GLSL 330 core 原生语法（避免 CompatShaderTransformer
 * 的 ANTLR 解析路径——它会对 GLSL 120 兼容语法做转换，遇到 GlShader 附加的
 * '\0' 结尾会报语法错误甚至卡死编译）。</p>
 */
public class EOHBPostChain {

    private static final String SHADER_PATH = "shaders/";

    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;

    /** 标记颜色（对齐 Forge 移植版默认配置 #FFFFFF 的解析结果）。 */
    private static final float MARKER_INNER_ALPHA = 0.95F;
    private static final float MARKER_OUTER_ALPHA = 0.85F;

    private final RailgunClientState state;

    private int width = -1;
    private int height = -1;
    private int strikeWidth = -1;
    private int strikeHeight = -1;

    // pass 1（strike）输出：C0，同时是 pass 2 的 DiffuseSampler
    private GLProgram strikeProgram;
    private int strikeFramebuffer;
    private int strikeColorTexture;

    // pass 2（色差）输出：C1，同时是 pass 3 的 DiffuseSampler
    private GLProgram chromaticProgram;
    private int chromaFramebuffer;
    private int chromaColorTexture;

    // pass 3（GUI 瞄准覆盖）输出：C2，最终 blit 回主 FBO
    private GLProgram guiProgram;
    private int guiFramebuffer;
    private int guiColorTexture;

    // 主 FBO 深度的拷贝（供 strike/gui pass 采样）
    private int depthTexture;
    private int depthFramebuffer;

    private int quadVao;
    private int quadVbo;

    /** 矩阵读取/上传用缓冲。 */
    private final FloatBuffer matrixBuffer = floatBuffer(new float[16]);

    /** 创建失败后置位：不再重试（避免每帧失败风暴拖死渲染线程）。 */
    private boolean broken;

    /** 当前帧 partialTicks，供相机插值使用。 */
    private float partialTicks;

    public EOHBPostChain(RailgunClientState state) {
        this.state = state;
    }

    /** 渲染一帧后处理。创建/尺寸变化在内部处理；失败时静默降级（不影响阶段一几何特效）。 */
    public void render(Framebuffer main, float partialTicks) {
        if (broken) {
            return;
        }
        this.partialTicks = partialTicks;
        int strikeW = strikeWidth(main.framebufferWidth);
        int strikeH = strikeHeight(main.framebufferHeight);
        if (main.framebufferWidth != width || main.framebufferHeight != height
            || strikeW != strikeWidth || strikeH != strikeHeight || chromaticProgram == null) {
            resize(main.framebufferWidth, main.framebufferHeight);
            if (chromaticProgram == null) {
                return;
            }
        }

        // 捕获当前 投影 与 模型视图 矩阵。此刻处于 RenderWorldLastEvent：
        // 世界透视与相机矩阵仍在 GL 栈上。1.7.10 相机矩阵是纯旋转（无平移），
        // 世界坐标重建 = inverse(投影)*ndc -> 视图空间，再 inverse(旋转)*视图 + 相机位置
        // （与 Forge 移植版 gui.fsh/strike.fsh 约定一致）
        float[] invProjection = captureInverseProjection();
        float[] modelView = captureModelView();

        // 主 FBO 深度 -> 离屏深度纹理（主 FBO 深度不可直接采样，需 blit 拷贝）
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.framebufferObject);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthFramebuffer);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        // 全屏后处理不需要深度测试/深度写入/混合（离屏 RT 无深度 attachment，
        // 开着深度测试会导致片段全部被丢弃、输出纯黑）
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);

        // pass 1：strike（输入：主 FBO 颜色 + 深度 -> 离屏 C0，分辨率按配置缩放）
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, strikeFramebuffer);
        GL11.glViewport(0, 0, strikeWidth, strikeHeight);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.framebufferTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        strikeProgram.use();
        GL20.glUniform1i(strikeProgram.uniform("DiffuseSampler"), 0);
        GL20.glUniform1i(strikeProgram.uniform("DepthSampler"), 1);
        setStrikeUniforms(strikeProgram, invProjection, modelView);
        drawFullscreenQuad();

        // pass 2：色差（输入：C0 -> 离屏 C1）
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, chromaFramebuffer);
        GL11.glViewport(0, 0, width, height);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, strikeColorTexture);
        chromaticProgram.use();
        setChromaticUniforms(chromaticProgram);
        drawFullscreenQuad();

        // pass 3：GUI 瞄准覆盖（输入：C1 + 深度纹理 -> 离屏 C2）
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, guiFramebuffer);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, chromaColorTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        guiProgram.use();
        GL20.glUniform1i(guiProgram.uniform("DiffuseSampler"), 0);
        GL20.glUniform1i(guiProgram.uniform("DepthSampler"), 1);
        setGuiUniforms(guiProgram, invProjection, modelView);
        drawFullscreenQuad();
        GL20.glUseProgram(0);

        // blit：GUI 输出 -> 主 FBO（绑定统一走 GLStateManager，保持状态追踪一致）
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, guiFramebuffer);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // 恢复深度测试/深度写入（blend 由后续渲染自行设置）
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        // 恢复主 FBO 绑定与视口（后续手/HUD 渲染继续）
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject);
        GL11.glViewport(0, 0, main.framebufferWidth, main.framebufferHeight);

        // 解绑采样纹理
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void setStrikeUniforms(GLProgram program, float[] inverseProjection, float[] modelView) {
        GL20.glUniform1f(program.uniform("iTime"), effectSeconds());
        GL20.glUniform1f(program.uniform("StrikeActive"), state.isStrikeActive() ? 1.0F : 0.0F);
        GL20.glUniform3f(program.uniform("CameraPosition"), (float) camX(), (float) camY(), (float) camZ());
        GL20.glUniform3f(program.uniform("BlockPosition"), blockX(), blockY(), blockZ());
        GL20.glUniform1f(program.uniform("StrikeRadius"), state.getStrikeRadius());
        GL20.glUniform3f(program.uniform("u_BeamColor"), 1.0F, 1.0F, 1.0F);
        GL20.glUniform1f(program.uniform("u_BeamAlpha"), 1.0F);
        GL20.glUniform3f(program.uniform("u_MarkerInnerColor"), 1.0F, 1.0F, 1.0F);
        GL20.glUniform1f(program.uniform("u_MarkerInnerAlpha"), MARKER_INNER_ALPHA);
        GL20.glUniform3f(program.uniform("u_MarkerOuterColor"), 1.0F, 1.0F, 1.0F);
        GL20.glUniform1f(program.uniform("u_MarkerOuterAlpha"), MARKER_OUTER_ALPHA);
        uploadMatrix(program, "InverseTransformMatrix", inverseProjection);
        uploadMatrix(program, "ModelViewMat", modelView);
    }

    private void setChromaticUniforms(GLProgram program) {
        GL20.glUniform1f(program.uniform("iTime"), effectSeconds());
        GL20.glUniform1f(program.uniform("StrikeActive"), state.isStrikeActive() ? 1.0F : 0.0F);
        GL20.glUniform3f(program.uniform("CameraPosition"), (float) camX(), (float) camY(), (float) camZ());
        GL20.glUniform3f(program.uniform("BlockPosition"), blockX(), blockY(), blockZ());
        GL20.glUniform2f(program.uniform("OutSize"), width, height);
        GL20.glUniform1f(program.uniform("StrikeRadius"), state.getStrikeRadius());
    }

    private void setGuiUniforms(GLProgram program, float[] inverseProjection, float[] modelView) {
        GL20.glUniform1f(program.uniform("iTime"), effectSeconds());
        GL20.glUniform3f(program.uniform("BlockPosition"), blockX(), blockY(), blockZ());
        GL20.glUniform1f(program.uniform("IsBlockHit"), isBlockHit());
        GL20.glUniform1f(program.uniform("StrikeActive"), state.isStrikeActive() ? 1.0F : 0.0F);
        GL20.glUniform1f(program.uniform("SelectionActive"), state.isCharging() ? 1.0F : 0.0F);
        GL20.glUniform2f(program.uniform("OutSize"), width, height);
        GL20.glUniform1f(program.uniform("StrikeRadius"), state.getStrikeRadius());
        GL20.glUniform3f(program.uniform("u_MarkerInnerColor"), 1.0F, 1.0F, 1.0F);
        GL20.glUniform1f(program.uniform("u_MarkerInnerAlpha"), MARKER_INNER_ALPHA);
        GL20.glUniform3f(program.uniform("u_MarkerOuterColor"), 1.0F, 1.0F, 1.0F);
        GL20.glUniform1f(program.uniform("u_MarkerOuterAlpha"), MARKER_OUTER_ALPHA);
        GL20.glUniform3f(program.uniform("CameraPosition"), (float) camX(), (float) camY(), (float) camZ());
        uploadMatrix(program, "InverseTransformMatrix", inverseProjection);
        uploadMatrix(program, "ModelViewMat", modelView);
    }

    private void uploadMatrix(GLProgram program, String name, float[] matrix) {
        // ModelViewMat 语义为纯旋转：1.7.10 相机矩阵含 0.1 级小平移（yOffset/近景偏移），
        // 置零平移列以对齐移植版语义（平移已由 CameraPosition 承担）
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

    /** 特效时间轴：打击优先于充能（对齐 Forge 移植版：renderStrike ? strikeSeconds : chargeSeconds）。 */
    private float effectSeconds() {
        return state.isStrikeActive() ? state.getStrikeSeconds(partialTicks) : state.getChargeSeconds(partialTicks);
    }

    private float blockX() {
        return (state.isStrikeActive() ? state.getStrikeX() : state.getHitX()) + 0.5F;
    }

    private float blockY() {
        return (state.isStrikeActive() ? state.getStrikeY() : state.getHitY()) + 0.5F;
    }

    private float blockZ() {
        return (state.isStrikeActive() ? state.getStrikeZ() : state.getHitZ()) + 0.5F;
    }

    /** 目标选框可见性：打击时目标即命中方块；充能时取决于是否有方块命中。 */
    private float isBlockHit() {
        if (state.isStrikeActive()) {
            return 1.0F;
        }
        return state.isCharging() && state.hasTarget() ? 1.0F : 0.0F;
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

    /** strike pass 分辨率：主分辨率 * 配置比例（0.25~1.0）。 */
    private static int strikeWidth(int w) {
        return Math.max(1, (int) Math.round(w * strikeScale()));
    }

    private static int strikeHeight(int h) {
        return Math.max(1, (int) Math.round(h * strikeScale()));
    }

    private static double strikeScale() {
        double s = MainConfig.OrbitalRailgunStrikePassScale;
        return s <= 0.0 ? 1.0 : Math.min(1.0, s);
    }

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

    /** 列主序 4x4 伴随矩阵求逆（gl-matrix mat4.invert 同款公式），in/out 可同一数组。 */
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

        float[] inv = new float[16];
        inv[0]  = (a11 * b11 - a12 * b10 + a13 * b09) * det;
        inv[1]  = (a02 * b10 - a01 * b11 - a03 * b09) * det;
        inv[2]  = (a31 * b05 - a32 * b04 + a33 * b03) * det;
        inv[3]  = (a22 * b04 - a21 * b05 - a23 * b03) * det;
        inv[4]  = (a12 * b08 - a10 * b11 - a13 * b07) * det;
        inv[5]  = (a00 * b11 - a02 * b08 + a03 * b07) * det;
        inv[6]  = (a32 * b02 - a30 * b05 - a33 * b01) * det;
        inv[7]  = (a20 * b05 - a22 * b02 + a23 * b01) * det;
        inv[8]  = (a10 * b10 - a11 * b08 + a13 * b06) * det;
        inv[9]  = (a01 * b08 - a00 * b10 - a03 * b06) * det;
        inv[10] = (a30 * b04 - a31 * b02 + a33 * b00) * det;
        inv[11] = (a21 * b02 - a20 * b04 - a23 * b00) * det;
        inv[12] = (a11 * b07 - a10 * b09 - a12 * b06) * det;
        inv[13] = (a00 * b09 - a01 * b07 + a02 * b06) * det;
        inv[14] = (a31 * b01 - a30 * b03 - a32 * b00) * det;
        inv[15] = (a20 * b03 - a21 * b01 + a22 * b00) * det;
        System.arraycopy(inv, 0, out, 0, 16);
    }

    private void resize(int w, int h) {
        destroy();
        if (w <= 0 || h <= 0) {
            return;
        }
        this.width = w;
        this.height = h;
        this.strikeWidth = strikeWidth(w);
        this.strikeHeight = strikeHeight(h);
        try {
            // 深度纹理 + 专用 FBO（主 FBO 深度的拷贝目标；post pass 不需要深度测试）
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

            // C0：strike pass 输出（按配置比例缩放）
            strikeColorTexture = createColorTexture(strikeWidth, strikeHeight);
            strikeFramebuffer = createFramebuffer(strikeColorTexture, "strike framebuffer incomplete");

            // C1：色差 pass 输出
            chromaColorTexture = createColorTexture(w, h);
            chromaFramebuffer = createFramebuffer(chromaColorTexture, "chroma framebuffer incomplete");

            // C2：GUI pass 输出
            guiColorTexture = createColorTexture(w, h);
            guiFramebuffer = createFramebuffer(guiColorTexture, "gui framebuffer incomplete");
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

            strikeProgram = new GLProgram(loadShader("fullscreen.vsh"), loadShader("strike.fsh"));
            chromaticProgram = new GLProgram(loadShader("fullscreen.vsh"), loadShader("chromatic_abjuration.fsh"));
            guiProgram = new GLProgram(loadShader("fullscreen.vsh"), loadShader("gui.fsh"));
        } catch (Throwable t) {
            EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Failed to create OrbitalRailgun post chain, disabled permanently", t);
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

    /** 自研 GLSL program 封装（标准 GL20 API）。 */
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
                throw new IllegalStateException("EOHB post program link failed: "
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
                throw new IllegalStateException("EOHB post shader compile failed (type " + type + "): " + log);
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
        if (strikeProgram != null) {
            strikeProgram.destroy();
            strikeProgram = null;
        }
        if (chromaticProgram != null) {
            chromaticProgram.destroy();
            chromaticProgram = null;
        }
        if (guiProgram != null) {
            guiProgram.destroy();
            guiProgram = null;
        }
        if (strikeFramebuffer != 0) {
            GL30.glDeleteFramebuffers(strikeFramebuffer);
            strikeFramebuffer = 0;
        }
        if (chromaFramebuffer != 0) {
            GL30.glDeleteFramebuffers(chromaFramebuffer);
            chromaFramebuffer = 0;
        }
        if (guiFramebuffer != 0) {
            GL30.glDeleteFramebuffers(guiFramebuffer);
            guiFramebuffer = 0;
        }
        if (depthFramebuffer != 0) {
            GL30.glDeleteFramebuffers(depthFramebuffer);
            depthFramebuffer = 0;
        }
        if (strikeColorTexture != 0) {
            GL11.glDeleteTextures(strikeColorTexture);
            strikeColorTexture = 0;
        }
        if (chromaColorTexture != 0) {
            GL11.glDeleteTextures(chromaColorTexture);
            chromaColorTexture = 0;
        }
        if (guiColorTexture != 0) {
            GL11.glDeleteTextures(guiColorTexture);
            guiColorTexture = 0;
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
    }
}
