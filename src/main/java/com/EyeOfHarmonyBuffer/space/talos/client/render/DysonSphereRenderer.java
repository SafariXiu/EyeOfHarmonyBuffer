package com.EyeOfHarmonyBuffer.space.talos.client.render;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import net.minecraft.client.multiplayer.WorldClient;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    /** 戴森球壳/框架半径（原 14.5，放大 2 倍）。 */
    private static final double RADIUS = 29.0D;
    /** 太阳到原点的距离（与天空盒保持一致）。 */
    private static final double SUN_DISTANCE = 100.0D;
    /** 戴森云环的半径，远大于戴森球壳，类似星环围绕恒星。 */
    private static final double RING_RADIUS = 56.0D;
    /** 框架厚度：面板厚度与节点正多边形边长。 */
    private static final double FRAME_THICKNESS = 2.5D;
    /** 棱的厚度（约为面板厚度的三分之一，细梁观感）。 */
    private static final double BEAM_THICKNESS = 0.8D;
    /**
     * 前半球裁剪（框架/云环等透明结构）：
     * 硬边界取球体自身轮廓线（dot = -R²/D）再向外放宽一点，宁可裁得保守一点、
     * 在边界做透明度虚化，也不要让远半球边缘插进太阳盘。
     */
    /** 框架边缘虚化带宽度（与半径同尺度）。 */
    private static final double FRAME_EDGE_FADE = 3.0D;
    private static final double FRAME_FRONT_CLIP = RADIUS * RADIUS / SUN_DISTANCE + FRAME_EDGE_FADE;
    /** 云环同理，半径更大所以虚化带更宽。 */
    /** 云环边缘虚化带宽度（与半径同尺度）。 */
    private static final double CLOUD_EDGE_FADE = 8.0D;
    private static final double CLOUD_FRONT_CLIP = RING_RADIUS * RING_RADIUS / SUN_DISTANCE + CLOUD_EDGE_FADE;
    /** 完工球壳是实心的，可见边界仍是球体自身轮廓线 dot = -R²/D。 */
    private static final double SHELL_FRONT_CLIP = RADIUS * RADIUS / SUN_DISTANCE;
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

    /** 平滑动画时钟：只按每帧 tick 增量前进，世界时间跳变不会让动画瞬移。 */
    private static double animTime = 0.0D;
    /** 上一次渲染时记录的世界 tick，用于计算增量。 */
    private static long lastAnimTick = Long.MIN_VALUE;

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

    /** 测地线细分度：正二十面体每条边分成 n 段，总面数 = 20 * n²。 */
    private static final int ICO_SUBDIVISION = 3;
    /** 测地线球：细分后的全部单位顶点。 */
    private static final double[][] ICO_VERTICES;
    /** 测地线球：细分后的三角面（顶点索引）。 */
    private static final int[][] ICO_FACES;
    /** 每个三角面的朝外单位法线（与 ICO_FACES 一一对应）。 */
    private static final double[][] ICO_FACE_NORMALS;
    /** 面板随机显示顺序（固定种子，同一阶段每次进入图案稳定）。 */
    private static final int[] PANEL_ORDER;
    /** 测地线球：全部棱（顶点索引对，小索引在前）。 */
    private static final int[][] ICO_EDGES;
    /** 棱的生长顺序：从节点 0 沿 BFS 向四周蔓延铺开。 */
    private static final int[] EDGE_ORDER;

    private static final int[][] ICO_FACES_20 = {
        {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
        {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
        {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
        {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
    };

    static {
        double phi = (1.0D + Math.sqrt(5.0D)) / 2.0D;
        double[][] raw = {
            {-1.0D, phi, 0.0D}, {1.0D, phi, 0.0D}, {-1.0D, -phi, 0.0D}, {1.0D, -phi, 0.0D},
            {0.0D, -1.0D, phi}, {0.0D, 1.0D, phi}, {0.0D, -1.0D, -phi}, {0.0D, 1.0D, -phi},
            {phi, 0.0D, -1.0D}, {phi, 0.0D, 1.0D}, {-phi, 0.0D, -1.0D}, {-phi, 0.0D, 1.0D}
        };

        List<double[]> verts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double len = Math.sqrt(raw[i][0] * raw[i][0] + raw[i][1] * raw[i][1] + raw[i][2] * raw[i][2]);
            verts.add(new double[] {raw[i][0] / len, raw[i][1] / len, raw[i][2] / len});
        }

        int n = ICO_SUBDIVISION;
        int totalFaces = 20 * n * n;
        int[][] faces = new int[totalFaces][3];
        double[][] normals = new double[totalFaces][3];
        int faceIdx = 0;

        for (int[] f : ICO_FACES_20) {
            double[] a = verts.get(f[0]);
            double[] b = verts.get(f[1]);
            double[] c = verts.get(f[2]);

            // 面内网格点（重心坐标），已归一化并去重
            int[][] grid = new int[n + 1][];
            for (int i = 0; i <= n; i++) {
                grid[i] = new int[i + 1];
                for (int j = 0; j <= i; j++) {
                    double px = ((n - i) * a[0] + j * b[0] + (i - j) * c[0]) / n;
                    double py = ((n - i) * a[1] + j * b[1] + (i - j) * c[1]) / n;
                    double pz = ((n - i) * a[2] + j * b[2] + (i - j) * c[2]) / n;
                    grid[i][j] = addNormalizedVertex(verts, px, py, pz);
                }
            }

            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    int p00 = grid[i][j];
                    int p10 = grid[i + 1][j];
                    int p11 = grid[i + 1][j + 1];

                    int idx = faceIdx++;
                    faces[idx][0] = p00;
                    faces[idx][1] = p10;
                    faces[idx][2] = p11;
                    normals[idx] = faceNormal(verts, p00, p10, p11);

                    if (j < i) {
                        int p01 = grid[i][j + 1];
                        idx = faceIdx++;
                        faces[idx][0] = p00;
                        faces[idx][1] = p11;
                        faces[idx][2] = p01;
                        normals[idx] = faceNormal(verts, p00, p11, p01);
                    }
                }
            }
        }

        ICO_VERTICES = verts.toArray(new double[0][]);
        ICO_FACES = faces;
        ICO_FACE_NORMALS = normals;

        // 棱去重
        Set<Long> edgeSet = new HashSet<>();
        List<int[]> edgeList = new ArrayList<>();
        for (int[] f : faces) {
            for (int k = 0; k < 3; k++) {
                int a = f[k];
                int b = f[(k + 1) % 3];
                int lo = Math.min(a, b);
                int hi = Math.max(a, b);
                long key = ((long) lo << 32) | hi;
                if (edgeSet.add(key)) {
                    edgeList.add(new int[] {lo, hi});
                }
            }
        }
        ICO_EDGES = edgeList.toArray(new int[0][]);

        // BFS 生长顺序：从节点 0 开始，逐条激活“一端已激活、另一端未激活”的棱
        int edgeTotal = ICO_EDGES.length;
        EDGE_ORDER = new int[edgeTotal];
        boolean[] nodeActive = new boolean[ICO_VERTICES.length];
        boolean[] edgeUsed = new boolean[edgeTotal];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        nodeActive[0] = true;
        queue.add(0);
        int orderIdx = 0;
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int e = 0; e < edgeTotal; e++) {
                if (edgeUsed[e]) {
                    continue;
                }
                int a = ICO_EDGES[e][0];
                int b = ICO_EDGES[e][1];
                int next = -1;
                if (a == cur && !nodeActive[b]) {
                    next = b;
                } else if (b == cur && !nodeActive[a]) {
                    next = a;
                }
                if (next >= 0) {
                    edgeUsed[e] = true;
                    EDGE_ORDER[orderIdx++] = e;
                    nodeActive[next] = true;
                    queue.add(next);
                }
            }
        }
        for (int e = 0; e < edgeTotal; e++) {
            if (!edgeUsed[e]) {
                EDGE_ORDER[orderIdx++] = e;
            }
        }

        PANEL_ORDER = new int[ICO_FACES.length];
        for (int i = 0; i < PANEL_ORDER.length; i++) {
            PANEL_ORDER[i] = i;
        }
        Random orderRandom = new Random(20260812L);
        for (int i = PANEL_ORDER.length - 1; i > 0; i--) {
            int j = orderRandom.nextInt(i + 1);
            int tmp = PANEL_ORDER[i];
            PANEL_ORDER[i] = PANEL_ORDER[j];
            PANEL_ORDER[j] = tmp;
        }
    }

    /** 把点归一化到单位球并加入顶点表（线性去重）。 */
    private static int addNormalizedVertex(List<double[]> verts, double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        double nx = x / len;
        double ny = y / len;
        double nz = z / len;
        for (int i = 0; i < verts.size(); i++) {
            double[] v = verts.get(i);
            if (Math.abs(v[0] - nx) < 1.0e-5D
                && Math.abs(v[1] - ny) < 1.0e-5D
                && Math.abs(v[2] - nz) < 1.0e-5D) {
                return i;
            }
        }
        verts.add(new double[] {nx, ny, nz});
        return verts.size() - 1;
    }

    /** 计算三角面的朝外单位法线。 */
    private static double[] faceNormal(List<double[]> verts, int a, int b, int c) {
        double[] pa = verts.get(a);
        double[] pb = verts.get(b);
        double[] pc = verts.get(c);
        double ux = pb[0] - pa[0];
        double uy = pb[1] - pa[1];
        double uz = pb[2] - pa[2];
        double vx = pc[0] - pa[0];
        double vy = pc[1] - pa[1];
        double vz = pc[2] - pa[2];
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        double mx = (pa[0] + pb[0] + pc[0]) / 3.0D;
        double my = (pa[1] + pb[1] + pc[1]) / 3.0D;
        double mz = (pa[2] + pb[2] + pc[2]) / 3.0D;
        if (nx * mx + ny * my + nz * mz < 0.0D) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return new double[] {nx, ny, nz};
    }

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

        // 框架填充率：5万=20%、15万=50%、30万=80%、50万=100%
        float frameCoverage;
        if (frameCount >= DysonSphereState.FRAME_COMPLETE) {
            frameCoverage = 1.0F;
        } else if (frameCount >= DysonSphereState.FRAME_STAGE_3) {
            frameCoverage = 0.8F;
        } else if (frameCount >= DysonSphereState.FRAME_STAGE_2) {
            frameCoverage = 0.5F;
        } else if (frameCount >= DysonSphereState.FRAME_MIN) {
            frameCoverage = 0.2F;
        } else {
            frameCoverage = 0.0F;
        }

        // 完工后云不再显示（剩余云按后续设计慢慢掉落）
        if (completed) {
            cloudRings = 0;
            cloudDensity = 0.0F;
        }

        if (frameCoverage <= 0.0F && frameCount <= 0 && cloudRings <= 0) {
            return;
        }

        // 太阳球心随角度绕 Z 旋转：点 (0,-100,0) 旋转 angle 后的世界坐标
        float cel = world.getCelestialAngle(partialTicks);
        float angle = cel * 360.0F + 180.0F - 10.0F;
        double rad = Math.toRadians(angle);
        double coreX = SUN_DISTANCE * Math.sin(rad);
        double coreY = -SUN_DISTANCE * Math.cos(rad);

        // 使用平滑动画时钟而非原始世界时间，避免睡眠/时间同步导致动画瞬移
        double worldTime = getSmoothAnimTime(world.getWorldTime());
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

        drawFrame(frameCoverage, frameCount);
        if (cloudRings > 0 && cloudDensity > 0.0F) {
            drawCloudRings(worldTime, cloudRings, cloudDensity);
        }
        if (completed) {
            drawCompletedShell();
        }

        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
    }

    /** 更新并返回平滑动画时钟：单帧最多前进 1 tick，忽略回拨，避免时间跳变导致闪烁。 */
    private static double getSmoothAnimTime(long rawTick) {
        if (lastAnimTick == Long.MIN_VALUE) {
            lastAnimTick = rawTick;
            animTime = rawTick;
            return animTime;
        }
        long delta = rawTick - lastAnimTick;
        lastAnimTick = rawTick;
        if (delta > 1L) {
            delta = 1L;
        } else if (delta < 0L) {
            delta = 0L;
        }
        animTime += delta;
        return animTime;
    }

    /**
     * 视线方向（球心指向玩家）变换到球体局部坐标系。
     * 与 GL 施加的旋转顺序保持一致：先 spinZ，再 spinY，再 spinX，最后绕 Z 翻转 90°。
     */
    private static void computeViewLocal(float angle, double animTime) {
        double rad = Math.toRadians(angle);
        float viewWorldX = (float) -Math.sin(rad);
        float viewWorldY = (float) Math.cos(rad);
        float viewWorldZ = 0.0F;

        float[] rz90 = rotZ(90.0F);
        float[] rx = rotX((float) (animTime * 0.0015D));
        float[] ry = rotY((float) (animTime * 0.0005D));
        float[] rz = rotZ((float) (animTime * 0.00025D));
        float[] r = mul(mul(mul(rz90, rx), ry), rz);

        // 正交矩阵：逆 = 转置。这里要把世界系的方向（太阳→玩家）转到球体局部系，
        // 必须乘 r 的转置（取行），之前乘了 r 本身导致前后半球判断正好相反。
        VIEW_LOCAL[0] = r[0] * viewWorldX + r[1] * viewWorldY + r[2] * viewWorldZ;
        VIEW_LOCAL[1] = r[3] * viewWorldX + r[4] * viewWorldY + r[5] * viewWorldZ;
        VIEW_LOCAL[2] = r[6] * viewWorldX + r[7] * viewWorldY + r[8] * viewWorldZ;
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

    /** 计算裁剪虚化带的透明度：dot 越接近硬边界越透明，边界外为 0。 */
    private static float clipAlpha(double dot, double limit, double fade) {
        double soft = limit + fade;
        if (dot >= soft) {
            return 1.0F;
        }
        if (dot <= limit) {
            return 0.0F;
        }
        return (float) ((dot - limit) / fade);
    }

    /**
     * 框架：正二十面体面板结构（戴森球计划风格）。
     * 测地线细分后带厚度的三角面板拼成球壳，顶点处为正五/六边形节点
     * （节点边长 = 面板厚度）；覆盖率决定显示的面板数。
     */
    /**
     * 框架双层渲染：
     * - 棱/节点层：框架数量小于 5 万时为“从节点向外生长”的搭建过程，5 万后棱全部铺完；
     * - 面板层：仅 5 万后开始，按覆盖率随机填充。
     */
    private static void drawFrame(float coverage, int frameCount) {
        boolean[] nodeShown = new boolean[ICO_VERTICES.length];

        // 棱数量：<5万按生长比例，≥5万全部铺完
        int edgeCount = frameCount >= DysonSphereState.FRAME_MIN
            ? ICO_EDGES.length
            : (int) Math.round(ICO_EDGES.length * (double) frameCount / DysonSphereState.FRAME_MIN);
        for (int i = 0; i < edgeCount; i++) {
            int e = EDGE_ORDER[i];
            nodeShown[ICO_EDGES[e][0]] = true;
            nodeShown[ICO_EDGES[e][1]] = true;
        }

        // 面板只做节点标记（可见性/虚化在绘制时按面中心计算）
        int faceCount = 0;
        if (frameCount >= DysonSphereState.FRAME_MIN) {
            faceCount = (int) Math.round(ICO_FACES.length * coverage);
            for (int i = 0; i < faceCount; i++) {
                int f = PANEL_ORDER[i];
                int[] face = ICO_FACES[f];
                nodeShown[face[0]] = true;
                nodeShown[face[1]] = true;
                nodeShown[face[2]] = true;
            }
        }

        // 棱：沿球面大圆弧的方形截面梁
        if (edgeCount > 0) {
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < edgeCount; i++) {
                emitStraightBeam(ICO_EDGES[EDGE_ORDER[i]][0], ICO_EDGES[EDGE_ORDER[i]][1]);
            }
            GL11.glEnd();
        }

        // 节点：正五/六边形连接块
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < ICO_VERTICES.length; i++) {
            if (!nodeShown[i]) {
                continue;
            }
            float alpha = nodeAlpha(i);
            if (alpha <= 0.0F) {
                continue;
            }
            GL11.glColor4f(0.32F, 0.38F, 0.55F, 0.92F * alpha);
            emitNodeFaces(i);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < ICO_VERTICES.length; i++) {
            if (!nodeShown[i]) {
                continue;
            }
            float alpha = nodeAlpha(i);
            if (alpha <= 0.0F) {
                continue;
            }
            GL11.glColor4f(0.70F, 0.78F, 0.95F, 0.9F * alpha);
            emitNodeEdges(i);
        }
        GL11.glEnd();

        if (faceCount <= 0) {
            return;
        }

        // 面板层：外表面 / 内表面 / 侧边
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < faceCount; i++) {
            int f = PANEL_ORDER[i];
            float alpha = panelAlpha(f);
            if (alpha <= 0.0F) {
                continue;
            }
            GL11.glColor4f(0.42F, 0.48F, 0.66F, 0.88F * alpha);
            emitPanelTriangle(f, true);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < faceCount; i++) {
            int f = PANEL_ORDER[i];
            float alpha = panelAlpha(f);
            if (alpha <= 0.0F) {
                continue;
            }
            GL11.glColor4f(0.42F, 0.48F, 0.66F, 0.88F * alpha);
            emitPanelTriangle(f, false);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < faceCount; i++) {
            int f = PANEL_ORDER[i];
            float alpha = panelAlpha(f);
            if (alpha <= 0.0F) {
                continue;
            }
            GL11.glColor4f(0.42F, 0.48F, 0.66F, 0.88F * alpha);
            emitPanelEdges(f);
        }
        GL11.glEnd();
    }

    /** 输出一条棱：两点之间的直线方形截面梁（假定 GL_QUADS 已开始）。 */
    private static void emitStraightBeam(int a, int b) {
        double[] p0 = ICO_VERTICES[a];
        double[] p1 = ICO_VERTICES[b];

        double x0 = p0[0] * RADIUS;
        double y0 = p0[1] * RADIUS;
        double z0 = p0[2] * RADIUS;
        double x1 = p1[0] * RADIUS;
        double y1 = p1[1] * RADIUS;
        double z1 = p1[2] * RADIUS;

        double limit = -FRAME_FRONT_CLIP;
        float d0 = dotLocal((float) x0, (float) y0, (float) z0);
        float d1 = dotLocal((float) x1, (float) y1, (float) z1);
        if (d0 < limit && d1 < limit) {
            return;
        }
        if (d0 < limit) {
            double t = (limit - d0) / (d1 - d0);
            x0 += (x1 - x0) * t;
            y0 += (y1 - y0) * t;
            z0 += (z1 - z0) * t;
        } else if (d1 < limit) {
            double t = (limit - d0) / (d1 - d0);
            x1 = x0 + (x1 - x0) * t;
            y1 = y0 + (y1 - y0) * t;
            z1 = z0 + (z1 - z0) * t;
        }

        float avgDot = (dotLocal((float) x0, (float) y0, (float) z0)
            + dotLocal((float) x1, (float) y1, (float) z1)) * 0.5F;
        float alpha = clipAlpha(avgDot, limit, FRAME_EDGE_FADE);
        if (alpha <= 0.0F) {
            return;
        }
        GL11.glColor4f(0.58F, 0.62F, 0.78F, 0.85F * alpha);

        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double tx = dx / len;
        double ty = dy / len;
        double tz = dz / len;

        double ax = -tz;
        double ay = 0.0D;
        double az = tx;
        double aLen = Math.sqrt(ax * ax + az * az);
        if (aLen < 0.001D) {
            ax = 1.0D;
            az = 0.0D;
            aLen = 1.0D;
        }
        ax /= aLen;
        az /= aLen;

        double bx = ty * az - tz * ay;
        double by = tz * ax - tx * az;
        double bz = tx * ay - ty * ax;

        double h = BEAM_THICKNESS * 0.5D;
        quad(
            x0 + ax * h + bx * h, y0 + ay * h + by * h, z0 + az * h + bz * h,
            x1 + ax * h + bx * h, y1 + ay * h + by * h, z1 + az * h + bz * h,
            x1 + ax * h - bx * h, y1 + ay * h - by * h, z1 + az * h - bz * h,
            x0 + ax * h - bx * h, y0 + ay * h - by * h, z0 + az * h - bz * h);
        quad(
            x0 + ax * h + bx * h, y0 + ay * h + by * h, z0 + az * h + bz * h,
            x0 - ax * h + bx * h, y0 - ay * h + by * h, z0 - az * h + bz * h,
            x1 - ax * h + bx * h, y1 - ay * h + by * h, z1 - az * h + bz * h,
            x1 + ax * h + bx * h, y1 + ay * h + by * h, z1 + az * h + bz * h);
        quad(
            x0 - ax * h + bx * h, y0 - ay * h + by * h, z0 - az * h + bz * h,
            x1 - ax * h + bx * h, y1 - ay * h + by * h, z1 - az * h + bz * h,
            x1 - ax * h - bx * h, y1 - ay * h - by * h, z1 - az * h - bz * h,
            x0 - ax * h - bx * h, y0 - ay * h - by * h, z0 - az * h - bz * h);
        quad(
            x0 - ax * h - bx * h, y0 - ay * h - by * h, z0 - az * h - bz * h,
            x0 + ax * h - bx * h, y0 + ay * h - by * h, z0 + az * h - bz * h,
            x1 + ax * h - bx * h, y1 + ay * h - by * h, z1 + az * h - bz * h,
            x1 - ax * h - bx * h, y1 - ay * h - by * h, z1 - az * h - bz * h);
    }

    /** 输出一块面板的外/内表面三角形（假定 GL_TRIANGLES 已开始）。 */
    private static void emitPanelTriangle(int f, boolean outer) {
        int[] face = ICO_FACES[f];
        double[] n = ICO_FACE_NORMALS[f];
        double h = FRAME_THICKNESS * 0.5D;
        double sign = outer ? h : -h;
        for (int k = 0; k < 3; k++) {
            double[] v = ICO_VERTICES[face[k]];
            GL11.glVertex3d(
                v[0] * RADIUS + n[0] * sign,
                v[1] * RADIUS + n[1] * sign,
                v[2] * RADIUS + n[2] * sign);
        }
    }

    /** 输出一块面板的三条侧边（假定 GL_QUADS 已开始）。 */
    private static void emitPanelEdges(int f) {
        int[] face = ICO_FACES[f];
        double[] n = ICO_FACE_NORMALS[f];
        double h = FRAME_THICKNESS * 0.5D;
        for (int k = 0; k < 3; k++) {
            double[] a = ICO_VERTICES[face[k]];
            double[] b = ICO_VERTICES[face[(k + 1) % 3]];
            quad(
                a[0] * RADIUS + n[0] * h, a[1] * RADIUS + n[1] * h, a[2] * RADIUS + n[2] * h,
                b[0] * RADIUS + n[0] * h, b[1] * RADIUS + n[1] * h, b[2] * RADIUS + n[2] * h,
                b[0] * RADIUS - n[0] * h, b[1] * RADIUS - n[1] * h, b[2] * RADIUS - n[2] * h,
                a[0] * RADIUS - n[0] * h, a[1] * RADIUS - n[1] * h, a[2] * RADIUS - n[2] * h);
        }
    }

    /** 节点中心的虚化透明度。 */
    private static float nodeAlpha(int index) {
        double[] v = ICO_VERTICES[index];
        return clipAlpha(
            dotLocal((float) (v[0] * RADIUS), (float) (v[1] * RADIUS), (float) (v[2] * RADIUS)),
            -FRAME_FRONT_CLIP, FRAME_EDGE_FADE);
    }

    /** 面板中心的虚化透明度。 */
    private static float panelAlpha(int f) {
        int[] face = ICO_FACES[f];
        double[] va = ICO_VERTICES[face[0]];
        double[] vb = ICO_VERTICES[face[1]];
        double[] vc = ICO_VERTICES[face[2]];
        double mx = (va[0] + vb[0] + vc[0]) / 3.0D * RADIUS;
        double my = (va[1] + vb[1] + vc[1]) / 3.0D * RADIUS;
        double mz = (va[2] + vb[2] + vc[2]) / 3.0D * RADIUS;
        return clipAlpha(dotLocal((float) mx, (float) my, (float) mz), -FRAME_FRONT_CLIP, FRAME_EDGE_FADE);
    }

    /** 节点边数：原正二十面体 12 个顶点为五边形，细分新增顶点为六边形。 */
    private static int nodeSides(int index) {
        return index < 12 ? 5 : 6;
    }

    /** 节点正多边形第 k 个角（切平面上）。 */
    private static double[] nodeCorner(int index, int k) {
        double[] n = ICO_VERTICES[index];
        double ux;
        double uy = 0.0D;
        double uz;
        if (Math.abs(n[1]) < 0.9D) {
            ux = n[2];
            uz = -n[0];
        } else {
            ux = 1.0D;
            uz = 0.0D;
        }
        double uLen = Math.sqrt(ux * ux + uz * uz);
        ux /= uLen;
        uz /= uLen;

        double vx = n[1] * uz - n[2] * uy;
        double vy = n[2] * ux - n[0] * uz;
        double vz = n[0] * uy - n[1] * ux;

        int sides = nodeSides(index);
        double radius = FRAME_THICKNESS / (2.0D * Math.sin(Math.PI / sides));
        double a = Math.PI * 2.0D * k / sides;
        return new double[] {
            n[0] * RADIUS + radius * (Math.cos(a) * ux + Math.sin(a) * vx),
            n[1] * RADIUS + radius * (Math.cos(a) * uy + Math.sin(a) * vy),
            n[2] * RADIUS + radius * (Math.cos(a) * uz + Math.sin(a) * vz)
        };
    }

    /** 输出节点面板三角形（假定 GL_TRIANGLES 已开始）。 */
    private static void emitNodeFaces(int index) {
        int sides = nodeSides(index);
        double[] c = ICO_VERTICES[index];
        double cx = c[0] * RADIUS;
        double cy = c[1] * RADIUS;
        double cz = c[2] * RADIUS;
        for (int k = 0; k < sides; k++) {
            double[] p0 = nodeCorner(index, k);
            double[] p1 = nodeCorner(index, (k + 1) % sides);
            GL11.glVertex3d(cx, cy, cz);
            GL11.glVertex3d(p0[0], p0[1], p0[2]);
            GL11.glVertex3d(p1[0], p1[1], p1[2]);
        }
    }

    /** 输出节点边线（假定 GL_LINES 已开始）。 */
    private static void emitNodeEdges(int index) {
        int sides = nodeSides(index);
        for (int k = 0; k < sides; k++) {
            double[] p0 = nodeCorner(index, k);
            double[] p1 = nodeCorner(index, (k + 1) % sides);
            GL11.glVertex3d(p0[0], p0[1], p0[2]);
            GL11.glVertex3d(p1[0], p1[1], p1[2]);
        }
    }

    /** 画一个四边形（假定 GL_QUADS 已开始）。 */
    private static void quad(double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3) {
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
    }

    /**
     * 戴森云：围绕恒星的星环结构。
     * 每个环画内外两条轨道线 + 环带内漂浮的云组件；
     * 组件在环的径向宽度内随机分布，按“远 → 近”排序绘制形成前后遮挡，
     * 公转到背向玩家的一侧时隐藏（被恒星/球壳遮挡）。
     */
    private static void drawCloudRings(double animTime, int ringCount, float density) {
        // 使用平滑动画时钟，公转角度连续累积，世界时间跳变不会导致瞬移
        double dayTicks = animTime;

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
            float fade = clipAlpha(piece.depth, -CLOUD_FRONT_CLIP, CLOUD_EDGE_FADE);
            if (fade <= 0.0F) {
                continue;
            }
            GL11.glColor4f(0.95F, 0.93F, 0.85F, piece.alpha * fade);
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
        float limit = (float) -SHELL_FRONT_CLIP;

        if (s0 >= limit && s1 >= limit) {
            GL11.glVertex3d(x0, y0, z0);
            GL11.glVertex3d(x1, y1, z1);
        } else if (s0 >= limit) {
            double t = (limit - s0) / (s1 - s0);
            GL11.glVertex3d(x0, y0, z0);
            GL11.glVertex3d(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
        } else if (s1 >= limit) {
            double t = (limit - s0) / (s1 - s0);
            GL11.glVertex3d(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
            GL11.glVertex3d(x1, y1, z1);
        }
    }

    private static void drawSphereQuads(double radius, int lonSeg, int latSeg) {
        double clipUnit = radius / SUN_DISTANCE;
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
                if (dotLocal((float) mx, (float) my, (float) mz) < -clipUnit) {
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
