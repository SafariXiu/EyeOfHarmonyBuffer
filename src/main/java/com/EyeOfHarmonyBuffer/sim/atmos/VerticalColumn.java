package com.EyeOfHarmonyBuffer.sim.atmos;

/**
 * §500 ★★★★★★★ **完整垂直维度：单柱辐射—对流平衡（RCE）内核。**
 *
 * <p>「甲″」的地基。**不接线任何东西**（纯静态配置 + 实例状态）⇒ 对 `P293` 影响结构上为零。
 *
 * <h3>为什么用【气压坐标】（这是守恒性的前提，不是口味问题）</h3>
 * <pre>
 * 层质量 MASS[k] = (p_edg[k+1] − p_edg[k]) / g    ← 【常数】，不随 T 变
 * ⇒ 温度变化不会改变层质量，柱能量才可能逐位闭合。
 * 几何网格上 ρ 必须由 T 反诊断 ⇒ 每步改一次质量 ⇒ 收支【结构性地】合不上。
 * </pre>
 *
 * <h3>守恒量：`∫(cp*T + L*q) dp/g`（★ 不含 g*z）</h3>
 * <pre>
 * 气压坐标下的湿静力能收支：
 *   ∂/∂t(cpT+Lq) + ∇·(…) + ∂/∂p(ω(cpT+Lq)) = Q_rad + Q_surf
 * 本柱无水平通量，且 ω = 0 于 p_sfc（刚性地表）与 p_top ⇒ 【位势通量项两端都消失】
 * ⇒ dH/dt = Ra + SH + LH − L*P        （H = Σ MASS[k]*(cp*T_k + L*q_k)）
 * ⇒ 凝结会【逐位】守恒 H：q 少 L*dm，T 多 L*dm。
 * </pre>
 *
 * <h3>辐射必须【逐边望远镜化】</h3>
 * <pre>
 * 用层【边界】的通量 + 吸收率 (1−e^{−Δτ})：
 *   F↓(k+1) = F↓(k)*T_k + B_k*(1−T_k),   F↓(TOA) = 0
 *   F↑(k)   = F↑(k+1)*T_k + B_k*(1−T_k), F↑(sfc) = B_s
 * 层净加热 = (F↑(k) − F↑(k+1)) + (F↓(k+1) − F↓(k))
 * ⇒ Σ_k 层净加热 = (B_s − OLR) + F↓_sfc   【严格恒等】
 * 用【层中心】通量做差分则【不望远镜化】，Σ ≠ 边界收支 ⇒ 收支永远合不上。
 * </pre>
 *
 * <h3>四个算子</h3>
 * <ol>
 *   <li><b>灰体双流辐射</b>（Frierson/Held/Zurita-Gotor 2006 的结构）。τ0LW 由
 *       {@link #calibrateTauLW} 标定到模型【已有】的 {@link StationaryWave#olrClear} 闭合 ⇒ 不是自由参数。</li>
 *   <li><b>对流调整</b>（Betts &amp; Miller 1986/2004）。<b>做成【H 守恒】</b>：约定
 *       「对流只沿垂直方向搬运热与水汽、不改变柱湿静力能；降水把水带走」——
 *       这正是质量通量在上下边界为零的必然结果，也是 Betts-Miller 里
 *       "Correction of reference profiles to satisfy enthalpy constraint" 那一条。
 *       <pre>q 松弛到 q_ref ⇒ 变干的部分成为降水 P_conv；其潜热 L*P_conv 全部还给 T
 *       T 松弛到 T_ref ⇒ 只取【形状】，减去质量加权均值使其柱积分为 0</pre></li>
 *   <li><b>大尺度凝结</b>：q &gt; q_s ⇒ 凝结 + 潜热还给 T（H 守恒），凝结物立即降落。</li>
 *   <li><b>地—气体块通量</b>：SH/LH 注入最底层。</li>
 * </ol>
 */
public final class VerticalColumn {

