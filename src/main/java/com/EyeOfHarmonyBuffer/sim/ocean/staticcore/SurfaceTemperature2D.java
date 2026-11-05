package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.ClimberOcean;
import com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;
import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * 2D 表层温度 + 局地热输送（B 骨架）。
 *
 * <p>稳态：u dT/dx + v dT/dz = Qnet/(rho cp H)。
 * 平流用半拉格朗日；Qnet 用 DiagnosticSeaSurfaceForcing。
 * 输出 Q_OHT = rho cp H (u dT/dx + v dT/dz) 的逐点残差，供验收。
 */
public final class SurfaceTemperature2D {
    private SurfaceTemperature2D() {}

    public static final double DT = 86400.0;

    public static final class Result {
        public final OceanGrid grid;
        public final OceanState state;
        public final double residual, tMin, tMax, qOhtMax, qOhtMean;
        Result(OceanGrid g, OceanState st, double res, double mn, double mx, double qm, double qa) {
            grid = g; state = st; residual = res; tMin = mn; tMax = mx; qOhtMax = qm; qOhtMean = qa;
        }
    }

    public static Result solveRegion(int worldSeed, int cell, int x0, int z0,
                                     double theta, int maxIter) {
        OceanGrid g = OceanGrid.landV3(worldSeed, cell, x0, z0);
        OceanState st = new OceanState(g);
        DiagnosticSeaSurfaceForcing f = new DiagnosticSeaSurfaceForcing(worldSeed, cell, 12500);
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = st.idx(i, j, 0);
                st.t[q] = Atmosphere.seaSurfaceTemp(WorldContract.latOf((int) Math.round(g.zOf(j))), 0.0);
                st.s[q] = 35.0;
                st.surfaceLayer[j * g.nx + i] = ClimberOcean.H_MIXED;
            }
        BarotropicSolver.Result baro = BarotropicSolver.solve(g, f, theta);
        for (int q = 0; q < st.u.length; q++) { st.u[q] = baro.state.u[q]; st.v[q] = baro.state.v[q]; }

        double res = 0.0;
        int it = 0;
        double[] tOld = new double[g.nx * g.ny];
        for (; it < maxIter; it++) {
            res = 0.0;
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++)
                    if (g.isWet(i, j)) tOld[j * g.nx + i] = st.t[st.idx(i, j, 0)];
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q = st.idx(i, j, 0);
                    double u = st.u[q], v = st.v[q];
                    double xb = i - u * DT / g.dx;
                    double yb = j - v * DT / g.dy;
                    double tAdv = bilinear(g, tOld, i, j, xb, yb);
                    double tf = FreezingPoint.referenceKelvin();
                    int xi = (int) Math.round(g.xOf(i));
                    int zj = (int) Math.round(g.zOf(j));
                    double ice = ClimberOcean.iceFraction(tAdv);
                    double h = Math.max(1.0, st.surfaceLayer[j * g.nx + i]);
                    double qOpen = f.netHeatFluxWm2(xi, zj, theta, tAdv, 0.0);
                    double tWater = tAdv + qOpen * DT / (ClimberOcean.RHO_REF * ClimberOcean.C_OCEAN * h);
                    if (!Double.isFinite(tWater)) tWater = tAdv;
                    tWater = Math.max(250.0, Math.min(330.0, tWater));
                    double tNew = (1.0 - ice) * tWater + ice * tf;
                    if (!Double.isFinite(tNew)) tNew = tAdv;
                    res = Math.max(res, Math.abs(tNew - st.t[q]));
                    st.t[q] = tNew;
                }
            // 一次五点平滑：数值扩散，抑制冰缘 T 的格点跳变。
            double[] sm = new double[g.nx * g.ny];
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q = st.idx(i, j, 0);
                    int im = Math.max(0, i - 1), ip = Math.min(g.nx - 1, i + 1);
                    int jm = Math.max(1, j - 1), jp = Math.min(g.ny - 2, j + 1);
                    double avg = 0.25 * (st.t[st.idx(im, j, 0)] + st.t[st.idx(ip, j, 0)]
                                       + st.t[st.idx(i, jm, 0)] + st.t[st.idx(i, jp, 0)]);
                    sm[q] = 0.5 * st.t[q] + 0.5 * avg;
                }
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++) if (g.isWet(i, j)) st.t[st.idx(i, j, 0)] = sm[st.idx(i, j, 0)];
            if (res < 1.0e-6) break;
        }

        double mn = 1e9, mx = -1e9, qm = 0.0, qs = 0.0; int n = 0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = st.idx(i, j, 0);
                double t = st.t[q];
                mn = Math.min(mn, t); mx = Math.max(mx, t);
                int im = Math.max(0, i - 1), ip = Math.min(g.nx - 1, i + 1);
                int jm = Math.max(1, j - 1), jp = Math.min(g.ny - 2, j + 1);
                double dTdx = (st.t[st.idx(ip, j, 0)] - st.t[st.idx(im, j, 0)]) / ((ip - im) * g.dx);
                double dTdz = (st.t[st.idx(i, jp, 0)] - st.t[st.idx(i, jm, 0)]) / ((jp - jm) * g.dy);
                double qo = ClimberOcean.RHO_REF * ClimberOcean.C_OCEAN * ClimberOcean.H_MIXED
                          * (st.u[q] * dTdx + st.v[q] * dTdz);
                qm = Math.max(qm, Math.abs(qo)); qs += Math.abs(qo); n++;
            }
        return new Result(g, st, res, mn, mx, qm, n > 0 ? qs / n : 0.0);
    }

    private static double bilinear(OceanGrid g, double[] a, int i, int j, double x, double y) {
        int i0 = (int) Math.floor(x), j0 = (int) Math.floor(y);
        double tx = x - i0, ty = y - j0;
        int i1 = Math.min(g.nx - 1, Math.max(0, i0 + 1));
        int j1 = Math.min(g.ny - 2, Math.max(1, j0 + 1));
        i0 = Math.min(g.nx - 1, Math.max(0, i0));
        j0 = Math.min(g.ny - 2, Math.max(1, j0));
        double s00 = a[j0 * g.nx + i0], s10 = a[j0 * g.nx + i1];
        double s01 = a[j1 * g.nx + i0], s11 = a[j1 * g.nx + i1];
        return (1 - tx) * (1 - ty) * s00 + tx * (1 - ty) * s10
             + (1 - tx) * ty * s01 + tx * ty * s11;
    }
}
