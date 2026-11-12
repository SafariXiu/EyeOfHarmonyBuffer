package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.PlanetMetric;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 静态气候解的基础网格：WIDTH_LAPS x 40,000 km（1 圈 = 40,000）、72 x 72 x 15。
 * x 周期、y 墙、LandV3 mask；不依赖 MITgcm。
 */
public final class OceanGrid {

    /**
     * ★ 2026-11-03（阶段 0.5）：144 -> 72。宽度从 2 圈（80,000 km）改回 1 圈（40,000 km）后，
     * 保持格距 555.6 km（5°）不变，必须同时把列数减半——否则 dx 会变成 277.8 km，
     * 而 {@code BarotropicSolver} 的 Laplacian/beta 离散假定 dx=dy，会引入各向异性误差。
     * <p>分辨率与 v7 相同（5°），变的只有【域宽】与【旋度配对】两项，便于 A/B。
     */
    public static final int NX = 72;
    public static final int NY = 72;
    public static final int NZ = 15;
    /**
     * 口径：x 方向跨几圈赤道。
     * <p>★ 2026-11-03（阶段 0.5）：2.0 -> 1.0。旧值来自 MITgcm 80k 的
     * {@code delX=144*555555.5556}，使海洋箱宽 = 2 圈赤道（80,000 km），
     * 与大气/陆地/降水（40,000 km）不一致；同一块大陆会被复制两遍。
     * <p>1.0 = 行星周长 40,000 km。格距随之变成 dx=277.8 km（2.5°）、dy=555.6 km（5°）——
     * <b>各向异性是过渡态</b>，区域 1° 架构（3D海洋-无限世界架构.md §3）会重新定义。
     */
    public static final double WIDTH_LAPS = 1.0;
    public static final double WIDTH_M = WIDTH_LAPS * PlanetMetric.CIRCUMFERENCE_M;   // = 80,000,000（逐位不变）
    public static final double HEIGHT_M = WorldContract.Z_CYCLE;                      // = 40,000,000（一整条经圈）
    public static final double[] DZ = {
        50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190
    };

    /**
     * ★ 2026-11-04（阶段 3）：是否启用海底地形。
     *
     * <p>false（默认）= 平底 1,800 m、15 层，与阶段 2 逐位相同。
     * <p>true = 每列从 LandV3 的负高程取水深，换 25 层（{@link #DZ_BATHY}），
     * 海底以下的层置干（{@link #wetLayers}）。
     * <p>为什么必须做：`NBATHY` 实测洋底深度中位 4,540 m，平底 1,800 m 把 84–91% 的
     * 洋柱截到不足一半——体积、热容量、MOC 深度全被低估。
     */
    public static boolean BATHY_ON = false;

    /**
     * 阶段 3 对照开关：true（默认）= 用 LandV3 水深截断洋柱；
     * false = 25 层**全柱满深**（只改层数/深度，不改地形）——用来把
     * “深度标度”与“地形掩膜”两项分开。
     */
    public static boolean BATHY_MASK_ON = true;

    /**
     * 25 层：0–1,900 m（现有 15 层，表层 50 m）+ 150…375 m 的 10 层（2,625 m）⟹ 总深 4,525 m。
     * <p>深层厚度按 150→375 m 渐增；这是<b>结构选择</b>（P3c），不是拟合参数。
     */
    public static final double[] DZ_BATHY = {
        50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190,
        150, 175, 200, 225, 250, 275, 300, 325, 350, 375
    };

    /**
     * ★ 2026-11-04（A0）：球面模式。false（默认）= 旧笛卡尔箱，逐位不变。
     * <p>true 时由 {@link #sphere(int,int)} 构造：y = 0–20,000 km 极到极、极点干、
     * 纬度由 {@link #latAt(int)} 直接给出（不再走 40,000 km 三角波）。
     */
    public static boolean SPHERE_ON = false;

    /** 球面域的高（m）：极到极 = πR = 20,000 km。 */
    public static final double HEIGHT_SPHERE_M = 20_000_000.0;

