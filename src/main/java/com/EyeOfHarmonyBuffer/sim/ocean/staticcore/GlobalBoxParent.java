package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * 用一次全球粗解（{@link OceanSolution}）当父状态。
 *
 * <p>这是架构里的 "A" 级：40,000 km 周期箱、5° 网格。它只用于【边界】，
 * 不参与 patch 内部；x 方向按父网格周期折叠，z 方向夹边（父的 z 覆盖一整个
 * 纬度循环，0 -> N -> 0 -> S -> 0）。
 *
 * <p>插值：双线性（x 周期、z 夹边），层取最近 k。任一角是陆地则跳过该角；
 * 四角全干返回 NaN。
 */
public final class GlobalBoxParent implements OceanParent {

    private final OceanSolution sol;
    private final OceanGrid g;

    public GlobalBoxParent(OceanSolution sol) {
        if (sol == null || sol.state == null) throw new IllegalArgumentException("null solution");
        this.sol = sol;
        this.g = sol.state.grid;
    }

    @Override public boolean covers(double x, double z) { return true; }

    private int ix(double x) {
        double f = (x - g.x0) / g.dx - 0.5;
        int i = (int) Math.floor(f) % g.nx;
        return i < 0 ? i + g.nx : i;
    }

    private double fx(double x) {
        double f = (x - g.x0) / g.dx - 0.5;
        return f - Math.floor(f);
    }

    /**
     * 世界 z → 父网格的连续行号。
     *
     * <p>★ 2026-11-24（S0.2）：**球面父解的 y 轴是「极点→极点」**（20,000 km，2.5°/行），
     * 而笛卡尔箱的 y 轴才是世界 z（一整圈，5°/行）。原实现无条件用
     * {@code (z - z0)/dy - 0.5} ⟹ 只在笛卡尔箱上成立；生产切到球面父解后，
     * 目标纬度全错（实测中位差 58°、最大 90°；赤道处拿到南极的水，SST 差 40 K）。
     *
     * <p>球面：先由 {@link WorldContract#latOf(int)} 还原纬度，再换算行号
     * （与 {@code StaticOceanProduction.indexZ} 的口径**逐字一致**）。
     */
    private double rowF(double z) { return rowFor(g, z); }

    /**
     * 世界 z → 父网格连续行号（**生产口径**，供 S0 的门直接调用）。
     * <p>球面：先还原纬度再换算；笛卡尔箱：裸 z（原行为，逐位不变）。
     */
    public static double rowFor(OceanGrid g, double z) {
        if (g.sphere) {
            double latDeg = Math.toDegrees(WorldContract.latOf((int) Math.round(z)));
            return (90.0 - latDeg) / 180.0 * g.ny - 0.5;
        }
        return (z - g.z0) / g.dy - 0.5;
    }

    private int jz(double z) {
        int j = (int) Math.floor(rowF(z));
        // S0.2b：允许到 ny-1（极点行）。原实现钳到 ny-2 是为了让 j+1 合法，但那会在极点
        // 强制跨行插值 ⟹ 极点被映射到 -86.25°（误差 3.75°，超过 1 行）。钳到 ny-1 后，
        // fz 在 f>=ny-1 时返回 0，只用极点行本身（误差 <= 1.25°）。
        return Math.max(0, Math.min(g.ny - 1, j));
    }

    private double fz(double z) {
        double f = rowF(z);
        if (f <= 0.0) return 0.0;
        if (f >= g.ny - 1) return 0.0;
        return f - Math.floor(f);
    }

    private double bilinear(double[] fld, double x, double z, int k) {
        int i0 = ix(x), i1 = (i0 + 1) % g.nx;
        int j0 = jz(z), j1 = Math.min(g.ny - 1, j0 + 1);
        double wx = fx(x), wz = fz(z);
        int q00 = (k * g.ny + j0) * g.nx + i0;
        int q10 = (k * g.ny + j0) * g.nx + i1;
        int q01 = (k * g.ny + j1) * g.nx + i0;
        int q11 = (k * g.ny + j1) * g.nx + i1;
        boolean w00 = g.isWet(i0, j0), w10 = g.isWet(i1, j0);
        boolean w01 = g.isWet(i0, j1), w11 = g.isWet(i1, j1);
        double sum = 0.0, wsum = 0.0;
        if (w00) { double w = (1 - wx) * (1 - wz); sum += w * fld[q00]; wsum += w; }
        if (w10) { double w = wx * (1 - wz); sum += w * fld[q10]; wsum += w; }
        if (w01) { double w = (1 - wx) * wz; sum += w * fld[q01]; wsum += w; }
        if (w11) { double w = wx * wz; sum += w * fld[q11]; wsum += w; }
        return wsum > 0.0 ? sum / wsum : Double.NaN;
    }

    @Override public double temperatureK(double x, double z, int k) {
        return bilinear(sol.state.t, x, z, clampK(k));
    }

    @Override public double salinityPsu(double x, double z, int k) {
        return bilinear(sol.state.s, x, z, clampK(k));
    }

    @Override public double uMs(double x, double z, int k) {
        return bilinear(sol.state.u, x, z, clampK(k));
    }

    @Override public double vMs(double x, double z, int k) {
        return bilinear(sol.state.v, x, z, clampK(k));
    }

    /** 正压 psi 的双线性采样（2D；湿角参与，全干返回 NaN）。 */
    @Override public double psi(double x, double z) {
        int i0 = ix(x), i1 = (i0 + 1) % g.nx;
        int j0 = jz(z), j1 = Math.min(g.ny - 1, j0 + 1);
        double wx = fx(x), wz = fz(z);
        double[] p = sol.state.psi;
        boolean w00 = g.isWet(i0, j0), w10 = g.isWet(i1, j0);
        boolean w01 = g.isWet(i0, j1), w11 = g.isWet(i1, j1);
        double sum = 0.0, wsum = 0.0;
        if (w00) { double w = (1 - wx) * (1 - wz); sum += w * p[j0 * g.nx + i0]; wsum += w; }
        if (w10) { double w = wx * (1 - wz); sum += w * p[j0 * g.nx + i1]; wsum += w; }
        if (w01) { double w = (1 - wx) * wz; sum += w * p[j1 * g.nx + i0]; wsum += w; }
        if (w11) { double w = wx * wz; sum += w * p[j1 * g.nx + i1]; wsum += w; }
        return wsum > 0.0 ? sum / wsum : Double.NaN;
    }

    @Override public double iceFraction(double x, double z) {
        int i = ix(x), j = jz(z);
        return g.isWet(i, j) ? sol.state.iceFraction[j * g.nx + i] : 0.0;
    }

    @Override public double iceThicknessM(double x, double z) {
        int i = ix(x), j = jz(z);
        return g.isWet(i, j) ? sol.state.iceThickness[j * g.nx + i] : 0.0;
    }

    private int clampK(int k) { return Math.max(0, Math.min(g.nz - 1, k)); }
}
