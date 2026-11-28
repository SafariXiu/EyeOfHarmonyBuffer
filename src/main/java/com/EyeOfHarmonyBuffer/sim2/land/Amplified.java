package com.EyeOfHarmonyBuffer.sim2.land;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion;

/**
 * 放大层（Amplification）—— 对应 Cortial et al. 2019 §5。
 *
 * <p>论文的结构是：
 * <pre>
 *   Crust C  (构造模拟输出, ~50 km)  →  Amplification  →  T  (最终地形)
 * </pre>
 *
 * <p>论文用归档的 SRTM 90 m 真实地形块作为基元；这里用我们自己的 LEM
 * （{@code TalosLandErosion}，stream power + 河流）做同样的工作：把粗地形演化出
 * 分叉山脊、树枝状水系与坡面。
 *
 * <p>与 {@link TectonicSolver} 同构：瓦片化、确定性、有缓存。区别是
 * {@code TectonicSolver} 在<b>构造</b>层积分，而本类在<b>侵蚀</b>层放大。
 *
 * <p><b>成本</b>：1 瓦片（768 格 @1 km / 200 迭代）≈ 100 s。只能离线预热或分级使用，
 * 不能在区块加载路径上调用。
 */
public final class Amplified {

    private Amplified() {}

    /** 总开关（默认关：开启后每次未命中瓦片约 100 s）。 */
    public static boolean ENABLED = false;

    /** 传给 LEM 的求解参数（放大层可按需降级）。 */
    public static int SOLVE_CELLS = 768;
    public static int ITER = 200;

    private static int installedFor = Integer.MIN_VALUE;

    /** 绑定种子（幂等）。 */
    public static void install(long worldSeed) {
        int s = (int) worldSeed;
        if (installedFor == s) return;
        TalosLandErosion.install(s);
        installedFor = s;
    }

    private static void applyConfig() {
        // REQUIRED: without this the LEM falls back to the legacy TalosLandField and we
        // would be amplifying the OLD terrain, silently ignoring the tectonic model.
        if (!LandV3.ENABLED) LandV3.ENABLED = true;
        if (TalosLandErosion.SOLVE_CELLS != SOLVE_CELLS) TalosLandErosion.SOLVE_CELLS = SOLVE_CELLS;
        if (TalosLandErosion.ITER != ITER) TalosLandErosion.ITER = ITER;
    }

    /**
     * 最终地形高度（米）。未开启时直接返回粗地形，保证逐位不变。
     */
    public static double height(long ws, double x, double z) {
        if (!ENABLED) return LandV3.height(ws, x, z);
        install(ws);
        applyConfig();
        return TalosLandErosion.height(ws, x, z);
    }

    /** 同上，返回千米。 */
    public static double heightKm(long ws, double x, double z) {
        return height(ws, x, z) / 1000.0;
    }

    /** 预先求解以 (x,z) 为中心的瓦片（后台预热用）。 */
    public static void warm(long ws, double x, double z) {
        if (!ENABLED) return;
        install(ws);
        applyConfig();
        TalosLandErosion.height(ws, x, z);
    }

    /** 已解瓦片数与累计耗时（毫秒），用于性能观测。 */
    public static long tiles() { return TalosLandErosion.tilesSolved(); }
    public static long solveMs() { return TalosLandErosion.solveNanos() / 1_000_000L; }
    public static int cached() { return TalosLandErosion.cachedTiles(); }
}