    public final int nx, ny, nz;
    public final double dx, dy;
    public final double[] dz;
    /** 各层【格心】深度（m，从海面算起）；供分层扩散率等使用。 */
    public final double[] zCenter;
    public final boolean[] wet;
    /**
     * 每列的有效层数（阶段 3）：湿层从 k=0 连续到 wetLayers-1，海底以下为干。
     * <p>BATHY_ON=false 时恒为 nz（老行为）。
     */
    public final int[] wetLayers;
    /** 是否球面网格（A0）；false = 旧笛卡尔箱。 */
    public final boolean sphere;
    public final int x0, z0;

    private OceanGrid(int nx, int ny, int nz, double dx, double dy,
                      double[] dz, boolean[] wet, int[] wetLayers, int x0, int z0) {
        this(nx, ny, nz, dx, dy, dz, wet, wetLayers, x0, z0, false);
    }

    /**
     * 探针/落盘恢复：用同一组参数重建网格（不做地形采样，也不改任何既有路径）。
     * <p>为什么要它：生产解每次 JVM 重算 ~11 min，而探针只需要同一个网格 + 状态。
     */
    public static OceanGrid restore(int nx, int ny, int nz, double dx, double dy, double[] dz,
                                    boolean[] wet, int[] wetLayers, int x0, int z0, boolean sphere) {
        return new OceanGrid(nx, ny, nz, dx, dy, dz, wet, wetLayers, x0, z0, sphere);
    }

    private OceanGrid(int nx, int ny, int nz, double dx, double dy,
                      double[] dz, boolean[] wet, int[] wetLayers, int x0, int z0,
                      boolean sphere) {
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.wet = wet; this.wetLayers = wetLayers; this.x0 = x0; this.z0 = z0;
        this.sphere = sphere;
        this.zCenter = new double[nz];
        double zc = 0.0;
        for (int k = 0; k < nz; k++) { zCenter[k] = zc + 0.5 * dz[k]; zc += dz[k]; }
    }

    /**
     * 阶段 3：按 LandV3 的负高程算每列有效层数（整层法；partial cell 以后再做）。
     * <p>BATHY_ON=false 时直接返回总层数 ⟹ 与阶段 2 逐位相同。
     */
    private static int layersFor(long seed, int cell, int x, int z, double[] dz) {
        if (!BATHY_ON || !BATHY_MASK_ON) return dz.length;
        double depth = -PlateField.elevationWithCell(x, z, seed, cell);
        if (!(depth > 0.0)) return 0;
        double cum = 0.0;
        int n = 0;
        for (int k = 0; k < dz.length; k++) {
            cum += dz[k];
            if (cum <= depth) n++; else break;
        }
        return n;
    }

    public static OceanGrid landV3(int worldSeed, int cell, int x0, int z0) {
        long seed = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(worldSeed);
        double dx = WIDTH_M / NX;
        double dy = HEIGHT_M / NY;
        boolean[] wet = new boolean[NX * NY];
        int[] wl = new int[NX * NY];
        double[] dz = BATHY_ON ? DZ_BATHY : DZ;
        for (int j = 0; j < NY; j++) {
            double z = z0 + (j + 0.5) * dy;
            for (int i = 0; i < NX; i++) {
                double x = x0 + (i + 0.5) * dx;
                x = x % WIDTH_M;
                if (x < 0) x += WIDTH_M;
                boolean wall = (j == 0 || j == NY - 1);
                boolean land = PlateField.isLandWithCell(
                    (int) Math.round(x), (int) Math.round(z), seed, cell);
                int n = (wall || land) ? 0 : layersFor(seed, cell, (int) Math.round(x), (int) Math.round(z), dz);
                wl[j * NX + i] = n;
                wet[j * NX + i] = n > 0;
            }
        }
        return new OceanGrid(NX, NY, dz.length, dx, dy, dz, wet, wl, x0, z0);
    }

    /**
     * ★ 2026-11-03（阶段 2）：任意域 patch（无限世界架构 §2）。
     *
     * <p>与 {@link #landV3} 的唯一区别：<b>不做 x 折叠</b>——世界无限、海陆不周期，
     * patch 必须看到自己位置的真实 LandV3。y 两端暂用墙；sponge 边界在阶段 2b 接入
     * （届时墙只保留在真海岸）。
     */
    public static OceanGrid patch(int worldSeed, int cell, int x0, int z0,
                                  int nx, int ny, double widthM, double heightM) {
        return patch(worldSeed, cell, x0, z0, nx, ny, widthM, heightM, false);
    }

