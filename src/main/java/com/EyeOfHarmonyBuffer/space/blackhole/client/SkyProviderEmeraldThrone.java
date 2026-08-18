package com.EyeOfHarmonyBuffer.space.blackhole.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.IRenderHandler;

import org.lwjgl.opengl.GL11;

/**
 * 翡翠王座天空盒（程序化，无贴图）：
 * <p>L1 虚空深渊：占据 ~57° 天区的黑洞视界——顶点渐变黑球（中心 #000 纯黑、
 * 边缘暗灰制造凹陷球面错觉）+ 湍流光子环（内铂金 → 外紫/冰蓝，时间驱动径向扰动）+
 * 环上被引力拉直的流丝（沸腾光泡沿环流动）。
 * <p>L2 虚假太阳：紧贴视界正上方边缘外侧、第一象限方向固定的椭圆聚光斑
 * （中心白核 + 蓝白同心层 + 衍射晕）+ 银蓝日冕螺旋（尾端向黑洞弯折 = 光芒向下流淌）。
 * <p>坐标潮汐锁定：黑洞/假太阳方向全部为常量，永不移动（昼夜/季节由后续 L5 处理）。
 */
public class SkyProviderEmeraldThrone extends IRenderHandler {

    // ===== 黑洞（虚空深渊）=====
    /** 视界角直径 ≈ 2·atan(55/100) ≈ 57.6°，符合 55°~65° 天区设定。 */
    private static final double BH_SIZE = 55.0D;
    private static final double BH_DIST = -100.0D;
    /** 黑洞中心方向：仰角 60°（绕 X 抬 30°）、方位 0°（正前方/北方）。 */
    private static final float BH_PITCH = 30.0F;
    private static final float BH_YAW = 0.0F;

    // ===== 光子环 =====
    private static final double RING_INNER = 57.5D;
    /** 环外缘（含羽化带）：扩大到 90，边缘虚化融入背景。 */
    private static final double RING_OUTER = 90.0D;
    private static final int RING_SEG = 128;

    // ===== 假太阳（第一象限方向固定，紧贴视界正上方边缘外侧）=====
    /** 相对黑洞中心再偏：方位 +45°（第一象限 = 东南），仰角 30°（中心贴近视界边缘 28.8°，光斑内侧压边 = 从深渊边缘溢出的光）。 */
    private static final float SUN_YAW_OFFSET = 45.0F;
    private static final float SUN_PITCH_OFFSET = 30.0F;
    private static final double SUN_DIST = -45.0D;
    private static final double SUN_SX = 10.0D; // 椭圆长半轴（视直径 ≈ 25°，巨大光斑）
    private static final double SUN_SY = 6.2D;  // 椭圆短半轴

    // ===== L3 引力透镜场 =====
    /** 弧线星流固定种子：星空形状每帧确定，不闪烁。 */
    private static final long STAR_SEED = 0x5EED_5EEDL;
    /** 吸入流固定种子。 */
    private static final long STREAM_SEED = 0xACC_2017L;
    /** 透镜场球面半径（与环带同层）。 */
    private static final double LENS_R = 95.0D;

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        if (world == null || mc.thePlayer == null) {
            return;
        }

        Tessellator tess = Tessellator.instance;
        // 平滑动画时间（tick + 帧间插值）
        float t = world.getWorldTime() + partialTicks;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 背景：暗紫黑渐变（不透明，完全接管天空底色）
        drawBackgroundGradient();

        // L4 大气辉光已取消（视觉效果不佳）；禁云由 provider 的 getCloudRenderer 处理

        // 黑洞系统（含假太阳，同一旋转坐标系 → 潮汐锁定）
        drawBlackHoleSystem(tess, t);

        GL11.glColor4f(1F, 1F, 1F, 1F);

        // 清掉天空写入的深度，避免遮挡之后的地形渲染
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthMask(false);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    // ================= 黑洞系统 =================

