package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/** 生产 3D 静态海洋缓存（B）。 */
public final class StaticOceanProduction {
    private StaticOceanProduction() {}

    public static boolean ON = true;

    /**
     * ★ 2026-11-04（A4）：海气不动点。
     * <p>true 时走 {@link AirSeaCoupling}：首解 → 装 provider → 重解 2 轮（阻尼 α）→ 用最终解装 provider。
     *
     * <p>★ <b>2026-11-04（A6，用户裁决方案 A）：默认翻为 true。</b>证据：
     * <ul>
     *   <li>门（`NCOUPLING`，生产路径 + 带地形）：dSST 0.0280 → <b>0.0012 K</b>、冰翻转 1 → <b>0</b>、
     *       `GATE_COUPLING_FIXPOINT=PASS`（含 `tol=1e-3` 必须 FAIL 的有牙自检）；</li>
     *   <li>影响面（`NCOUPLING2`，同 seed 的 OFF/ON 对照）：T/S/SST 差 ≤0.001 K、冰覆盖与冰厚不变，
     *       **只有 h 诊断改变**（平底 193.5→134.7、带地形 271.7→178.0）；</li>
     *   <li>因此以温度/降水为锚的气候门风险低，h 类判据会动——这是翻默认的已知代价，写在交接文档里。</li>
     * </ul>
     * <p>成本：生产首解从 1 次变成 3 次（72×72 + 地形实测 110 s → 241 s）；结果仍进 {@link OceanSolution} 缓存，
     * 每种子的世界只付一次。
     */
    public static boolean COUPLING_ON = true;
    public static int COUPLING_ITERATIONS = 2;
    /** 耦合阻尼（数值，不是物理常数）。 */
    public static double COUPLING_ALPHA = 0.5;

    /**
     * ★ 2026-11-04（A6 收尾）：生产网格。
     *
     * <p>false（默认）= 72×72 笛卡尔 5°、15 层、平底 1,800 m（`OceanGrid.landV3`）。
     * <p>true = **A′ 的球面 144×72×25（2.5°）+ 真实地形**（`OceanGrid.sphere` + `BATHY_ON`）。
     * <p>⚠ 成本实测：球面 + 地形单次求解 216 s ⟹ 带耦合 3 次 ≈ 650 s（11 min）世界首解，
     * 超架构 §8 的 1–2 min 目标；性能优化是**下一步**，先按用户裁决"把物理做完再优化"。
     */
    public static boolean GRID_SPHERE = true;
    /** 球面网格强制开地形（地形是 A′ 的一部分；关掉它等于回到平底 1,800 m）。 */
    public static boolean SPHERE_BATHY = true;
    /** 生产格子的 cell 尺寸（与探针一致）。 */
    public static final int CELL = 160_000;
    private static OceanSolution sol;
    private static int seed = Integer.MIN_VALUE;

    /**
     * ★★ 2026-11-06：缓存键必须含**影响解的开关**，不能只有 seed。
     *
     * <p>实测（分组跑）：NOHT3D 在构建前把 `BarriclinicFlow.OHT_ON` 置 true，单跑 JVM 时它拿到的是
     * “OHT_ON=true 的解”；而在共享 JVM 里，缓存早已被别的探针用 OHT_ON=false 建好 ⟹ 它**复用了错解**，
     * 于是读数与基线不同。NSTEADY6（BATHY_ON/DEEP_KMAX/INTERIOR_FREEZE_ON）、NTHC5、P484 同理。
     *
     * <p>契约：这一串开关只要有一个变了，就必须重建。新增影响解的开关时，务必加进这里。
     */
    private static long flagsHash;