    /**
     * ★ 2026-11-04（阶段 2c）：{@code openY=true} 时 y 两端【不设墙】，保留为湿的 ghost 行：
     * 由 sponge 钉到父、由父 psi 规定穿过边界的输运。真海岸仍然按 LandV3 判干。
     */
    public static OceanGrid patch(int worldSeed, int cell, int x0, int z0,
                                  int nx, int ny, double widthM, double heightM, boolean openY) {
        long seed = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(worldSeed);
        double dx = widthM / nx, dy = heightM / ny;
        boolean[] wet = new boolean[nx * ny];
        int[] wl = new int[nx * ny];
        double[] dz = BATHY_ON ? DZ_BATHY : DZ;
        for (int j = 0; j < ny; j++) {
            double z = z0 + (j + 0.5) * dy;
            for (int i = 0; i < nx; i++) {
                double x = x0 + (i + 0.5) * dx;
                boolean wall = !openY && (j == 0 || j == ny - 1);
                boolean land = PlateField.isLandWithCell(
                    (int) Math.round(x), (int) Math.round(z), seed, cell);
                int n = (wall || land) ? 0 : layersFor(seed, cell, (int) Math.round(x), (int) Math.round(z), dz);
                wl[j * nx + i] = n;
                wet[j * nx + i] = n > 0;
            }
        }
        return new OceanGrid(nx, ny, dz.length, dx, dy, dz, wet, wl, x0, z0);
    }

    /** 测试用：y 墙，其余全湿。 */
    public static OceanGrid allWet(int x0, int z0) {
        boolean[] wet = new boolean[NX * NY];
        int[] wl = new int[NX * NY];
        for (int j = 1; j < NY - 1; j++) {
            for (int i = 0; i < NX; i++) { wet[j * NX + i] = true; wl[j * NX + i] = NZ; }
        }
        return new OceanGrid(NX, NY, NZ, WIDTH_M / NX, HEIGHT_M / NY, DZ, wet, wl, x0, z0);
    }

    public int idx(int i, int j) {
        int ii = i % nx;
        if (ii < 0) ii += nx;
        return j * nx + ii;
    }

    public boolean isWet(int i, int j) {
        if (j < 0 || j >= ny) return false;
        // ★ 2026-11-05（性能，逐位不变）：绝大多数调用点的 i 已在 [0,nx) ⟹ 走免取模快路径。
        //   JFR 复采（优化后）里 isWet 仍占 19.2%，主要成本就是 idx() 的 i % nx（负数取模）。
        if (i >= 0 && i < nx) return wet[j * nx + i];
        int ii = i % nx;
        if (ii < 0) ii += nx;
        return wet[j * nx + ii];
    }

    /**
     * ★ 2026-11-05（性能，逐位不变）：调用者**保证** 0 <= i < nx 时的湿判据。
     *
     * <p>为什么单开一个：`isWet` 内部走 `idx()`，那里有 `i % nx`（**负数取模**）。
     * JFR 调用链实测 `isWet` 占 **34.9%** 的样本（applyL 20.9% + 平流 10.0% + 自由面 3.1%），
     * 而热循环里的 i 本来就落在 [0,nx) ⟹ 取模是纯浪费。
     * <p>判据与 `isWet` 完全一致（同一张 wet 表、同一套 j 边界规则），只是省掉环绕。
     */
    public boolean isWetRaw(int i, int j) {
        return USE_WETRAW ? (j >= 0 && j < ny && wet[j * nx + i]) : isWet(i, j);
    }

    /** ★ 2026-11-05：A/B 开关（-Deoh.wetraw=false 关掉快路径），默认开。 */
    public static boolean USE_WETRAW = Boolean.parseBoolean(System.getProperty("eoh.wetraw", "true"));

