package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * Stage F：KPP 边界层的最小 OBL 深度实现（默认关，尚未接生产）。
 *
 * <p>公式对齐 CVMix cvmix_kpp.F90：
 * <pre>
 * Ri_bulk(z) = -(1-0.5*surf_layer_ext) * z * (b_s-b(z))
 *              / (|V_s-V(z)|^2 + Vt^2)
 * OBL = 解 Ri_bulk = Ri_crit
 * </pre>
 * z 向上为正；Ri_crit=0.3、surf_layer_ext=0.1、minVtsqr=1e-10 均来自 CVMix 默认。
 */
public final class KppBoundaryLayer {

    private KppBoundaryLayer() {}

    /** 默认关；打开前不改变任何生产结果。 */
    public static boolean ON = false;

    public static final double RI_CRIT = 0.3;          // CVMix default
    public static final double SURF_LAYER_EXT = 0.1;   // CVMix default
    public static final double MIN_VT2 = 1.0e-10;      // CVMix minVtsqr
    public static final double GRAV = 9.80616;

    /**
     * 最小 OBL：只做 bulk Ri，不做 Ekman/Monin-Obukhov 限制。
     *
     * @param zUp  层中心高度，m，向上为正，zUp[0]=0
     * @param rho  位势密度，kg/m^3
     * @param u    层中心东西速度，m/s
     * @param v    层中心南北速度，m/s
     * @return OBL 深度，m（正数）
     */
    public static double oblDepth(double[] zUp, double[] rho, double[] u, double[] v) {
        int n = Math.min(zUp.length, Math.min(rho.length, Math.min(u.length, v.length)));
        if (n < 2) return Math.max(0.0, -zUp[0]);
        double rho0 = rho[0];
        double b0 = -GRAV * (rho0 - 1027.8) / 1027.8;
        double u0 = u[0], v0 = v[0];
        double scaling = 1.0 - 0.5 * SURF_LAYER_EXT;
        double prevRi = 0.0, prevZ = zUp[0];
        for (int k = 1; k < n; k++) {
            double b = -GRAV * (rho[k] - 1027.8) / 1027.8;
            double db = b0 - b;
            double du = u0 - u[k], dv = v0 - v[k];
            double den = du * du + dv * dv + MIN_VT2;
            double ri = -scaling * zUp[k] * db / den;
            if (ri > RI_CRIT) {
                double denom = ri - prevRi;
                double frac = denom > 1.0e-12 ? (RI_CRIT - prevRi) / denom : 1.0;
                frac = Math.max(0.0, Math.min(1.0, frac));
                double zc = prevZ + frac * (zUp[k] - prevZ);
                return Math.max(0.0, -zc);
            }
            prevRi = ri;
            prevZ = zUp[k];
        }
        return Math.max(0.0, -zUp[n - 1]);
    }

    /** Thickness-weighted mean from surface to slDepth [m]; zUp is up-positive. */
    public static double surfaceLayerMean(double[] zUp, double[] val, double slDepth) {
        if (slDepth <= 0.0) return val.length > 0 ? val[0] : 0.0;
        double sum = 0.0, wsum = 0.0, used = 0.0;
        for (int k = 0; k < Math.min(zUp.length, val.length); k++) {
            double top = (k == 0) ? 0.0 : zUp[k - 1];
            double thick = Math.max(0.0, top - zUp[k]);
            double w = Math.min(thick, slDepth - used);
            if (w <= 0.0) break;
            sum += val[k] * w;
            wsum += w;
            used += w;
        }
        return wsum > 0.0 ? sum / wsum : val[0];
    }

    /** Buoyancy frequency squared at interface below center k (k>=1). */
    public static double buoyancyFrequency2(double[] zUp, double[] rho, int k) {
        if (k < 1 || k >= Math.min(zUp.length, rho.length)) return 0.0;
        double dz = Math.max(1.0e-9, zUp[k - 1] - zUp[k]);
        return GRAV * (rho[k] - rho[k - 1]) / (1027.8 * dz);
    }