    // ---- 气压网格 ----
    public static final int N = 60;
    /**
     * 柱顶气压（Pa）。5000 Pa ≈ 35 km（含整个平流层）。
     * ⚠ §500 记账：首版用 P_TOP=1000 Pa 且 `p_edg = P_TOP+Δp*s²` —— 那个拉伸把
     * 【顶层】做薄了（Δp(0) = 2Δp/N），顶层质量只有 2.84 kg/m² ⇒ 辐射加热除以它
     * 出现巨大 ΔT ⇒ NaN。**改成【均匀 Δp】**：层质量处处相等 = 最稳、也最利于守恒。
     */
    public static final double P_TOP = 5000.0;
    /** 层边气压 p_edg[0] = P_TOP（顶），p_edg[N] = P_SURF（底）。 */
    public static final double[] P_EDG = new double[N + 1];
    /** 层中心气压。 */
    public static final double[] PC = new double[N];
    /** 层质量 kg/m² —— 【常数】。 */
    public static final double[] MASS = new double[N];
    static {
        for (int i = 0; i <= N; i++) {
            double s = (double) i / N;
            P_EDG[i] = P_SURF_TOP_LIN(s);
        }
        for (int k = 0; k < N; k++) {
            PC[k] = 0.5 * (P_EDG[k] + P_EDG[k + 1]);
            MASS[k] = (P_EDG[k + 1] - P_EDG[k]) / PrecipField.G_ACC;
        }
    }
    public static final double THETA_SB = 5.670374419e-8;

    /** 均匀 Δp 的层边气压。 */
    private static double P_SURF_TOP_LIN(double s) {
        return P_TOP + (PrecipField.P_SURF - P_TOP) * s;
    }

    // ---- 辐射配置 ----
    public static double TAU0_LW = 6.0;
    public static double F_WELLMIXED = 0.10;
    /**
     * §503 ★★★★★★★ **水汽反馈**：参考柱的水汽路径（kg/m²）。
     *
     * <p>长波光学厚度的物理形式是【质量路径】，不是气压：
     * <pre>
     *   tau(p) = k_m*(p/g) + k_w*W(p),   W(p) = ∫_0^p q dp'/g      ← 柱内【实际】水汽
     * </pre>
     * 旧的固定廓线（只按气压算的四次式）⇒ **柱里水汽变多，光学厚度不变**
     * ⇒ 没有水汽反馈 ⇒ P702 实测 ∂OLR/∂Ts = 6.230（物理期望 2~4，Planck 参照 4.600）。
     *
     * <p>标定：TAU0_LW 仍是【唯一】可标定常数，它约束的是【参考柱】的总光学厚度：
     * <pre>
     *   K_M = TAU0_LW*F_WELLMIXED / MASS_total        （充分混合气体，正比于 p）
     *   K_W = TAU0_LW*(1-F_WELLMIXED) / W_REF         （水汽，正比于实际水汽路径）
     * </pre>
     * ⇒ 在参考柱上总 tau 与旧格式【相同】（olrClear 标定关系不变），
     *   但**只要柱内水汽变了，tau 就跟着变** —— 这才是缺的那条反馈。
     *
     * <p>⚠ 诚实记账：W_REF 是这一条里的建模选择。取 50 kg/m² 的依据是物理量级：
     * 湿热带柱 q_sfc≈20 g/kg、水汽标高≈2 km、气压标高≈8.8 km
     * ⇒ W ≈ 0.020*(101300/9.807)*(2/8.8) ≈ 47 kg/m²。**它不是拟合出来的**；
     * 若将来要标定，应与 TAU0_LW 【联合】标定到 olrClear。
     */
    public static double W_REF = 50.0;
    /** 充分混合气体的质量吸收系数（由 TAU0_LW 与 F_WELLMIXED 导出，非独立参数）。 */
    public static double K_M = 0.0;
    /** 水汽的质量吸收系数（由 TAU0_LW、F_WELLMIXED、W_REF 导出，非独立参数）。 */
    public static double K_W = 0.0;

    /** 由 (TAU0_LW, F_WELLMIXED, W_REF) 导出两个吸收系数。轻量，调用于 radiation() 开头。 */
    public static void setupOptics() {
        // ★★★★★★★ §504：模型【自己】早就有这个常数，不该另立一套。
        //   StationaryWave.olrClear 的单层灰体是
        //       OLR = sigma*Ts^4*e^{-k*CWV} + (1-e^{-k*CWV})*sigma*T_ft^4
        //   其中 OLR_K = 0.0667 m^2/kg 【就是水汽的宽带质量吸收系数】—— 与 K_W 同一个物理量。
        //   从 (TAU0_LW, W_REF) 反推出来的是 0.108，比模型自己的值大 1.6 倍
        //   （正是 §503 里手算发现的 ~1.7 倍偏差的来源）。
        //   ⇒ 直接【复用】模型已有的常数；且模型闭合里没有充分混合气体项 ⇒ K_M = 0。
        //   ⇒ 长波光学厚度从此【零新常数】：tau(p) = OLR_K * W(p)。
        K_W = StationaryWave.OLR_K;
        // ★ §504 记账：这里【曾经】无条件写 K_M = 0.0，而 setupOptics() 每个 radiation() 子步都调一次
        //   ⇒ 探针赋的 K_M 立刻被清掉：二分时四个不同 K_M 给出【完全相同】的 OLR=336.621 / tau=5.050，
        //     整列标定结果是废的（是仪器 bug，不是物理结论）。K_M 改由字段初值 0.0 提供，此处不再覆盖。
    }

