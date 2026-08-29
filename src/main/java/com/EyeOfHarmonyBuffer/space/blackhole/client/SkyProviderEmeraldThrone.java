package com.EyeOfHarmonyBuffer.space.blackhole.client;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.Random;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.client.IRenderHandler;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * 翡翠王座天空盒——黑洞逐像素 ray-march（GLSL 120 全屏着色器）。
 * <p>
 * 黑洞渲染算法参考 IterationT（Kary / Tahnass）的末地天空实现（EndSky.glsl），
 * 按算法规格独立重写：物理模型与参数一致（WarpSpace 引力透镜、50 步体积吸积盘、
 * 黑体色温、FBM 卷云、相对论多普勒、torus 白热环、程序化恒星、爱因斯坦环），
 * 代码为 1.7.10 GLSL 120 独立实现。
 * <p>
 * 坐标潮汐锁定：黑洞方向固定（BH_PITCH/BH_YAW），盘朝向由固定光方向决定。
 */
public class SkyProviderEmeraldThrone extends IRenderHandler {

    private static final String VSH_PATH = "/assets/eyeofharmonybuffer/shaders/blackhole.vsh";
    private static final String FSH_PATH = "/assets/eyeofharmonybuffer/shaders/blackhole.fsh";

    /** 黑洞中心方向：仰角 60°（绕 X 抬 30°）、方位 0°（正前方/北方）。 */
    private static final float BH_PITCH = 30.0F;
    private static final float BH_YAW = 0.0F;

    /** 盘面朝向参考方向（黑洞局部系；潮汐锁定固定值）。 */
    private static final float[] LIGHT_DIR = { 0.0F, 0.0F, -1.0F };

    /** 程序噪声纹理尺寸（RGBA 四通道值噪声，REPEAT）。 */
    private static final int NOISE_SIZE = 64;
    private static final long NOISE_SEED = 0xDECAF_BADL;

    private static int programId = 0;
    private static int noiseTexId = 0;
    /** 复用 DirectBuffer 传 uniform 矩阵（Angelica GL 后端要求 native 内存，FloatBuffer.wrap 的堆缓冲会驱动崩溃）。 */
    private static final FloatBuffer BH_MATRIX_BUFFER = BufferUtils.createFloatBuffer(9);
    private static int uTime, uTanHalfFov, uAspect, uWorldToBhLocal, uLightDir, uTilt, uNoise;

    // ===== 游戏内调参（F8 循环切换预设，找到效果后告诉我，我再固化成默认值）=====
    /** 预设：[盘面倾角(rad), 光方向x, 光方向y, 光方向z]。光方向 = 虚拟相机方位（世界太阳方向在视线系的投影）。 */
    private static final float[][] PRESETS = {
        { 0.785f, 1.0f, 0.0f, 0.0f },    // 0(默认): 45° + 光=水平右 —— 赤道吸积盘带
        { 1.571f, 1.0f, 0.0f, 0.0f },    // 1: 90° + 光=水平右 —— 更细的盘带
    };
    private static int presetIndex = 0;
    private static boolean f8Down = false;