    /** Ekman depth limit 0.7*uStar/abs(f). */
    public static double ekmanDepth(double ustar, double coriolis) {
        if (Math.abs(coriolis) < 1.0e-12) return Double.POSITIVE_INFINITY;
        return 0.7 * ustar / Math.abs(coriolis);
    }

    /** Monin-Obukhov depth limit u*^3/(Bf*kappa), only stable Bf>0. */
    public static double moninObukhovDepth(double ustar, double bf) {
        if (!(bf > 0.0)) return Double.POSITIVE_INFINITY;
        return ustar * ustar * ustar / (bf * VONK);
    }

    /** Full minimal OBL: surface-layer means + N/Vt2; no Ekman/MO yet. */
    public static double oblDepthFull(double[] zUp, double[] dz, double[] rho,
                                      double[] u, double[] v, double bf, double ustar) {
        return oblDepthFull(zUp, dz, rho, u, v, bf, ustar, 0.0);
    }

    public static double oblDepthFull(double[] zUp, double[] dz, double[] rho,
                                      double[] u, double[] v, double bf, double ustar,
                                      double coriolis) {
        int n = Math.min(zUp.length, Math.min(rho.length, Math.min(u.length, v.length)));
        double obl = Math.max(1.0, oblDepth(zUp, rho, u, v));
        for (int it = 0; it < 3; it++) {
            double sl = Math.max(1.0, SURF_LAYER_EXT * obl);
            double rhoS = surfaceLayerMeanDz(dz, rho, sl);
            double uS = surfaceLayerMeanDz(dz, u, sl);
            double vS = surfaceLayerMeanDz(dz, v, sl);
            double bS = -GRAV * (rhoS - 1027.8) / 1027.8;
            double prevRi = 0.0, prevZ = zUp[0];
            double found = 0.0;
            for (int k = 1; k < n; k++) {
                double b = -GRAV * (rho[k] - 1027.8) / 1027.8;
                double db = bS - b;
                double du = uS - u[k], dv = vS - v[k];
                double N2 = buoyancyFrequency2(zUp, rho, k);
                double N = Math.sqrt(Math.max(0.0, N2));
                double w = ws(Math.max(0.0, Math.min(1.0, zUp[k] / -Math.max(1.0, obl))),
                        obl, bf, ustar);
                double vt2 = unresolvedShear2(zUp[k], N, w);
                double den = du * du + dv * dv + vt2;
                double ri = -(1.0 - 0.5 * SURF_LAYER_EXT) * zUp[k] * db / den;
                if (ri > RI_CRIT) {
                    double den2 = ri - prevRi;
                    double frac = den2 > 1.0e-12 ? (RI_CRIT - prevRi) / den2 : 1.0;
                    frac = Math.max(0.0, Math.min(1.0, frac));
                    found = Math.max(1.0, -(prevZ + frac * (zUp[k] - prevZ)));
                    break;
                }
                prevRi = ri;
                prevZ = zUp[k];
            }
            if (found <= 0.0) found = Math.max(1.0, -zUp[n - 1]);
            if (Math.abs(found - obl) < 1.0e-9) break;
            obl = found;
        }
        double lim = Math.min(ekmanDepth(ustar, coriolis), moninObukhovDepth(ustar, bf));
        return Math.max(1.0, Math.min(obl, lim));
    }

    /** CVMix no-Langmuir unresolved shear: Vt2 = -Cv*Vtc*z*N*ws/Ri_crit, floored. */
    public static double unresolvedShear2(double zUp, double N, double ws) {
        double Cv = N < 0.002 ? 2.1 - 200.0 * N : 1.7;
        double Vtc = Math.sqrt(0.2 / (C_S * SURF_LAYER_EXT)) / (VONK * VONK);
        double vt2 = -Cv * Vtc * zUp * N * ws / RI_CRIT;
        return Math.max(MIN_VT2, vt2);
    }

