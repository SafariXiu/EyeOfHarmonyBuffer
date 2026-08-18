package com.EyeOfHarmonyBuffer.client.orbitalrailgun;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.opengl.GL11;

/**
 * 轨道炮世界空间视觉（阶段1：几何方案，Tessellator + 加法混合）。
 * 时间轴：
 *   0-4s   天降蓄力光束（轴心 + 螺旋）
 *   4-36s  扩张：地面冲击波环 + 球体三环 + 六根旋转光束 + 核心光球
 *   36s+   湮灭闪光 + 爆炸粒子
 */
public final class RailgunWorldRenderer {

    public static final float BEAM_TOP_OFFSET = 260.0F;

    private RailgunWorldRenderer() {}

    // ================= 瞄准标记（充能时） =================

    public static void renderAimMarker(float partialTicks, int blockX, int blockY, int blockZ) {
        GL11.glPushMatrix();
        translateToWorld();

        double x = blockX;
        double y = blockY;
        double z = blockZ;
        float radius = (float) MainConfig.OrbitalRailgunRadius;

        // 目标方块线框（青色）
        drawBox(x, y, z, 1.0, 0.62F, 0.93F, 0.93F, 0.9F, 2.0F);
        // AOE 地面圆（青色）
        drawRingXZ(x, y + 1.06, z, radius, 0.62F, 0.93F, 0.93F, 0.6F, 2.0F);
        // 天降瞄准线（微弱）
        drawLine(x + 0.5, y + 1.0 + BEAM_TOP_OFFSET, z + 0.5, x + 0.5, y + 1.0, z + 0.5,
            0.62F, 0.93F, 0.93F, 0.10F, 1.0F);

        GL11.glPopMatrix();
    }

    // ================= 打击特效 =================

