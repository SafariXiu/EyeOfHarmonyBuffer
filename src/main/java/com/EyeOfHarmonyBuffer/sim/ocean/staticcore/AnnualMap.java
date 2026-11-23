package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import java.util.ArrayList;

/**
 * ★ 2026-11-23（平衡解改造）：**一年映射** M(x) 的打包/拆包与推进。
 *
 * <p>为什么需要它：平衡解 = 解 M(x) = x。M 必须是 x 的纯函数 —— 实测已证（BZ 报告）：
 * 删掉「第一年第一季多做一次 Picard」的特判后，同一状态出发两次运行**逐位相同**。
 *
 * <p>状态向量 = {@link StaticOceanSolver#copyPrognostic} 定义的**全部**逐格数组 + ITD/FSD，
 * 按固定顺序打平成一个 double[]（顺序即 pack/unpack 的约定，不能改）。
 */
public final class AnnualMap {
    private AnnualMap() {}

    /** 状态向量长度（对一个给定网格固定）。 */
    public static int size(OceanGrid grid) {
        OceanState a = new OceanState(grid);
        return pack(a).length;
    }

    /** 把状态打平成 double[]（顺序固定）。 */
    public static double[] pack(OceanState st) {
        ArrayList<double[]> parts = new ArrayList<>();
        parts.add(st.t); parts.add(st.s);
        parts.add(st.u); parts.add(st.v); parts.add(st.w);
        parts.add(st.uBaroclinic); parts.add(st.vBaroclinic);
        parts.add(st.uF); parts.add(st.vF); parts.add(st.wF);
        parts.add(st.eta); parts.add(st.surfaceLayer); parts.add(st.mixedLayerDepth);
        parts.add(st.hTOld); parts.add(st.hTNew);
        parts.add(st.iceFraction); parts.add(st.iceThickness); parts.add(st.snowThickness);
        parts.add(st.meltPondArea); parts.add(st.meltPondDepth);   // iceSurfaceTemp 排除：NaN 哨兵，会污染范数
        parts.add(st.iceU); parts.add(st.iceV);
        parts.add(st.sig1); parts.add(st.sig2); parts.add(st.sig12);
        parts.add(st.iceSaltFlux); parts.add(st.snowMeltFlux); parts.add(st.psi);
        for (int c = 0; c < st.itd.area.length; c++) { parts.add(st.itd.area[c]); parts.add(st.itd.volume[c]); parts.add(st.itd.snow[c]); }
        for (int c = 0; c < st.fsd.area.length; c++) parts.add(st.fsd.area[c]);
        int n = 0;
        for (double[] p : parts) n += p.length;
        n += 1;                       // itd.initialized
        n += 1;                       // fsd.initialized
        double[] out = new double[n];
        int o = 0;
        for (double[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
        out[o++] = st.itd.initialized ? 1.0 : 0.0;
        out[o] = st.fsd.initialized ? 1.0 : 0.0;
        return out;
    }

    /** 把 double[] 拆回状态（写入 dst）。 */
    public static void unpack(double[] x, OceanState dst) {
        ArrayList<double[]> parts = new ArrayList<>();
        parts.add(dst.t); parts.add(dst.s);
        parts.add(dst.u); parts.add(dst.v); parts.add(dst.w);
        parts.add(dst.uBaroclinic); parts.add(dst.vBaroclinic);
        parts.add(dst.uF); parts.add(dst.vF); parts.add(dst.wF);
        parts.add(dst.eta); parts.add(dst.surfaceLayer); parts.add(dst.mixedLayerDepth);
        parts.add(dst.hTOld); parts.add(dst.hTNew);
        parts.add(dst.iceFraction); parts.add(dst.iceThickness); parts.add(dst.snowThickness);
        parts.add(dst.meltPondArea); parts.add(dst.meltPondDepth);   // iceSurfaceTemp 排除（同上）
        parts.add(dst.iceU); parts.add(dst.iceV);
        parts.add(dst.sig1); parts.add(dst.sig2); parts.add(dst.sig12);
        parts.add(dst.iceSaltFlux); parts.add(dst.snowMeltFlux); parts.add(dst.psi);
        for (int c = 0; c < dst.itd.area.length; c++) { parts.add(dst.itd.area[c]); parts.add(dst.itd.volume[c]); parts.add(dst.itd.snow[c]); }
        for (int c = 0; c < dst.fsd.area.length; c++) parts.add(dst.fsd.area[c]);
        int o = 0;
        for (double[] p : parts) { System.arraycopy(x, o, p, 0, p.length); o += p.length; }
        dst.itd.initialized = x[o++] != 0.0;
        dst.fsd.initialized = x[o] != 0.0;
    }

    /** 施加一次映射：x --一年--> M(x)。 */
    public static double[] apply(double[] x, OceanGrid grid, int worldSeed, SeaSurfaceForcing f) {
        OceanState seed = new OceanState(grid);
        unpack(x, seed);
        StaticOceanSolver.SEASONAL_YEARS = 1;
        StaticOceanSolver.MAP_SEED = seed;
        StaticOceanSolver.MAP_CAPTURE = new ArrayList<>();
        StaticOceanSolver.solve(grid, worldSeed, f, 0.0, 1, 0, 5000);
        OceanState out = StaticOceanSolver.MAP_CAPTURE.get(0);
        StaticOceanSolver.MAP_SEED = null;
        StaticOceanSolver.MAP_CAPTURE = null;
        return pack(out);
    }

    /** 残差 R(x) = M(x) − x 的无穷范数（只看 T 分量，单位 K/年）。 */
    public static double residualInf(double[] x, double[] mx, int nT) {
        double m = 0;
        for (int i = 0; i < nT; i++) m = Math.max(m, Math.abs(mx[i] - x[i]));
        return m;
    }
}