    /** Layer-thickness version: dz[k] positive, val[k] per layer. */
    public static double surfaceLayerMeanDz(double[] dz, double[] val, double slDepth) {
        if (slDepth <= 0.0) return val.length > 0 ? val[0] : 0.0;
        double sum = 0.0, wsum = 0.0, used = 0.0;
        for (int k = 0; k < Math.min(dz.length, val.length); k++) {
            double w = Math.min(dz[k], slDepth - used);
            if (w <= 0.0) break;
            sum += val[k] * w;
            wsum += w;
            used += w;
        }
        return wsum > 0.0 ? sum / wsum : val[0];
    }

    /**
     * 层 k 的【上界面】深度（m，正数、从海面向下）；`zTop(g,0) == 0` 就是海面。
     *
     * <p>★ 2026-11-17（F2b）：这是 {@link #oblDepth(double[],double[],double[],double[])} 的契约口径
     * （它的 javadoc 写 `zUp[0]=0`）。生产重载原来传的是层**中心** `-zCenter[k]`（顶点 = -dz0/2），
     * 于是 OBL 是"从第一层中心量起"的，**永远不小于 dz0/2**（dz0=50 m 时 ≥25 m），
     * 而交接 §4.1 需要处理 `OBL < dz0` 的【顶层部分层】—— 那条路被这个偏移直接堵死了。
     * 同时 OBL 会被高估 (dz0/2)/dz ≈ 20%（dz0=50、dz=60 时）。
     */
    public static double zTop(OceanGrid g, int k) {
        double d = 0.0;
        for (int m = 0; m < k && m < g.nz; m++) d += g.dz[m];
        return d;
    }

    /** Build the column profile from OceanState and return OBL depth (measured from the SURFACE). */
    public static double oblDepth(OceanGrid g, OceanState st, int i, int j) {
        int n = g.nz;
        double[] zUp = new double[n], rho = new double[n], u = new double[n], v = new double[n];
        for (int k = 0; k < n; k++) {
            int q = st.idx(i, j, k);
            zUp[k] = -zTop(g, k);   // ★ 契约：zUp[0]=0（海面），旧式 -zCenter 把原点放在顶层格中心
            rho[k] = 1027.8 * (1.0 - com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.ALPHA * (st.t[q] - 285.0)
                    + com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.BETA * (st.s[q] - 35.0));
            u[k] = st.u[q];
            v[k] = st.v[q];
        }
        return oblDepth(zUp, rho, u, v);
    }

    // ---- F2: scalar turbulent scale / nonlocal shape (not yet used in production) ----
    public static final double VONK = 0.4;
    public static final double CSTAR = 10.0;       // CVMix default
    public static final double ZETA_S = -1.0;      // CVMix default
    public static final double A_S = Math.sqrt(1.0 - 16.0 * ZETA_S) * (1.0 + 8.0 * ZETA_S);
    public static final double C_S = 24.0 * Math.sqrt(1.0 - 16.0 * ZETA_S);

    /** CVMix compute_phi_inv for scalars, non-Stokes. */
    public static double phiInvS(double zeta) {
        if (zeta >= 0.0) return 1.0 / (1.0 + 5.0 * zeta);
        if (zeta >= ZETA_S) return Math.sqrt(1.0 - 16.0 * zeta);
        return Math.cbrt(A_S - C_S * zeta);
    }

    /** w_s(sigma) = vonk * u* * phi_inv(zeta), zeta = sigma*OBL*Bf*vonk/u*^3. */
    public static double ws(double sigma, double obl, double bf, double ustar) {
        if (!(ustar > 0.0) || !(obl > 0.0)) return 0.0;
        double s = Math.max(0.0, Math.min(1.0, sigma));
        double zeta = s * obl * bf * VONK / (ustar * ustar * ustar);
        return VONK * ustar * phiInvS(zeta);
    }

    /** CVMix nonlocal_coeff (dimensionless shape amplitude). */
    public static double nonlocalCoeff() {
        return CSTAR * VONK * Math.cbrt(VONK * SURF_LAYER_EXT * C_S);
    }