    public static void renderStrike(float partialTicks, RailgunClientState state) {
        double x = state.getStrikeX() + 0.5;
        double y = state.getStrikeY() + 0.5;
        double z = state.getStrikeZ() + 0.5;
        float groundY = state.getStrikeY() + 1.0F;
        float t = state.getStrikeSeconds(partialTicks);
        float radius = state.getStrikeRadius();

        GL11.glPushMatrix();
        translateToWorld();

        if (t < RailgunClientState.STRIKE_START_SECONDS) {
            renderChargeBeam(x, y, z, t);
        } else if (t < RailgunClientState.STRIKE_END_SECONDS) {
            renderExpansion(x, groundY, z, t - RailgunClientState.STRIKE_START_SECONDS, radius);
        } else {
            renderFlash(x, groundY, z, t - RailgunClientState.STRIKE_END_SECONDS, radius);
        }

        GL11.glPopMatrix();

        // 湮灭粒子（只触发一次）
        if (t >= RailgunClientState.STRIKE_END_SECONDS && !state.isExplosionParticleFired()) {
            state.markExplosionParticleFired();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                mc.theWorld.spawnParticle("hugeexplosion", x, groundY - 0.5, z, 0.0, 0.0, 0.0);
                mc.theWorld.spawnParticle("largeexplode", x, groundY - 0.5, z, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void renderChargeBeam(double x, double y, double z, float t) {
        float fade = Math.min(1.0F, t * 2.0F); // 0.5s 淡入
        double topY = y + 0.5 + BEAM_TOP_OFFSET;

        // 轴心光束
        drawLine(x, topY, z, x, y + 0.5, z, 0.62F, 0.93F, 0.93F, 0.55F * fade, 3.0F);
        // 螺旋缠绕（约 1.5 圈/秒）
        drawHelix(x, y + 0.5, z, 2.0, BEAM_TOP_OFFSET, t, 0.62F, 0.93F, 0.93F, 0.5F * fade);
    }

    private static void renderExpansion(double x, double y, double z, float local, float radius) {
        float p = local / RailgunClientState.STRIKE_EXPANSION_SECONDS; // 0..1
        p = Math.min(1.0F, p);
        float R = radius * easeOutCubic(p);
        float alpha = Math.max(0.0F, 1.0F - p);

        // 地面冲击波环
        drawRingXZ(x, y + 0.06, z, R, 0.62F, 0.93F, 0.93F, 0.5F * alpha + 0.1F, 3.5F);

        // 球体三环（水平 + 两个垂直）
        double sphereY = y - 0.5F;
        drawRingXZ(x, sphereY, z, R, 0.62F, 0.93F, 0.93F, 0.45F * alpha, 2.5F);
        drawRingXY(x, sphereY, z, R, 0.62F, 0.93F, 0.93F, 0.4F * alpha, 2.0F);
        drawRingZY(x, sphereY, z, R, 0.62F, 0.93F, 0.93F, 0.4F * alpha, 2.0F);

        // 六根旋转光束（从球面附近垂直到半空）
        double beamR = R * 0.55;
        double beamTop = Math.max(2.0, R * 0.45);
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(i * 60 + local * 30);
            double bx = x + beamR * Math.cos(ang);
            double bz = z + beamR * Math.sin(ang);
            drawLine(bx, sphereY + 0.2, bz, bx, sphereY + beamTop, bz,
                0.62F, 0.93F, 0.93F, 0.35F * alpha, 1.5F);
        }

        // 核心光球（十字 billboard）
        float size = Math.max(1.0F, 4.0F * (1.0F - p));
        float coreAlpha = Math.max(0.0F, 0.8F - 1.5F * p);
        drawBillboard(x, sphereY, z, size, coreAlpha);
    }

    private static void renderFlash(double x, double y, double z, float fade, float radius) {
        fade = Math.min(1.0F, fade / 1.5F);
        float R = radius + 10.0F * fade;
        float alpha = Math.max(0.0F, 0.7F * (1.0F - fade));
        // 扩张白光环
        drawRingXZ(x, y + 0.06, z, R, 1.0F, 1.0F, 1.0F, alpha, 5.0F);
        drawRingXZ(x, y - 0.5, z, radius * 0.8F, 1.0F, 1.0F, 1.0F, alpha * 0.7F, 3.0F);
    }

    // ================= 绘制原语 =================

    private static void translateToWorld() {
        double px = RenderManager.instance.renderPosX;
        double py = RenderManager.instance.renderPosY;
        double pz = RenderManager.instance.renderPosZ;
        GL11.glTranslated(-px, -py, -pz);
    }

    /** 单段线段。 */
    private static void drawLine(double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a, float width) {
        begin(GL11.GL_LINE_STRIP, r, g, b, a, width);
        Tessellator.instance.addVertex(x1, y1, z1);
        Tessellator.instance.addVertex(x2, y2, z2);
        Tessellator.instance.draw();
    }

    /** XZ 平面圆环。 */
    private static void drawRingXZ(double cx, double cy, double cz, double radius,
                                   float r, float g, float b, float a, float width) {
        begin(GL11.GL_LINE_LOOP, r, g, b, a, width);
        int segments = Math.max(24, (int) (radius * 3));
        for (int i = 0; i < segments; i++) {
            double ang = Math.PI * 2.0 * i / segments;
            Tessellator.instance.addVertex(cx + radius * Math.cos(ang), cy, cz + radius * Math.sin(ang));
        }
        Tessellator.instance.draw();
    }

    /** XY 平面圆环（垂直，绕 Z 轴）。 */
    private static void drawRingXY(double cx, double cy, double cz, double radius,
                                   float r, float g, float b, float a, float width) {
        begin(GL11.GL_LINE_LOOP, r, g, b, a, width);
        int segments = Math.max(24, (int) (radius * 3));
        for (int i = 0; i < segments; i++) {
            double ang = Math.PI * 2.0 * i / segments;
            Tessellator.instance.addVertex(cx + radius * Math.cos(ang), cy + radius * Math.sin(ang), cz);
        }
        Tessellator.instance.draw();
    }

    /** ZY 平面圆环（垂直，绕 X 轴）。 */
    private static void drawRingZY(double cx, double cy, double cz, double radius,
                                   float r, float g, float b, float a, float width) {
        begin(GL11.GL_LINE_LOOP, r, g, b, a, width);
        int segments = Math.max(24, (int) (radius * 3));
        for (int i = 0; i < segments; i++) {
            double ang = Math.PI * 2.0 * i / segments;
            Tessellator.instance.addVertex(cx, cy + radius * Math.sin(ang), cz + radius * Math.cos(ang));
        }
        Tessellator.instance.draw();
    }

    /** 螺旋线（绕 Y 轴上升）。 */
    private static void drawHelix(double cx, double cy, double cz, double radius, double height,
                                  float time, float r, float g, float b, float a) {
        begin(GL11.GL_LINE_STRIP, r, g, b, a, 1.5F);
        double turns = time * 1.5; // 圈数随时间增加
        double step = 4.0;
        for (double y = 0; y <= height; y += step) {
            double ang = turns * Math.PI * 2.0 + (y / step) * 0.45;
            Tessellator.instance.addVertex(cx + radius * Math.cos(ang), cy + y, cz + radius * Math.sin(ang));
        }
        Tessellator.instance.draw();
    }

    /** 单位方块线框（12 条边）。 */
    private static void drawBox(double x, double y, double z, double size,
                                float r, float g, float b, float a, float width) {
        begin(GL11.GL_LINES, r, g, b, a, width);
        double x2 = x + size, y2 = y + size, z2 = z + size;
        // 底面
        vertex(x, y, z); vertex(x2, y, z);
        vertex(x2, y, z); vertex(x2, y, z2);
        vertex(x2, y, z2); vertex(x, y, z2);
        vertex(x, y, z2); vertex(x, y, z);
        // 顶面
        vertex(x, y2, z); vertex(x2, y2, z);
        vertex(x2, y2, z); vertex(x2, y2, z2);
        vertex(x2, y2, z2); vertex(x, y2, z2);
        vertex(x, y2, z2); vertex(x, y2, z);
        // 竖棱
        vertex(x, y, z); vertex(x, y2, z);
        vertex(x2, y, z); vertex(x2, y2, z);
        vertex(x2, y, z2); vertex(x2, y2, z2);
        vertex(x, y, z2); vertex(x, y2, z2);
        Tessellator.instance.draw();
    }

    /** 十字 billboard 光球。 */
    private static void drawBillboard(double cx, double cy, double cz, float size, float alpha) {
        beginQuads(1.0F, 1.0F, 1.0F, alpha);
        Tessellator.instance.addVertex(cx - size, cy - size, cz);
        Tessellator.instance.addVertex(cx + size, cy - size, cz);
        Tessellator.instance.addVertex(cx + size, cy + size, cz);
        Tessellator.instance.addVertex(cx - size, cy + size, cz);
        Tessellator.instance.addVertex(cx, cy - size, cz - size);
        Tessellator.instance.addVertex(cx, cy - size, cz + size);
        Tessellator.instance.addVertex(cx, cy + size, cz + size);
        Tessellator.instance.addVertex(cx, cy + size, cz - size);
        Tessellator.instance.draw();
    }

    private static void vertex(double x, double y, double z) {
        Tessellator.instance.addVertex(x, y, z);
    }

    private static void begin(int mode, float r, float g, float b, float a, float width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // 加法混合
        GL11.glDepthMask(false);
        GL11.glLineWidth(width);
        Tessellator.instance.startDrawing(mode);
        Tessellator.instance.setColorRGBA_F(r, g, b, a);
    }

    private static void beginQuads(float r, float g, float b, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        Tessellator.instance.startDrawingQuads();
        Tessellator.instance.setColorRGBA_F(r, g, b, a);
    }

    private static float easeOutCubic(float t) {
        float x = 1.0F - t;
        return 1.0F - x * x * x;
    }
}
