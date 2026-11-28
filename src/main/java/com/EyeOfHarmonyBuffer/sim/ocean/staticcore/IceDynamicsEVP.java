package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * EVP 冰动力学的常数与 D2b 诊断（应变率/冰强度/黏性）。
 *
 * <p>★ 2026-11-17 核实（约定 4）：本类**已含完整动量步**（{@link #step}：CICE 半隐式 + 应力松弛），
 * 由 {@link AdvectionDiffusionSolver#ICE_EVP_ON} 在冰热力之后调用（默认 false ⟹ 生产不执行）。
 * 旧注释"只做诊断、不改状态、不接生产"已过期。
 * 常数出处：
 * - Pstar/Cstar/e：Hibler 1979；CICE ug_case_settings 默认 Pstar=2.75e4、Cstar=20、e=2。
 * - elasticDamp=0.36、ndte=120、deltaminEVP=1e-11：CICE ug_case_settings 默认。
 * - Cdn_ocn=0.00536：CICE 冰-海拖曳默认（版本相关，D2c 前再核对）。
 */
public final class IceDynamicsEVP {

    private IceDynamicsEVP() {}

    public static final double PSTAR = 2.75e4;       // N/m (Hibler 1979)
    public static final double CSTAR = 20.0;         // Hibler 1979
    public static final double E = 2.0;              // elliptical yield curve
    /** CICE e_factor = e_yieldcurve^2 / e_plasticpot^4 = 4/16 = 0.25（不是 e^2=4）。 */
    public static final double E_FACTOR = 0.25;
    public static final double ELASTIC_DAMP = 0.36;  // CICE default
    public static final int NDTE = 120;              // CICE default
    public static final double DELTAMIN = 1.0e-11;   // CICE deltaminEVP
    /** Icepack dragio=0.00536（icepack_parameters.F90）；D2c 冰-海拖曳用。 */
    public static final double CDN_OCN = 0.00536;
    /** CICE 脊化参数：Cf=17、mu_rdg=3.0（icepack_parameters.F90）；D2d 用。 */
    public static final double CF_RIDGE = 17.0;
    public static final double MU_RDG = 3.0;

    /** Hibler 1979 冰强度 P = Pstar*h*exp(-Cstar*(1-a))。 */
    public static double strength(double aice, double hIce) {
        double a = Math.min(1.0, Math.max(0.0, aice));
        if (a <= 1.0e-3 || hIce <= 0.0) return 0.0;
        return PSTAR * hIce * Math.exp(-CSTAR * (1.0 - a));
    }

    /** 椭圆屈服面的等效应变率 Delta；CICE 用 e_factor=0.25，不是 e^2=4。 */
    public static double delta(double div, double tension, double shear) {
        return Math.sqrt(div * div + E_FACTOR * (tension * tension + shear * shear));
    }

    public static double zeta(double strength, double delta) {
        return strength / (2.0 * Math.max(DELTAMIN, delta));
    }

    public static double eta(double zeta) {
        return zeta / (E * E);
    }

    /** 把海洋表层速度作为冰速初值（诊断用；D2c 由动量方程给冰速）。 */
    public static void initFromOcean(OceanGrid g, OceanState st) {
        for (int j = 1; j < g.ny - 1; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                int q = st.idx(i, j, 0);
                if (st.iceFraction[js] <= 1.0e-3) {
                    st.iceU[js] = 0.0;
                    st.iceV[js] = 0.0;
                } else {
                    st.iceU[js] = st.uF[q];
                    st.iceV[js] = st.vF[q];
                }
            }
        }
    }

    /**
     * D2c：最小 EVP 步进。默认不调用；AdvectionDiffusionSolver.ICE_EVP_ON 打开后
     * 在冰热力之后、输运之前调用。只做诊断级实现，未接 ridging。
     */
    public static void step(OceanGrid g, OceanState st, SeaSurfaceForcing f,
                            double theta, double dt) {
        int nx = g.nx, ny = g.ny;
        double arlx = 2.0 * ELASTIC_DAMP * NDTE;
        double arlx1i = 1.0 / arlx;
        double denom1 = 1.0 / (1.0 + arlx1i);
        double outer = Math.min(dt, AdvectionDiffusionSolver.ICE_DYN_DT);
        int nOuter = Math.max(1, (int) Math.ceil(dt / outer));
        double dtOuter = dt / nOuter;
        double dte = dtOuter / NDTE;
        // 风应力只依赖 (x,z,theta)，在 step 内只算一次；否则每个子循环每格点重算，极慢。
        double[] tauX = new double[nx * ny];
        double[] tauY = new double[nx * ny];
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                tauX[js] = f.windStressX((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), theta);
                tauY[js] = f.windStressZ((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), theta);
            }
        }
        LAST_MAX_STRESS_LAG = 0.0;
        LAST_MAX_STRESS_LAG_LAT = Double.NaN;
        double[] uNew = new double[nx * ny];
        double[] vNew = new double[nx * ny];
        for (int o = 0; o < nOuter; o++) {
            for (int sub = 0; sub < NDTE; sub++) {
                System.arraycopy(st.iceU, 0, uNew, 0, uNew.length);
                System.arraycopy(st.iceV, 0, vNew, 0, vNew.length);
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int js = j * nx + i;
                        double a = st.iceFraction[js];
                        double h = st.iceActualThickness(i, j);
                        if (a <= 1.0e-3 || h <= 0.0) {
                            st.sig1[js] = 0.0; st.sig2[js] = 0.0; st.sig12[js] = 0.0;
                            // 无冰格不能保留海洋表层速度，否则 CFL/输运在冰缘被它污染。
                            st.iceU[js] = 0.0; st.iceV[js] = 0.0;
                            continue;
                        }
                        int iE = (i + 1) % nx, iW = (i - 1 + nx) % nx;
                        int jN = j + 1, jS = j - 1;
                        double uE = st.iceU[j * nx + iE], uW = st.iceU[j * nx + iW];
                        double vE = st.iceV[j * nx + iE], vW = st.iceV[j * nx + iW];
                        double uN = st.iceU[jN * nx + i], uS = st.iceU[jS * nx + i];
                        double vN = st.iceV[jN * nx + i], vS = st.iceV[jS * nx + i];
                        double dudx = (uE - uW) / (2.0 * g.dx);
                        double dudy = (uN - uS) / (2.0 * g.dy);
                        double dvdx = (vE - vW) / (2.0 * g.dx);
                        double dvdy = (vN - vS) / (2.0 * g.dy);
                        double div = dudx + dvdy;
                        double tension = dudx - dvdy;
                        double shear = dudy + dvdx;
                        double D = delta(div, tension, shear);
                        double P = strength(a, h);
                        double zeta2 = P / Math.max(DELTAMIN, D);
                        double eta2 = zeta2 / (E * E);
                        double rep = P * D / (D + DELTAMIN);
                        double t1 = zeta2 * div - rep;
                        double t2 = eta2 * tension;
                        double t12 = 0.5 * eta2 * shear;
                        st.sig1[js] = (st.sig1[js] + arlx1i * t1) * denom1;
                        st.sig2[js] = (st.sig2[js] + arlx1i * t2) * denom1;
                        st.sig12[js] = (st.sig12[js] + arlx1i * t12) * denom1;
                        double lag = Math.max(Math.abs(st.sig1[js] - t1),
                                Math.max(Math.abs(st.sig2[js] - t2), Math.abs(st.sig12[js] - t12)))
                                / (P + 1.0e-30);
                        if (lag > LAST_MAX_STRESS_LAG) {
                            LAST_MAX_STRESS_LAG = lag;
                            LAST_MAX_STRESS_LAG_LAT = g.latAt(j) * 180.0 / Math.PI;
                        }
                    }
                }
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int js = j * nx + i;
                        double a = st.iceFraction[js];
                        double h = st.iceActualThickness(i, j);
                        if (a <= 1.0e-3 || h <= 0.0) continue;
                        double m = OceanConstants.RHO_ICE * st.iceThickness[js]
                                + OceanConstants.RHO_SNOW * st.snowThickness[js];
                        if (m <= 1.0e-6) continue;
                        int iE = (i + 1) % nx, iW = (i - 1 + nx) % nx;
                        int jN = j + 1, jS = j - 1;
                        double s11E = 0.5 * (st.sig1[j * nx + iE] + st.sig2[j * nx + iE]);
                        double s11W = 0.5 * (st.sig1[j * nx + iW] + st.sig2[j * nx + iW]);
                        double s22N = 0.5 * (st.sig1[jN * nx + i] - st.sig2[jN * nx + i]);
                        double s22S = 0.5 * (st.sig1[jS * nx + i] - st.sig2[jS * nx + i]);
                        double s12E = st.sig12[j * nx + iE];
                        double s12W = st.sig12[j * nx + iW];
                        double s12N = st.sig12[jN * nx + i];
                        double s12S = st.sig12[jS * nx + i];
                        double fx = (s11E - s11W) / (2.0 * g.dx)
                                + (s12N - s12S) / (2.0 * g.dy);
                        double fy = (s12E - s12W) / (2.0 * g.dx)
                                + (s22N - s22S) / (2.0 * g.dy);
                        int q = st.idx(i, j, 0);
                        double uo = st.uF[q], vo = st.vF[q];
                        double du = uo - st.iceU[js], dv = vo - st.iceV[js];
                        double sp = Math.sqrt(du * du + dv * dv);
                        double vrel = a * OceanConstants.RHO_REF * CDN_OCN * sp;
                        double taux = vrel * du;
                        double tauy = vrel * dv;
                        double fcor = 2.0 * WorldContract.OMEGA * Math.sin(g.latAt(j));
                        double fm = fcor * m;
                        double umassdti = m / dte;
                        double brlx = NDTE;
                        double cca = brlx * umassdti + vrel; // cosw=1
                        double ccb = fm;                     // sinw=0
                        // m 是格点平均质量，风应力是海表单位面积；作用在冰上的力必须乘 a。
                        double cc1 = fx + a * tauX[js] + taux + brlx * umassdti * st.iceU[js];
                        double cc2 = fy + a * tauY[js] + tauy + brlx * umassdti * st.iceV[js];
                        double ab2 = cca * cca + ccb * ccb;
                        uNew[js] = (cca * cc1 + ccb * cc2) / ab2;
                        vNew[js] = (cca * cc2 - ccb * cc1) / ab2;
                        if (!Double.isFinite(uNew[js])) uNew[js] = 0.0;
                        if (!Double.isFinite(vNew[js])) vNew[js] = 0.0;
                    }
                }
                System.arraycopy(uNew, 0, st.iceU, 0, uNew.length);
                System.arraycopy(vNew, 0, st.iceV, 0, vNew.length);
            }
        }
    }

    /** 诊断：最大弹性应力滞后（|σ_new − σ_target|/P，0 = 瞬时平衡）。last* 不进 A7。 */
    public static double LAST_MAX_STRESS_LAG = 0.0;
    /** 最大应力滞后所在纬度 [deg]。 */
    public static double LAST_MAX_STRESS_LAG_LAT = Double.NaN;

    /** 诊断结果（不写状态）。 */
    public static final class Stats {
        public int cells, iceCells;
        public double maxStrength, maxDelta, maxZeta, maxEta;
        public boolean finite = true;
    }

    public static Stats diagnose(OceanGrid g, OceanState st) {
        Stats s = new Stats();
        int nx = g.nx, ny = g.ny;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                double a = st.iceFraction[js];
                double h = st.iceActualThickness(i, j);
                double P = strength(a, h);
                if (P > 0) s.iceCells++;
                s.cells++;
                int iE = (i + 1) % nx, iW = (i - 1 + nx) % nx;
                int jN = j + 1, jS = j - 1;
                double uE = st.iceU[j * nx + iE], uW = st.iceU[j * nx + iW];
                double vE = st.iceV[j * nx + iE], vW = st.iceV[j * nx + iW];
                double uN = st.iceU[jN * nx + i], uS = st.iceU[jS * nx + i];
                double vN = st.iceV[jN * nx + i], vS = st.iceV[jS * nx + i];
                double dudx = (uE - uW) / (2.0 * g.dx);
                double dudy = (uN - uS) / (2.0 * g.dy);
                double dvdx = (vE - vW) / (2.0 * g.dx);
                double dvdy = (vN - vS) / (2.0 * g.dy);
                double div = dudx + dvdy;
                double tension = dudx - dvdy;
                double shear = dudy + dvdx;
                double D = delta(div, tension, shear);
                double z = zeta(P, D);
                double e = eta(z);
                if (!Double.isFinite(P) || !Double.isFinite(D) || !Double.isFinite(z) || !Double.isFinite(e)) s.finite = false;
                s.maxStrength = Math.max(s.maxStrength, P);
                s.maxDelta = Math.max(s.maxDelta, D);
                s.maxZeta = Math.max(s.maxZeta, z);
                s.maxEta = Math.max(s.maxEta, e);
            }
        }
        return s;
    }
}
