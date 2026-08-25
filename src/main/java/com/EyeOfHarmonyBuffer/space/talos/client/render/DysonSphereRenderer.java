package com.EyeOfHarmonyBuffer.space.talos.client.render;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import net.minecraft.client.multiplayer.WorldClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    /** 戴森球半径（GUI 预览换算尺寸用）。 */
    public static double getSphereRadius() {
        return RADIUS;
    }
    private static final Logger LOGGER = LogManager.getLogger("DysonSphereRenderer");
    /** 太阳到原点的距离（与天空盒保持一致）。 */
    private static final double SUN_DISTANCE = 100.0D;
    /** 戴森云环的半径，远大于戴森球壳，类似星环围绕恒星。 */
    private static final double RING_RADIUS = 56.0D;
    /** 框架厚度：面板厚度与节点正多边形边长。 */
    private static final double FRAME_THICKNESS = 2.5D;
    /** 棱的厚度（约为面板厚度的三分之一，细梁观感）。 */
    private static final double BEAM_THICKNESS = 0.8D;
    /** 节点板外移量：略大于面板外表面，避免共面 z-fighting，让节点盖在最外层。 */
    private static final double NODE_OUTER_OFFSET = BEAM_THICKNESS * 0.5D + 0.04D;
    /** 棱的径向微偏移：略大于面板外表面，避免共面 z-fighting。 */
    private static final double BEAM_RADIAL_OFFSET = 0.02D;
    /** 棱上能量光点的移动速度（每 tick 沿棱前进的比例）。 */
    private static final double BEAM_ENERGY_SPEED = 0.02D;
    /** 棱上能量光点沿棱方向的半长。 */
    private static final double BEAM_ENERGY_HALF_LEN = 2.0D;
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
    /** 每个环上的云组件数量（满密度时，批量渲染所以可以拉高）。 */
    private static final int COMPONENTS_PER_RING = 1000;
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
    /** 当前帧的暖色强度（0=正午冷色，1=晨昏暖色），用于天空染色。 */
    private static float tintWarmth = 0.0F;
    /** 平贴预览模式（GUI 2D 图）：只参与深度测试（被墙/方块正常遮挡），不写深度。
     *  天空盒 3D 路径（render）为 false：面板需要写深度做前后遮挡。单线程渲染下与 tintWarmth 一样随入口设置。 */
    private static boolean flatPreview = false;
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

    /** 环组件静态定义：[环][组件]{径向偏移, 相位, 尺寸, 透明度系数(预留), 上下偏移, 倾斜角(预留), 厚度}。 */
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
        int pasteCount = DysonSphereState.getPasteCount();
        float pasteCoverage = DysonSphereState.getPasteCoverage();
        boolean completed = pasteCount >= DysonSphereState.PASTE_COMPLETE;

        // 戴森云：环数/密度由可见云数量驱动；超过 3 万的部分不显示在环上（仍在轨道池等待每日贴片）
        int visibleCloud = Math.min(cloudCount, DysonSphereState.CLOUD_LEVEL_3);
        int cloudRings = visibleCloud >= 20_000 ? 3 : visibleCloud >= 10_000 ? 2 : visibleCloud > 0 ? 1 : 0;
        float cloudDensity = cloudRings == 0
            ? 0.0F
            : visibleCloud / (float) DysonSphereState.CLOUD_LEVEL_3;

        // 完工（贴片打满）后云环不再显示（剩余轨道云按每日掉落慢慢消失）
        if (completed) {
            cloudRings = 0;
            cloudDensity = 0.0F;
        }

        if (frameCount <= 0 && pasteCount <= 0 && cloudRings <= 0) {
            return;
        }

        // 太阳球心随角度绕 Z 旋转：点 (0,-100,0) 旋转 angle 后的世界坐标
        float cel = world.getCelestialAngle(partialTicks);
        float angle = cel * 360.0F + 180.0F - 10.0F;
        double rad = Math.toRadians(angle);
        // 天空染色：太阳越高越冷，靠近地平线（晨昏）越暖
        double sunHeight = -Math.cos(rad);
        tintWarmth = (float) Math.max(0.0D, 1.0D - Math.abs(sunHeight));
        flatPreview = false;
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

        // 开启深度测试：前面不透明的面板会真正挡住后面的结构
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);

        drawFrame(pasteCoverage, frameCount, worldTime);
        if (cloudRings > 0 && cloudDensity > 0.0F) {
            drawCloudRings(worldTime, cloudRings, cloudDensity);
        }
        if (completed) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            drawCompletedGlow(worldTime);
        }

        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
    }

    /**
     * GUI 专用入口：在“调用方矩阵”下绘制戴森球（球心在原点，尺寸由外层矩阵 scale 决定）。
     * 与天空盒 {@link #render} 共用同一套几何绘制，但：
     * <ul>
     *   <li>不触碰全局平滑时钟：animTime 由调用方传入（世界时间或 GUI 自己的时钟），避免与天空盒互相推进；</li>
     *   <li>不设外层矩阵：由 HoloCanvas 3D 视口负责 translate/scale/rotate；</li>
     *   <li>观察方向固定（沿屏法向 +Z），由 rotX/rotY/rotZ 自转决定视角；</li>
     *   <li>暖色固定为冷色（GUI 预览无晨昏概念）。</li>
     * </ul>
     * 单线程渲染下 tintWarmth/VIEW_LOCAL 作为当前上下文在此设置，下一次 {@link #render} 会重新覆盖，互不干扰。
     *
     * @param animTime   动画时钟（GUI 侧传入）
     * @param rotX/rotY/rotZ 自转角度（度）
     * @param showClouds 是否绘制云环（GUI 预览默认 false，省性能）
     */
    public static void renderPreview(double animTime, float rotX, float rotY, float rotZ, boolean showClouds) {
        int cloudCount = DysonSphereState.getCloudCount();
        int frameCount = DysonSphereState.getFrameCount();
        int pasteCount = DysonSphereState.getPasteCount();
        float pasteCoverage = DysonSphereState.getPasteCoverage();
        boolean completed = pasteCount >= DysonSphereState.PASTE_COMPLETE;

        int visibleCloud = Math.min(cloudCount, DysonSphereState.CLOUD_LEVEL_3);
        int cloudRings = visibleCloud >= 20_000 ? 3 : visibleCloud >= 10_000 ? 2 : visibleCloud > 0 ? 1 : 0;
        float cloudDensity = cloudRings == 0 ? 0.0F : visibleCloud / (float) DysonSphereState.CLOUD_LEVEL_3;
        if (completed || !showClouds) {
            cloudRings = 0;
            cloudDensity = 0.0F;
        }

        // GUI 上下文：暖色固定冷色；观察方向固定 +Z，由自转决定视角
        tintWarmth = 0.0F;
        computeViewLocalForGui(rotX, rotY, rotZ);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // 平贴预览：深度测试保持开启（LEQUAL，与屏内容一致 → 被墙/方块正常遮挡，不会隔墙可见），
        // 但全程不写深度（球内遮挡靠各层远→近排序；平面内等深，LEQUAL 全过 → 画家算法生效）。
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        flatPreview = true;

        // 纯色几何必须显式关闭贴图/光照/雾：屏的 2D 管线会残留 GL_TEXTURE_2D 与旧贴图绑定，
        // 不关的话 glColor 会被贴图调制、染成黑/透明 → 整球不可见（与天空盒调用侧同款处理）。
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);

        // 基础骨架：无论进度多少都先画完整框架线框（蓝图底色），保证预览区任何阶段都有一颗可识别的戴森球。
        // 进度几何（梁/面板/节点/云/完工辉光）叠加其上 —— 低进度时按比例映射出的结构很少，
        // 若不画底图，预览会看上去“一片空白”而误判为渲染失败。
        drawSkeleton(animTime);
        try {
            drawFrame(pasteCoverage, frameCount, animTime);
            if (cloudRings > 0 && cloudDensity > 0.0F) {
                drawCloudRings(animTime, cloudRings, cloudDensity);
            }
            if (completed) {
                drawCompletedGlow(animTime);
            }
        } catch (Throwable t) {
            // 进度态绘制异常不影响基础骨架；堆栈留档便于排查
            LOGGER.error("[Dyson preview] progressed render failed", t);
        }

        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
    }

    /** 预览蓝图底：淡显完整框架骨架（全部棱），任何进度下保证预览区有可识别的球体轮廓。
     *  跟随当前深度状态（平贴预览下深度测试开启 → 与屏内容一样被墙/方块正常遮挡）。 */
    private static void drawSkeleton(double animTime) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1.4F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(0.40F, 0.60F, 1.0F, 0.65F);
        for (int[] e : ICO_EDGES) {
            double[] a = ICO_VERTICES[e[0]];
            double[] b = ICO_VERTICES[e[1]];
            GL11.glVertex3d(a[0] * RADIUS, a[1] * RADIUS, a[2] * RADIUS);
            GL11.glVertex3d(b[0] * RADIUS, b[1] * RADIUS, b[2] * RADIUS);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 预览衬底板：半透明深蓝圆盘 + 细亮环边，先于球体绘制，形成"2D 图像"底座。
     *  由调用方在旋转之前、缩放之后调用（恒为正圆，不被自转带歪）。半径略大于球壳。 */
    public static void drawPreviewDisc() {
        double r = RADIUS * 1.06D;
        int seg = 48;
        GL11.glDepthMask(false);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(0.05F, 0.10F, 0.18F, 0.60F);
        GL11.glVertex3d(0, 0, 0);
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2.0D * i / seg;
            GL11.glVertex3d(Math.cos(a) * r, Math.sin(a) * r, 0);
        }
        GL11.glEnd();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(0.30F, 0.62F, 1.0F, 0.85F);
        for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2.0D * i / seg;
            GL11.glVertex3d(Math.cos(a) * r, Math.sin(a) * r, 0);
        }
        GL11.glEnd();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 平贴矩阵：R = ROTX·ROTY·ROTZ（与 GL 施加顺序一致），把模型顶点旋转后压平到 z=0
     *  （正交投影到屏面）。与 {@link #computeViewLocalForGui} 同一旋转，因此 dotLocal
     *  （= 旋转后 z）仍是正确的前后排序/裁剪键。4×4 列主序填充 out16。 */
    public static void buildFlattenMatrix(float rotX, float rotY, float rotZ, float[] out16) {
        float[] rx = rotX(rotX);
        float[] ry = rotY(rotY);
        float[] rz = rotZ(rotZ);
        float[] r = mul(mul(rx, ry), rz);
        // 列主序 m[col*4+row]：前两列 = R 的行0/行1，第三列清零（压平 z），第四列平移单位
        out16[0] = r[0];  out16[1] = r[3];  out16[2] = 0; out16[3] = 0;
        out16[4] = r[1];  out16[5] = r[4];  out16[6] = 0; out16[7] = 0;
        out16[8] = r[2];  out16[9] = r[5];  out16[10] = 0; out16[11] = 0;
        out16[12] = 0; out16[13] = 0; out16[14] = 0; out16[15] = 1;
    }

    /** GUI 视角：观察方向固定为球体局部系的 (0,0,1)（屏法向 +Z 朝玩家），按自转角求其在球体基系中的表示。
     * 与天空盒 {@link #computeViewLocal} 同一逻辑：视线方向的基系表示 = 模型矩阵的转置 × 该方向。
     * GUI 模型矩阵 M = ROTX·ROTY·ROTZ（glRotatef 依序后乘，顶点 v' = M·v），
     * M^T·(0,0,1) = M 的第三行（行主序 m[6]/m[7]/m[8]）。 */
    private static void computeViewLocalForGui(float rotX, float rotY, float rotZ) {
        float[] rx = rotX(rotX);
        float[] ry = rotY(rotY);
        float[] rz = rotZ(rotZ);
        // GL 模型矩阵（与 modelDyson 施加顺序一致：先 X 后 Y 后 Z）
        float[] m = mul(mul(rx, ry), rz);
        VIEW_LOCAL[0] = m[6];
        VIEW_LOCAL[1] = m[7];
        VIEW_LOCAL[2] = m[8];
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

    /** 按当前暖度把冷色基色向暖色混合。 */
    private static float warmMix(float cold, float warm) {
        return cold + (warm - cold) * tintWarmth;
    }

    /**
     * 框架：正二十面体面板结构（戴森球计划风格）。
     * 测地线细分后带厚度的三角面板拼成球壳，顶点处为正五/六边形节点
     * （节点边长 = 面板厚度）；覆盖率决定显示的面板数。
     */
    /**
     * 框架双层渲染：
     * - 棱/节点层：框架数量小于 5 万时为“从节点向外生长”的搭建过程，5 万后棱全部铺完；
     * - 面板层：贴片覆盖率决定面板铺满比例，贴片满即完工。
     */
    private static void drawFrame(float pasteCoverage, int frameCount, double animTime) {
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

        // 面板（贴片）只做节点标记（可见性/虚化在绘制时按面中心计算）
        int faceCount = 0;
        if (frameCount > 0) {
            faceCount = (int) Math.round(ICO_FACES.length * pasteCoverage);
            for (int i = 0; i < faceCount; i++) {
                int f = PANEL_ORDER[i];
                int[] face = ICO_FACES[f];
                nodeShown[face[0]] = true;
                nodeShown[face[1]] = true;
                nodeShown[face[2]] = true;
            }
        }

        // 固定层级：面板最底 → 棱中间 → 节点最上；每层内部按深度从远到近
        // 面板写深度（挡住背面），棱/节点不写深度（只做叠加）
        List<Integer> visiblePanels = new ArrayList<>();
        for (int i = 0; i < faceCount; i++) {
            int f = PANEL_ORDER[i];
            if (panelAlpha(f) > 0.0F) {
                visiblePanels.add(f);
            }
        }
        visiblePanels.sort((a, b) -> Float.compare(panelDepth(b), panelDepth(a)));
        if (!visiblePanels.isEmpty()) {
            for (int f : visiblePanels) {
                float alpha = panelAlpha(f);
                // 天空盒 3D 模式写深度（前后遮挡）；平贴预览不写（平面内等深，画家算法即可）
                GL11.glDepthMask(!flatPreview);
                GL11.glColor4f(
                    warmMix(0.16F, 0.26F), warmMix(0.21F, 0.23F), warmMix(0.34F, 0.26F),
                    1.0F * alpha);
                GL11.glBegin(GL11.GL_TRIANGLES);
                emitPanelTriangle(f, true);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_TRIANGLES);
                emitPanelTriangle(f, false);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_QUADS);
                emitPanelEdges(f);
                GL11.glEnd();
                // 高科技线条：中点三角 + 中心发光点（加法混合）
                GL11.glDepthMask(false);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glBegin(GL11.GL_LINES);
                emitPanelLines(f, alpha);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_QUADS);
                emitPanelCoreDot(f, alpha);
                GL11.glEnd();
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
        }

        // 棱层（不写深度，靠面板深度做遮挡）：辉光 + 实体梁 + 中心亮条纹 + 能量光点
        GL11.glDepthMask(false);
        List<Integer> visibleBeams = new ArrayList<>();
        for (int i = 0; i < edgeCount; i++) {
            int e = EDGE_ORDER[i];
            if (clipAlpha(beamDepth(e), -FRAME_FRONT_CLIP, FRAME_EDGE_FADE) > 0.0F) {
                visibleBeams.add(e);
            }
        }
        visibleBeams.sort((a, b) -> Float.compare(beamDepth(b), beamDepth(a)));
        for (int e : visibleBeams) {
            BeamSeg seg = clipBeam(ICO_EDGES[e][0], ICO_EDGES[e][1]);
            if (seg == null) {
                continue;
            }
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBegin(GL11.GL_QUADS);
            emitBeamGlow(seg);
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_QUADS);
            emitStraightBeam(seg);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINES);
            emitBeamCoreLine(seg);
            GL11.glEnd();

            // 能量流动光点：贴片覆盖过半（面板铺设过半）棱上才有流动能量特效
            if (pasteCoverage >= 0.5F) {
                double phase = (e * 0.618033988749895D) % 1.0D;
                double t = (animTime * BEAM_ENERGY_SPEED + phase) % 1.0D;
                double px = seg.x0 + (seg.x1 - seg.x0) * t;
                double py = seg.y0 + (seg.y1 - seg.y0) * t;
                double pz = seg.z0 + (seg.z1 - seg.z0) * t;
                double dx = seg.x1 - seg.x0;
                double dy = seg.y1 - seg.y0;
                double dz = seg.z1 - seg.z0;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double tx = dx / len;
                double ty = dy / len;
                double tz = dz / len;
                double hl = BEAM_ENERGY_HALF_LEN;
                BeamSeg pulse = new BeamSeg();
                pulse.x0 = px - tx * hl;
                pulse.y0 = py - ty * hl;
                pulse.z0 = pz - tz * hl;
                pulse.x1 = px + tx * hl;
                pulse.y1 = py + ty * hl;
                pulse.z1 = pz + tz * hl;
                pulse.alpha = seg.alpha;

                // 外圈柔光：宽而淡，让光斑在远处也能被注意到
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glColor4f(0.30F, 0.60F, 1.0F, 0.25F * pulse.alpha);
                emitBeamBox(pulse, BEAM_THICKNESS * 1.5D);
                GL11.glEnd();
                // 亮核：窄而亮，保持清晰的流动感
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glColor4f(0.70F, 0.90F, 1.0F, 0.95F * pulse.alpha);
                emitBeamBox(pulse, BEAM_THICKNESS * 0.55D);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_LINES);
                GL11.glColor4f(0.85F, 0.98F, 1.0F, 1.0F * pulse.alpha);
                GL11.glVertex3d(pulse.x0, pulse.y0, pulse.z0);
                GL11.glVertex3d(pulse.x1, pulse.y1, pulse.z1);
                GL11.glEnd();
                GL11.glLineWidth(1.5F);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
        }

        // 节点层（最上，不写深度，按深度从远到近绘制）：光源面板 + 内缩辉光 + 亮边
        List<Integer> visibleNodes = new ArrayList<>();
        for (int i = 0; i < ICO_VERTICES.length; i++) {
            if (nodeShown[i] && nodeAlpha(i) > 0.0F) {
                visibleNodes.add(i);
            }
        }
        visibleNodes.sort((a, b) -> Float.compare(nodeDepth(b), nodeDepth(a)));
        GL11.glLineWidth(1.5F);
        for (int i : visibleNodes) {
            GL11.glBegin(GL11.GL_TRIANGLES);
            emitNodeFaces(i);
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            emitNodeGlow(i);
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_LINES);
            emitNodeEdges(i);
            GL11.glEnd();
        }
    }

    /** 计算棱的可见段（按前半球裁剪），完全不可见时返回 null。 */
    private static BeamSeg clipBeam(int a, int b) {
        double[] p0 = ICO_VERTICES[a];
        double[] p1 = ICO_VERTICES[b];

        BeamSeg s = new BeamSeg();
        double r = RADIUS + BEAM_RADIAL_OFFSET;
        s.x0 = p0[0] * r;
        s.y0 = p0[1] * r;
        s.z0 = p0[2] * r;
        s.x1 = p1[0] * r;
        s.y1 = p1[1] * r;
        s.z1 = p1[2] * r;

        double limit = -FRAME_FRONT_CLIP;
        float d0 = dotLocal((float) s.x0, (float) s.y0, (float) s.z0);
        float d1 = dotLocal((float) s.x1, (float) s.y1, (float) s.z1);
        if (d0 < limit && d1 < limit) {
            return null;
        }
        if (d0 < limit) {
            double t = (limit - d0) / (d1 - d0);
            s.x0 += (s.x1 - s.x0) * t;
            s.y0 += (s.y1 - s.y0) * t;
            s.z0 += (s.z1 - s.z0) * t;
        } else if (d1 < limit) {
            double t = (limit - d0) / (d1 - d0);
            s.x1 = s.x0 + (s.x1 - s.x0) * t;
            s.y1 = s.y0 + (s.y1 - s.y0) * t;
            s.z1 = s.z0 + (s.z1 - s.z0) * t;
        }

        float avgDot = (dotLocal((float) s.x0, (float) s.y0, (float) s.z0)
            + dotLocal((float) s.x1, (float) s.y1, (float) s.z1)) * 0.5F;
        s.alpha = clipAlpha(avgDot, limit, FRAME_EDGE_FADE);
        if (s.alpha <= 0.0F) {
            return null;
        }
        return s;
    }

    /** 输出棱的辉光层（宽而淡，配合加法混合），假定 GL_QUADS 已开始。 */
    private static void emitBeamGlow(BeamSeg s) {
        GL11.glColor4f(0.30F, 0.55F, 1.0F, 0.14F * s.alpha);
        emitBeamBox(s, BEAM_THICKNESS * 1.8D);
    }

    /** 输出棱的实体梁（细而暗），假定 GL_QUADS 已开始。 */
    private static void emitStraightBeam(BeamSeg s) {
        GL11.glColor4f(
            warmMix(0.20F, 0.32F), warmMix(0.26F, 0.28F), warmMix(0.42F, 0.30F),
            1.0F * s.alpha);
        emitBeamBox(s, BEAM_THICKNESS * 0.5D);
    }

    /** 输出棱中心亮条纹，假定 GL_LINES 已开始。 */
    private static void emitBeamCoreLine(BeamSeg s) {
        GL11.glColor4f(0.72F, 0.86F, 1.0F, 0.85F * s.alpha);
        GL11.glVertex3d(s.x0, s.y0, s.z0);
        GL11.glVertex3d(s.x1, s.y1, s.z1);
    }

    /** 输出一段方形截面梁（假定 GL_QUADS 已开始）。 */
    private static void emitBeamBox(BeamSeg s, double thickness) {
        double dx = s.x1 - s.x0;
        double dy = s.y1 - s.y0;
        double dz = s.z1 - s.z0;
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

        double h = thickness * 0.5D;
        quad(
            s.x0 + ax * h + bx * h, s.y0 + ay * h + by * h, s.z0 + az * h + bz * h,
            s.x1 + ax * h + bx * h, s.y1 + ay * h + by * h, s.z1 + az * h + bz * h,
            s.x1 + ax * h - bx * h, s.y1 + ay * h - by * h, s.z1 + az * h - bz * h,
            s.x0 + ax * h - bx * h, s.y0 + ay * h - by * h, s.z0 + az * h - bz * h);
        quad(
            s.x0 + ax * h + bx * h, s.y0 + ay * h + by * h, s.z0 + az * h + bz * h,
            s.x0 - ax * h + bx * h, s.y0 - ay * h + by * h, s.z0 - az * h + bz * h,
            s.x1 - ax * h + bx * h, s.y1 - ay * h + by * h, s.z1 - az * h + bz * h,
            s.x1 + ax * h + bx * h, s.y1 + ay * h + by * h, s.z1 + az * h + bz * h);
        quad(
            s.x0 - ax * h + bx * h, s.y0 - ay * h + by * h, s.z0 - az * h + bz * h,
            s.x1 - ax * h + bx * h, s.y1 - ay * h + by * h, s.z1 - az * h + bz * h,
            s.x1 - ax * h - bx * h, s.y1 - ay * h - by * h, s.z1 - az * h - bz * h,
            s.x0 - ax * h - bx * h, s.y0 - ay * h - by * h, s.z0 - az * h - bz * h);
        quad(
            s.x0 - ax * h - bx * h, s.y0 - ay * h - by * h, s.z0 - az * h - bz * h,
            s.x0 + ax * h - bx * h, s.y0 + ay * h - by * h, s.z0 + az * h - bz * h,
            s.x1 + ax * h - bx * h, s.y1 + ay * h - by * h, s.z1 + az * h - bz * h,
            s.x1 - ax * h - bx * h, s.y1 - ay * h - by * h, s.z1 - az * h - bz * h);
    }

    /** 输出一块面板的外/内表面三角形（厚度与棱一致，保证边缘严格对齐）。 */
    private static void emitPanelTriangle(int f, boolean outer) {
        int[] face = ICO_FACES[f];
        double[] n = ICO_FACE_NORMALS[f];
        double h = BEAM_THICKNESS * 0.5D;
        double sign = outer ? h : -h;
        for (int k = 0; k < 3; k++) {
            double[] v = ICO_VERTICES[face[k]];
            GL11.glVertex3d(
                v[0] * RADIUS + n[0] * sign,
                v[1] * RADIUS + n[1] * sign,
                v[2] * RADIUS + n[2] * sign);
        }
    }

    /** 输出一块面板的三条侧边（厚度与棱一致，保证边缘严格对齐）。 */
    private static void emitPanelEdges(int f) {
        int[] face = ICO_FACES[f];
        double[] n = ICO_FACE_NORMALS[f];
        double h = BEAM_THICKNESS * 0.5D;
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

    /** 输出面板内部的中点三角线条（加法混合），假定 GL_LINES 已开始。 */
    private static void emitPanelLines(int f, float alpha) {
        int[] face = ICO_FACES[f];
        double[] n = ICO_FACE_NORMALS[f];
        double h = NODE_OUTER_OFFSET;
        double[][] p = new double[3][3];
        for (int k = 0; k < 3; k++) {
            double[] a = ICO_VERTICES[face[k]];
            double[] b = ICO_VERTICES[face[(k + 1) % 3]];
            p[k][0] = (a[0] + b[0]) * 0.5D * RADIUS + n[0] * h;
            p[k][1] = (a[1] + b[1]) * 0.5D * RADIUS + n[1] * h;
            p[k][2] = (a[2] + b[2]) * 0.5D * RADIUS + n[2] * h;
        }
        GL11.glColor4f(0.55F, 0.75F, 1.0F, 0.70F * alpha);
        for (int k = 0; k < 3; k++) {
            GL11.glVertex3d(p[k][0], p[k][1], p[k][2]);
            GL11.glVertex3d(p[(k + 1) % 3][0], p[(k + 1) % 3][1], p[(k + 1) % 3][2]);
        }
    }

    /** 输出面板中心的发光菱形点（加法混合），假定 GL_QUADS 已开始。 */
    private static void emitPanelCoreDot(int f, float alpha) {
        int[] face = ICO_FACES[f];
        double[] n = ICO_FACE_NORMALS[f];
        double h = NODE_OUTER_OFFSET;
        double[] va = ICO_VERTICES[face[0]];
        double[] vb = ICO_VERTICES[face[1]];
        double[] vc = ICO_VERTICES[face[2]];
        double cx = (va[0] + vb[0] + vc[0]) / 3.0D * RADIUS + n[0] * h;
        double cy = (va[1] + vb[1] + vc[1]) / 3.0D * RADIUS + n[1] * h;
        double cz = (va[2] + vb[2] + vc[2]) / 3.0D * RADIUS + n[2] * h;

        double ux = vb[0] - va[0];
        double uy = vb[1] - va[1];
        double uz = vb[2] - va[2];
        double uLen = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= uLen;
        uy /= uLen;
        uz /= uLen;
        double vx = n[1] * uz - n[2] * uy;
        double vy = n[2] * ux - n[0] * uz;
        double vz = n[0] * uy - n[1] * ux;

        double s = 0.45D;
        GL11.glColor4f(0.70F, 0.85F, 1.0F, 0.90F * alpha);
        quad(
            cx + ux * s, cy + uy * s, cz + uz * s,
            cx + vx * s, cy + vy * s, cz + vz * s,
            cx - ux * s, cy - uy * s, cz - uz * s,
            cx - vx * s, cy - vy * s, cz - vz * s);
    }

    /** 节点中心在视线方向上的深度（越大越靠近玩家）。 */
    private static float nodeDepth(int index) {
        double[] v = ICO_VERTICES[index];
        return dotLocal((float) (v[0] * RADIUS), (float) (v[1] * RADIUS), (float) (v[2] * RADIUS));
    }

    /** 节点中心的虚化透明度。 */
    private static float nodeAlpha(int index) {
        return clipAlpha(nodeDepth(index), -FRAME_FRONT_CLIP, FRAME_EDGE_FADE);
    }

    /** 棱中心在视线方向上的深度（两个端点深度平均）。 */
    private static float beamDepth(int edge) {
        int[] e = ICO_EDGES[edge];
        return (nodeDepth(e[0]) + nodeDepth(e[1])) * 0.5F;
    }

    /** 面板中心的虚化透明度。 */
    private static float panelDepth(int f) {
        int[] face = ICO_FACES[f];
        double[] va = ICO_VERTICES[face[0]];
        double[] vb = ICO_VERTICES[face[1]];
        double[] vc = ICO_VERTICES[face[2]];
        double mx = (va[0] + vb[0] + vc[0]) / 3.0D * RADIUS;
        double my = (va[1] + vb[1] + vc[1]) / 3.0D * RADIUS;
        double mz = (va[2] + vb[2] + vc[2]) / 3.0D * RADIUS;
        return dotLocal((float) mx, (float) my, (float) mz);
    }

    /** 面板中心的虚化透明度。 */
    private static float panelAlpha(int f) {
        return clipAlpha(panelDepth(f), -FRAME_FRONT_CLIP, FRAME_EDGE_FADE);
    }

    /** 节点边数：原正二十面体 12 个顶点为五边形，细分新增顶点为六边形。 */
    private static int nodeSides(int index) {
        return index < 12 ? 5 : 6;
    }

    /** 节点正多边形第 k 个角（切平面上）。 */
    private static double[] nodeCorner(int index, int k) {
        return nodeCorner(index, k, 1.0D);
    }

    /** 节点正多边形第 k 个角（切平面上），scale 用于辉光外扩。 */
    private static double[] nodeCorner(int index, int k, double scale) {
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
        double radius = FRAME_THICKNESS / (2.0D * Math.sin(Math.PI / sides)) * scale;
        double a = Math.PI * 2.0D * k / sides;
        double outer = RADIUS + NODE_OUTER_OFFSET;
        return new double[] {
            n[0] * outer + radius * (Math.cos(a) * ux + Math.sin(a) * vx),
            n[1] * outer + radius * (Math.cos(a) * uy + Math.sin(a) * vy),
            n[2] * outer + radius * (Math.cos(a) * uz + Math.sin(a) * vz)
        };
    }

    /** 输出节点的大面积辉光（配合加法混合），假定 GL_TRIANGLES 已开始。 */
    private static void emitNodeGlow(int index) {
        int sides = nodeSides(index);
        double[] c = ICO_VERTICES[index];
        double outer = RADIUS + NODE_OUTER_OFFSET;
        double cx = c[0] * outer;
        double cy = c[1] * outer;
        double cz = c[2] * outer;
        GL11.glColor4f(0.30F, 0.55F, 1.0F, 0.20F * nodeAlpha(index));
        for (int k = 0; k < sides; k++) {
            double[] p0 = nodeCorner(index, k, 0.7D);
            double[] p1 = nodeCorner(index, (k + 1) % sides, 0.7D);
            GL11.glVertex3d(cx, cy, cz);
            GL11.glVertex3d(p0[0], p0[1], p0[2]);
            GL11.glVertex3d(p1[0], p1[1], p1[2]);
        }
    }

    /** 输出节点面板的可见部分（按裁剪面裁边并做顶点级虚化，假定 GL_TRIANGLES 已开始）。 */
    private static void emitNodeFaces(int index) {
        double[] c = ICO_VERTICES[index];
        double outer = RADIUS + NODE_OUTER_OFFSET;
        double cx = c[0] * outer;
        double cy = c[1] * outer;
        double cz = c[2] * outer;
        float centerAlpha = clipAlpha(dotLocal((float) cx, (float) cy, (float) cz), -FRAME_FRONT_CLIP, FRAME_EDGE_FADE);
        if (centerAlpha <= 0.0F) {
            return;
        }

        List<float[]> poly = clippedNodePolygon(index);
        if (poly.size() < 3) {
            return;
        }
        for (int k = 0; k < poly.size(); k++) {
            float[] p0 = poly.get(k);
            float[] p1 = poly.get((k + 1) % poly.size());
            GL11.glColor4f(
                warmMix(0.38F, 0.50F), warmMix(0.52F, 0.52F), warmMix(0.80F, 0.66F),
                1.0F * centerAlpha);
            GL11.glVertex3d(cx, cy, cz);
            GL11.glColor4f(
                warmMix(0.38F, 0.50F), warmMix(0.52F, 0.52F), warmMix(0.80F, 0.66F),
                1.0F * p0[3]);
            GL11.glVertex3d(p0[0], p0[1], p0[2]);
            GL11.glColor4f(
                warmMix(0.38F, 0.50F), warmMix(0.52F, 0.52F), warmMix(0.80F, 0.66F),
                1.0F * p1[3]);
            GL11.glVertex3d(p1[0], p1[1], p1[2]);
        }
    }

    /** 把节点正多边形按前半球裁剪面裁出可见轮廓，返回 {x,y,z,alpha} 点列。 */
    private static List<float[]> clippedNodePolygon(int index) {
        int sides = nodeSides(index);
        double limit = -FRAME_FRONT_CLIP;
        List<float[]> out = new ArrayList<>();
        double[] prev = nodeCorner(index, sides - 1);
        float prevDot = dotLocal((float) prev[0], (float) prev[1], (float) prev[2]);
        for (int k = 0; k < sides; k++) {
            double[] cur = nodeCorner(index, k);
            float curDot = dotLocal((float) cur[0], (float) cur[1], (float) cur[2]);
            boolean prevIn = prevDot >= limit;
            boolean curIn = curDot >= limit;
            if (curIn) {
                if (!prevIn) {
                    out.add(intersectNodePoint(prev, cur, prevDot, curDot, limit));
                }
                out.add(new float[] {
                    (float) cur[0], (float) cur[1], (float) cur[2],
                    clipAlpha(curDot, limit, FRAME_EDGE_FADE)
                });
            } else if (prevIn) {
                out.add(intersectNodePoint(prev, cur, prevDot, curDot, limit));
            }
            prev = cur;
            prevDot = curDot;
        }
        return out;
    }

    /** 线段与裁剪面的交点（交点处 alpha = 0，用于边缘虚化）。 */
    private static float[] intersectNodePoint(double[] a, double[] b, float da, float db, double limit) {
        double t = (da - limit) / (da - db);
        return new float[] {
            (float) (a[0] + (b[0] - a[0]) * t),
            (float) (a[1] + (b[1] - a[1]) * t),
            (float) (a[2] + (b[2] - a[2]) * t),
            0.0F
        };
    }

    /** 输出节点边线（按裁剪面裁边并做顶点级虚化，假定 GL_LINES 已开始）。 */
    private static void emitNodeEdges(int index) {
        int sides = nodeSides(index);
        double limit = -FRAME_FRONT_CLIP;
        for (int k = 0; k < sides; k++) {
            double[] a = nodeCorner(index, k);
            double[] b = nodeCorner(index, (k + 1) % sides);
            float da = dotLocal((float) a[0], (float) a[1], (float) a[2]);
            float db = dotLocal((float) b[0], (float) b[1], (float) b[2]);
            if (da < limit && db < limit) {
                continue;
            }
            float aa = clipAlpha(da, limit, FRAME_EDGE_FADE);
            float ab = clipAlpha(db, limit, FRAME_EDGE_FADE);
            if (da < limit) {
                float[] p = intersectNodePoint(a, b, da, db, limit);
                GL11.glColor4f(warmMix(0.62F, 0.70F), warmMix(0.72F, 0.68F), warmMix(0.92F, 0.78F), 0.0F);
                GL11.glVertex3d(p[0], p[1], p[2]);
                GL11.glColor4f(warmMix(0.62F, 0.70F), warmMix(0.72F, 0.68F), warmMix(0.92F, 0.78F), 0.9F * ab);
                GL11.glVertex3d(b[0], b[1], b[2]);
            } else if (db < limit) {
                float[] p = intersectNodePoint(a, b, da, db, limit);
                GL11.glColor4f(warmMix(0.62F, 0.70F), warmMix(0.72F, 0.68F), warmMix(0.92F, 0.78F), 0.9F * aa);
                GL11.glVertex3d(a[0], a[1], a[2]);
                GL11.glColor4f(warmMix(0.62F, 0.70F), warmMix(0.72F, 0.68F), warmMix(0.92F, 0.78F), 0.0F);
                GL11.glVertex3d(p[0], p[1], p[2]);
            } else {
                GL11.glColor4f(warmMix(0.62F, 0.70F), warmMix(0.72F, 0.68F), warmMix(0.92F, 0.78F), 0.9F * aa);
                GL11.glVertex3d(a[0], a[1], a[2]);
                GL11.glColor4f(warmMix(0.62F, 0.70F), warmMix(0.72F, 0.68F), warmMix(0.92F, 0.78F), 0.9F * ab);
                GL11.glVertex3d(b[0], b[1], b[2]);
            }
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
     * 环带内漂浮的云组件使用径向渐变贴图绘制成柔和光斑，按“远 → 近”排序，
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

                // 镜片法线：严格指向戴森球中心（恒星），用该片实际位置归一化
                double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
                double rx = cx / cl;
                double ry = cy / cl;
                double rz = cz / cl;

                // 长轴：沿环切线方向，投影到镜片平面（与法线严格垂直）
                double tx = -sin * u[0] + cos * v[0];
                double ty = -sin * u[1] + cos * v[1];
                double tz = -sin * u[2] + cos * v[2];
                double tDotR = tx * rx + ty * ry + tz * rz;
                double ax = tx - rx * tDotR;
                double ay = ty - ry * tDotR;
                double az = tz - rz * tDotR;
                double aLen = Math.sqrt(ax * ax + ay * ay + az * az);
                ax /= aLen;
                ay /= aLen;
                az /= aLen;

                // 短轴 = 法线 × 长轴，构成镜片平面
                double bx = ry * az - rz * ay;
                double by = rz * ax - rx * az;
                double bz = rx * ay - ry * ax;

                double len = def[2];
                double width = def[6];
                double spriteScale = 1.35D;

                CloudPiece piece = new CloudPiece();
                piece.cx = cx;
                piece.cy = cy;
                piece.cz = cz;
                // 保存单位轴（长轴沿切线、短轴在镜面平面内），尺寸单独存半径
                piece.tx = ax;
                piece.ty = ay;
                piece.tz = az;
                piece.nx = bx;
                piece.ny = by;
                piece.nz = bz;
                piece.hexR = (float) ((len + width) * 0.5D * spriteScale);
                piece.alpha = 1.0F;
                piece.depth = dotLocal((float) cx, (float) cy, (float) cz);
                pieces.add(piece);
            }
        }

        Collections.sort(pieces, (a, b) -> Float.compare(b.depth, a.depth));
        // 六边形镜片：外圈深蓝 + 内部浅蓝小六边形，不透明，按远近批量绘制
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (CloudPiece piece : pieces) {
            float fade = clipAlpha(piece.depth, -CLOUD_FRONT_CLIP, CLOUD_EDGE_FADE);
            if (fade <= 0.0F) {
                continue;
            }
            float alpha = piece.alpha * fade;
            GL11.glColor4f(
                warmMix(0.10F, 0.20F), warmMix(0.22F, 0.26F), warmMix(0.48F, 0.40F),
                alpha);
            emitCloudHexagon(piece, piece.hexR);
            GL11.glColor4f(
                warmMix(0.55F, 0.80F), warmMix(0.80F, 0.88F), warmMix(1.00F, 0.98F),
                alpha);
            emitCloudHexagon(piece, piece.hexR * 0.55F);
        }
        GL11.glEnd();
    }

    /** 在镜片平面内发射一个正六边形（长轴为 0° 参考方向），假定 GL_TRIANGLES 已开始。 */
    private static void emitCloudHexagon(CloudPiece piece, double radius) {
        for (int k = 0; k < 6; k++) {
            double a0 = Math.PI / 3.0D * k;
            double a1 = Math.PI / 3.0D * (k + 1);
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            GL11.glVertex3d(piece.cx, piece.cy, piece.cz);
            GL11.glVertex3d(
                piece.cx + (c0 * piece.tx + s0 * piece.nx) * radius,
                piece.cy + (c0 * piece.ty + s0 * piece.ny) * radius,
                piece.cz + (c0 * piece.tz + s0 * piece.nz) * radius);
            GL11.glVertex3d(
                piece.cx + (c1 * piece.tx + s1 * piece.nx) * radius,
                piece.cy + (c1 * piece.ty + s1 * piece.ny) * radius,
                piece.cz + (c1 * piece.tz + s1 * piece.nz) * radius);
        }
    }

    /** 单个云组件（含绘制所需几何与深度）。 */
    private static final class CloudPiece {
        double cx, cy, cz;
        double tx, ty, tz;
        double nx, ny, nz;
        float hexR;
        float alpha;
        float depth;
    }

    /** 棱的可见段（裁剪后）与虚化透明度。 */
    private static final class BeamSeg {
        double x0, y0, z0, x1, y1, z1;
        float alpha;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    /** 完工：保留铺满面板的测地线框架外观，在外围叠一圈向外渐隐的幽蓝光晕作为完工标识。 */
    private static void drawCompletedGlow(double animTime) {
        double pulse = 0.5D + 0.5D * Math.sin(animTime * 0.01D);
        float breathe = (float) (0.75D + 0.25D * pulse);
        double inner = RADIUS * 1.04D;
        double outer = RADIUS * 1.6D;
        int layers = 16;
        // 从外到内绘制：外层几乎透明，越靠近球壳越亮，形成柔和的向外渐隐
        for (int i = layers - 1; i >= 0; i--) {
            double t = i / (double) (layers - 1);
            double r = inner + (outer - inner) * t;
            // 二次衰减：光晕紧贴球壳处最亮，向外平滑归零
            float alpha = (float) (0.09D * (1.0D - t) * (1.0D - t) * breathe);
            if (alpha <= 0.001F) {
                continue;
            }
            GL11.glColor4f(0.15F, 0.45F, 0.95F, alpha);
            drawSphereQuads(r, 20 + (int) (t * 8), 10 + (int) (t * 4));
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