    /** 柱水汽路径 CWV（kg/m²）—— §504 诊断。 */
    public double cwv() {
        double w = 0.0;
        for (int k = 0; k < N; k++) w += MASS[k] * q[k];
        return w;
    }
    public static double TAU0_SW = 0.22;
    public static double ALBEDO = 0.12;
    public static double S0 = 1361.0;
    public static double LW_SFC_EMIS = 1.0;

    // ---- 对流 ----
    public static double TAU_ADJ = 7200.0;
    /**
     * §510 ★★★ Betts-Miller 参考廓线的稳定性权重。
     *
     * <p>**由 0.9 改为 1.0（零自由参数）**，理由两条，互相独立：
     * <ol>
     *   <li><b>物理</b>：Betts &amp; Miller (2004) 逐字写明「系数 0.9 对应【湿虚拟绝热】的斜率，
     *       0.85 是更不稳定的『折中值』」。**0.9 的存在意义是在【忽略虚温】的方案里代偿虚温效应。**
     *       而本内核的 ParcelLift 是【显式】计算 T_v = T*(1+0.608q) 的
     *       ⇒ 取 0.9 就是【重复计账】。取 1.0 = 纯湿伪绝热 = 中性廓线。</li>
     *   <li><b>实测</b>（P707，Ts=300）：W_STAB 越大 Γmax 越小 —— 因为式 (8) 里那个随高度
     *       衰减的偏移 (tRF − tcF) = (1−W_STAB)*(tB − tcF) ≈ +3.2 K（W_STAB=0.9 时）
     *       会额外贡献一个递减率，把 (e) 顶成超绝热：
     *       <pre>0.85 → 10.18 / 0.90 → 9.96 / 0.95 → 9.73 / 1.00 → 9.72 K/km（Γ_d = 9.768）</pre></li>
     * </ol>
     * ⇒ 0.95 与 1.00 都能让 (e) 通过；取 **1.0**，因为它是唯一【不需要辩护】的值。 */
    public static double W_STAB = 1.0;
    public static double RH_B = 0.90, RH_M = 0.70, RH_T = 0.50;

    // ---- 状态 ----
    public final double[] t = new double[N];
    public final double[] q = new double[N];
    public final double[] z = new double[N];
    public double tSfc, pSfc = PrecipField.P_SURF;
    public double chv = 1.2e-3, beta = 1.0, windSpeed = 6.0;

    // ---- 诊断 ----
    public double olr, asr, lwSfcDown, swSfcDown, sh, lh, ra;
    /** 本柱当前的总长波光学厚度（§503 诊断：水汽反馈的证据）。 */
    public double lastTauTot;
    /** 辐射子步的 ra 累加器（★ 必须在 step 里清零：radiation() 每调一次都会重算 ra）。 */
    public double raAccum;
    public double precipConv, precipLs;
    public double dHdtMeasured;
    /** 本步的 dt（s）。★ precipConv/precipLs 是【每步质量】不是通量，算收支时必须除以 dt。 */
    public double lastDt = 1.0;
    /** 首次出现非有限值的步号与层号（-1 = 从未出现）。 */
    public long firstBadStep = -1;
    public int firstBadLayer = -1;

    public VerticalColumn() {}

    /** 用地面条件初始化（初值不重要，RCE 会忘掉它）。 */
    public void init(double tSfcK, double qSfcKgKg, double betaIn, double wind) {
        tSfc = tSfcK; beta = betaIn; windSpeed = wind;
        // ⚠ §500 记账：首版初值是【干绝热】p^κ ⇒ 顶层 80 K，辐射立刻炸。
        // 改用【等温标高估 z + 模型自己的线性递减率 + 200 K 底】，再夹到饱和以内。
        double hScale = ParcelLift.R_D * tSfcK / PrecipField.G_ACC;
        for (int k = 0; k < N; k++) {
            double pr = PC[k] / pSfc;
            double zGuess = -hScale * Math.log(pr);
            t[k] = Math.max(200.0, tSfcK - Atmosphere.GAMMA * zGuess);
            double qsK = ParcelLift.qs(t[k], PC[k]);
            double qg = qSfcKgKg * pr * pr * pr;
            q[k] = (qg < qsK) ? qg : qsK;
        }
        diagnoseGeopotential();
    }

