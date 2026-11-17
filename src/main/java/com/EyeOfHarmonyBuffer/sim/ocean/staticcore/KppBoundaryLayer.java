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

    /**
     * 非局部的形状 G(σ)。★ 2026-11-17（P3 第 3 步取证）：CVMix 的 @code CVMIX_KPP_SIMPLE_SHAPES@
     * 分支里 @code Tshape2 = Tshape = σ(1−σ)²@（cvmix_kpp.F90 L989-998），**不是** (1−σ)²。
     * 旧实现用 (1−σ)² 与出处不符；由于非局部项尚未接线（其通量标定 NOT FOUND），
     * 这个更正**不改变任何生产数值**，只是让库与出处一致。
     */
    public static double nonlocalShape(double sigma) {
        return gradientShape(sigma);
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
     * ★ 2026-11-17（Stage G2 第 1~2 步）：整列的【面扩散率】，含 CVMix 的 OBL 底增强。
     *
     * <p>出处：CVMix-src `src/shared/cvmix_kpp.F90`（2026-11-17 抓取，见 Talos2
     * 工作目录/中期/四问题-处置计划-2026-11-17.md "P3 出处"）：
     * <ul>
     *   <li>`cvmix_coeffs_kpp_low` L802-L832：`kwup=floor(kOBL)`、`ktup=nint(kOBL)-1`、
     *       `delta=(OBL+zt(ktup))/(zt(ktup)-zt(ktup+1))`；</li>
     *   <li>`cvmix_kpp_compute_enhanced_diff` L1874-L1979（LMD94 附录 D）：
     *       `omd=1-delta`；界面在 OBL 外（lkteqkw）时
     *       `enh = omd²·ktup + delta²·interior`、`face = omd·interior + delta·enh`；
     *       界面在 OBL 内时把 `interior` 换成该界面的 OBL 值。</li>
     * </ul>
     *
     * <p><b>本仓的映射</b>（我们是有限体积、按【面】组织，CVMix 是按【格心 + 数组】）：
     * `ktup` = 最后一个"格心深度 ≤ OBL"的层；`delta` = OBL 底在该层中心与下一层中心之间的比例；
     * "interior" = 该面的背景扩散率，"OBL 值" = 该面的 KPP 形状值。增强只作用在
     * 【与 OBL 底相邻的那一个面】上，这正是 LMD94 Appendix D 的用意：让 OBL 底不出现 Kt 跳变。
     *
     * @return 长度 wl+1 的数组；下标 f = 第 f 层顶面（0 = 海面，未用；wl = 海底，恒 0）
     */
    public static double[] columnKt(double[] dz, int wl, double obl, double bf, double ustar, double[] bgFace) {
        double[] zc = new double[wl];
        double[] zf = new double[wl + 1];
        double acc = 0.0;
        for (int k = 0; k < wl; k++) { zc[k] = acc + 0.5 * dz[k]; acc += dz[k]; zf[k + 1] = acc; }
        double[] kt = new double[wl + 1];
        for (int f = 1; f < wl; f++)
            kt[f] = (obl > 0.0 && zf[f] <= obl) ? scalarDiffusivity(zf[f] / obl, obl, bf, ustar)
                                                : Math.max(0.0, bgFace[f]);
        int ktup = -1;
        for (int k = 0; k < wl; k++) if (zc[k] <= obl) ktup = k;
        int fB = ktup + 1;
        if (ktup >= 0 && fB >= 1 && fB < wl && obl > 0.0) {
            double ktupV = scalarDiffusivity(zc[ktup] / obl, obl, bf, ustar);
            double delta = (obl - zc[ktup]) / Math.max(1.0e-9, zc[ktup + 1] - zc[ktup]);
            delta = Math.max(0.0, Math.min(1.0, delta));
            double omd = 1.0 - delta;
            boolean lkteqkw = delta <= 0.5;                 // 界面在 OBL 之外
            double interior = Math.max(0.0, bgFace[fB]);
            double other = lkteqkw ? interior
                                   : scalarDiffusivity(Math.min(1.0, zf[fB] / obl), obl, bf, ustar);
            double enh = omd * omd * ktupV + delta * delta * other;
            kt[fB] = omd * (lkteqkw ? interior : interior) + delta * enh;
        }
        return kt;
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

    /**
     * ★ 2026-11-17（Stage G2 第 4 步 / P3）：**表层浮力通量** B0 [m²/s³]，由本模型自己的
     * 线性 EOS 导出（α、β 来自 {@link com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline}，
     * 无新常数、无新标定）：
     *
     * <pre>
     *   b = -g(ρ-ρ0)/ρ0 = g(α·ΔT − β·ΔS)
     *   ∂T/∂t|surf = Q/(ρ0·cp)                        （Q 正 = 进入海洋）
     *   ∂S/∂t|surf = −S·(E−P)/ρ0                      （淡水稀释；E−P 单位 kg/(m²·s)，P−E 正 = 加淡水）
     *   ⟹ B0 = g·[ α·Q/(ρ0·cp) + β·S·(E−P)/ρ0 ]
     * </pre>
     *
     * <p>**符号约定**（与 {@link #ws} 的 {@code bf<0 = 不稳定} 一致）：
     * 冷却（Q&lt;0）⟹ B0&lt;0 ⟹ 不稳定；加淡水（E−P&gt;0）⟹ B0&gt;0 ⟹ 稳定。
     * 量级自检：Q=−200 W/m²、E−P=0 ⟹ B0 ≈ −8e-8 m²/s³（本仓单柱门钉住）。
     */
    public static double surfaceBuoyancyFlux(double qNetWm2, double eMinusPKgm2s,
                                             double seaSurfaceSalinityPsu, double rho0, double cp) {
        return GRAV * (com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.ALPHA * qNetWm2 / (rho0 * cp)
                + com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.BETA * seaSurfaceSalinityPsu
                  * eMinusPKgm2s / rho0);
    }

    // ==================================================================================
    // P3 第 5 步：把面扩散率接进生产扩散算子（默认关 ⟹ 逐位不变）
    // ==================================================================================
    /** 本季的 KPP 面扩散率缓存，布局 [(j*nx+i)*(nz+1) + f]；仅 ON 且 KT_READY 时有效。 */
    public static double[] KT_FACE = null;
    /**
     * 缓存是否与当前状态/强迫一致。
     * <p>★ 2026-11-17：**包内可见**（不是 {@code public}）—— 它是内部缓存标志，不是生产开关，
     * 不应出现在 A7 的"生产端开关总表"里（switch_registry 只收 {@code public static boolean}）。
     * 探针请用 {@link #isReady()}。
     */
    static boolean KT_READY = false;

    /** 探针可读：本季 KPP 面系数缓存是否已准备好。 */
    public static boolean isReady() { return KT_READY; }
    private static int ktNx = -1, ktNy = -1, ktNz = -1;

    /**
     * 逐列准备 KPP 面扩散率（ON 时由 {@code AdvectionDiffusionSolver} 每季调用一次）。
     *
     * <p>⚠ 这是【季节快照】：OBL、表层浮力通量、摩擦速度都取自调用时刻的 T/S 与强迫。
     * 与 {@code VelocityProjection.FACES_READY} 同一类"季节性静态缓存"，每季重算。
     *
     * <p>⛔ <b>2026-11-17 实测：这些面系数【不能】接显式扩散。</b>
     * 生产 {@code PrognosticT} 的垂向扩散是显式的，而 Kt ~ 0.3 m²/s、dz=50 m、dt=8.6e4 s
     * ⟹ {@code dt·Kt/dz² ≈ 10 ≫ 1} ⟹ 全部湿格立刻 NaN（探针 {@code NKPPPROD} 实测 bad=50340）。
     * 现有 {@code cflSteps} 只约束平流、不约束扩散。
     * <p>⟹ 本方法目前**不接生产**，它是给"逐列隐式（三对角）扩散"准备输入的；
     * 隐式求解器的单柱原型见 {@code probe\NKPPCOL}（Thomas + 保热门，已 PASS）。
     * <p>默认 OFF 时直接置 {@code KT_READY=false}。
     */
    public static void prepareColumnKt(OceanGrid g, OceanState st,
                                       com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing f, double theta) {
        if (!ON) { KT_READY = false; return; }
        int nx = g.nx, ny = g.ny, nz = g.nz, stride = nz + 1;
        if (KT_FACE == null || ktNx != nx || ktNy != ny || ktNz != nz) {
            KT_FACE = new double[nx * ny * stride];
            ktNx = nx; ktNy = ny; ktNz = nz;
        }
        double[] bg = new double[stride];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                for (int k = 0; k < stride; k++)
                    bg[k] = (k < wl) ? AdvectionDiffusionSolver.kvAt(g, st, i, j, k) : 0.0;
                double obl = oblDepth(g, st, i, j);
                int x = (int) Math.round(g.xOf(i)), z = (int) Math.round(g.zOf(j));
                int q0 = st.idx(i, j, 0);
                double t0 = st.t[q0];
                double ice = st.iceFraction[j * nx + i];
                double q = f.netHeatFluxWm2(x, z, theta, t0, ice);
                double fw = f.freshwaterFluxKgM2S(x, z, theta, t0);
                double bf = surfaceBuoyancyFlux(q, fw, st.s[q0],
                        AdvectionDiffusionSolver.RHO0, AdvectionDiffusionSolver.CP_SEA);
                double tx = f.windStressX(x, z, theta), tz = f.windStressZ(x, z, theta);
                double ustar = Math.sqrt(Math.hypot(tx, tz) / 1025.0);
                double[] kt = columnKt(g.dz, wl, obl, bf, ustar, bg);
                int base = (j * nx + i) * stride;
                for (int k = 0; k <= wl; k++) KT_FACE[base + k] = kt[k];
            }
        KT_READY = true;
    }

    /** Nonlocal scalar term: only unstable (Bf<0); Cs*G2(sigma). */
    public static double nonlocalScalar(double sigma, double bf) {
        if (!(bf < 0.0)) return 0.0;
        return nonlocalCoeff() * nonlocalShape(sigma);
    }

    /**
     * ★ 2026-11-18（A1，出处已取得）：**非局部通量** {@code K·γ_s(σ) = C_s·G(σ)·Q_s}。
     *
     * <p>出处：MOM6 文档 "The K-Profile Parameterization"（CVMix_KPP 页，2026-11-18 抓取）：
     * <blockquote>
     * {@code overline{w's'} = -K ∂_z s + K γ_s(σ)}；{@code K = h w_s(σ) G(σ)}；
     * "the entire non-local transport term can be equivalently written
     *  {@code K γ_s(σ) = C_s G(σ) Q_s}"，其中 {@code Q_s} 是 s 的表面通量、{@code C_s} 是常数；
     * 形状 {@code G(σ) = σ(1−σ)²}（{@code G(0)=G(1)=0}）。
     * </blockquote>
     *
     * <p>本仓的 {@link #nonlocalCoeff()} 就是那个 {@code C_s}（CVMix 的 nonlocal_coeff 默认值），
     * 形状用 {@link #gradientShape}（{@code σ(1−σ)²}，与出处一致；见交接 §18.3 的更正）。
     *
     * <p>返回的是**通量**（与被扩散标量同量纲 × m/s）；趋势由调用方取通量散度得到：
     * {@code ∂s/∂t |= -(F_{k+1/2} - F_{k-1/2})/dz}。因为 G(0)=G(1)=0，柱积分恒为 0（纯重分布）。
     *
     * @param sigma  归一化深度 z/OBL ∈ [0,1]
     * @param qSurf  该标量的表面通量（T: Q/(ρc_p) [K·m/s]；S: 淡水通量等效 [psu·m/s]）
     */
    public static double nonlocalFlux(double sigma, double qSurf) {
        return nonlocalCoeff() * gradientShape(sigma) * qSurf;
    }

    /**
     * 逐列非局部趋势 [标量/s]（只对 OBL 内的面计算；G(0)=G(1)=0 ⟹ 柱积分恒为 0）。
     *
     * @return 长度 wl 的趋势数组（未初始化部分为 0）
     */
    public static double[] nonlocalTendency(double[] dz, int wl, double obl, double qSurf) {
        double[] tend = new double[wl];
        if (!(obl > 0.0)) return tend;
        // 面通量：面 f 在深度 zf；σ = zf/obl；OBL 外通量为 0（G 的定义域只有 [0,1]）
        double[] flux = new double[wl + 1];
        double z = 0.0;
        for (int f = 0; f <= wl; f++) {
            double sig = z / obl;
            flux[f] = (sig >= 0.0 && sig <= 1.0) ? nonlocalFlux(sig, qSurf) : 0.0;
            if (f < wl) z += dz[f];
        }
        for (int k = 0; k < wl; k++) tend[k] = -(flux[k + 1] - flux[k]) / dz[k];
        return tend;
    }
}