    private static void drawBlackHoleSystem(Tessellator tess, float t) {
        GL11.glPushMatrix();
        GL11.glRotatef(BH_YAW, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(BH_PITCH, 1.0F, 0.0F, 0.0F);

        // L3：弧线星流先画（视界内的部分会被黑球遮挡——星星不可能出现在黑洞前面）
        drawLensStars(tess, t);
        // 宇宙瀑布（径向吸入流）已按需求关闭

        // L1：视界黑球（凹陷球面：中心纯黑、边缘暗灰；后画 → 遮挡视界内的星流）
        drawBlackHoleSphere(tess);
        // L1：湍流光子环（内铂金 → 外紫/冰蓝，径向时间扰动）
        drawPhotonRing(tess, t);
        // L1：环上沸腾流丝（沿环切线拉直的短线，绕环流动）
        drawRingStreaks(tess, t);

        // L2 假太阳（光斑 + 日冕）与 L3 爱因斯坦环：按需求暂时关闭，后续有头绪再恢复
        // GL11.glPushMatrix();
        // GL11.glRotatef(SUN_YAW_OFFSET, 0.0F, 1.0F, 0.0F);
        // GL11.glRotatef(SUN_PITCH_OFFSET, 1.0F, 0.0F, 0.0F);
        // drawFalseSun(tess, t);
        // drawEinsteinRing(tess, t);
        // GL11.glPopMatrix();

        GL11.glPopMatrix();
    }

    /** 视界球体：经纬网格，顶点色按纬度渐变（正对相机的最黑 → 两极边缘暗灰）。 */
    private static void drawBlackHoleSphere(Tessellator tess) {
        int lonSeg = 48;
        int latSeg = 20;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tess.startDrawingQuads();
        for (int j = 0; j < latSeg; j++) {
            double phi0 = Math.PI * (j / (double) latSeg) - Math.PI / 2.0D;
            double phi1 = Math.PI * ((j + 1) / (double) latSeg) - Math.PI / 2.0D;
            for (int i = 0; i < lonSeg; i++) {
                double theta0 = Math.PI * 2.0D * i / lonSeg;
                double theta1 = Math.PI * 2.0D * (i + 1) / lonSeg;
                addBlackHoleVertex(tess, phi0, theta0);
                addBlackHoleVertex(tess, phi0, theta1);
                addBlackHoleVertex(tess, phi1, theta1);
                addBlackHoleVertex(tess, phi1, theta0);
            }
        }
        tess.draw();
    }

    private static void addBlackHoleVertex(Tessellator tess, double phi, double theta) {
        double x = BH_SIZE * Math.cos(phi) * Math.cos(theta);
        double y = BH_SIZE * Math.cos(phi) * Math.sin(theta);
        double z = BH_DIST + BH_SIZE * Math.sin(phi);
        // 边缘因子：|sin(phi)| = 1 在两极（视界剪影边缘），0 在正对相机处
        float edge = (float) Math.abs(Math.sin(phi));
        float b = edge * edge * 0.06F; // 边缘最亮 ~0.06（#0F0F0F 级），中心绝对 #000
        tess.setColorRGBA_F(b, b, b * 1.1F, 1.0F);
        tess.addVertex(x, y, z);
    }

    /**
     * 湍流光子环：三段三角带（半径扰动全部统一为 r + turb，任意分界处严格连续，无两层环）——
     * 实芯带（57.5→68，铂金→紫蓝，alpha 0.95→0.8）+ 羽化带两段（68→76→90，alpha 0.8→0.4→0），
     * 外缘双段缓降融入背景，虚化明显；扰动以角度为变量、每圈整数个波，首尾无接缝。
     */
    private static void drawPhotonRing(Tessellator tess, float t) {
        drawRingBand(tess, t, RING_INNER, 68.0D, 0.95F, 0.80F);
        drawRingBand(tess, t, 68.0D, 76.0D, 0.80F, 0.40F);
        drawRingBand(tess, t, 76.0D, RING_OUTER, 0.40F, 0.0F);
    }

    /** 单段环带：半径 [rIn, rOut]（内外顶点统一叠加同一 turb，带间无缝），alpha 线性插值，颜色按全局径向位置渐变。 */
    private static void drawRingBand(Tessellator tess, float t, double rIn, double rOut, float aIn, float aOut) {
        double uIn = (rIn - RING_INNER) / (RING_OUTER - RING_INNER);
        double uOut = (rOut - RING_INNER) / (RING_OUTER - RING_INNER);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= RING_SEG; i++) {
            double th = Math.PI * 2.0D * i / RING_SEG;
            // 扰动必须以角度为变量且每圈整数个波（3 波 + 9 波），首尾相位严格闭合，无接缝
            double turb = Math.sin(t * 0.15D + th * 3.0D) * 0.9D
                + Math.sin(t * 0.31D + th * 9.0D) * 0.35D;

            double ri = rIn + turb;
            float[] ci = ringColor(uIn, th, t);
            tess.setColorRGBA_F(ci[0], ci[1], ci[2], aIn);
            tess.addVertex(Math.cos(th) * ri, Math.sin(th) * ri, BH_DIST);

            double ro = rOut + turb;
            float[] co = ringColor(uOut, th, t);
            tess.setColorRGBA_F(co[0], co[1], co[2], aOut);
            tess.addVertex(Math.cos(th) * ro, Math.sin(th) * ro, BH_DIST);
        }
        tess.draw();
    }

