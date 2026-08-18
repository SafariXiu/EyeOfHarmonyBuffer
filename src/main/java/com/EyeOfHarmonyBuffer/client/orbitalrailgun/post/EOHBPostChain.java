package com.EyeOfHarmonyBuffer.client.orbitalrailgun.post;

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
 * <p>shader 一律使用 GLSL 330 core 原生语法（避免 CompatShaderTransformer
 * 的 ANTLR 解析路径——它会对 GLSL 120 兼容语法做转换，遇到 GlShader 附加的
 * '\0' 结尾会报语法错误甚至卡死编译）。</p>
 */
public class EOHBPostChain {

    private static final String SHADER_PATH = "shaders/";

    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;

    private final RailgunClientState state;

    private int width = -1;
    private int height = -1;

    private GLProgram chromaticProgram;
    private int offscreenFramebuffer;
    private int offscreenColorTexture;
    private int offscreenDepthTexture;

    private int quadVao;
    private int quadVbo;

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
        if (main.framebufferWidth != width || main.framebufferHeight != height || chromaticProgram == null) {
            resize(main.framebufferWidth, main.framebufferHeight);
            if (chromaticProgram == null) {
                return;
            }
        }

        // 输入：主 FBO 颜色纹理 → 采样单元 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.framebufferTexture);

        // pass：色差 → 离屏 RT（全屏后处理不需要深度测试/深度写入/混合，
        // 且离屏 RT 的深度纹理未初始化，开着深度测试会导致片段全部被丢弃、输出纯黑）
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, offscreenFramebuffer);
        GL11.glViewport(0, 0, width, height);
        chromaticProgram.use();
        setUniforms(chromaticProgram);
        drawFullscreenQuad();
        GL20.glUseProgram(0);

        // blit：离屏 RT → 主 FBO（绑定统一走 GLStateManager，保持状态追踪一致）
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, offscreenFramebuffer);
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
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void setUniforms(GLProgram program) {
        GL20.glUniform1f(program.uniform("iTime"), state.getStrikeSeconds(partialTicks));
        GL20.glUniform3f(program.uniform("CameraPosition"), (float) camX(), (float) camY(), (float) camZ());
        GL20.glUniform3f(program.uniform("BlockPosition"),
            state.getStrikeX() + 0.5F, state.getStrikeY() + 0.5F, state.getStrikeZ() + 0.5F);
        GL20.glUniform2f(program.uniform("OutSize"), width, height);
        GL20.glUniform1f(program.uniform("StrikeRadius"), state.getStrikeRadius());
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

    private void resize(int w, int h) {
        destroy();
        if (w <= 0 || h <= 0) {
            return;
        }
        this.width = w;
        this.height = h;
        try {
            // 离屏颜色纹理
            offscreenColorTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, offscreenColorTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // 离屏深度纹理
            offscreenDepthTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, offscreenDepthTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // FBO：颜色 + 深度
            offscreenFramebuffer = GL30.glGenFramebuffers();
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, offscreenFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, offscreenColorTexture, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, offscreenDepthTexture, 0);
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                EyeOfHarmonyBuffer.LOGGER.error("[EOHB] OrbitalRailgun post framebuffer incomplete, status=" + status);
                broken = true;
                destroy();
                return;
            }
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

            chromaticProgram = new GLProgram(loadShader("fullscreen.vsh"), loadShader("chromatic_abjuration.fsh"));
        } catch (Throwable t) {
            EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Failed to create OrbitalRailgun post chain, disabled permanently", t);
            broken = true;
            destroy();
        }
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
        if (chromaticProgram != null) {
            chromaticProgram.destroy();
            chromaticProgram = null;
        }
        if (offscreenFramebuffer != 0) {
            GL30.glDeleteFramebuffers(offscreenFramebuffer);
            offscreenFramebuffer = 0;
        }
        if (offscreenColorTexture != 0) {
            GL11.glDeleteTextures(offscreenColorTexture);
            offscreenColorTexture = 0;
        }
        if (offscreenDepthTexture != 0) {
            GL11.glDeleteTextures(offscreenDepthTexture);
            offscreenDepthTexture = 0;
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