    /** 静力积分求层中心位势高度（用于 g*z 与对流层顶判据）。 */
    public void diagnoseGeopotential() {
        double zz = 0.0;
        for (int k = N - 1; k >= 0; k--) {
            double tv = t[k] * (1.0 + 0.608 * q[k]);
            double dz = ParcelLift.R_D * tv / PrecipField.G_ACC
                      * Math.log(P_EDG[k + 1] / P_EDG[k]);
            z[k] = zz + 0.5 * dz;
            zz += dz;
        }
    }

    /** 柱湿静力能 H = Σ MASS*(cp*T + L*q)，J/m²。**不含 g*z**（见类注释）。 */
    public double columnEnergy() {
        double H = 0.0;
        for (int k = 0; k < N; k++) H += MASS[k] * (Radiation.CP * t[k] + Radiation.LV * q[k]);
        return H;
    }

    // ==================== 算子 1：灰体双流辐射（逐边望远镜化）====================

    private double tauOf(double pPa) {
        double s = pPa / pSfc;
        return TAU0_LW * (F_WELLMIXED * s + (1.0 - F_WELLMIXED) * s * s * s * s);
    }
    private double tauSwOf(double pPa) {
        double s = pPa / pSfc;
        return TAU0_SW * s * s * s;
    }

    /** 计算辐射：逐层加热写入 t[]，并给出 OLR/ASR/地面辐射。`cosZenith` 用于短波。 */
    public void radiation(double cosZenith, double dtRad) {
        double[] b = new double[N];
        for (int k = 0; k < N; k++) b[k] = THETA_SB * Math.pow(t[k], 4.0);
        double bS = LW_SFC_EMIS * THETA_SB * Math.pow(tSfc, 4.0);

        // 层透射率（长波）
        double[] tr = new double[N];
        // §503 水汽反馈：光学厚度 = k_m*Δp/g + k_w*q*Δp/g = MASS[k]*(K_M + K_W*q[k])
        //   MASS[k] 已是 kg/m²，故两项都是无量纲光程。
        setupOptics();
        double[] tauE = new double[N + 1];
        tauE[0] = 0.0;
        for (int k = 0; k < N; k++) tauE[k + 1] = tauE[k] + MASS[k] * (K_M + K_W * q[k]);
        for (int k = 0; k < N; k++) tr[k] = Math.exp(-(tauE[k + 1] - tauE[k]));
        lastTauTot = tauE[N];

        // 下行：从 TOA 往下
        double[] fDn = new double[N + 1];
        fDn[0] = 0.0;
        for (int k = 0; k < N; k++) fDn[k + 1] = fDn[k] * tr[k] + b[k] * (1.0 - tr[k]);
        // 上行：从地面往上
        double[] fUp = new double[N + 1];
        fUp[N] = bS;
        for (int k = N - 1; k >= 0; k--) fUp[k] = fUp[k + 1] * tr[k] + b[k] * (1.0 - tr[k]);
        olr = fUp[0];
        lwSfcDown = fDn[N];

        // 短波（纯吸收，地面反照率一次反射）
        double mu = Math.max(0.05, cosZenith);
        double[] sDn = new double[N + 1];
        sDn[0] = S0 * mu;
        for (int k = 0; k < N; k++) sDn[k + 1] = sDn[k] * Math.exp(-(tauSwOf(P_EDG[k + 1]) - tauSwOf(P_EDG[k])) / mu);
        double[] sUp = new double[N + 1];
        sUp[N] = ALBEDO * sDn[N];
        for (int k = N - 1; k >= 0; k--) sUp[k] = sUp[k + 1] * Math.exp(-(tauSwOf(P_EDG[k + 1]) - tauSwOf(P_EDG[k])) / mu);
        swSfcDown = sDn[N];
        asr = sDn[0] - sUp[0];

        // 逐层净加热（望远镜化：Σ = (B_s − OLR) + F↓_sfc + (ASR − 地面净短波)）
        for (int k = 0; k < N; k++) {
            // ★ §501 记账：首版写成 (fUp[k]-fUp[k+1]) + (fDn[k+1]-fDn[k]) —— 长波散度的【符号反了】。
            //   层 k 跨边 k（上）与 k+1（下）：上行在 k+1 进、在 k 出 ⇒ fUp[k+1]−fUp[k]；
            //                                    下行在 k 进、在 k+1 出 ⇒ fDn[k]−fDn[k+1]。
            //   反号后柱子在被【该加热的地方冷却】：实测 ra = +2046 W/m²、dH/dt = +743 W/m²（都不可能）。
            //   短波那两项本来就是对的（下行 k→k+1、上行 k+1→k）。
            double net = (fUp[k + 1] - fUp[k]) + (fDn[k] - fDn[k + 1])
                       + (sDn[k] - sDn[k + 1]) + (sUp[k + 1] - sUp[k]);
            raAccum += net;
            t[k] += net * dtRad / (MASS[k] * Radiation.CP);
        }
        // ★ §501: 不在这里写 ra —— raAccum 是 4 个子步的【和】，而每步只施加了 net*dt/4。
        //   交给 step() 在子步循环后做 ra = raAccum/4（dt 加权平均），否则 ra 会被放大 4 倍。
    }