    /** 注册 F8 调参热键（ClientProxy.init 调用一次即可）。 */
    public static void registerKeyHandler() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new KeyHandler());
    }

    /** 下一个预设（聊天栏提示当前值）。 */
    public static void nextPreset() {
        setPreset((presetIndex + 1) % PRESETS.length);
    }

    /** 指定预设（0 ~ PRESETS.length-1，越界取模）。 */
    public static void setPreset(int index) {
        presetIndex = ((index % PRESETS.length) + PRESETS.length) % PRESETS.length;
        float[] p = PRESETS[presetIndex];
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(String.format(
                "[EOHB] 黑洞预设 %d/%d: tilt=%.0f° light=(%.1f, %.1f, %.1f)",
                presetIndex + 1, PRESETS.length, Math.toDegrees(p[0]), p[1], p[2], p[3])));
        }
    }

    /** F8：循环切换黑洞参数预设，并把当前参数打到聊天栏（便于反馈）。 */
    private static class KeyHandler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            boolean down = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_F8);
            if (down && !f8Down) {
                f8Down = true;
                nextPreset();
            } else if (!down) {
                f8Down = false;
            }
        }
    }

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        if (world == null || mc.thePlayer == null) {
            return;
        }
        float t = world.getWorldTime() + partialTicks;

        int prog = getProgram();
        if (prog == 0) {
            // shader 不可用：退化为暗色背景，避免花屏
            drawFallbackBackground();
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        GL20.glUseProgram(prog);

        // —— 视线方向重建所需参数 ——
        // 相机空间 → 世界 旋转矩阵（MODELVIEW 的旋转部分取转置）
        FloatBuffer mv = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        float[] worldRot = extractWorldRotation(mv);
        // 黑洞局部系：RotY(-yaw) * RotX(-pitch) * worldRot
        float[] bh = mul3(mul3(rotY(-BH_YAW), rotX(-BH_PITCH)), worldRot);
        // 投影矩阵反推 fov/aspect
        FloatBuffer pr = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pr);
        float invTanHalfFov = pr.get(5);
        float tanHalfFov = 1.0F / invTanHalfFov;
        float aspect = invTanHalfFov / pr.get(0);

        GL20.glUniform1f(uTime, t);
        GL20.glUniform1f(uTanHalfFov, tanHalfFov);
        GL20.glUniform1f(uAspect, aspect);
        BH_MATRIX_BUFFER.clear();
        BH_MATRIX_BUFFER.put(bh);
        BH_MATRIX_BUFFER.flip();
        GL20.glUniformMatrix3(uWorldToBhLocal, false, BH_MATRIX_BUFFER);
        float[] preset = PRESETS[presetIndex];
        GL20.glUniform3f(uLightDir, preset[1], preset[2], preset[3]);
        GL20.glUniform1f(uTilt, preset[0]);

        // 噪声纹理（单元 1）
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, getNoiseTexture());
        GL20.glUniform1i(uNoise, 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // 全屏 NDC quad（顶点着色器直接输出 NDC，无视矩阵）
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-1.0F, -1.0F, 1.0F);
        GL11.glVertex3f(1.0F, -1.0F, 1.0F);
        GL11.glVertex3f(1.0F, 1.0F, 1.0F);
        GL11.glVertex3f(-1.0F, 1.0F, 1.0F);
        GL11.glEnd();

        GL20.glUseProgram(0);

        // 清掉天空写入的深度，避免遮挡之后的地形渲染
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthMask(false);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    // ================= shader 管理 =================

    private static int getProgram() {
        if (programId != 0 && !GL20.glIsProgram(programId)) {
            programId = 0; // GL 上下文重建（F3+T）
        }
        if (programId == 0) {
            programId = buildProgram();
        }
        return programId;
    }

    private static int buildProgram() {
        int vs = compileShader(GL20.GL_VERTEX_SHADER, VSH_PATH);
        int fs = compileShader(GL20.GL_FRAGMENT_SHADER, FSH_PATH);
        if (vs == 0 || fs == 0) {
            return 0;
        }
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.out.println("[EOHB] blackhole shader link failed: " + GL20.glGetProgramInfoLog(prog, 4096));
            GL20.glDeleteProgram(prog);
            return 0;
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);

        uTime = GL20.glGetUniformLocation(prog, "uTime");
        uTanHalfFov = GL20.glGetUniformLocation(prog, "uTanHalfFov");
        uAspect = GL20.glGetUniformLocation(prog, "uAspect");
        uWorldToBhLocal = GL20.glGetUniformLocation(prog, "uWorldToBhLocal");
        uLightDir = GL20.glGetUniformLocation(prog, "uLightDir");
        uTilt = GL20.glGetUniformLocation(prog, "uTilt");
        uNoise = GL20.glGetUniformLocation(prog, "uNoise");
        return prog;
    }

    private static int compileShader(int type, String path) {
        String src = readResource(path);
        if (src == null) {
            return 0;
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.out.println("[EOHB] blackhole shader compile failed: " + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static String readResource(String path) {
        try {
            InputStream in = SkyProviderEmeraldThrone.class.getResourceAsStream(path);
            if (in == null) {
                System.out.println("[EOHB] missing shader resource: " + path);
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ================= 噪声纹理 =================

    private static int getNoiseTexture() {
        if (noiseTexId != 0 && !GL11.glIsTexture(noiseTexId)) {
            noiseTexId = 0;
        }
        if (noiseTexId == 0) {
            BufferedImage img = new BufferedImage(NOISE_SIZE, NOISE_SIZE, BufferedImage.TYPE_INT_ARGB);
            Random rand = new Random(NOISE_SEED);
            for (int y = 0; y < NOISE_SIZE; y++) {
                for (int x = 0; x < NOISE_SIZE; x++) {
                    int r = rand.nextInt(256);
                    int g = rand.nextInt(256);
                    int b = rand.nextInt(256);
                    int a = rand.nextInt(256);
                    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            TextureUtil.uploadTextureImageAllocate(id, img, false, false);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            noiseTexId = id;
        }
        return noiseTexId;
    }

    // ================= 矩阵数学（列主序 3x3）=================

    /** 从 MODELVIEW 提取世界旋转（相机空间 → 世界），列主序 9 元素。 */
    private static float[] extractWorldRotation(FloatBuffer mv) {
        float[] r = new float[9];
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                r[col * 3 + row] = mv.get(col * 4 + row);
            }
        }
        // 纯旋转：逆 = 转置
        float[] rt = new float[9];
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                rt[col * 3 + row] = r[row * 3 + col];
            }
        }
        return rt;
    }

    private static float[] rotX(float deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new float[] {
            1.0F, 0.0F, 0.0F,
            0.0F, (float) c, (float) s,
            0.0F, (float) -s, (float) c
        };
    }

    private static float[] rotY(float deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new float[] {
            (float) c, 0.0F, (float) -s,
            0.0F, 1.0F, 0.0F,
            (float) s, 0.0F, (float) c
        };
    }

    private static float[] mul3(float[] a, float[] b) {
        float[] r = new float[9];
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                float sum = 0.0F;
                for (int k = 0; k < 3; k++) {
                    sum += a[k * 3 + row] * b[col * 3 + k];
                }
                r[col * 3 + row] = sum;
            }
        }
        return r;
    }

    // ================= 兜底 =================

    /** shader 不可用时的暗色背景（不花屏）。 */
    private static void drawFallbackBackground() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_F(0.02F, 0.005F, 0.02F, 1.0F);
        tess.addVertex(-1.0F, -1.0F, 0.9F);
        tess.addVertex(1.0F, -1.0F, 0.9F);
        tess.setColorRGBA_F(0.06F, 0.015F, 0.08F, 1.0F);
        tess.addVertex(1.0F, 1.0F, 0.9F);
        tess.addVertex(-1.0F, 1.0F, 0.9F);
        tess.draw();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
