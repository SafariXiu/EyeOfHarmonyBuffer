package com.EyeOfHarmonyBuffer.space.talos.client.render;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import net.minecraft.client.multiplayer.WorldClient;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 戴森球球层动画（天空盒内，球心跟随太阳）。
 * <p>
 * 阶段：
 * 1 - 全是戴森云；
 * 2 - 戴森云 + 25% 框架；
 * 3 - 50% 戴森云 + 50% 框架；
 * 4 - 80% 框架 + 少量戴森云；
 * 5 - 完工：太阳被完全遮蔽，缝隙漏出幽暗蓝光。
 * <p>
 * 天空盒禁用了深度测试，因此这里只绘制朝向玩家的前半球，
 * 背面（本应被恒星挡住的部分）逐顶点裁剪，避免穿帮。
 * 只读取 {@link DysonSphereState}，后续发射机/接收机系统直接更新该状态即可。
 */
public final class DysonSphereRenderer {

    private static final double RADIUS = 14.5D;
    /** 太阳到原点的距离（太阳绘制在 y=-100，随角度绕 Z 旋转）。 */
    private static final double SUN_DISTANCE = 100.0D;
    /** 戴森云环的半径，远大于戴森球壳，类似星环围绕恒星。 */
    private static final double RING_RADIUS = 28.0D;
    /** 每个环上的云组件数量（满密度时）。 */
    private static final int COMPONENTS_PER_RING = 160;
    /** 环带半宽：组件在 RING_RADIUS ± 该值范围内漂浮。 */
    private static final double RING_WIDTH = 2.8D;
    /** 环带半高：组件在环平面上下 ± 该值范围内漂浮，形成立体环体。 */
    private static final double RING_HALF_HEIGHT = 2.2D;
    /** 公转速度倍率：降低到原来的 20%（更缓慢、更沉稳）。 */
    private static final double CLOUD_SPEED_MULTIPLIER = 0.2D;

    /** 视线方向在球体局部坐标系中的向量（每次 render 重新计算）。 */
    private static final float[] VIEW_LOCAL = new float[3];

    /** 三个环的平面法线（球体局部坐标）：赤道、与赤道成 30°、与赤道成 120°。 */
    private static final double[][] RING_NORMALS = {
        {0.0D, 1.0D, 0.0D},
        {0.0D, Math.cos(Math.toRadians(30.0D)), Math.sin(Math.toRadians(30.0D))},
        {0.0D, Math.cos(Math.toRadians(120.0D)), Math.sin(Math.toRadians(120.0D))}
    };
    /** 各环公转速度系数（每游戏日的圈数）。 */
    private static final double[] RING_SPEEDS = {1.0D, 0.85D, 0.7D};
    /** 各环的初始相位偏移。 */
    private static final double[] RING_PHASES = {0.0D, 30.0D, 60.0D};

    /** 环组件静态定义：[环][组件]{径向偏移, 相位, 尺寸, 透明度系数, 上下偏移, 倾斜角, 厚度}。 */
    private static final float[][][] RING_COMPONENTS = new float[3][COMPONENTS_PER_RING][7];

