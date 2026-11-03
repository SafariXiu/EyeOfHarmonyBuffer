package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 最小正压风生环流 solver（J1 起步）。
 *
 * <p>方程：curl(tau)/(rho0*H) = del2(psi)，u=-dpsi/dy，v=dpsi/dx。
 * x 周期、y 墙、LandV3 mask；psi=0 于陆地/墙。
 * rho0=1027.8 kg/m3（Stocker 1992 / ClimberOcean）；H=sum(dz)。
 */
public final class BarotropicSolver {

    public static final double RHO0 = 1027.8;
    /** 底摩擦 r=1/adrag，adrag=4 days（MITgcm ocn_par.nml 参考值）。 */
    public static final double R_FRICTION = 1.0 / (4.0 * 86400.0);
    public static final double SOR_OMEGA = 0.2;

    private BarotropicSolver() {}

    public static final class Result {
        public final OceanState state;
        public final int iterations;
        public final double residual;
        Result(OceanState state, int iterations, double residual) {
            this.state = state; this.iterations = iterations; this.residual = residual;
        }
    }

    /** 冷启动版本：新建状态。 */
    public static Result solve(OceanGrid g, SeaSurfaceForcing f, double theta) {
        return update(g, new OceanState(g), f, theta);
    }

    /**
     * ★ 2026-11-02（季节循环）：在【已有状态】上重算风生 psi 与 u/v，**保留 T/S**。
     * 供 4 季/12 月循环使用：每月换一次风场，但不重置海洋的记忆。
     */
    public static Result update(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta) {
        int nx = g.nx, ny = g.ny;
        int n2 = nx * ny;
        double dx = g.dx, dy = g.dy;
        double h = 0.0;
        for (double v : g.dz) h += v;

        double[] rhs = new double[n2];
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int iE = (i + 1) % nx, iW = (i - 1 + nx) % nx;
                double txE = f.windStressX((int) Math.round(g.xOf(iE)), (int) Math.round(g.zOf(j)), theta);
                double txW = f.windStressX((int) Math.round(g.xOf(iW)), (int) Math.round(g.zOf(j)), theta);
                double tyN = f.windStressZ((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j + 1)), theta);
                double tyS = f.windStressZ((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j - 1)), theta);
                double curl = (tyN - tyS) / (2.0 * dx) - (txE - txW) / (2.0 * dy);
                rhs[j * nx + i] = curl / (RHO0 * h);
            }
        }

        double[] psi = new double[n2];
        double residual = 0.0;
        int iter = 0;
        int maxIter = 50000;
        for (; iter < maxIter; iter++) {
            residual = 0.0;
            for (int j = 1; j < ny - 1; j++) {
                double beta = WorldContract.betaForLatitude(
                    WorldContract.latOf((int) Math.round(g.zOf(j))));
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) { psi[j * nx + i] = 0.0; continue; }
                    double w = g.isWet(i - 1, j) ? psi[j * nx + ((i - 1 + nx) % nx)] : 0.0;
                    double e = g.isWet(i + 1, j) ? psi[j * nx + ((i + 1) % nx)] : 0.0;
                    double s = g.isWet(i, j - 1) ? psi[(j - 1) * nx + i] : 0.0;
                    double nn = g.isWet(i, j + 1) ? psi[(j + 1) * nx + i] : 0.0;
                    // Stommel: central beta + bottom friction, Jacobi under-relaxation.
                    double rr = R_FRICTION;
                    double target = (rr / (dx * dx) * (w + e + s + nn)
                                   + beta / (2.0 * dx) * (e - w)
                                   - rhs[j * nx + i]) / (4.0 * rr / (dx * dx));
                    double old = psi[j * nx + i];
                    double nv = old + SOR_OMEGA * (target - old);
                    psi[j * nx + i] = nv;
                    double d = Math.abs(nv - old);
                    if (d > residual) residual = d;
                }
            }
            if (residual < 1.0e-12) break;
        }

        st.psi = psi;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                double u = -(psi[g.idx(i, j + 1)] - psi[g.idx(i, j - 1)]) / (2.0 * dy);
                double v = (psi[g.idx(i + 1, j)] - psi[g.idx(i - 1, j)]) / (2.0 * dx);
                for (int k = 0; k < g.nz; k++) {
                    int q = st.idx(i, j, k);
                    st.u[q] = u; st.v[q] = v;
                }
            }
        }
        return new Result(st, iter, residual);
    }
}