    /** G2(sigma)=(1-sigma)^2, used only when unstable. */
    public static double nonlocalShape(double sigma) {
        double s = Math.max(0.0, Math.min(1.0, sigma));
        return (1.0 - s) * (1.0 - s);
    }

    /** CVMix simple shape: G(sigma)=sigma*(1-sigma)^2. */
    public static double gradientShape(double sigma) {
        double s = Math.max(0.0, Math.min(1.0, sigma));
        return s * (1.0 - s) * (1.0 - s);
    }

    /** Minimal KPP scalar diffusivity: Kt=OBL*ws(sigma)*G(sigma); Ks=Kt. */
    public static double scalarDiffusivity(double sigma, double obl, double bf, double ustar) {
        return obl * ws(sigma, obl, bf, ustar) * gradientShape(sigma);
    }

    /**
     * ★ 2026-11-17（Stage G2 单柱）：面 k+1/2 的有效扩散率 [m²/s] —— KPP 形状 + **背景下限**。
     *
     * <p>为什么必须有下限（这是交接 §4.1「Kt≈0 → 三对角退化 → NaN」的结构根因）：
     * <ol>
     *   <li>{@code G(sigma)=sigma(1-sigma)^2} 在 sigma→1（OBL 底）**恒为 0**；</li>
     *   <li>当 OBL ≤ dz0 时，顶层格下面**唯一**的那个面深度 z&gt;OBL ⟹ 连形状项都不进 ⟹ Kt=0；</li>
     *   <li>整列每个面都 0 ⟹ 隐式矩阵退化成对角阵 ⟹ 层间零交换，任何再解都得到"冻结"的列。</li>
     * </ol>
     *
     * <p><b>下限取什么</b>：取该面两侧格子的**背景扩散率**（模型自己的 {@code KV}/{@code KV_DEEP}
     * 剖面）。这不是新常数，也不是调参：只是把"湍流扩散率为零"这个非物理状态排除掉
     * （分子扩散 + 未分辨背景永远存在）。真正的 OBL 底增强（CVMix enhanced_diff）仍需按其源码实现，
     * 本条只负责让矩阵**非退化**。
     *
     * @param zFace    面的深度（m，正数、从海面往下；0 = 海面）
     * @param obl      OBL 深度（m，&gt;0）
     * @param bf       表层浮力通量 [m²/s³]（&lt;0 = 不稳定）
     * @param ustar    摩擦速度 [m/s]
     * @param background 该面的背景扩散率（m²/s，由调用方给 KV/KV_DEEP）
     */
    public static double faceKtAt(double zFace, double obl, double bf, double ustar, double background) {
        double kt = 0.0;
        if (obl > 0.0 && zFace > 0.0 && zFace <= obl) {
            kt = scalarDiffusivity(zFace / obl, obl, bf, ustar);
        }
        return Math.max(background, kt);
    }

    /**
     * OBL 小于第一层厚度 dz0 时的部分层有效扩散：
     * (1/dz0) ∫_0^OBL Kt dz = (OBL/dz0)*OBL*<ws*G>，Simpson 平均。
     */
    public static double topCellDiffusivity(double obl, double dz0, double bf, double ustar) {
        if (!(obl > 0.0) || !(dz0 > 0.0)) return 0.0;
        double frac = Math.min(1.0, obl / dz0);
        double s0 = 0.0, sm = 0.5 * frac, s1 = frac;
        double gAvg = (gradientShape(s0) + 4.0 * gradientShape(sm) + gradientShape(s1)) / 6.0;
        double wAvg = (ws(s0, obl, bf, ustar) + 4.0 * ws(sm, obl, bf, ustar)
                + ws(s1, obl, bf, ustar)) / 6.0;
        double kt = frac * obl * wAvg * gAvg;
        return Double.isFinite(kt) && kt >= 0.0 ? kt : 0.0;
    }

    /** Nonlocal scalar term: only unstable (Bf<0); Cs*G2(sigma). */
    public static double nonlocalScalar(double sigma, double bf) {
        if (!(bf < 0.0)) return 0.0;
        return nonlocalCoeff() * nonlocalShape(sigma);
    }
}