    static {
        Random random = new Random(1919L);
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < COMPONENTS_PER_RING; i++) {
                RING_COMPONENTS[ring][i][0] = (float) ((random.nextDouble() * 2.0D - 1.0D) * RING_WIDTH);
                RING_COMPONENTS[ring][i][1] = (float) (random.nextDouble() * 360.0D);
                RING_COMPONENTS[ring][i][2] = 0.8F + random.nextFloat() * 1.0F;
                RING_COMPONENTS[ring][i][3] = 0.75F + random.nextFloat() * 0.25F;
                RING_COMPONENTS[ring][i][4] = (float) ((random.nextDouble() * 2.0D - 1.0D) * RING_HALF_HEIGHT);
                RING_COMPONENTS[ring][i][5] = (float) ((random.nextDouble() * 2.0D - 1.0D) * 0.6D);
                RING_COMPONENTS[ring][i][6] = 0.35F + random.nextFloat() * 0.4F;
            }
        }
    }

    private DysonSphereRenderer() {}

    public static void render(WorldClient world, float partialTicks) {
        int cloudCount = DysonSphereState.getCloudCount();
        int frameCount = DysonSphereState.getFrameCount();
        boolean completed = frameCount >= DysonSphereState.FRAME_COMPLETE;

        // 戴森云：环数/密度由可见云数量驱动；超过 3 万的部分视为已向框架转换，不再显示在环上
        int visibleCloud = Math.min(cloudCount, DysonSphereState.CLOUD_LEVEL_3);
        int cloudRings = visibleCloud >= 20_000 ? 3 : visibleCloud >= 10_000 ? 2 : visibleCloud > 0 ? 1 : 0;
        float cloudDensity = cloudRings == 0
            ? 0.0F
            : visibleCloud / (float) DysonSphereState.CLOUD_LEVEL_3;

        // 框架：低于 5 万不显示；5万/15万/30万/50万 四档连续过渡到完整球壳
        float frameCoverage;
        if (frameCount < DysonSphereState.FRAME_MIN) {
            frameCoverage = 0.0F;
        } else if (frameCount < DysonSphereState.FRAME_STAGE_2) {
            frameCoverage = 0.25F + 0.25F * (frameCount - DysonSphereState.FRAME_MIN)
                / (DysonSphereState.FRAME_STAGE_2 - DysonSphereState.FRAME_MIN);
        } else if (frameCount < DysonSphereState.FRAME_STAGE_3) {
            frameCoverage = 0.5F + 0.3F * (frameCount - DysonSphereState.FRAME_STAGE_2)
                / (DysonSphereState.FRAME_STAGE_3 - DysonSphereState.FRAME_STAGE_2);
        } else {
            frameCoverage = 0.8F + 0.2F * (frameCount - DysonSphereState.FRAME_STAGE_3)
                / (DysonSphereState.FRAME_COMPLETE - DysonSphereState.FRAME_STAGE_3);
        }

        // 完工后云不再显示（剩余云按后续设计慢慢掉落）
        if (completed) {
            cloudRings = 0;
            cloudDensity = 0.0F;
        }

        if (frameCoverage <= 0.0F && cloudRings <= 0) {
            return;
        }

        // 太阳球心随角度绕 Z 旋转：点 (0,-100,0) 旋转 angle 后的世界坐标
        float cel = world.getCelestialAngle(partialTicks);
        float angle = cel * 360.0F + 180.0F - 10.0F;
        double rad = Math.toRadians(angle);
        double coreX = SUN_DISTANCE * Math.sin(rad);
        double coreY = -SUN_DISTANCE * Math.cos(rad);

        // 使用连续世界时间而非 %24000，避免每天午夜时间回零导致角度跳变闪烁
        double worldTime = world.getWorldTime();
        computeViewLocal(angle, worldTime);

        GL11.glPushMatrix();
        // 平移到太阳球心
        GL11.glTranslatef((float) coreX, (float) coreY, 0.0F);
        // 极轴从 Y 转到 X：两极位于目视左右，赤道竖直正对玩家
        GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
        // 多轴慢速自转，产生轻微翻滚（每游戏日约 36° / 12° / 6°）
        GL11.glRotatef((float) (worldTime * 0.0015D), 1.0F, 0.0F, 0.0F);
        GL11.glRotatef((float) (worldTime * 0.0005D), 0.0F, 1.0F, 0.0F);
        GL11.glRotatef((float) (worldTime * 0.00025D), 0.0F, 0.0F, 1.0F);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);

        if (frameCoverage > 0.0F) {
            drawFrame(frameCoverage);
        }
        if (cloudRings > 0 && cloudDensity > 0.0F) {
            drawCloudRings(world.getWorldTime(), cloudRings, cloudDensity);
        }
        if (completed) {
            drawCompletedShell();
        }

        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
    }

    /**
     * 视线方向（球心指向玩家）变换到球体局部坐标系。
     * 与 GL 施加的旋转顺序保持一致：先 spinZ，再 spinY，再 spinX，最后绕 Z 翻转 90°。
     */
    private static void computeViewLocal(float angle, double worldTime) {
        double rad = Math.toRadians(angle);
        float viewWorldX = (float) -Math.sin(rad);
        float viewWorldY = (float) Math.cos(rad);
        float viewWorldZ = 0.0F;

        float[] rz90 = rotZ(90.0F);
        float[] rx = rotX((float) (worldTime * 0.0015D));
        float[] ry = rotY((float) (worldTime * 0.0005D));
        float[] rz = rotZ((float) (worldTime * 0.00025D));
        float[] r = mul(mul(mul(rz90, rx), ry), rz);

        // 正交矩阵：逆 = 转置
        VIEW_LOCAL[0] = r[0] * viewWorldX + r[3] * viewWorldY + r[6] * viewWorldZ;
        VIEW_LOCAL[1] = r[1] * viewWorldX + r[4] * viewWorldY + r[7] * viewWorldZ;
        VIEW_LOCAL[2] = r[2] * viewWorldX + r[5] * viewWorldY + r[8] * viewWorldZ;
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

    private static float[] rotZ(float deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new float[] {
            (float) c, (float) s, 0.0F,
            (float) -s, (float) c, 0.0F,
            0.0F, 0.0F, 1.0F
        };
    }

    private static float[] mul(float[] a, float[] b) {
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

    private static float dotLocal(float x, float y, float z) {
        return x * VIEW_LOCAL[0] + y * VIEW_LOCAL[1] + z * VIEW_LOCAL[2];
    }

    /** 框架：经线 + 纬线网格，覆盖率决定网格密度；线段按前半球裁剪。 */
    private static void drawFrame(float coverage) {
        int lonCount = Math.max(2, (int) Math.round(18 * coverage));
        int latCount = Math.max(1, (int) Math.round(9 * coverage));

        GL11.glLineWidth(1.2F);
        GL11.glColor4f(0.72F, 0.76F, 0.88F, 0.55F);

        for (int i = 0; i < latCount; i++) {
            double lat = -70.0D + 140.0D * i / Math.max(1, latCount - 1);
            drawLatCircle(lat, RADIUS);
        }
        for (int i = 0; i < lonCount; i++) {
            double lon = 360.0D * i / lonCount;
            drawLonCircle(lon, RADIUS);
        }
    }

    /**
     * 戴森云：围绕恒星的星环结构。
     * 每个环画内外两条轨道线 + 环带内漂浮的云组件；
     * 组件在环的径向宽度内随机分布，按“远 → 近”排序绘制形成前后遮挡，
     * 公转到背向玩家的一侧时隐藏（被恒星/球壳遮挡）。
     */
    private static void drawCloudRings(long worldTime, int ringCount, float density) {
        // 不取模：让公转角度平滑累积，避免午夜跳变
        double dayTicks = worldTime;

        // 轨道线：内外边缘各一条，体现环带宽度
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(0.85F, 0.82F, 0.72F, 0.35F);
        for (int ring = 0; ring < ringCount; ring++) {
            double[] n = RING_NORMALS[ring];
            drawRingLine(n, RING_RADIUS - RING_WIDTH);
            drawRingLine(n, RING_RADIUS + RING_WIDTH);
        }

        // 云组件：先收集全部组件，按远近排序（远先画、近后画），形成前后遮挡
        int componentCount = Math.max(20, (int) Math.round(COMPONENTS_PER_RING * density));
        List<CloudPiece> pieces = new ArrayList<>();
        for (int ring = 0; ring < ringCount; ring++) {
            double[] n = RING_NORMALS[ring];
            double[] u = {1.0D, 0.0D, 0.0D};
            double[] v = cross(n, u);
            double speed = RING_SPEEDS[ring] * CLOUD_SPEED_MULTIPLIER;
            double phase = RING_PHASES[ring];

            for (int j = 0; j < componentCount; j++) {
                float[] def = RING_COMPONENTS[ring][j];
                double radius = RING_RADIUS + def[0];
                double angle = Math.toRadians(phase + def[1] + dayTicks * speed);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                // 中心在环平面内偏移，并沿环法线上下浮动（立体高度）
                double cx = radius * (cos * u[0] + sin * v[0]) + n[0] * def[4];
                double cy = radius * (cos * u[1] + sin * v[1]) + n[1] * def[4];
                double cz = radius * (cos * u[2] + sin * v[2]) + n[2] * def[4];

                // 沿环切线的方向
                double tx = -sin * u[0] + cos * v[0];
                double ty = -sin * u[1] + cos * v[1];
                double tz = -sin * u[2] + cos * v[2];

                // 径向 = 切线 × 法线；扁片法线绕切线轴倾斜，朝向更多样
                double rx = ty * n[2] - tz * n[1];
                double ry = tz * n[0] - tx * n[2];
                double rz = tx * n[1] - ty * n[0];
                double tilt = def[5];
                double tiltCos = Math.cos(tilt);
                double tiltSin = Math.sin(tilt);
                double nwx = n[0] * tiltCos + rx * tiltSin;
                double nwy = n[1] * tiltCos + ry * tiltSin;
                double nwz = n[2] * tiltCos + rz * tiltSin;

                double len = def[2];
                double width = def[6];

                CloudPiece piece = new CloudPiece();
                piece.cx = cx;
                piece.cy = cy;
                piece.cz = cz;
                piece.tx = tx * len;
                piece.ty = ty * len;
                piece.tz = tz * len;
                piece.nx = nwx * width;
                piece.ny = nwy * width;
                piece.nz = nwz * width;
                piece.alpha = 0.55F * def[3];
                piece.depth = dotLocal((float) cx, (float) cy, (float) cz);
                pieces.add(piece);
            }
        }

        Collections.sort(pieces, (a, b) -> Float.compare(b.depth, a.depth));
        for (CloudPiece piece : pieces) {
            if (piece.depth > 0.0F) {
                continue;
            }
            GL11.glColor4f(0.95F, 0.93F, 0.85F, piece.alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3d(piece.cx + piece.tx + piece.nx, piece.cy + piece.ty + piece.ny, piece.cz + piece.tz + piece.nz);
            GL11.glVertex3d(piece.cx - piece.tx + piece.nx, piece.cy - piece.ty + piece.ny, piece.cz - piece.tz + piece.nz);
            GL11.glVertex3d(piece.cx - piece.tx - piece.nx, piece.cy - piece.ty - piece.ny, piece.cz - piece.tz - piece.nz);
            GL11.glVertex3d(piece.cx + piece.tx - piece.nx, piece.cy + piece.ty - piece.ny, piece.cz + piece.tz - piece.nz);
            GL11.glEnd();
        }
    }

    /** 单个云组件（含绘制所需几何与深度）。 */
    private static final class CloudPiece {
        double cx, cy, cz;
        double tx, ty, tz;
        double nx, ny, nz;
        float alpha;
        float depth;
    }

    /** 画一个完整的环轨道线（细线，穿恒星盘面的部分由绘制顺序自然遮挡）。 */
    private static void drawRingLine(double[] n, double radius) {
        double[] u = {1.0D, 0.0D, 0.0D};
        double[] v = cross(n, u);

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 96; i++) {
            double a0 = Math.PI * 2.0D * i / 96.0D;
            double a1 = Math.PI * 2.0D * (i + 1) / 96.0D;
            GL11.glVertex3d(
                radius * (Math.cos(a0) * u[0] + Math.sin(a0) * v[0]),
                radius * (Math.cos(a0) * u[1] + Math.sin(a0) * v[1]),
                radius * (Math.cos(a0) * u[2] + Math.sin(a0) * v[2]));
            GL11.glVertex3d(
                radius * (Math.cos(a1) * u[0] + Math.sin(a1) * v[0]),
                radius * (Math.cos(a1) * u[1] + Math.sin(a1) * v[1]),
                radius * (Math.cos(a1) * u[2] + Math.sin(a1) * v[2]));
        }
        GL11.glEnd();
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    /** 完工：深蓝黑前半球壳完全遮蔽太阳，缝隙亮蓝光，外围一层幽蓝光晕。 */
    private static void drawCompletedShell() {
        GL11.glColor4f(0.03F, 0.05F, 0.12F, 0.96F);
        drawSphereQuads(RADIUS, 24, 12);

        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.20F, 0.65F, 1.0F, 0.75F);
        for (int i = 0; i < 8; i++) {
            drawLonCircle(360.0D * i / 8.0D + 11.25D, RADIUS + 0.05D);
        }
        for (int i = 0; i < 3; i++) {
            drawLatCircle(-60.0D + 60.0D * i, RADIUS + 0.05D);
        }

        GL11.glColor4f(0.10F, 0.30F, 0.70F, 0.10F);
        drawSphereQuads(RADIUS * 1.06D, 18, 9);
    }

    private static void drawLatCircle(double lat, double radius) {
        double rad = Math.toRadians(lat);
        double y = radius * Math.sin(rad);
        double r = radius * Math.cos(rad);

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 48; i++) {
            double a0 = Math.PI * 2.0D * i / 48.0D;
            double a1 = Math.PI * 2.0D * (i + 1) / 48.0D;
            lineSeg(
                r * Math.cos(a0), y, r * Math.sin(a0),
                r * Math.cos(a1), y, r * Math.sin(a1));
        }
        GL11.glEnd();
    }

    private static void drawLonCircle(double lon, double radius) {
        double rad = Math.toRadians(lon);
        double cx = Math.cos(rad);
        double sz = Math.sin(rad);

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 48; i++) {
            double a0 = Math.PI * 2.0D * i / 48.0D;
            double a1 = Math.PI * 2.0D * (i + 1) / 48.0D;
            lineSeg(
                radius * cx * Math.sin(a0), radius * Math.cos(a0), radius * sz * Math.sin(a0),
                radius * cx * Math.sin(a1), radius * Math.cos(a1), radius * sz * Math.sin(a1));
        }
        GL11.glEnd();
    }

    /** 画一条线段，仅保留朝向玩家的前半球部分（背面被恒星遮挡）。 */
    private static void lineSeg(double x0, double y0, double z0, double x1, double y1, double z1) {
        float s0 = dotLocal((float) x0, (float) y0, (float) z0);
        float s1 = dotLocal((float) x1, (float) y1, (float) z1);

        if (s0 <= 0.0F && s1 <= 0.0F) {
            GL11.glVertex3d(x0, y0, z0);
            GL11.glVertex3d(x1, y1, z1);
        } else if (s0 <= 0.0F) {
            double t = s0 / (s0 - s1);
            GL11.glVertex3d(x0, y0, z0);
            GL11.glVertex3d(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
        } else if (s1 <= 0.0F) {
            double t = s1 / (s1 - s0);
            GL11.glVertex3d(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
            GL11.glVertex3d(x1, y1, z1);
        }
    }

    private static void drawSphereQuads(double radius, int lonSeg, int latSeg) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = 0; j < latSeg; j++) {
            double phi0 = Math.PI * j / latSeg - Math.PI / 2.0D;
            double phi1 = Math.PI * (j + 1) / latSeg - Math.PI / 2.0D;

            for (int i = 0; i < lonSeg; i++) {
                double theta0 = Math.PI * 2.0D * i / lonSeg;
                double theta1 = Math.PI * 2.0D * (i + 1) / lonSeg;

                // 四边形中心朝向前半球才绘制
                double midPhi = (phi0 + phi1) * 0.5D;
                double midTheta = (theta0 + theta1) * 0.5D;
                double mx = Math.cos(midPhi) * Math.cos(midTheta);
                double my = Math.sin(midPhi);
                double mz = Math.cos(midPhi) * Math.sin(midTheta);
                if (dotLocal((float) mx, (float) my, (float) mz) > 0.0F) {
                    continue;
                }

                GL11.glVertex3d(
                    radius * Math.cos(phi0) * Math.cos(theta0),
                    radius * Math.sin(phi0),
                    radius * Math.cos(phi0) * Math.sin(theta0));
                GL11.glVertex3d(
                    radius * Math.cos(phi1) * Math.cos(theta0),
                    radius * Math.sin(phi1),
                    radius * Math.cos(phi1) * Math.sin(theta0));
                GL11.glVertex3d(
                    radius * Math.cos(phi1) * Math.cos(theta1),
                    radius * Math.sin(phi1),
                    radius * Math.cos(phi1) * Math.sin(theta1));
                GL11.glVertex3d(
                    radius * Math.cos(phi0) * Math.cos(theta1),
                    radius * Math.sin(phi0),
                    radius * Math.cos(phi0) * Math.sin(theta1));
            }
        }
        GL11.glEnd();
    }
}