    /** 阶段 3：3D 湿判据（k >= wetLayers 的层是海底以下的干格）。 */
    public boolean isWet(int i, int j, int k) {
        if (j < 0 || j >= ny || k < 0 || k >= nz) return false;
        return k < wetLayers[idx(i, j)];
    }

    /** 该列的有效层数（0 = 陆地/墙）。 */
    public int wetLayers(int i, int j) {
        if (j < 0 || j >= ny) return 0;
        return wetLayers[idx(i, j)];
    }

    /** 该列的洋底深度（m）= 湿层厚度之和。 */
    public double columnDepthM(int i, int j) {
        int n = wetLayers(i, j);
        double d = 0.0;
        for (int k = 0; k < n; k++) d += dz[k];
        return d;
    }

    public double xOf(int i) { return x0 + (i + 0.5) * dx; }
    public double zOf(int j) { return z0 + (j + 0.5) * dy; }

    /**
     * 行 j 的纬度（rad）。
     * <p>球面：+90°（z=0，北极）→ −90°（z=20,000 km，南极），单调；
     * <p>笛卡尔：沿用 {@link WorldContract#latOf(int)}（与旧路径逐位相同）。
     */
    public double latAt(int j) {
        if (sphere) return Math.PI / 2.0 - (zOf(j) / HEIGHT_SPHERE_M) * Math.PI;
        return WorldContract.latOf((int) Math.round(zOf(j)));
    }

    /** cos(纬度)：球面面积/纬向长度权重。极点附近 → 0。 */
    public double cosLat(int j) { return Math.cos(latAt(j)); }

    /** 该列单元的真实面积（m²）：球面 = dx*dy*cos(lat)。 */
    public double cellAreaM2(int j) { return dx * dy * cosLat(j); }

    /**
     * ★ 2026-11-04（A0）：全球球面网格。
     * <p>y = 0–20,000 km（北极 → 南极），x = 40,000 km 周期；极点两行干。
     * <p>陆地/水深在对应的真实纬度上采样（用 {@link WorldContract#zOfLat(double)} 把
     * 纬度映射回世界 z），所以海陆与大气/陆地场一致。
     */
    /**
     * ★ 2026-11-08（分辨率对照）：球面网格的纬向/经向格数。
     * 默认 144x72 与历史逐位一致；改这两个值只用于分辨率实验（缓存键会变）。
     */
    public static int SPHERE_NX = 144;
    public static int SPHERE_NY = 72;

    public static OceanGrid sphere(int worldSeed, int cell) {
        long seed = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(worldSeed);
        final int nxs = SPHERE_NX, nys = SPHERE_NY;
        double dx = WIDTH_M / nxs;
        double dy = HEIGHT_SPHERE_M / nys;
        boolean[] wet = new boolean[nxs * nys];
        int[] wl = new int[nxs * nys];
        double[] dz = BATHY_ON ? DZ_BATHY : DZ;
        for (int j = 0; j < nys; j++) {
            double z = (j + 0.5) * dy;
            double lat = Math.PI / 2.0 - (z / HEIGHT_SPHERE_M) * Math.PI;
            double latDeg = Math.toDegrees(lat);
            int zLand = WorldContract.zOfLat(latDeg);
            for (int i = 0; i < nxs; i++) {
                double x = (i + 0.5) * dx;
                boolean pole = (j == 0 || j == nys - 1);
                boolean land = PlateField.isLandWithCell((int) Math.round(x), zLand, seed, cell);
                int n = (pole || land) ? 0 : layersFor(seed, cell, (int) Math.round(x), zLand, dz);
                wl[j * nxs + i] = n;
                wet[j * nxs + i] = n > 0;
            }
        }
        return new OceanGrid(nxs, nys, dz.length, dx, dy, dz, wet, wl, 0, 0, true);
    }

    public int wetCount() {
        int n = 0;
        for (boolean b : wet) if (b) n++;
        return n;
    }

    @Override public String toString() {
        return "OceanGrid(" + nx + "x" + ny + "x" + nz
            + ", " + (WIDTH_M / 1000) + "x" + (HEIGHT_M / 1000) + " km"
            + ", wet=" + wetCount() + ")";
    }
}
