package com.EyeOfHarmonyBuffer.sim.atmos;

/**
 * §498 ★★★★★★★ **气块抬升：LCL / LFC / LNB / CAPE / CIN —— 把 `M` 里那个硬编码的
 * 「FT 层 = H_EFF/2 = 6000 m」换成【模型自己的热力学算出来的中性浮力层】。**
 *
 * <h3>为什么这是「真算法」而不是又一个旋钮</h3>
 * <pre>
 * 旧：dh = cp*GAMMA*zFT - g*zFT + L*q*(1-exp(-zFT/H_MOIST))，zFT = M_FT_FRAC*H_EFF
 *     ⇒ 符号完全由 zFT 决定（q &gt; 8.46 g/kg 才 M&gt;0）⇒ 不是良定义的物理量（§482）
 * 新：把气块从地面【真抬升】，用模型自己的饱和曲线求 LCL，沿伪绝热上升，
 *     取【浮力为零的高度】z_LNB 作为与自由对流层交换的高度
 *     ⇒ 零自由参数；M 的符号由气块自己的浮力决定
 * </pre>
 *
 * <h3>环境廓线——【不引入任何新常数】</h3>
 * <pre>
 * T_env(z) = tS - GAMMA*z              （GAMMA = 6.5e-3 是模型已有的递减率，
 *                                       已被 tTh = tS - GAMMA*H_BL 使用）
 * q_env(z) = q_s*exp(-z/H_MOIST)       （H_MOIST = 2000 m 是模型已有的水汽标高，
 *                                       已被 depl 与旧 M 公式使用）
 * p(z)     = P0*(1 - GAMMA*z/tS)^(g/(R_d*GAMMA))   （静力 + 线性 T ⇒ 多元大气，解析）
 * </pre>
 * ⚠ 诚实记账：模型没有显式垂直廓线，所以上面这条廓线**是一个建模陈述**。
 * 它之所以不算「新自由度」，是因为它只用模型【已经承诺过】的两个量 (GAMMA, H_MOIST)，
 * 且 p(z) 的指数 g/(R_d*GAMMA) = 5.2561 与标准大气的 5.2559 吻合（这是对廓线的独立校验）。
 *
 * <h3>文献（逐条可查）</h3>
 * <ul>
 *   <li><b>饱和水汽压</b> Bolton (1980) <i>Mon. Wea. Rev.</i> <b>108</b>, 1046-1053, Eq.(10)：
 *       `es = 6.112*exp(17.67*Tc/(Tc+243.5))` hPa。<b>与 {@link PrecipField#qSat} 的
 *       611.2*exp(17.67*Tc/(Tc+243.5)) Pa 逐字相同</b> ⇒ 气块与模型用同一条饱和曲线。</li>
 *   <li><b>露点反演</b> 同上 Eq.(11) 的逆：`Tc = 243.5*ln(e/6.112)/(17.67-ln(e/6.112))`。
 *       （MetPy `dewpoint` 逐字采用此式并标注 [Bolton1980]_。）</li>
 *   <li><b>伪绝热递减率</b> Bakhshaii &amp; Stull (2013) <i>Atmos. Res.</i> <b>132-133</b>, 460-472：
 *       `dT/dP = (1/P)*(R_d*T + L_v*r_s)/(C_pd + L_v^2*r_s*eps/(R_d*T^2))`。
 *       （MetPy `moist_lapse` 逐字采用此式并标注 [Bakhshaii2013]_。）</li>
 *   <li><b>LCL 温度</b> Bolton (1980) Eq.(15)：
 *       `t_LCL = 56 + 1/(1/(td-56) + ln(t/td)/800)` [degC]。
 *       独立交叉校验用 Davies-Jones (1983) <i>Mon. Wea. Rev.</i> <b>111</b>, 2370-2375
 *       （<b>IFS 采用的那条</b>，见 earthkit-meteo `lcl_temperature(method="davies")`）：
 *       `t_LCL = td - (0.212 + 1.571e-3*(td-t0) - 4.36e-4*(t-t0))*(t-td)`, t0 = 273.16 K。</li>
 *   <li><b>CAPE/CIN 口径</b> MetPy `cape_cin`：CIN 在 sfc→LFC 积分，CAPE 在 LFC→EL 积分。</li>
 * </ul>
 *
 * <p>⚠ <b>本类不接线任何东西</b>（纯静态、无状态、无开关）⇒ 对 `P293` 影响【结构上为零】。
 */