    // ==================== 算子 2：对流调整（做成 H 守恒）====================

    /** Betts & Miller (2004) 温度参考廓线。 */
    public double[] bettsMillerTRef(double tTopK) {
        double[] tRef = new double[N];
        ParcelLift.Result r = ParcelLift.lift(tSfc, q[N - 1]);   // ★ 地面层是 N-1（k=0 是柱顶）
        double pF = levelOfT(273.15);
        // ★ §508：对流顶不再用 levelOfT(tTopK)（那含一个猜的常数 tTopK = 200 K），
        //   改用 §498 已【验证过】的气块抬升导出的 LNB（零自由参数）。
        //   无对流（LNB = NaN）时回落到冻结层。
        double pT = Double.isNaN(r.pLnb) ? pF : r.pLnb;
        double tcB = tSfc;
        double tcF = ParcelLift.moistAdiabatT(r.tLcl, r.pLcl, pF, 50);
        double tRF = tcB + W_STAB * (tcF - tcB);          // 式 (7)：斜率缩放
        // ★ §505 性能：原来对【每一层】都从 LCL 重新积分一次伪绝热（60 次 × 50 步 RK4）
        //   ⇒ 每步 1.2 万次 dTdlnp，是全部开销的主项。改成【自下而上单次扫描】：每层只走 1 步 RK4。
        //   顺带修正：LCL 以下的层改用【干绝热】（原来沿用 tLcl，把近地面参考值压到 tSfc 之下
        //   ⇒ 与「近地面 Γ 只有 1~2 K/km」的过稳现象一致）。
        double[] tc = new double[N];
        double pp = r.pLcl, tt = r.tLcl;
        for (int k = N - 1; k >= 0; k--) {
            if (PC[k] > r.pLcl) tc[k] = tSfc * Math.pow(PC[k] / pSfc, ParcelLift.R_D / Radiation.CP);
            else { tt = ParcelLift.rk4T(tt, pp, PC[k]); tc[k] = tt; pp = PC[k]; }
        }
        for (int k = 0; k < N; k++) {
            double pk = PC[k];
            if (pk >= pF) {
                tRef[k] = tcB + W_STAB * (tc[k] - tcB);
            } else {
                double y = (pF - pk) / Math.max(1.0, (pF - pT));
                if (y > 1.0) y = 1.0; if (y < 0.0) y = 0.0;
                tRef[k] = tc[k] + (tRF - tcF) * (1.0 - y * y);   // 式 (8)
            }
        }
        // ⚠⚠ §509 **等温帽已【撤回】**（实测否证，见下）。
        //   曾加：p < p_T 的层令 tRef 保持 p_T 处的值（等温），依据是「灰体辐射平衡上层等温」。
        //   它确实改善了 (e)（Γmax 失败裕度 0.8% → 0.3%，2/3 通过），但
        //   【把 (c) 从 2.357【带内,单调】打成 0.943【带外,非单调】】。
        //   §508 曾把 (c) 的回归归因于「K_M 未重标定」——**§509 实测证伪**：
        //     重标定 K_M 1.4453e-04 → 1.6641e-04 之后 (c) 仍是 1.026【带外,非单调】。
        //   ⇒ 回归来自等温帽【本身】：p_T 取自 LNB，而 LNB 随 Ts 变化极强
        //     （288 K 浅对流 / 312 K 深对流）⇒ 给上层温度结构引入强 Ts 依赖 ⇒ 扭曲 ∂OLR/∂Ts。
        //   ⇒ 撤回，但【保留】另一处独立改动：对流顶 p_T 改用导出的 LNB（消掉猜的 200 K 常数）。
        return tRef;
    }

