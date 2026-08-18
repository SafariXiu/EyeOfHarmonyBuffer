package com.EyeOfHarmonyBuffer.space.blackhole.client;

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
    /** 相对黑洞中心再偏：方位 +45°（第一象限 = 东南），仰角 +32°（超过视界角半径 28.8° = 边缘外侧）。 */
    private static final float SUN_YAW_OFFSET = 45.0F;
    private static final float SUN_PITCH_OFFSET = 32.0F;
    private static final double SUN_DIST = -45.0D;
    private static final double SUN_SX = 2.8D;  // 椭圆长半轴（视直径 ≈ 7°，小而刺眼）
    private static final double SUN_SY = 1.7D;  // 椭圆短半轴

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

        // L1：视界黑球（凹陷球面：中心纯黑、边缘暗灰）
        drawBlackHoleSphere(tess);
        // L1：湍流光子环（内铂金 → 外紫/冰蓝，径向时间扰动）
        drawPhotonRing(tess, t);
        // L1：环上沸腾流丝（沿环切线拉直的短线，绕环流动）
        drawRingStreaks(tess, t);

        // L2：假太阳（第一象限，紧贴视界正上方边缘外侧）
        GL11.glPushMatrix();
        GL11.glRotatef(SUN_YAW_OFFSET, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(SUN_PITCH_OFFSET, 1.0F, 0.0F, 0.0F);
        drawFalseSun(tess, t);
        GL11.glPopMatrix();

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

    // ================= 假太阳 =================

    /** 椭圆聚光斑 + 边缘衍射晕 + 银蓝日冕螺旋。 */
    private static void drawFalseSun(Tessellator tess, float t) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // 1. 边缘衍射晕（3 层大椭圆，低 alpha 蓝白）
        for (int i = 0; i < 3; i++) {
            double f = 1.0D + i * 0.9D;
            float alpha = 0.10F - i * 0.025F;
            GL11.glColor4f(0.85F, 0.90F, 1.0F, alpha);
            tess.startDrawingQuads();
            tess.addVertex(-SUN_SX * f, -SUN_SY * f, SUN_DIST);
            tess.addVertex(SUN_SX * f, -SUN_SY * f, SUN_DIST);
            tess.addVertex(SUN_SX * f, SUN_SY * f, SUN_DIST);
            tess.addVertex(-SUN_SX * f, SUN_SY * f, SUN_DIST);
            tess.draw();
        }

        // 2. 光斑本体：外 → 内同心椭圆渐变（边缘蓝白 #F0F8FF → 中心纯白）
        double[] scales = { 1.0D, 0.72D, 0.45D, 0.24D };
        float[] alphas = { 0.70F, 0.85F, 0.95F, 1.0F };
        float[] blues = { 0.90F, 0.95F, 0.99F, 1.0F };
        for (int i = 0; i < 4; i++) {
            double s = scales[i];
            GL11.glColor4f(1.0F, 1.0F, blues[i], alphas[i]);
            tess.startDrawingQuads();
            tess.addVertex(-SUN_SX * s, -SUN_SY * s, SUN_DIST);
            tess.addVertex(SUN_SX * s, -SUN_SY * s, SUN_DIST);
            tess.addVertex(SUN_SX * s, SUN_SY * s, SUN_DIST);
            tess.addVertex(-SUN_SX * s, SUN_SY * s, SUN_DIST);
            tess.draw();
        }

        // 3. 日冕：6 条银蓝螺旋细丝，尾端向黑洞方向（-z）弯折 = 光芒向下流淌
        drawCorona(tess, t);
    }

    private static void drawCorona(Tessellator tess, float t) {
        int strands = 6;
        for (int s = 0; s < strands; s++) {
            double startAngle = Math.PI * 2.0D * s / strands + t * 0.02D;
            int pts = 26;
            tess.startDrawing(GL11.GL_LINE_STRIP);
            for (int i = 0; i < pts; i++) {
                double ang = startAngle + i * 0.16D;
                double rr = 1.6D + i * 0.55D;
                double x = Math.cos(ang) * rr * SUN_SX * 0.5D;
                double y = Math.sin(ang) * rr * SUN_SY * 0.5D;
                // 尾端向黑洞弯折（-z 更远）
                double bend = Math.pow(i / (double) pts, 2.0D) * 4.0D;
                double z = SUN_DIST - bend;
                float a = (1.0F - (float) i / pts) * 0.8F;
                tess.setColorRGBA_F(0.78F, 0.88F, 1.0F, a);
                tess.addVertex(x, y, z);
            }
            tess.draw();
        }
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