public final class ParcelLift {

    private ParcelLift() {}

    // ---- 标准物理常数（全部是教科书常数，不是拟合量）----
    /** 干空气气体常数 J/(kg K)。 */
    public static final double R_D = 287.05;
    /** 水汽气体常数 J/(kg K)。 */
    public static final double R_V = 461.5;
    /** 分子量比 R_d/R_v。 */
    public static final double EPS = 0.622;
    /** 干空气定压比热 J/(kg K)，复用 {@link Radiation#CP}。 */
    public static final double CP_D = Radiation.CP;
    /** 汽化潜热 J/kg，复用 {@link Radiation#LV}。 */
    public static final double LV = Radiation.LV;
    /** 水三相点 K（Davies-Jones 公式里的 t0）。 */
    public static final double T_TRIPLE = 273.16;
    /** 地面气压 Pa，复用 {@link PrecipField#P_SURF}。 */
    public static final double P0 = PrecipField.P_SURF;
    /** 重力 m/s^2，复用 {@link PrecipField#G_ACC}。 */
    public static final double G = PrecipField.G_ACC;
    /** 干绝热递减率 K/m = g/cp = 9.768e-3。 */
    public static final double GAMMA_D = G / CP_D;

    // ---- 饱和曲线：与 PrecipField.qSat 同式，只把气压参数化 ----

    /** 饱和水汽压 Pa。Bolton(1980) Eq.(10)，与 {@link PrecipField#qSat} 逐字相同。 */
    public static double es(double tK) {
        double tc = tK - 273.15;
        return 611.2 * Math.exp(17.67 * tc / (tc + 243.5));
    }

    /** 饱和【混合比】kg/kg：`eps*es/(p-es)`（递减率公式里用的是 r_s，不是 q_s）。 */
    public static double rs(double tK, double pPa) {
        double e = es(tK);
        if (e >= pPa * 0.999) e = pPa * 0.999;
        return EPS * e / (pPa - e);
    }

    /** 饱和【比湿】kg/kg。`p = P_SURF` 时与 {@link PrecipField#qSat} 完全一致。 */
    public static double qs(double tK, double pPa) {
        double e = es(tK);
        if (e >= pPa * 0.999) e = pPa * 0.999;
        return EPS * e / (pPa - (1.0 - EPS) * e);
    }

    /** 由比湿与水汽压互推（比湿定义的反解）：`e = q*p/(eps + (1-eps)*q)`。 */
    public static double vaporPressure(double q, double pPa) {
        return q * pPa / (EPS + (1.0 - EPS) * q);
    }

    /** 露点 K。Bolton(1980) Eq.(11) 的反解，e 单位 Pa。 */
    public static double dewpoint(double ePa) {
        double v = Math.log(ePa / 611.2);
        return 273.15 + 243.5 * v / (17.67 - v);
    }

    // ---- LCL：两条独立公式（互相交叉校验）----

    /**
     * LCL 温度 K —— Bolton(1980) Eq.(15)。
     *
     * <p>⚠⚠ **"56" 是【开尔文】里的 56，不是摄氏度。** 首版误按摄氏度实现，被本类的
     * {@link #lclTempDaviesJones} 交叉校验抓出（§498）：
     * <pre>
     *   摄氏度版：300K/20g/kg 得 298.344（DJ 298.036，差 0.31 K）；
     *             310K/5g/kg  得 268.318（DJ 270.480，差 2.16 K）；
     *             295K/2g/kg  直接 NaN（ln(t/td) 的自变量为负）
     *   开尔文版：300K/20g/kg 得 298.0336（DJ 298.036，差 0.002 K）
     *             310K/5g/kg  得 270.484 （DJ 270.480，差 0.004 K）
     *             295K/2g/kg  得 259.030 （DJ 259.036，差 0.006 K）
     * </pre>
     * **两条【独立发表】的公式在毫开尔文量级吻合 ⇒ 只有开尔文解释成立。**
     */
    public static double lclTempBolton(double tK, double tdK) {
        return 56.0 + 1.0 / (1.0 / (tdK - 56.0) + Math.log(tK / tdK) / 800.0);
    }