    /**
     * 环色曲线（按全局径向位置 u∈[0,1]）：
     * u=0 铂金 #E5E4E2 → u=0.5 冷紫/冰蓝沿环交替 → u=1 紫蓝暗化（配合 alpha→0 羽化）。
     */
    private static float[] ringColor(double u, double th, float t) {
        double mix = (Math.sin(th + t * 0.08D) + 1.0D) * 0.5D;
        float rCol;
        float gCol;
        float bCol;
        if (u < 0.5D) {
            float k = (float) (u * 2.0D);
            rCol = 0.90F + (0.54F - 0.90F) * k;
            gCol = 0.89F + (0.17F - 0.89F) * k;
            bCol = 0.885F + (0.90F - 0.885F) * k;
        } else {
            // 羽化区颜色只轻微暗化（0.25），主要靠 alpha 渐隐，光晕感更明显
            float k = (float) ((u - 0.5D) * 2.0D);
            float dim = 1.0F - 0.25F * k;
            rCol = (0.54F + 0.14F * (float) mix) * dim;
            gCol = (0.17F + 0.68F * (float) (1.0D - mix)) * dim;
            bCol = 0.90F * dim;
        }
        return new float[] { rCol, gCol, bCol };
    }

    /**
     * 环上沸腾流丝：双轨道——内轨细密短丝（70 条）、外轨稀疏长丝（35 条、更粗），
     * 全部沿环切线方向（被引力拉直），绕环快速流动；另叠 24 颗超亮光泡点缀。
     */
    private static void drawRingStreaks(Tessellator tess, float t) {
        GL11.glLineWidth(2.0F);
        drawStreakBand(tess, t, 70, 58.6D, 0.9D, 3.2D, 2.4D);
        GL11.glLineWidth(2.8F);
        drawStreakBand(tess, t, 35, 63.5D, 1.4D, 4.8D, 3.6D);
        drawRingBubbles(tess, t);
        GL11.glLineWidth(1.0F);
    }

    /** 单轨流丝带：亮白金短线沿环切线拉直，绕环流动 + 径向扰动 + 亮度脉动。 */
    private static void drawStreakBand(Tessellator tess, float t, int count, double baseR,
        double radialAmp, double lenBase, double lenAmp) {
        tess.startDrawing(GL11.GL_LINES);
        for (int i = 0; i < count; i++) {
            double base = Math.PI * 2.0D * i / count + t * 0.07D;
            double r = baseR + Math.sin(t * 0.25D + i * 1.3D) * radialAmp;
            double len = lenBase + Math.sin(t * 0.45D + i * 0.7D) * lenAmp * 0.5D;
            double cx = Math.cos(base);
            double sy = Math.sin(base);
            double x0 = cx * r;
            double y0 = sy * r;
            double x1 = x0 - sy * len;
            double y1 = y0 + cx * len;
            // 亮白金（铂金偏白），亮度脉动保证流丝始终显眼
            double pulse = Math.abs(Math.sin(t * 0.3D + i * 0.5D));
            float bright = (float) (0.72D + 0.28D * pulse);
            tess.setColorRGBA_F(bright, bright * 0.98F, bright * 0.93F, 1.0F);
            tess.addVertex(x0, y0, BH_DIST);
            tess.addVertex(x1, y1, BH_DIST);
        }
        tess.draw();
    }