    private static long solveFlagsHash() {
        long h = 0x5DEECE66DL;
        h = h * 31 + (BaroclinicFlow.OHT_ON ? 1 : 0);
        h = h * 31 + BaroclinicFlow.DEEP_KMAX;
        h = h * 31 + (BarotropicSolver.CURL_PAIRING_FIXED ? 1 : 0);
        h = h * 31 + (OceanGrid.BATHY_ON ? 1 : 0);
        h = h * 31 + (OceanGrid.BATHY_MASK_ON ? 1 : 0);
        h = h * 31 + (AdvectionDiffusionSolver.INTERIOR_FREEZE_ON ? 1 : 0);
        h = h * 31 + (AdvectionDiffusionSolver.INTERIOR_FREEZE_ICE_ON ? 1 : 0);
        h = h * 31 + (AdvectionDiffusionSolver.FREEZE_SURFACE_ON ? 1 : 0);
        h = h * 31 + AdvectionDiffusionSolver.SUBSTEPS;
        h = h * 31 + AdvectionDiffusionSolver.OUTER_MAX;
        h = h * 31 + Double.doubleToLongBits(VelocityProjection.CG_TOL);
        h = h * 31 + (COUPLING_ON ? 1 : 0);
        h = h * 31 + COUPLING_ITERATIONS;
        h = h * 31 + (GRID_SPHERE ? 1 : 0);
        h = h * 31 + (SPHERE_BATHY ? 1 : 0);
        h = h * 31 + CELL;
        return h;
    }

    /**
     * ★★ 2026-11-05（NREPRO 实测缺陷修复）：作废生产解时，**必须连带作废所有派生缓存**。
     *
     * <p>原来的 invalidate() 只清 sol/seed/ICE_PROVIDER。实测（NREPRO）：同一 JVM、同一 seed、
     * 同一份代码，invalidate() 后重建得到的 T/S 全场校验和**与冷启动不同**：
     *   A cold   = 3060A96E4941FB93
     *   B inval  = 3301ACB6402EB7D2   ← DIFFERENT
     *   C 再跑 P296 后 = 03009C6AB7006398
     * ⟹ 解本身不是“只要 seed 相同就唯一”：大气/土壤/植被/气候的 memo 会顺着强迫管道漏进来。
     *
     * <p>把下面这些一并清掉后（NREPRO2），B 与 A **逐位相同**（3060A96E4941FB93 SAME）。
     * ⟹ 这一组清理就是“冷状态”的完整契约；以后新增缓存，必须加进这里，否则又会出现漂移。
     */
    /**
     * ★★ 2026-11-05：只清**派生缓存**、保留生产解。
     *
     * <p>用途：共享解（一个 JVM 跑多个探针）时，每个探针开始前必须把大气/土壤/植被/气候的
     * memo 清干净——它们会顺着强迫管道影响**探针自己算出来的量**（实测：不清时 NPOLAR 的反照率
     * 0.6253→0.5095、NSTEADY6 深层敏感度 0.0010→0.0241 K、NTHC5 判定翻转）。
     * 而生产解本身（`sol`）**保留**，这正是共享首解省下的那 6 分钟。
     */
    public static synchronized void clearDerivedCaches() {
        Atmosphere.ICE_PROVIDER = null;
        WeatherDerivedCaches.clear();
    }

    private static final class WeatherDerivedCaches {
        static void clear() {
            com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.invalidate();
            com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.invalidate();
            com.EyeOfHarmonyBuffer.sim.atmos.Vegetation.invalidate();
            com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM.resetOhtCache();
            com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.resetCache();
            com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache();
            com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.clearWmtMemo();
        }
    }

    public static synchronized void invalidate() {
        sol = null; seed = Integer.MIN_VALUE; flagsHash = 0;
        Atmosphere.ICE_PROVIDER = null;
        com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.invalidate();
        com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.invalidate();
        com.EyeOfHarmonyBuffer.sim.atmos.Vegetation.invalidate();
        com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM.resetOhtCache();
        com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.resetCache();
        com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache();
        com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.clearWmtMemo();
    }