    /** LCL 温度 K —— Davies-Jones(1983)，即 **IFS 采用的那条**（独立校验用）。 */
    public static double lclTempDaviesJones(double tK, double tdK) {
        double t = tK, td = tdK, t0 = T_TRIPLE;
        double a = 0.212 + 1.571e-3 * (td - t0) - 4.36e-4 * (t - t0);
        return td - a * (t - td);
    }

    /**
     * 伪绝热递减率的【气压梯度形式】：`dT/dlnP`。Bakhshaii &amp; Stull (2013)。
     * 注意它是**正数**：气压下降时温度下降。
     */
    public static double dTdlnp(double tK, double pPa) {
        double r = rs(tK, pPa);
        return (R_D * tK + LV * r) / (CP_D + LV * LV * r * EPS / (R_D * tK * tK));
    }

    /** 湿绝热递减率 K/m（诊断用）：`-dT/dlnp * g/(R_d*T_v)`。 */
    public static double gammaMoist(double tK, double pPa, double q) {
        double tv = tK * (1.0 + 0.608 * q);
        return dTdlnp(tK, pPa) * G / (R_D * tv);
    }

    /** 多元大气环境气压 Pa。静力 + `T = tS - gamma*z` 的解析解；指数 g/(R_d*gamma) = 5.2561。 */
    public static double pOfZ(double z, double tSfc, double gammaEnv) {
        double f = 1.0 - gammaEnv * z / tSfc;
        if (f <= 1.0e-6) return 0.0;
        return P0 * Math.pow(f, G / (R_D * gammaEnv));
    }

    /** 一元大气高度的反解：由 LCL 气压求 LCL 几何高度 m。 */
    public static double zOfP(double pPa, double tSfc, double gammaEnv) {
        if (pPa <= 0.0) return Double.NaN;
        double f = Math.pow(pPa / P0, R_D * gammaEnv / G);
        return (tSfc / gammaEnv) * (1.0 - f);
    }

    /** 一步 RK4 沿 `ln p` 积分伪绝热（从 p1 到 p2，p2 &lt; p1）。 */
    public static double rk4T(double t, double p1, double p2) {
        double l1 = Math.log(p1), h = Math.log(p2) - l1;
        double k1 = dTdlnp(t, p1);
        double k2 = dTdlnp(t + 0.5 * h * k1, Math.exp(l1 + 0.5 * h));
        double k3 = dTdlnp(t + 0.5 * h * k2, Math.exp(l1 + 0.5 * h));
        double k4 = dTdlnp(t + h * k3, p2);
        return t + h * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0;
    }

    /**
     * 沿湿伪绝热从 `(tStart, pStart)` 积分到 `pEnd`（pEnd &lt; pStart），`steps` 步 RK4。
     *
     * <p>§499：Betts & Miller (2004) 的深对流【参考廓线】就是这条伪绝热，所以把它做成公开入口，
     * 供探针构造廓线用。**纯函数，不改变任何现有分支。**
     */
    public static double moistAdiabatT(double tStart, double pStart, double pEnd, int steps) {
        if (steps < 1) steps = 1;
        if (pEnd >= pStart) return tStart;
        double l1 = Math.log(pStart), l2 = Math.log(pEnd);
        double t = tStart;
        // 在 ln p 上等步长推进，保证低层的分辨率
        double lp = l1;
        for (int i = 1; i <= steps; i++) {
            double lpNext = l1 + (l2 - l1) * i / steps;
            t = rk4T(t, Math.exp(lp), Math.exp(lpNext));
            lp = lpNext;
        }
        return t;
    }

