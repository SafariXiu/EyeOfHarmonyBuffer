package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

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

    private int jz(double z) {
        double f = (z - g.z0) / g.dy - 0.5;
        int j = (int) Math.floor(f);
        return Math.max(0, Math.min(g.ny - 2, j));
    }

    private double fz(double z) {
        double f = (z - g.z0) / g.dy - 0.5;
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

    private int clampK(int k) { return Math.max(0, Math.min(g.nz - 1, k)); }
}
