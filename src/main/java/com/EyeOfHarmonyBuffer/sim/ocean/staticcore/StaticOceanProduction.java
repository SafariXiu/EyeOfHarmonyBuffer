package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing;
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
    private static OceanSolution sol;
    private static int seed = Integer.MIN_VALUE;

    public static synchronized void invalidate() {
        sol = null; seed = Integer.MIN_VALUE;
        Atmosphere.ICE_PROVIDER = null;
    }

    public static synchronized OceanSolution solution(int worldSeed) {
        if (sol == null || seed != worldSeed) {
            // ★ 阶段 4a：求解期间卸下冰提供者（防自反馈），解完再安装。
            Atmosphere.ICE_PROVIDER = null;
            DiagnosticSeaSurfaceForcing f = new DiagnosticSeaSurfaceForcing(worldSeed, 160000, 12500);
            if (COUPLING_ON) {
                AirSeaCoupling.ALPHA = COUPLING_ALPHA;
                AirSeaCoupling.Result cr = AirSeaCoupling.run(worldSeed, 160000, f, 0.0, 0, 5000,
                        Math.max(1, COUPLING_ITERATIONS));
                sol = cr.solution;
            } else {
                sol = StaticOceanSolver.solve(worldSeed, 160000, 0, 0, f, 0.0, 1, 0, 5000);
            }
            seed = worldSeed;
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

    private static int indexZ(OceanGrid g, int z) {
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        return Math.max(1, Math.min(g.ny - 2, (int) Math.floor(zz / g.dy)));
    }
}