    public static synchronized OceanSolution solution(int worldSeed) {
        // ★ 2026-11-06：先归一化由 SPHERE_BATHY 派生的 OceanGrid.BATHY_ON，再算开关指纹。
        //   否则第一次调用（BATHY_ON 还是别人留下的旧值）与构建内部设好的值不一致 ⟹
        //   指纹变化 ⟹ 第二次调用又重建一次（实测多花一整次 400 s）。
        OceanGrid.BATHY_ON = SPHERE_BATHY;
        long fh = solveFlagsHash();
        if (sol == null || seed != worldSeed || flagsHash != fh) {
            // ★ 阶段 4a：求解期间卸下冰提供者（防自反馈），解完再安装。
            Atmosphere.ICE_PROVIDER = null;
            DiagnosticSeaSurfaceForcing raw = new DiagnosticSeaSurfaceForcing(worldSeed, CELL, 12500);
            OceanGrid grid;
            SeaSurfaceForcing f;
            if (GRID_SPHERE) {
                // A′：全球球面 144×72×25 + 地形。BATHY_ON 在造网格前设好（layersFor 读它）。
                OceanGrid.BATHY_ON = SPHERE_BATHY;
                grid = OceanGrid.sphere(worldSeed, CELL);
                f = new SphereSeaSurfaceForcing(raw, OceanGrid.HEIGHT_SPHERE_M);
            } else {
                grid = OceanGrid.landV3(worldSeed, CELL, 0, 0);
                f = raw;
            }
            if (COUPLING_ON) {
                AirSeaCoupling.ALPHA = COUPLING_ALPHA;
                AirSeaCoupling.Result cr = AirSeaCoupling.run(grid, worldSeed, f, 0.0, 0, 5000,
                        Math.max(1, COUPLING_ITERATIONS));
                sol = cr.solution;
            } else {
                sol = StaticOceanSolver.solve(grid, worldSeed, f, 0.0, 1, 0, 5000);
            }
            seed = worldSeed;
            flagsHash = fh;      // ★ 2026-11-06：缓存键含开关指纹
            final OceanSolution s = sol;
            Atmosphere.ICE_PROVIDER = new Atmosphere.IceProvider() {
                @Override public double iceFraction(int x, int z) { return iceAt(s, x, z, false); }
                @Override public double iceThickness(int x, int z) { return iceAt(s, x, z, true); }
            };
        }
        return sol;
    }

    private static double iceAt(OceanSolution s, int x, int z, boolean thickness) {
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return 0.0;
        int js = j * g.nx + i;
        return thickness ? s.state.iceThickness[js] : s.state.iceFraction[js];
    }

    /**
     * ★ 2026-11-05（P484 判据要问“这里有 3D 水吗”）：该 (x,z) 是否落在 3D 网格的湿格上。
     *
     * <p>为什么要问：chunk 路径的冰是【大气最冷月温度】判据，它对陆地/无 3D 水的格点照样给冰；
     * 而 3D 冰只在湿格里存在（干格 `iceAt` 恒 0）。比较两者前必须先排除“3D 无水的格点”，
     * 否则量到的是**定义域差异**，不是物理矛盾。
     */
    /** ★ 2026-11-05：3D 解的表层流速 (u,v)，供 /talosmap 的洋流箭头用。无 3D 水返回 NaN。 */
    public static synchronized double atU(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return Double.NaN;
        return s.state.u[s.state.idx(i, j, 0)];
    }

    public static synchronized double atV(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return Double.NaN;
        return s.state.v[s.state.idx(i, j, 0)];
    }

    public static synchronized boolean wetAt(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        return g.isWet(indexX(g, x), indexZ(g, z));
    }

    public static synchronized double atT(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return Double.NaN;
        return s.surfaceT[j * g.nx + i];
    }

    public static synchronized double atS(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return Double.NaN;
        return s.state.s[s.state.idx(i, j, 0)];
    }

    public static double atAnomaly(int x, int z, int worldSeed) {
        double t = atT(x, z, worldSeed);
        if (!Double.isFinite(t)) return 0.0;
        int zz = z;
        return t - Atmosphere.seaSurfaceTemp(WorldContract.latOf(zz), 0.0);
    }

    private static int indexX(OceanGrid g, int x) {
        double xx = ((x % (long) OceanGrid.WIDTH_M) + OceanGrid.WIDTH_M) % OceanGrid.WIDTH_M;
        return Math.max(0, Math.min(g.nx - 1, (int) Math.floor(xx / g.dx)));
    }

    /** 世界 z → 行号。★ 2026-11-04（A6）：球面网格的 y 是纬度轴，必须先还原纬度。 */
    private static int indexZ(OceanGrid g, int z) {
        if (g.sphere) {
            double latDeg = Math.toDegrees(WorldContract.latOf(z));
            int j = (int) Math.floor((90.0 - latDeg) / 180.0 * g.ny);
            return Math.max(0, Math.min(g.ny - 1, j));
        }
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        return Math.max(1, Math.min(g.ny - 2, (int) Math.floor(zz / g.dy)));
    }
}