    /** 湿伪绝热上温度等于 `tTarget` 时的【几何高度】m（用于求对流层顶/云顶）。 */
    public static double zOfMoistAdiabatT(double tSfcK, double qSfc, double tTarget,
                                          double gammaEnv, double zMax, double dz) {
        Result r = lift(tSfcK, qSfc, gammaEnv, PrecipField.H_MOIST, zMax, dz);
        double best = Double.NaN;
        int n = (int) Math.round(zMax / dz);
        // 复用 lift 的积分器：逐层重算（探针用途，代价可忽略）
        double tParPrev = tSfcK, pPrev = P0, t = tSfcK;
        for (int i = 0; i < n; i++) {
            double z = (i + 1) * dz;
            double p = pOfZ(z, tSfcK, gammaEnv);
            if (p <= 0.0) break;
            if (z <= r.zLcl) {
                t = tSfcK * Math.pow(p / P0, R_D / CP_D);
                tParPrev = t; pPrev = p;
            } else {
                t = (pPrev > r.pLcl) ? rk4T(r.tLcl, r.pLcl, p) : rk4T(tParPrev, pPrev, p);
                tParPrev = t; pPrev = p;
            }
            if (t <= tTarget) { best = z; break; }
        }
        return best;
    }

    /** 抬升结果。 */
    public static final class Result {
        public double tSfc, qSfc, tdSfc, tLcl, tLclDJ, pLcl, zLcl;
        public double zLfc, zLnb, cape, cin, buoyMin, buoyMax, zBuoyMax;
        public double hBl, hEnvLnb, hParLnb, mParcel, mUpdraft;
        public double gammaSfc, pLnb, tEnvLnb, qEnvLnb, tParLnb;
        public int nPos, nSteps;
        /** 有 LFC 且 CAPE &gt; 0 ⇒ 该点【物理上可以深对流】。 */
        public boolean convective;

        public String line() {
            return String.format(java.util.Locale.ROOT,
                "tS=%.3f q=%.6f td=%.3f | LCL T=%.3f(DJ %.3f) p=%.1f z=%.1f | LFC=%s LNB=%s "
                + "CAPE=%s CIN=%s | M_par=%.6e M_up=%.6e | conv=%s",
                tSfc, qSfc, tdSfc, tLcl, tLclDJ, pLcl, zLcl,
                Double.isNaN(zLfc) ? "none" : String.format(java.util.Locale.ROOT, "%.1f", zLfc),
                Double.isNaN(zLnb) ? "none" : String.format(java.util.Locale.ROOT, "%.1f", zLnb),
                Double.isNaN(cape) ? "none" : String.format(java.util.Locale.ROOT, "%.2f", cape),
                Double.isNaN(cin) ? "none" : String.format(java.util.Locale.ROOT, "%.2f", cin),
                mParcel, mUpdraft, Boolean.toString(convective));
        }
    }