    /** 沸腾光泡：环上快速流动的超亮白点（个别瞬间过曝）。 */
    private static void drawRingBubbles(Tessellator tess, float t) {
        int count = 24;
        GL11.glPointSize(3.2F);
        tess.startDrawing(GL11.GL_POINTS);
        for (int i = 0; i < count; i++) {
            double base = Math.PI * 2.0D * i / count + t * 0.11D;
            double r = 58.0D + Math.sin(t * 0.35D + i * 2.1D) * 2.2D;
            double x = Math.cos(base) * r;
            double y = Math.sin(base) * r;
            float b = 0.88F + 0.12F * (float) Math.sin(t * 0.5D + i);
            tess.setColorRGBA_F(b, b, b * 0.97F, 1.0F);
            tess.addVertex(x, y, BH_DIST);
        }
        tess.draw();
        GL11.glPointSize(1.0F);
    }

    // ================= L3 引力透镜场 =================

    /**
     * 弧线星流：全天空 4500 条以黑洞为圆心的同心弧（星点被引力拉成火柴棍），
     * 近黑洞的弧更长更弯更亮，远处接近点状；全部顺时针流动（phi 增大），
     * 近快远慢（视超光速）——"天空在转，但黑洞不动"。
     * 黑洞局部坐标：极轴 -z（黑洞中心），lensDir(rho, phi) 生成方向。
     * 注意 Tessellator 单次 buffer 上限 ~4096 顶点，按 1400 顶点/批分段提交。
     */
    private static void drawLensStars(Tessellator tess, float t) {
        int count = 4500;
        int segs = 8;
        Random rand = new Random(STAR_SEED);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.0F);
        boolean drawing = false;
        int batch = 0;
        for (int i = 0; i < count; i++) {
            double rho = Math.toRadians(15.0D + rand.nextDouble() * 135.0D); // 15° ~ 150°
            double phi0 = rand.nextDouble() * Math.PI * 2.0D;
            // 顺时针流动速度：近处快、远处慢（当前为原速度的 1/24，接近静止）
            double omega = 0.0005D * (Math.toRadians(90.0D) / (rho + Math.toRadians(18.0D)));
            double phi = phi0 + t * omega;
            // 弧长：近处被强透镜拉长，远处接近点状
            double rhoCap = Math.min(rho, Math.toRadians(90.0D));
            double arcLen = Math.toRadians(2.0D) + Math.toRadians(18.0D) * Math.cos(rhoCap) * Math.cos(rhoCap);
            // 亮度：近处增亮（引力压缩），白偏蓝
            double brightF = Math.max(0.0D, 1.0D - rho / Math.toRadians(160.0D));
            float bright = (float) (0.22D + 0.78D * brightF * brightF);
            // 弧：8 段短线段（首尾相接 → 连续弧线）
            for (int s = 0; s < segs; s++) {
                double a0 = phi + arcLen * (s / (double) segs - 0.5D);
                double a1 = phi + arcLen * ((s + 1) / (double) segs - 0.5D);
                double[] p0 = lensDir(rho, a0);
                double[] p1 = lensDir(rho, a1);
                if (!drawing) {
                    tess.startDrawing(GL11.GL_LINES);
                    drawing = true;
                }
                tess.setColorRGBA_F(bright, bright, bright * 0.96F, 1.0F);
                tess.addVertex(p0[0] * LENS_R, p0[1] * LENS_R, p0[2] * LENS_R);
                tess.addVertex(p1[0] * LENS_R, p1[1] * LENS_R, p1[2] * LENS_R);
                batch += 2;
                if (batch >= 1400) {
                    tess.draw();
                    drawing = false;
                    batch = 0;
                }
            }
        }
        if (drawing) {
            tess.draw();
        }
    }

    /** 黑洞局部球面方向：极轴 -z（黑洞中心），rho = 离黑洞角距离，phi = 绕黑洞方位（顺时针 = 增大）。 */
    private static double[] lensDir(double rho, double phi) {
        double sr = Math.sin(rho);
        return new double[] { sr * Math.sin(phi), sr * Math.cos(phi), -Math.cos(rho) };
    }

    /**
     * 宇宙瀑布：光子环外 35°~60° 的 1200 条径向细丝，缓慢向黑洞收缩（到 ~8° 重置），
     * 亮白金、亮度脉动——无数发光的极细丝线被缓慢吸入深渊。
     */
    private static void drawAccretionStreams(Tessellator tess, float t) {
        int count = 1200;
        Random rand = new Random(STREAM_SEED);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.2F);
        boolean drawing = false;
        int batch = 0;
        for (int i = 0; i < count; i++) {
            double phi = rand.nextDouble() * Math.PI * 2.0D;
            double rhoOut = Math.toRadians(35.0D + rand.nextDouble() * 25.0D); // 35°~60°
            double speed = Math.toRadians(0.045D) * (0.5D + rand.nextDouble()); // 收缩速度
            double phase = rand.nextDouble() * 200.0D;
            double rhoMin = Math.toRadians(8.0D);
            // 收缩循环：rho 从 rhoOut 一路收到 rhoMin，然后瞬间重置
            double span = rhoOut - rhoMin;
            double rho = rhoOut - ((t * speed + phase) % span);
            double rhoIn = rho - Math.toRadians(4.5D); // 丝线长 ~4.5°
            if (rhoIn < rhoMin) {
                rhoIn = rhoMin;
            }
            double[] pOut = lensDir(rho, phi);
            double[] pIn = lensDir(rhoIn, phi);
            float bright = 0.55F + 0.45F * (float) Math.abs(Math.sin(t * 0.05D + i * 1.7D));
            if (!drawing) {
                tess.startDrawing(GL11.GL_LINES);
                drawing = true;
            }
            tess.setColorRGBA_F(0.95F * bright, 0.93F * bright, 0.90F * bright, 0.9F);
            tess.addVertex(pOut[0] * LENS_R, pOut[1] * LENS_R, pOut[2] * LENS_R);
            tess.addVertex(pIn[0] * LENS_R, pIn[1] * LENS_R, pIn[2] * LENS_R);
            batch += 2;
            if (batch >= 1400) {
                tess.draw();
                drawing = false;
                batch = 0;
            }
        }
        if (drawing) {
            tess.draw();
        }
        GL11.glLineWidth(1.0F);
    }

    /**
     * 爱因斯坦环：假太阳周围被引力弯折 180° 的星系盘倒影——双层甜甜圈环
     * （外晕 + 亮芯环），冷白星光色，光泽沿环流动，微呼吸。
     */
    private static void drawEinsteinRing(Tessellator tess, float t) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // 加法混合：发光体
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawRingQuad(tess, 0.0D, 0.0D, SUN_DIST, 12.0D, 21.0D, 0.20F, t, false);
        drawRingQuad(tess, 0.0D, 0.0D, SUN_DIST, 14.5D, 18.5D, 0.40F, t, true);
        // 恢复标准混合
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** 平面上同心圆环三角带：冷白星光色，可选光泽沿环流动。 */
    private static void drawRingQuad(Tessellator tess, double cx, double cy, double cz, double ri, double ro,
        float alphaBase, float t, boolean swirl) {
        int seg = 64;
        tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= seg; i++) {
            double th = Math.PI * 2.0D * i / seg;
            double glint = swirl ? (0.6D + 0.4D * Math.sin(th - t * 0.15D)) : 1.0D;
            float a = alphaBase * (float) glint;
            tess.setColorRGBA_F(0.75F, 0.82F, 0.95F, a);
            tess.addVertex(cx + Math.cos(th) * ri, cy + Math.sin(th) * ri, cz);
            tess.setColorRGBA_F(0.75F, 0.82F, 0.95F, a * 0.6F);
            tess.addVertex(cx + Math.cos(th) * ro, cy + Math.sin(th) * ro, cz);
        }
        tess.draw();
    }

    // ================= 假太阳 =================

    /**
     * 椭圆聚光斑（真正的椭圆渐变，无矩形硬边）：
     * 12 条同心椭圆环带——中心 25% 纯白核（alpha 1，过曝刺眼），
     * 外圈白→蓝白二次渐隐到 0（#F0F8FF 系）；最外叠 2 条宽泛衍射晕；
     * 全部加法混合。日冕为 12 条更细更弥散的银蓝螺旋丝。
     */
    private static void drawFalseSun(Tessellator tess, float t) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // 加法混合：发光体（中心过曝）
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        int bands = 12;
        int seg = 32;
        // 1. 椭圆渐变光斑本体
        for (int b = 0; b < bands; b++) {
            double u0 = b / (double) bands;
            double u1 = (b + 1) / (double) bands;
            tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= seg; i++) {
                double th = Math.PI * 2.0D * i / seg;
                double c = Math.cos(th);
                double s = Math.sin(th);
                float[] col0 = sunColor(u0);
                tess.setColorRGBA_F(col0[0], col0[1], col0[2], sunAlpha(u0));
                tess.addVertex(SUN_SX * u0 * c, SUN_SY * u0 * s, SUN_DIST);
                float[] col1 = sunColor(u1);
                tess.setColorRGBA_F(col1[0], col1[1], col1[2], sunAlpha(u1));
                tess.addVertex(SUN_SX * u1 * c, SUN_SY * u1 * s, SUN_DIST);
            }
            tess.draw();
        }

        // 2. 宽光晕层（解决黑背景下边缘过渡不可见的问题）：
        //    从光斑表面延伸到 2.6 倍半轴，3 层 alpha 0.10 → 0.04 → 0.015，
        //    加法混合下给边缘提供充足的中间亮度态
        double[] haloU = { 1.0D, 1.55D, 2.1D, 2.6D };
        float[] haloA = { 0.10F, 0.055F, 0.03F, 0.015F };
        for (int i = 0; i < 3; i++) {
            double u0 = haloU[i];
            double u1 = haloU[i + 1];
            tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * 2.0D * j / seg;
                double c = Math.cos(th);
                double s = Math.sin(th);
                tess.setColorRGBA_F(0.82F, 0.88F, 1.0F, haloA[i]);
                tess.addVertex(SUN_SX * u0 * c, SUN_SY * u0 * s, SUN_DIST);
                tess.setColorRGBA_F(0.82F, 0.88F, 1.0F, haloA[i + 1]);
                tess.addVertex(SUN_SX * u1 * c, SUN_SY * u1 * s, SUN_DIST);
            }
            tess.draw();
        }

        // 3. 日冕：12 条更细更弥散的银蓝螺旋丝，尾端向黑洞方向（-z）弯折
        drawCorona(tess, t);

        // 恢复标准混合
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** 光斑色：中心纯白 → 边缘微蓝白（#F0F8FF 系）。 */
    private static float[] sunColor(double u) {
        if (u < 0.25D) {
            return new float[] { 1.0F, 1.0F, 1.0F };
        }
        double t = (u - 0.25D) / 0.75D;
        return new float[] { (float) (1.0D - 0.06D * t), (float) (1.0D - 0.04D * t), 1.0F };
    }

    /**
     * 光斑 alpha：中心 25% 纯白核全实，外圈 cos 缓尾渐隐到 0——
     * cos 曲线在末端斜率趋缓，配合宽光晕层，边缘过渡有充足中间态。
     */
    private static float sunAlpha(double u) {
        if (u < 0.25D) {
            return 1.0F;
        }
        double t = (u - 0.25D) / 0.75D;
        return (float) Math.cos(t * Math.PI * 0.5D);
    }

    private static void drawCorona(Tessellator tess, float t) {
        int strands = 12;
        int pts = 26;
        GL11.glLineWidth(1.0F);
        for (int s = 0; s < strands; s++) {
            double startAngle = Math.PI * 2.0D * s / strands + t * 0.015D;
            tess.startDrawing(GL11.GL_LINE_STRIP);
            for (int i = 0; i < pts; i++) {
                double ang = startAngle + i * 0.13D;
                // 从光斑表面（半轴）向外螺旋甩出
                double rr = 1.0D + i * 0.11D;
                double x = Math.cos(ang) * rr * SUN_SX;
                double y = Math.sin(ang) * rr * SUN_SY;
                // 尾端向黑洞弯折（-z 更远）= 光芒向下流淌
                double bend = Math.pow(i / (double) pts, 2.0D) * 6.0D;
                double z = SUN_DIST - bend;
                float a = (1.0F - (float) i / pts) * 0.55F;
                tess.setColorRGBA_F(0.80F, 0.88F, 1.0F, a);
                tess.addVertex(x, y, z);
            }
            tess.draw();
        }
        GL11.glLineWidth(1.0F);
    }

    // ================= 背景 =================

    /** NDC 全屏渐变：上深（近黑）下略浅（暗紫）。 */
    private static void drawBackgroundGradient() {
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
    }
}