    /** Betts & Miller 湿度参考廓线：RH 在 (950,550,200) hPa 上取 (RH_B,RH_M,RH_T)。 */
    public double qRef(int k) {
        double a = 95000.0, m = 55000.0, tp = 20000.0, pp = PC[k], rh;
        if (pp >= a) rh = RH_B;
        else if (pp >= m) rh = RH_B + (RH_M - RH_B) * (a - pp) / (a - m);
        else if (pp >= tp) rh = RH_M + (RH_T - RH_M) * (m - pp) / (m - tp);
        else rh = RH_T;
        return rh * ParcelLift.qs(t[k], pp);
    }

    private double levelOfT(double tK) {
        double lo = P_TOP, hi = pSfc;
        ParcelLift.Result r = ParcelLift.lift(tSfc, q[N - 1]);
        for (int i = 0; i < 60; i++) {
            double mm = 0.5 * (lo + hi);
            double tm = ParcelLift.moistAdiabatT(r.tLcl, r.pLcl, mm, 50);
            if (tm > tK) hi = mm; else lo = mm;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * 对流调整。**返回值单位是【每步质量 kg/m²】，不是通量 kg/(m²·s)。**
     *
     * <p>★ §531 修正（审计面 1，P1-11）：本 javadoc 原写「kg/m²/s」，与实现不符 ——
     * {@code pConv = Σ(-dq·MASS)}，而 {@code dq = (dt/TAU_ADJ)·(qRef-q)}，即已含 dt。
     * 同文件的字段注释与 {@link #budgetTerms()}（那里除 {@code lastDt}）才是对的。
     * <b>三处曾互斥；现以「每步质量」为唯一口径。</b>
     *
     * <p>**柱 H 逐位不变**。
     */
    public double convectiveAdjust(double dt, double tTopK) {
        double[] tRef = bettsMillerTRef(tTopK);
        double a = dt / TAU_ADJ;
        double[] dq = new double[N];
        double[] dT = new double[N];
        double available = 0.0, demand = 0.0, hSum = 0.0, mSum = 0.0;
        // ⚠⚠ §506 **已被实测否证，故回退**：曾把对流限制在云顶 p_T 以下
        //   （理由：Betts & Miller 只在云顶以下有定义，且 y>=1 时 tRef 渐近干绝热）。
        //   实测后果【远比原病更糟】：Γmax 21.24 K/km（超绝热）、Ts=312 直接 NaN、
        //   收支门从 1.107e-09 崩到 5.756（BROKEN）。
        //   物理原因：云顶以上只剩辐射，而【灰体辐射平衡对该廓线本身是不稳定的】——
        //   对流调整正是让它稳定的那个算子。关掉它 = 把柱子交给一个不稳定的平衡。
        //   ⇒ 保留原写法（逐层调整）。这条否证已记账。
        for (int k = 0; k < N; k++) {
            dq[k] = a * (qRef(k) - q[k]);
            if (dq[k] < 0.0) available += -dq[k] * MASS[k];
            else             demand    +=  dq[k] * MASS[k];
            dT[k] = a * (tRef[k] - t[k]);
            hSum += dT[k] * MASS[k] * Radiation.CP;
            mSum += MASS[k];
        }
        // ★ §501：对流【不能凭空造水】。变湿的那部分必须由变干的那部分供给。
        //   首版让 dq>0 的层直接加水 ⇒ 那些层白拿了 L*dq*MASS 的能量 ⇒ 收支差恰好 ≈ L*P。
        //   现在把加湿按 available/demand 缩放 ⇒ Σ dq_k*MASS_k = −available + demand*scale = −pConv 严格成立。
        double scale = 1.0;
        if (demand > available && demand > 0.0) scale = available / demand;
        double pConv = available - demand * scale;
        for (int k = 0; k < N; k++) q[k] += (dq[k] > 0.0) ? dq[k] * scale : dq[k];
        // 潜热：把降水的潜热【全部】还给柱子（使 H 守恒）
        double lat = Radiation.LV * pConv;
        // T 形状项：减掉质量加权均值 ⇒ 柱积分为 0 ⇒ H 守恒
        // ★ §501 记账：首版写成 hSum/mSum，单位是 J/kg 不是 K（多带了一个 cp≈1004）
        //   ⇒ 每层被一次性加上 ~+50 K ⇒ 4~6 步内 NaN。守恒性当时【是对的】
        //   （Σ MASS*cp*(dT−bias) = hSum − bias*mSum = 0），所以收支代数看不出这个错。
        double bias = hSum / (mSum * Radiation.CP);
        for (int k = 0; k < N; k++) t[k] += dT[k] - bias;
        // 潜热按层质量均匀分配（零柱积分之外的唯一选择是均匀；不影响 H）
        double latPerMass = lat / mSum;
        for (int k = 0; k < N; k++) t[k] += latPerMass / Radiation.CP;
        return pConv;
    }

    // ==================== 算子 3/4 ====================

    /**
     * 大尺度凝结。**返回值单位同为【每步质量 kg/m²】**（见 {@link #convectiveAdjust} 的 §531 说明）。
     * 要得到通量必须除以 {@code lastDt}。**柱 H 逐位不变**。
     */
    public double largeScaleCondense() {
        double pLs = 0.0;
        for (int k = 0; k < N; k++) {
            double qsK = ParcelLift.qs(t[k], PC[k]);
            if (q[k] > qsK) {
                double dm = (q[k] - qsK) * MASS[k];
                pLs += dm;
                t[k] += Radiation.LV * dm / (MASS[k] * Radiation.CP);
                q[k] = qsK;
            }
        }
        return pLs;
    }

    /** §511 表面能量平衡残差（W/m²）—— 这是【独立】判据。 */
    public double lastSfcResid;
    /**
     * §512 为 true 时【不解】表面能量平衡，tSfc 保持 init() 给的值。
     * 用途：拿【模型自己的】地面温度/比湿驱动气柱，从而与 §498 的 ParcelLift 结果直接可比。
     * 默认 false ⇒ 不改任何现有行为。
     */
    public static boolean FIXED_TS = false;

    /**
     * §511 ★★★★★★★ **诊断地面温度**：解表面能量平衡。
     *
     * <pre>
     *   (1-a)*SW_down + LW_down - eps*sigma*Ts^4 - SH(Ts) - LH(Ts) = 0
     * </pre>
     * 在此之前 tSfc 是【外部给定的输入】⇒ 表面能量平衡【从未闭合】(目标第 (1) 条的一半)。
     * 解出来之后：Ts 是【输出】，于是 OLR(Ts) 也变成【预测】而非输入 ⇒ (c) 从构造性变成独立判据。
     *
     * <p>sfcNet(Ts) 对 Ts 单调递减（-sigma*Ts^4、-SH、-LH 三项都递减）⇒ 二分必收敛且唯一。
     */
    public void solveSurfaceT() {
        double lo = 150.0, hi = 400.0;
        for (int i = 0; i < 80; i++) {
            double m = 0.5 * (lo + hi);
            if (sfcNet(m) < 0.0) hi = m; else lo = m;
        }
        tSfc = 0.5 * (lo + hi);
        lastSfcResid = sfcNet(tSfc);
    }

    private double sfcNet(double ts) {
        double swNet = (1.0 - ALBEDO) * swSfcDown;
        double lwNet = lwSfcDown - LW_SFC_EMIS * THETA_SB * ts * ts * ts * ts;
        double shv = chv * Radiation.CP * windSpeed * (ts - t[N - 1]);
        double qsS = ParcelLift.qs(ts, pSfc);
        double lhv = chv * Radiation.LV * windSpeed * (qsS * beta - q[N - 1]);
        if (lhv < 0.0) lhv = 0.0;
        return swNet + lwNet - shv - lhv;
    }

    /** 地—气通量 W/m²（正 = 进入大气）。 */
    public void surfaceFlux() {
        double qsS = ParcelLift.qs(tSfc, pSfc);
        sh = chv * Radiation.CP * windSpeed * (tSfc - t[N - 1]);
        lh = chv * Radiation.LV * windSpeed * (qsS * beta - q[N - 1]);
        if (lh < 0.0) lh = 0.0;
    }

    // ==================== 时间推进 ====================

    /** 一步。返回本步的 dH/dt 实测值（W/m²）。 */
    public double step(double dt, double cosZenith, double tTopK) {
        lastDt = dt;
        double h0 = columnEnergy();
        // §511：先取辐射通量（swSfcDown / lwSfcDown 与 Ts 无关，见 radiation() 的下行扫描）
        radiation(cosZenith, 0.0);
        if (!FIXED_TS) solveSurfaceT();        // §511 解表面能量平衡 ⇒ tSfc 成为【诊断量】
        raAccum = 0.0;
        for (int s = 0; s < 4; s++) radiation(cosZenith, dt / 4.0);
        ra = raAccum / 4.0;      // ★ dt 加权平均（见 radiation() 末尾的记账）
        surfaceFlux();
        double dT0 = sh * dt / (MASS[N - 1] * Radiation.CP);
        double dq0 = lh * dt / (MASS[N - 1] * Radiation.LV);
        t[N - 1] += dT0; q[N - 1] += dq0;
        precipConv = convectiveAdjust(dt, tTopK);
        precipLs = largeScaleCondense();
        diagnoseGeopotential();
        double h1 = columnEnergy();
        dHdtMeasured = (h1 - h0) / dt;
        if (firstBadStep < 0) {
            if (!isFinite(h0) || !isFinite(h1)) { firstBadStep = -1; }
            for (int k = 0; k < N; k++) if (!isFinite(t[k]) || !isFinite(q[k])) { firstBadLayer = k; break; }
        }
        return dHdtMeasured;
    }

    private static boolean isFinite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    /** 供探针调用：报告第一个非有限层。 */
    public void markBad(long n) { if (firstBadLayer >= 0 && firstBadStep < 0) firstBadStep = n; }

    /** 收支残差（W/m²）：dH/dt − (Ra + SH + LH − L*P)。**这才是真判据**。 */
    public double budgetResidual() {
        // ★ §501 记账（这一条是【实测逼出来的】）：H = ∫(cp*T + L*q) dp/g 的收支里【没有 −L*P】。
        //   凝结把 L 释放给空气、凝结物落下时【不带潜热走】⇒ 降水对 H 是零贡献。
        //   证据：P=0 的中纬海洋情形残差 = −1.097e-08（机器精度），
        //         而有降水的三个情形残差【恰好等于 L*P】（30.29/30.29、54.01/54.01、82.43/82.43）
        //         ⇒ 是我多减了一项，不是代码漏了能量。
        //   （−L*P 属于【干静力能】收支 / 地表收支，不属于湿静力能收支。）
        double rhs = ra + sh + lh;
        return dHdtMeasured - rhs;
    }

    /** 收支各项，便于打印。 */
    public double[] budgetTerms() {
        return new double[]{dHdtMeasured, ra, sh + lh, Radiation.LV * (precipConv + precipLs) / lastDt,
                            budgetResidual()};   // [3] 仅作诊断显示，不参与残差
    }

    /** 解到 RCE。返回步数（≤0 表示没收敛）。 */
    public int solveRCE(double dt, double cosZenith, double tTopK, double tol, int maxSteps) {
        for (int n = 1; n <= maxSteps; n++) {
            step(dt, cosZenith, tTopK);
            if (n > 30 && Math.abs(dHdtMeasured) < tol) return n;
        }
        return -maxSteps;
    }

    /**
     * 标定 `TAU0_LW`：使本内核的 OLR 匹配模型【已有】的 {@link StationaryWave#olrClear} 闭合。
     * ⇒ 不是把 OLR 拟合到地球数据，而是让它与模型自己的辐射闭合一致。
     */
    public static double calibrateTauLW(double tSfcK, double qSfc, double cwv, double tTopK,
                                        double cosZenith, double lo, double hi, int iters) {
        double save = TAU0_LW, best = 0.5 * (lo + hi);
        for (int i = 0; i < iters; i++) {
            double m = 0.5 * (lo + hi);
            TAU0_LW = m; best = m;
            VerticalColumn c = new VerticalColumn();
            c.init(tSfcK, qSfc, 1.0, 6.0);
            c.solveRCE(600.0, cosZenith, tTopK, 0.05, 6000);
            double target = StationaryWave.olrClear(tSfcK, cwv);
            if (c.olr > target) hi = m; else lo = m;
        }
        TAU0_LW = save;
        return best;
    }
}