    /**
     * 真抬升。**零自由参数**。
     *
     * @param tSfcK     地面温度 K
     * @param qSfc      地面比湿 kg/kg
     * @param gammaEnv  环境递减率 K/m（模型已有：Atmosphere.GAMMA）
     * @param hMoist    水汽标高 m（模型已有：PrecipField.H_MOIST）
     * @param zMax      抬升到多高 m
     * @param dz        步长 m
     */
    public static Result lift(double tSfcK, double qSfc, double gammaEnv, double hMoist,
                              double zMax, double dz) {
        Result r = new Result();
        r.tSfc = tSfcK; r.qSfc = qSfc;
        double pSfc = P0;
        double e = vaporPressure(qSfc, pSfc);
        r.tdSfc = dewpoint(e);
        r.tLcl = lclTempBolton(tSfcK, r.tdSfc);
        r.tLclDJ = lclTempDaviesJones(tSfcK, r.tdSfc);
        r.pLcl = pSfc * Math.pow(r.tLcl / tSfcK, CP_D / R_D);
        r.zLcl = zOfP(r.pLcl, tSfcK, gammaEnv);
        r.gammaSfc = gammaMoist(tSfcK, pSfc, qSfc);
        r.hBl = CP_D * tSfcK + LV * qSfc;

        int n = (int) Math.round(zMax / dz);
        double[] zz = new double[n], bu = new double[n];
        double[] tP = new double[n], qP = new double[n], pP = new double[n];
        double tParPrev = tSfcK, pPrev = pSfc;
        r.buoyMin = Double.MAX_VALUE; r.buoyMax = -Double.MAX_VALUE; r.zBuoyMax = 0.0;
        int nSteps = 0;
        for (int i = 0; i < n; i++) {
            double z = (i + 1) * dz;
            double p = pOfZ(z, tSfcK, gammaEnv);
            if (p <= 0.0) { zz[i] = z; bu[i] = 0.0; tP[i] = 0.0; qP[i] = 0.0; pP[i] = p; continue; }
            double tEnv = tSfcK - gammaEnv * z;
            double qEnv = qSfc * Math.exp(-z / hMoist);
            double qsEnv = qs(tEnv, p);
            if (qEnv > qsEnv) qEnv = qsEnv;
            double tvEnv = tEnv * (1.0 + 0.608 * qEnv);

            double tPar, qPar;
            if (z <= r.zLcl) {
                tPar = tSfcK * Math.pow(p / pSfc, R_D / CP_D);
                qPar = qSfc;
                tParPrev = tPar; pPrev = p;
            } else {
                tPar = (pPrev > r.pLcl) ? rk4T(r.tLcl, r.pLcl, p) : rk4T(tParPrev, pPrev, p);
                double qsl = qs(tPar, p);
                qPar = (qsl < qSfc) ? qsl : qSfc;
                tParPrev = tPar; pPrev = p;
            }
            double tvPar = tPar * (1.0 + 0.608 * qPar);
            double b = G * (tvPar - tvEnv) / tvEnv;
            zz[i] = z; bu[i] = b; tP[i] = tPar; qP[i] = qPar; pP[i] = p;
            nSteps++;
            if (b < r.buoyMin) r.buoyMin = b;
            if (b > r.buoyMax) { r.buoyMax = b; r.zBuoyMax = z; }
            if (b > 0.0) r.nPos++;
        }
        r.nSteps = nSteps;

        // ---- LFC = LCL 之上【最低】的浮力正区间起点；LNB = 该区间之后最高的正点 ----
        int iLfc = -1;
        for (int i = 0; i < n; i++) {
            if (zz[i] > r.zLcl && bu[i] > 0.0) { iLfc = i; break; }
        }
        r.zLfc = (iLfc < 0) ? Double.NaN : zz[iLfc];
        r.zLnb = Double.NaN; r.cape = Double.NaN; r.cin = Double.NaN;
        r.mParcel = Double.NaN; r.mUpdraft = Double.NaN;
        r.hEnvLnb = Double.NaN; r.hParLnb = Double.NaN; r.pLnb = Double.NaN;
        r.tEnvLnb = Double.NaN; r.qEnvLnb = Double.NaN; r.tParLnb = Double.NaN;
        r.convective = false;
        if (iLfc >= 0) {
            int iLnb = iLfc;
            for (int i = iLfc; i < n; i++) { if (bu[i] > 0.0) iLnb = i; else break; }
            r.zLnb = zz[iLnb];
            double cape = 0.0, cin = 0.0;
            for (int i = iLfc; i <= iLnb; i++) cape += bu[i] * dz;
            for (int i = 0; i < iLfc; i++) cin += bu[i] * dz;
            r.cape = cape; r.cin = cin;
            r.convective = cape > 0.0;
            double zL = zz[iLnb];
            r.tEnvLnb = tSfcK - gammaEnv * zL;
            r.qEnvLnb = qSfc * Math.exp(-zL / hMoist);
            double qsE = qs(r.tEnvLnb, pP[iLnb]);
            if (r.qEnvLnb > qsE) r.qEnvLnb = qsE;
            r.hEnvLnb = CP_D * r.tEnvLnb + G * zL + LV * r.qEnvLnb;
            r.tParLnb = tP[iLnb]; r.pLnb = pP[iLnb];
            r.hParLnb = CP_D * tP[iLnb] + G * zL + LV * qP[iLnb];
            r.mParcel = r.hBl - r.hEnvLnb;
            r.mUpdraft = r.hBl - r.hParLnb;
        }
        return r;
    }

    /** 便捷：用模型的默认环境参数抬升。 */
    public static Result lift(double tSfcK, double qSfc) {
        return lift(tSfcK, qSfc, Atmosphere.GAMMA, PrecipField.H_MOIST, 20000.0, 20.0);
    }
}
