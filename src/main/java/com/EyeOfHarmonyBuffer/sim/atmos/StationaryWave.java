package com.EyeOfHarmonyBuffer.sim.atmos;

// S2 定常波求解器（辐散 Gill 型，稳态）。设计冻结 351/352/389/390/391/393。
// 几何：2*pi*R = 40,000 km（赤道周长）、pi*R = 20,000 km（极到极）=> R = 6366 km，恰好是一个球。
// 边界：两极 p = 0；x 方向周期（X 无限无周期，取周期 BC 是文档化近似，见 347）。
// 强迫必须取【降水型】L_v*P（393 实测：蒸发型会把辐合放到北太平洋）。
//
// ===== p 的物理身份（文献核实后写死，避免再出现符号混乱）=====
//   p = Phi1 = 第一斜压模的位势振幅，单位 m^2/s^2（**不是 Pa**）。
//   垂直结构 Phi'(z) = Phi1*cos(pi*z/H)：z=0 处 +Phi1、z=H 处 -Phi1、z=H/2（约 500 hPa）为零。
//   所以：  p < 0  <=>  低层（地面）低压，同时高层为高压；中层温度扰动 T'_mid = -(pi*theta0/(gH))*p，p<0 => 暖。
//   低层水平风就是第一模振幅 u,v（Randall Ch.8 明写 u,v 是 lower-tropospheric variables）。
//   三条来源：Gill 1983 ECMWF 讲义 p.349/p.353（phi = -theta 静力关系 => 右端为 -Q）、
//            Randall《General Circulation》Ch.8 式(103)、第一斜压模投影 dPhi1/dt + c^2 D1 = -Q1。
//
// ===== ⚠ 干 c + 纯潜热 = 系统性低估 2~10 倍（尚未解决）=====
//   深对流区的有效稳定度是 gross moist stability（GMS, M1），不是干 N^2；M1 远小于干层结，
//   同样加热会产生强得多的环流。若坚持用干 c，则 Q 必须是【净柱加热】
//   （潜热 + 净辐射 + 感热），热带净辐射冷却约 -100 W/m^2，与 3~4 mm/day 的潜热同量级。
//   现在只喂潜热 => 低估。文献里"降水潜热 + 干 c"这条路（Keil et al. 2023, QJRMS）也被接受，
//   但必须同时知道它低估；本求解器当前正是这条路。
//   ⇒ 正确做法是 S1a 的 Radiation 给出净柱加热，或改用 QTCM 的 MSE 闭合（M1*div = -F_net）。
// 数值：数值扰动装配 + 稠密复数消元，已验证到机器精度（391：L2 相对残差 4.974e-15）。
// 默认 OFF => 逐位不变。
public final class StationaryWave {

    private StationaryWave() {}

    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【6 份】，语义各不相同、默认值也不一致
     * （4 个 false：HadleyCell/SoilMoisture/StationaryWave/Vegetation；2 个 true：OceanField/SimTerrain）。
     * **本份的含义是：StationaryWave（定常波 / 环流侧 §405）。** 引用时务必写全类名（如 `StationaryWave.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     */
    public static boolean ENABLED = false;
    public static int NX = 64;
    public static int NPHI = 46;
    public static double DAMPING = 1.0 / (1.5 * 86400.0);
    public static double C_GRAV = 70.0;
    /** 潜热换算：1 mm/day 降水 <=> 28.356 W/m^2（= 1 kg m^-2 day^-1 * 2.45e6 J/kg / 86400 s）。 */
    public static final double LV_W = 28.356;

    /**
     * ★★★ 量纲桥：把降水 (mm/day) 换成浅水方程要的【质量源】(m^2/s^3)。
     *
     * <p>推导（三源核实：Gill 1983 ECMWF 讲义、Randall 教科书 Ch.8、以及第一斜压模投影）：
     * <pre>
     *   Q_SW = (g*H / (pi*theta0)) * Qhat_theta            Qhat_theta = 加热率振幅 [K/s]
     *        = (g / (2*theta0)) * Integral[Q_theta dz]     对 sin(pi*z/H) 廓线严格成立
     * </pre>
     *
     * <p>代入 1 mm/day（= 28.356 W/m^2）、H = 16 km、theta0 = 300 K，得
     * {@code Q_SW} 落在 4.6e-4 ~ 1.3e-3 m^2/s^3，中心值约 <b>1.0e-3</b>。
     *
     * <p>⚠ 最大不确定源是 H 与 rho0 的取法（会让系数差 2~7 倍）；这里取区间中心值，
     * 不确定度按 ±1.4 倍计。**这不是拟合出来的，是推导出来的。**
     */
    public static double Q_PER_MMDAY = 1.0e-3;

    /**
     * 强迫的整体乘子（**无量纲**）。1.0 = 物理值 {@link #Q_PER_MMDAY}。
     * ⚠ 历史：这里曾经是 1e-9，而 {@code LV_W=28.356} 被当成量纲桥用 —— 那不是量纲桥，
     * 只是潜热换算（W/m^2 per mm/day）。两者一起用等于把 Q 放大了 28 倍（2026-09 修正）。
     */
    public static double Q_SCALE = 1.0;

    /**
     * ★★★ §406：强迫换成【净柱加热】而不是纯潜热。**默认 false => 逐位不变。**
     *
     * <p><b>为什么必须换</b>（§405 的结构性结论）：`wZm` 是纬向平均，经向差异必须由区域环流提供；
     * 而区域环流的强度由 `Q` 决定。只喂潜热时撒哈拉与亚洲的 `Q` 差不多（潜热都来自降水），
     * **净加热才带正确的经向对比 —— 撒哈拉的净柱加热是【负】的（强辐射冷却 + 弱潜热）。**
     *
     * <p><b>净柱加热的三个分量</b>（全部用模型自己的参数化，零新常数）：
     * <pre>
     *   Q_lat  = LV_W * P                      潜热（W/m^2；LV_W = 28.356 per mm/day）
     *   Q_sens = chv * CP * (Ts - Ta)          感热（正 = 向上 => 大气获得）
     *   Q_rad  = sigma*Ts^4 * (1 - 2*EPS)      净辐射（见下）
     * </pre>
     *
     * <p><b>Q_rad 的推导</b>：模型是单层灰体 —— 地表净长波损失 `EPS*sigma*Ts^4`（= OLR 参数化），
     * 而大气从地表吸收 `sigma*Ts^4`、向太空发射 `EPS*sigma*Ts^4`
     * `=> Q_rad,atm = sigma*Ts^4*(1-EPS) - EPS*sigma*Ts^4 = sigma*Ts^4*(1 - 2*EPS)`。
     * 代入 `EPS = 0.6101`、`Ts = 300 K` 得 **-101 W/m^2**，
     * 与研究报告给出的「热带柱净辐射冷却约 -100 W/m^2」吻合。
     *
     * <p>⚠ `Ts` 用 {@link Radiation#skinTempLand}（带 beta 的皮温解），
     * 因为诊断温度没有蒸发冷却、会偏高（§385 的 P568 实测陆地上 H 是负的）。
     *
     * <p>★★★★★ <b>§456 a″ 配对要求（必读）</b>：本支路的物理前提是**陆地上的 `beta` 来自土壤桶**
     * （{@link SoilMoisture#ENABLED} = true）。
     *
     * <p>为什么：`beta = 1` 是**饱和面**（对海洋是对的），用在陆地上等于「处处湿地」⇒
     * `le = beta*chv*LV*(qSat(Ts)-qa)` 潜热冷却过量 ⇒ 皮温被压到空气**之下** ⇒ `Q_sens` 为负。
     * <b>实测代价</b>（P668，`Q源`+`源季节项` 开，JJA）：
     * <pre>
     *   S3 关 (beta=1.000)：ts-ta = -5.43 K, Q_sens = -179.98 W/m2, F_net = -44.65 / -75.87   <- 处处强下沉
     *   S3 开 (beta=0.252)：ts-ta = -0.74 K, Q_sens =  -40.46 W/m2, F_net = +62.43 / +35.30   <- 两盒都上升
     * </pre>
     * ⇒ **`Q_NET_HEATING = true` 而 `SoilMoisture.ENABLED = false` 是一个【物理上不一致的配置】**，
     * 且它不会报错、只会静默给出一套错的 `F_net`（与 §444「水汽源必须与水汽长波配对」同型：
     * **一个开关的物理前提由另一个开关提供**）。
     *
     * <p>守卫：陆地取到 `beta = 1` 回落时会累加 {@link #betaFallbackLandCalls} 并在首次打印一次
     * 警告。**验收与探针必须断言它为 0**（{@link #betaGuardTripped()}）。
     */
    public static boolean Q_NET_HEATING = false;

    /**
     * ★★★★★ **§456 a″：配对守卫的计数器** —— 陆地上取到 `beta = 1` 回落的次数。
     *
     * <p>`beta = 1` 只在**海洋**上是正确的；陆地上它代表「饱和面」，会让潜热冷却过量、
     * `Q_sens` 翻负、`F_net` 全错（见 {@link #Q_NET_HEATING} 的配对要求）。
     * **正常配置（`SoilMoisture.ENABLED = true`）下它恒为 0；探针与验收必须断言 0。**
     */
    public static long betaFallbackLandCalls = 0;
    private static volatile boolean betaWarned = false;

    /** 配对守卫是否被触发过（= 配置不一致）。 */
    public static boolean betaGuardTripped() { return betaFallbackLandCalls > 0; }

    /** 清空守卫计数（探针在每段配置开头调用）。 */
    public static void resetBetaGuard() { betaFallbackLandCalls = 0; betaWarned = false; }

    /** §397 的量纲桥：W/m^2 -> m^2/s^3（子代理三源核实的推荐值）。 */
    public static double Q_WM2_TO_SW = 3.5e-5;

    private static final double OM = 7.2921e-5;
    private static final double R_EFF = 10_000_000.0 / (Math.PI / 2.0);

    private static long cacheKey = Long.MIN_VALUE;
    private static double[] divCache;
    private static int rows;

    public static long solveCount = 0, nodeCount = 0, SOLVE_NANOS = 0;

    // ★ 重入守卫：ensureSolved 内部要调 PrecipField.mmPerDay 来建强迫场（Q = L_v*P），
    //   而 mmPerDay 的接线又会调 ensureSolved => 无限递归。thread-local 标志切断它。
    //   （与 Atmosphere.SST_SUPPRESS 同一类机制；区别是这里只挡【求解自身】的递归，
    //     不影响任何外部调用者。）
    private static final ThreadLocal<Boolean> IN_SOLVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * ★ 测试钩子（**只给探针用，生产恒为 null/false**）。
     * 为什么需要：mmPerDay 给的是真实降水场，形状复杂、且我们并不知道"哪里有热源"的独立答案，
     * 于是无法判断求解器输出的【符号】对不对。注入一个自造的、已知符号的局地热源，
     * 才能把"符号约定"和"场形状"两件事分开 —— 这是 391 那套自证缺的一环。
     */
    public static double[][] Q_OVERRIDE = null;
    /** 测试钩子：把科氏参数置零（f=0 时算子是实的、且逐波数解耦，便于解析对照）。 */
    public static boolean ZERO_F = false;

    // ================= 闭环（不动点迭代） =================
    // 开环（默认）：Q 取自【种子】降水场，解一次，接一次线。反馈没闭合。
    // 闭环：Q <- P(divU + div(V))，用【上一次迭代】的 div 建强迫，迭代到收敛。
    //   ★ 为什么不能直接用这一次的：建强迫本身要调 mmPerDay，而那会读 divAt；
    //     用同一轮的值就成了自指。用上一轮的值才是标准的不动点迭代。
    //   ★ 为什么默认 false：默认状态不得改变任何现有行为（目标 3）。
    public static boolean CLOSED_LOOP = false;
    public static int LOOP_MAX_ITER = 40;
    /** 收敛判据：相邻两轮 div(V) 的最大相对变化。 */
    public static double LOOP_TOL = 1.0e-3;
    /** 松弛因子 (0,1]：<1 时按下松弛，用来压住正反馈的振荡。 */
    public static double LOOP_RELAX = 1.0;
    public static int lastIterations = 0;
    public static double lastLoopChange = -1;
    /**
     * ★ §352 预登记第 4 门「谱半径 / 最大特征值」的落地形式。
     * 对不动点迭代 {@code div^(n+1) = F(div^(n))}，环路增益
     * {@code g_n = max|div^(n+1)-div^(n)| / max|div^(n)-div^(n-1)|}
     * 就是迭代 Jacobian 的谱半径估计：{@code g < 1} 收缩（收敛），{@code g > 1} 发散。
     * 为什么用这个而不是去算 46x46 复矩阵的特征值：预登记那一门想问的是
     * 「这个耦合系统稳不稳」，而耦合系统的稳定性正是这个 g，不是单个算子矩阵的谱。
     */
    public static double lastLoopGain = -1;
    private static double[] prevDelta;
    private static double maxAbs(double[] a) {
        double m = 0;
        for (double v : a) { double x = Math.abs(v); if (x > m) m = x; }
        return m;
    }

    /** 迭代中建强迫时读的【上一次迭代】的场（外部读者永远看不到它）。 */
    private static double[] divPrevField;
    /** p = Phi1 的网格（rows*NX，m^2/s^2）。只累加 m>=1（Qk[0]=0 ⇒ 纬向平均也为 0）。 */
    private static double[] pGrid;

    /** 相对变化 max|b-a| / max|b|。a 为 null（第一轮）时返回 +inf，表示"远未收敛"。 */
    private static double relChange(double[] a, double[] b) {
        if (a == null) return Double.POSITIVE_INFINITY;
        double num = 0, den = 0;
        for (int t = 0; t < b.length; t++) {
            double d = Math.abs(b[t] - a[t]);
            if (d > num) num = d;
            double e = Math.abs(b[t]);
            if (e > den) den = e;
        }
        return den <= 0 ? num : num / den;
    }
    /** 诊断：第 j 行的 f。 */
    public static double fAtRow(int j) { return fOf == null ? 0 : fOf[j]; }

    /**
     * ★★★ **§443：柱长波吸收的水汽依赖系数** `k`（m²/kg）。默认 **0.0**（吸收率恒为 1，逐位不变）。
     *
     * <p>物理：晴空 OLR `= sigma*Ts^4*exp(-k*CWV)`，柱吸收的长波 `= sigma*Ts^4*(1-exp(-k*CWV))`。
     * CWV 大（季风区）⇒ 柱被加热；CWV 小（沙漠）⇒ 长波直接逃逸 ⇒ 柱净冷却。
     * 文献标定：CWV 从 ~10 → ~50 kg/m² 时晴空 OLR 约从 340 → 265 W/m² ⇒ `k ≈ 0.02~0.03`。
     *
     * <p>⚠ 这是**新常数**，取值必须落在文献区间内；且 `k = 0` 时逐位不变。
     */
    public static double WVLW_K = 0.0;

    /**
     * ★★★★★ **§455：柱净辐射改成物理上定义的 `ASR − OLR`。**
     *
     * <p>⚠ <b>§533 修正（P1-10）</b>：本条曾写「默认 **false** ⇒ 逐位不变」，但
     * **实际默认已是 `true`**（§487 用户裁决「按照最物理最正确的方向做」后改的，
     * 见 :230 声明行与 :231-235 的说明）。原「逐位不变」只对 `false` 支成立。
     *
     * <p><b>为什么</b>（§454 实测）：`netColumnHeating` 第 190 行算出净短波 `absSolar`，
     * **只喂给 `skinTempLand`，从未进入加热** ⇒ 净柱辐射里**完全没有短波**，而这一项是
     * **+371.96（亚洲）/ +403.51（撒哈拉）W/m²**。而旧 `qRad = sigma*Ts^4*(1-2*EPS) = -0.2202*sigma*Ts^4`
     * 是一个**纯长波项**（不是 OLR），所以**不能**把 ASR 直接加进去 —— 那样 `Qnet` 会变成
     * +320/+365（量级过大且对比符号反）。
     *
     * <p><b>正确口径</b>（Neelin 的 `F_net`）：`F_net = ASR − OLR + LH + SH`，
     * 三项模型都有（`absSolar` / `qLat` / `qSens`），**缺的只有 `OLR`**。
     *
     * <p><b>`OLR` 的形式是推导出来的，不是拟合的</b>：单层灰体
     * <pre>
     *   OLR = sigma*Ts^4 * exp(-k*CWV) + (1 - exp(-k*CWV)) * sigma*T_ft^4
     * </pre>
     * 干极限 `CWV→0` ⇒ `OLR → sigma*Ts^4`（**只依赖地表温度，水汽无影响**）——
     * 这正是文献对撒哈拉的描述；湿极限 ⇒ `OLR → sigma*T_ft^4`（由大气发射层决定）。
     * `T_ft = ts - GAMMA*H_EFF/2` **用本世界自己的递减率**。
     * 唯一的常数 `k` 由观测锚定（CWV = 10 kg/m² 时晴空 OLR = 340 W/m² ⇒ `k = 0.0667`），
     * 第二个观测点（CWV = 50 ⇒ 265 W/m²）作为**核对**：本式给出 250.7（差 6%，记账）。
     */
    public static boolean QRAD_ASR_MINUS_OLR = true;   // §487：默认改为 true —— 旧式是【已确证的缺陷】
    //  为什么改默认（用户裁决「按照最物理最正确的方向做」）：旧式 qRad = sigma*Ts^4*(1-2*EPS)
    //  是一个**纯长波项**、且**短波（absSolar = 372~404 W/m²）从未接入**（§454）。
    //  新式 qRad = ASR - OLR（olrClear 是推导的单层灰体，唯一常数 OLR_K 由观测锚定）。
    //  ⚠ 生产影响：netColumnHeating 目前只被 StationaryWave.ENABLED（默认 false）消费 ⇒
    //  【默认为 true 在实践中是无操作】，但它使该函数在被动用时物理正确（防御性正确）。

    /** OLR 的宽带吸收系数（m²/kg）。由观测锚定，**不是拟合目标量**。 */
    public static double OLR_K = 0.0667;

    /**
     * ★★★★★ §575：<b>大气吸收占「到达地表的光束」的比例</b>，用于把【地表吸收短波】
     * 换算成【行星吸收短波 ASR】。
     *
     * <p><b>为什么必须要有它（这是一个已确证的 bug）</b>：原式
     * {@code qRad = absSolar - olrClear(...)} 里的 {@code absSolar} 是
     * {@code insolation × (1 − 地表反照率)}，那是<b>地表吸收的短波</b>；
     * 而 {@code qRad} 是<b>柱净辐射</b>，该用<b>行星 ASR</b>。
     * 两者之间差的正是<b>被大气自己吸收的那一份</b> —— 它算在行星 ASR 里，却从不到达地表。
     *
     * <p><b>两个独立锚（我自己算的，两处都给了原始数）</b>：
     * <pre>
     * 全球（Trenberth et al. 2009 能量收支，本文件 OLR 定标已引同一来源）：
     *   S = 341.3，地表反照率 0.125 ⇒ S(1−αs) = 298.6，ASR = 240
     *   ⇒ A_atm = 1 − 240/298.6 = 0.1962
     * 撒哈拉（GERB 卫星，Alamirew et al. 2018 ACP 18, 1241, Table 2）：
     *   S = 486.8，αs = 0.1875 ⇒ S(1−αs) = 395.5，TOA 净短波 = 314
     *   ⇒ A_atm = 1 − 314/395.5 = 0.2061
     * </pre>
     * 两个独立来源差 5% ⇒ 取 <b>0.20</b>。
     *
     * <p>⚠ 置 0.0 可逐位恢复旧行为（旧行为已确证错误，保留只作 A/B）。
     */
    public static double ABS_ATM_FRAC = 0.20;

    /** 晴空 OLR（W/m²）：单层灰体，干极限 → sigma*Ts^4，湿极限 → sigma*T_ft^4。 */
    public static double olrClear(double ts, double cwv) {
        double tft = ts - Atmosphere.GAMMA * 0.5 * Atmosphere.H_EFF;
        if (tft < 150.0) tft = 150.0;
        double e = Math.exp(-OLR_K * Math.max(0.0, cwv));
        double sTs = Radiation.SIGMA * ts * ts * ts * ts;
        double sTf = Radiation.SIGMA * tft * tft * tft * tft;
        return sTs * e + sTf * (1.0 - e);
    }

    /** §443 诊断槽：[0]qLat [1]qSens [2]qRad [3]CWV [4]ts [5]ts-ta（默认 null ⇒ 不写）。 */
    public static ThreadLocal<double[]> DIAG_HEAT = null;

    /** 净柱加热（W/m^2）：潜热 + 感热 + 净辐射。见 {@link #Q_NET_HEATING}。 */
    public static double netColumnHeating(int x, int z, long seed, int cell, double theta, int gradStep, double pMmDay) {
        double lat = com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(z);
        double k = Atmosphere.kappaMemo(x, z, seed, cell);
        // ★ 感热通量要比的是【同高度的近地面气温】，不是海平面等效温度。
        //   第一版用了海平面温度 => 高原的递减项（青藏 4740 m 约 -31 K）完全丢失
        //   => 亚洲的 T_s - T_a 失真 => Q_net 的经向对比反号（实测亚洲 -80.9 比撒哈拉 -48.1 更冷）。
        //   surfaceTemp 已含 -GAMMA*max(0,elev)*kappa，且尊重 SKIN_TEMP_FROM_ENERGY_BALANCE。
        double ta = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
        double[] u = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double chv = Radiation.bulkCoeff(k, Math.hypot(u[0], u[1]));
        double dec = Atmosphere.subsolarLat(theta);
        // §459：植被/干旱度状态 V 进反照率（步 b 的判据由它满足）。
        // ⚠ V 的输入是**本函数已经收到的** pMmDay —— 不重算降水 ⇒ 没有递归、没有额外 windAt。
        //   代价：每点一次【有界不动点】，且被 Vegetation.MEMO 记住，本配置下只解一次。
        // §462：回调返回【干降水 P_d】。第一版把它当常数（= 传入的 pMmDay，即 V=1 时的降水）
        // ⇒ 只闭合 D_B 通道（文献指定的主导通道，闭式求解，**零次额外 mmPerDay**）。
        // ⚠ RS_BARE / 反照率的【次级】通道尚未进入回调 ⇒ 记账：要迭代它们必须先让 mmPerDay 认 V。
        final double pForVeg = pMmDay;
        final boolean isLandHere = k > 0.5;
        double veg = isLandHere
                  ? Vegetation.vegAt(x, z, seed, cell, vv -> pForVeg)
                  : 1.0;
        double albHere = Radiation.albedo(isLandHere, ta)
                       + (isLandHere ? Vegetation.albedoAdd(veg) : 0.0);
        if (albHere > 0.95) albHere = 0.95;
        double absSolar = Radiation.insolation(lat, dec) * (1.0 - albHere);
        double qa = PrecipField.Q_FROM_SOURCE
                  ? PrecipField.moistureFromSource(x, z, seed, cell, theta, gradStep, ta, 0.0, k, u[0], u[1])
                  : PrecipField.moisture(ta, 0.0, k);
        // §456 a″：配对守卫 —— 陆地上 beta 必须来自桶，否则 Q_sens / F_net 全错。
        double beta;
        if (SoilMoisture.ENABLED) {
            beta = SoilMoisture.betaAt(x, z, seed, cell, theta, gradStep);
        } else {
            beta = 1.0;
            if (k > 0.5) {
                betaFallbackLandCalls++;
                if (!betaWarned) {
                    betaWarned = true;
                    System.err.println("[StationaryWave] §456 pairing guard: Q_NET_HEATING with"
                        + " SoilMoisture.ENABLED=false fell back to beta=1 on LAND => Q_sens / F_net unusable.");
                }
            }
        }
        double ts = Radiation.skinTempLand(absSolar, ta, qa, chv, beta);
        double qLat = LV_W * pMmDay;
        double qSens = chv * Radiation.CP * (ts - ta);
        // ★★★ §443：柱长波吸收的【水汽依赖】（文献 Jalihal & Mikolajewicz 2025 的主导项）。
        //   现状：吸收率恒为 1（`sigma*Ts^4` 全被吸收）⇒ 只依赖地表温度，与柱水汽无关，
        //   而且归因相反（更热的沙漠辐射掉更多 ⇒ 被推成更强的辐射汇）。
        //   宽带吸收率形式：A = 1 - exp(-k*CWV)，CWV = q*rho*H_MOIST（模型自己的 2-D 场）。
        //   WVLW_K = 0 ⇒ A = 1 ⇒ 与接线前【逐位相同】。
        double cwv = qa * Atmosphere.RHO_AIR * PrecipField.H_MOIST;      // kg/m^2
        double qRad;
        if (QRAD_ASR_MINUS_OLR) {
            // §455：物理口径 F_net = ASR - OLR + LH + SH。
            // ★★★ §575：absSolar 是【地表吸收短波】，必须先去掉大气吸收那一份才是【行星 ASR】。
            //   实测（GERB）：撒哈拉 TOA 净短波 314 W/m²，而 absSolar = 395.5 ⇒ 差 81 W/m²。
            qRad = absSolar * (1.0 - ABS_ATM_FRAC) - olrClear(ts, cwv);
        } else {
            double absFrac = (WVLW_K > 0.0) ? (1.0 - Math.exp(-WVLW_K * cwv)) : 1.0;
            qRad = Radiation.SIGMA * ts * ts * ts * ts * (absFrac - 2.0 * Radiation.EPS);
        }
        if (DIAG_HEAT != null) {
            double[] d = DIAG_HEAT.get();
            d[0] = qLat; d[1] = qSens; d[2] = qRad; d[3] = cwv; d[4] = ts; d[5] = ts - ta;
        }
        return qLat + qSens + qRad;
    }
    /** 当前线程是否正在解定常波（供 PrecipField 的接线判断）。 */
    public static boolean isSolving() { return IN_SOLVE.get(); }
    public static double lastResidual = -1;

    // p 是复数：p = curP + i*curPi。391 的复空间推导要求【全程】保留虚部。
    // 只留实部 => 强迫与解的相位被丢掉，场会被镜像（E127，2026-09 P580 抓到）。
    private static double[] curP, curPi;
    private static double[] qGrid;          // rows*NX，求解时实际吃的强迫（诊断用）
    public static double lastForcing = -1;  // max|Q|（诊断用）
    private static double dy_, kk_;
    private static double[] fOf, Uy;

    private static long stamp() {
        long h = 1125899906842597L;
        h = h * 31 + NX; h = h * 31 + NPHI;
        h = h * 31 + Double.doubleToLongBits(DAMPING);
        h = h * 31 + Double.doubleToLongBits(C_GRAV);
        h = h * 31 + Double.doubleToLongBits(Q_SCALE);
        h = h * 31 + Double.doubleToLongBits(Q_PER_MMDAY);
        h = h * 31 + (Q_NET_HEATING ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(Q_WM2_TO_SW);
        h = h * 31 + (ZERO_F ? 1 : 0);      // ★ 忘了它 => 改了 ZERO_F 却命中旧缓存（探针会读到假结果）
        h = h * 31 + (CLOSED_LOOP ? 1 : 0); // 闭环解与开环解不同 => 必须进缓存键
        h = h * 31 + LOOP_MAX_ITER;
        h = h * 31 + Double.doubleToLongBits(LOOP_TOL);
        h = h * 31 + Double.doubleToLongBits(LOOP_RELAX);
        return h;
    }

    // p'（复数）。中心差分；两端单侧，系数必须是 1/dy（不是 1/(2dy)）—— 391 抓到过。
    private static double[] pPrimeAt(int j) {
        double[] o = new double[2];
        if (j <= 0) { o[0] = curP[1] / dy_; o[1] = curPi[1] / dy_; return o; }
        if (j >= rows - 1) { o[0] = -curP[rows - 2] / dy_; o[1] = -curPi[rows - 2] / dy_; return o; }
        o[0] = (curP[j + 1] - curP[j - 1]) / (2 * dy_);
        o[1] = (curPi[j + 1] - curPi[j - 1]) / (2 * dy_);
        return o;
    }

    // v（复数）：A*v + f*u = -p'，消去 u 得 v = (-A*p' + i*k*f*p)/(A^2+f^2)，A = eps + i*k*U。
    private static double[] vAt(int j) {
        int jc = j < 0 ? 0 : (j > rows - 1 ? rows - 1 : j);
        boolean edge = (jc <= 0 || jc >= rows - 1);
        double pjr = edge ? 0.0 : curP[jc], pji = edge ? 0.0 : curPi[jc];
        double[] pp = pPrimeAt(jc);
        double ar = DAMPING, ai = kk_ * Uy[jc], fr = fOf[jc];
        double dr = ar * ar - ai * ai + fr * fr, di = 2 * ar * ai;
        double m2 = dr * dr + di * di;
        double nr = -(ar * pp[0] - ai * pp[1]) - kk_ * fr * pji;
        double ni = -(ar * pp[1] + ai * pp[0]) + kk_ * fr * pjr;
        double[] out = new double[2];
        out[0] = (nr * dr + ni * di) / m2;
        out[1] = (ni * dr - nr * di) / m2;
        return out;
    }

    // 连续方程残差（复数）：A*p + c^2*(i*k*u + v') + Q = 0。
    // ★ qre/qim 必须【分开传】：原来只传实部 => 解的是余弦型强迫，任意相位的 Q 全错（E127）。
    private static double[] resid(int j, double[] qre, double[] qim) {
        double pjr = curP[j], pji = curPi[j];
        double[] pp = pPrimeAt(j);
        double ar = DAMPING, ai = kk_ * Uy[j], fr = fOf[j];
        double dr = ar * ar - ai * ai + fr * fr, di = 2 * ar * ai;
        double m2 = dr * dr + di * di;
        // u = (-A*i*k*p - f*p')/(A^2+f^2)，A*i*k*p = i*k*(ar+i*ai)*(pjr+i*pji)
        double nur = kk_ * (ar * pji + ai * pjr) - fr * pp[0];
        double nui = -kk_ * (ar * pjr - ai * pji) - fr * pp[1];
        double ur = (nur * dr + nui * di) / m2, ui = (nui * dr - nur * di) / m2;
        double[] vm = vAt(j - 1), vp = vAt(j + 1);
        double vpr = (vp[0] - vm[0]) / (2 * dy_), vpi = (vp[1] - vm[1]) / (2 * dy_);
        double c2 = C_GRAV * C_GRAV;
        double[] out = new double[2];
        out[0] = (ar * pjr - ai * pji) + c2 * (0.0 - kk_ * ui + vpr) + qre[j];
        out[1] = (ar * pji + ai * pjr) + c2 * (kk_ * ur + vpi) + qim[j];
        return out;
    }

    /** 求解时实际吃的强迫网格（rows*NX，行优先，诊断/自证用）。 */
    public static double[] qGridCopy() { return qGrid == null ? null : qGrid.clone(); }
    /** 解出的 div(V) 网格（rows*NX，行优先，诊断/自证用）。 */
    public static double[] divGridCopy() { return divCache == null ? null : divCache.clone(); }
    /** 解出的 p = Phi1 网格（rows*NX，m^2/s^2，诊断/自证用）。p<0 = 低层低压。 */
    public static double[] pGridCopy() { return pGrid == null ? null : pGrid.clone(); }

    /**
     * 符号 + 量纲自证：注入一个**正**的高斯热源，断言
     *   (i)   中心 div &lt; 0（热源 => 上升 => 低层辐合）
     *   (ii)  中心 p   &lt; 0（热源 => 低层低压）
     *   (iii) |div(中心)|*c^2/Q(中心) 落在文献区间 [0.90, 1.05]
     *         （文献：Randall Ch.8 / Gill 1983；独立 β 平面数值解给 0.9906，
     *           β=0 时与解析 -1/c^2 吻合 0.9%；热源尺度 250~2000 km 时该比值 0.997~0.910）
     * 返回 {divCenter, pCenter, qCenter, 比值, 通过标志, Q_有效(已去纬向平均), 逐点收支残差}。
     * ⚠ 会覆盖缓存（内部 invalidate()），调用方之后必须重新 ensureSolved。
     */
    public static double[] signSelfCheck() {
        int rowsL = NPHI + 2;
        double[][] q = new double[rowsL][NX];
        int j0 = rowsL / 2, i0 = NX / 2;
        for (int j = 1; j < rowsL - 1; j++)
            for (int i = 0; i < NX; i++) {
                double di = i - i0;
                if (di > NX / 2.0) di -= NX;
                if (di < -NX / 2.0) di += NX;
                double dj = j - j0;
                // ★ 半宽必须落在文献测过的 250~2000 km 内。网格 dx=625 km、dy_=425 km，
                //   取 sigma_x=1.41 格=884 km、sigma_y=2.24 格=953 km => 对应文献的 ~0.97。
                //   一开始我用了 sigma~4.3e3 km（远超文献范围），比值掉到 0.61，那不是求解器的问题。
                q[j][i] = Math.exp(-(di * di / 2.0 + dj * dj / 5.0));
            }
        double[][] savedQ = Q_OVERRIDE;
        boolean savedF = ZERO_F, savedE = ENABLED;
        Q_OVERRIDE = q; ZERO_F = false; ENABLED = true;
        invalidate();
        ensureSolved(1L, 0, 0.0, 500_000);
        double dc = divCache[j0 * NX + i0], pc = pGrid[j0 * NX + i0], qc = q[j0][i0];
        // ★ 逐点恒等式实际是 eps*p + c^2*div = -(Q - Qbar)，因为 Qk[0]（纬向平均）被抹掉了。
        //   拿【原始 Q】当分母会得到偏低的比值 —— 我一开始就是这么算的：
        //     c^2*div = -0.6140、eps*p = -0.1902，和 = -0.8042；而 Qbar = 12.53/64 = 0.1958
        //     => Q - Qbar = 0.8042  【精确吻合】。是我的分母错了，不是求解器错了。
        double qbar = 0;
        for (int i = 0; i < NX; i++) qbar += q[j0][i];
        qbar /= NX;
        double qEff = qc - qbar;
        Q_OVERRIDE = savedQ; ZERO_F = savedF; ENABLED = savedE;
        invalidate();   // 清掉自证用的场，避免污染调用方
        double ratio = Math.abs(dc) * C_GRAV * C_GRAV / qEff;
        double budget = C_GRAV * C_GRAV * dc + DAMPING * pc + qEff;   // 必须 ~0
        double pass = (dc < 0 && pc < 0 && ratio > 0.90 && ratio < 1.05
                       && Math.abs(budget) < 1e-9 * qEff) ? 1.0 : 0.0;
        return new double[]{dc, pc, qc, ratio, pass, qEff, budget};
    }
    public static int gridRows() { return rows; }

    static double[][] solveDense(double[][][] A, double[][] b, int n) {
        for (int c = 0; c < n; c++) {
            int piv = c; double best = -1;
            for (int r = c; r < n; r++) {
                double m = A[r][c][0] * A[r][c][0] + A[r][c][1] * A[r][c][1];
                if (m > best) { best = m; piv = r; }
            }
            double[][][] tt = { A[c] }; A[c] = A[piv]; A[piv] = tt[0];
            double[] bt = b[c]; b[c] = b[piv]; b[piv] = bt;
            double ar = A[c][c][0], ai = A[c][c][1], m2 = ar * ar + ai * ai;
            for (int r = c + 1; r < n; r++) {
                double xr = A[r][c][0], xi = A[r][c][1];
                double fr = (xr * ar + xi * ai) / m2, fi = (xi * ar - xr * ai) / m2;
                for (int cc = c; cc < n; cc++) {
                    double vr = A[c][cc][0], vi = A[c][cc][1];
                    A[r][cc][0] -= fr * vr - fi * vi;
                    A[r][cc][1] -= fr * vi + fi * vr;
                }
                b[r][0] -= fr * b[c][0] - fi * b[c][1];
                b[r][1] -= fr * b[c][1] + fi * b[c][0];
            }
        }
        double[][] x = new double[n][2];
        for (int r = n - 1; r >= 0; r--) {
            double sr = b[r][0], si = b[r][1];
            for (int cc = r + 1; cc < n; cc++) {
                double vr = A[r][cc][0], vi = A[r][cc][1];
                sr -= vr * x[cc][0] - vi * x[cc][1];
                si -= vr * x[cc][1] + vi * x[cc][0];
            }
            double ar = A[r][r][0], ai = A[r][r][1], m2 = ar * ar + ai * ai;
            x[r][0] = (sr * ar + si * ai) / m2;
            x[r][1] = (si * ar - sr * ai) / m2;
        }
        return x;
    }

    // 解一次并按 seed 缓存。强迫取自 PrecipField.mmPerDay（降水型，393 定案）。
    // 成本：NX*NPHI 次 mmPerDay（粗网格）+ NX 次 ~46x46 稠密复数消元，一次约秒级。
    public static synchronized void ensureSolved(long seed, int cell, double theta, int gradStep) {
        long key = seed * 1000003L + stamp();
        if (cacheKey == key && divCache != null) return;
        long t0 = System.nanoTime();
        IN_SOLVE.set(Boolean.TRUE);
        try {
        rows = NPHI + 2;
        double dyTot = R_EFF * Math.PI;
        dy_ = dyTot / (rows - 1);
        double lx = 2 * Math.PI * R_EFF;
        fOf = new double[rows]; Uy = new double[rows];
        double[] phiOf = new double[rows];
        for (int j = 0; j < rows; j++) {
            double phi = -Math.PI / 2 + Math.PI * j / (rows - 1);
            phiOf[j] = phi;
            fOf[j] = ZERO_F ? 0.0 : 2 * OM * Math.sin(phi);
            Uy[j] = 0.0;
        }
        // ★ 闭环：把「建强迫 + 求解」整体放进不动点迭代。开环时 itMax = 1 => 与原路径逐位相同。
        divPrevField = null;                       // 每换一次缓存键都从种子态起步（可复现）
        double[] prevSol = null;
        prevDelta = null;
        double[] paccOut = null;                   // pacc 声明在循环内 => 发布要经这个外层引用
        int iters = 0;
        int itMax = CLOSED_LOOP ? LOOP_MAX_ITER : 1;
        for (iters = 1; iters <= itMax; iters++) {
        double[][] Q = new double[rows][NX];
        if (Q_OVERRIDE != null) {
            // 测试钩子：用自造强迫替换降水场。
            for (int j = 0; j < rows && j < Q_OVERRIDE.length; j++)
                for (int i = 0; i < NX && i < Q_OVERRIDE[j].length; i++) Q[j][i] = Q_OVERRIDE[j][i];
        } else {
            for (int j = 1; j < rows - 1; j++) {
                int z = com.EyeOfHarmonyBuffer.sim.world.WorldContract.zOfLat(Math.toDegrees(phiOf[j]));
                for (int i = 0; i < NX; i++) {
                    int x = (int) Math.round(lx * i / NX);
                    double pm = PrecipField.mmPerDay(x, z, seed, cell, theta, gradStep);
                    if (Q_NET_HEATING) {
                        Q[j][i] = Q_WM2_TO_SW * netColumnHeating(x, z, seed, cell, theta, gradStep, pm) * Q_SCALE;
                    } else {
                        Q[j][i] = Q_PER_MMDAY * pm * Q_SCALE;
                    }
                    nodeCount++;
                }
            }
        }
        double[][][] Qk = new double[NX][rows][2];
        for (int m = 0; m < NX; m++)
            for (int j = 0; j < rows; j++) {
                double sr = 0, si = 0;
                for (int i = 0; i < NX; i++) {
                    double a = -2 * Math.PI * m * i / NX;
                    sr += Q[j][i] * Math.cos(a);
                    si += Q[j][i] * Math.sin(a);
                }
                Qk[m][j][0] = sr / NX; Qk[m][j][1] = si / NX;
            }
        for (int j = 0; j < rows; j++) { Qk[0][j][0] = 0.0; Qk[0][j][1] = 0.0; }   // 去掉平均：距平强迫
        // ★ 用局部累加器：闭环有多轮，divCache 必须【收敛后一次性发布】，
        //   否则别的线程会在迭代中途读到半成品场。
        double[] acc = new double[rows * NX];
        double[] pacc = new double[rows * NX];
        curP = new double[rows];
        curPi = new double[rows];
        qGrid = new double[rows * NX];
        for (int j = 0; j < rows; j++) for (int i = 0; i < NX; i++) qGrid[j * NX + i] = Q[j][i];
        int n = rows - 2;
        double maxResid = 0, maxQ = 0;
        // ★ m 只走到 NX/2：实序列的 DFT 满足 Qk[NX-m] = conj(Qk[m])，负波数那一半是冗余的。
        //   而且原来对 m > NX/2 直接拿 2*pi*m/lx 当波数【是错的波数】（真波数应为负）——E127 一并修掉。
        //   重建时成对取 2*Re(...)；NX 为偶数时 m = NX/2 是 Nyquist，权重 1、只取实部（响应必须是实的）。
        //   副作用：解算量减半（32 次而非 63 次稠密消元）。
        int mh = NX / 2;
        for (int m = 1; m <= mh; m++) {
            kk_ = 2 * Math.PI * m / lx;
            double[] Fkre = new double[rows], Fkim = new double[rows];
            for (int j = 0; j < rows; j++) { Fkre[j] = Qk[m][j][0]; Fkim[j] = Qk[m][j][1]; }
            double w = (NX % 2 == 0 && m == mh) ? 1.0 : 2.0;
            double[][][] A = new double[n][n][2];
            double[][] b = new double[n][2];
            // ★ 关键：装配【矩阵】时必须用【零强迫】。
            //   否则 A[r][c] = resid(e_c) = L[r][c] + Q[r]  =>  A = L + Q 外积 1（秩 1 污染），
            //   解得 L x + Q*(sum_c x_c) = -Q，多出一项。
            //   该项只在 sum_c x_c == 0 时恰好为零 —— 正弦靶子（整周期求和 ~0）正好落在这个特例上，
            //   所以探针的自证通过了；真实强迫不满足 => 污染发作，表现为「解随强迫非线性饱和」。
            double[] zeroQ = new double[rows];
            for (int c = 0; c < n; c++) {
                java.util.Arrays.fill(curP, 0.0);
                java.util.Arrays.fill(curPi, 0.0);
                curP[c + 1] = 1.0;
                for (int r = 0; r < n; r++) { double[] rr = resid(r + 1, zeroQ, zeroQ); A[r][c][0] = rr[0]; A[r][c][1] = rr[1]; }
            }
            java.util.Arrays.fill(curP, 0.0);
            java.util.Arrays.fill(curPi, 0.0);
            for (int r = 0; r < n; r++) { double[] rr = resid(r + 1, Fkre, Fkim); b[r][0] = -rr[0]; b[r][1] = -rr[1]; }
            double[][] x = solveDense(A, b, n);
            // ★ 虚部必须一起装回去。原来只取 x[..][0] => 用的是 Re(p)，场被镜像（E127）。
            for (int j = 1; j <= rows - 2; j++) { curP[j] = x[j - 1][0]; curPi[j] = x[j - 1][1]; }
            for (int j = 1; j < rows - 1; j++) {
                double pjr = curP[j], pji = curPi[j];
                double[] pp = pPrimeAt(j);
                double ar = DAMPING, ai = kk_ * Uy[j], fr = fOf[j];
                double dr = ar * ar - ai * ai + fr * fr, di = 2 * ar * ai;
                double m2 = dr * dr + di * di;
                double nur = kk_ * (ar * pji + ai * pjr) - fr * pp[0];
                double nui = -kk_ * (ar * pjr - ai * pji) - fr * pp[1];
                double ur = (nur * dr + nui * di) / m2, ui = (nui * dr - nur * di) / m2;
                double[] vm = vAt(j - 1), vp = vAt(j + 1);
                double vpr = (vp[0] - vm[0]) / (2 * dy_), vpi = (vp[1] - vm[1]) / (2 * dy_);
                double divr = -kk_ * ui + vpr;
                double divi = kk_ * ur + vpi;
                for (int i = 0; i < NX; i++) {
                    double a = 2 * Math.PI * m * i / NX;
                    // ★ 逆变换必须写全：只加 divr*cos 就是「只取实部」=> 相位错（E127）。
                    acc[j * NX + i] += w * (divr * Math.cos(a) - divi * Math.sin(a));
                    pacc[j * NX + i] += w * (pjr * Math.cos(a) - pji * Math.sin(a));
                }
            }
            // 残差扫【全部】内点（原来只看 rows/2 一行），并统计强迫幅度。
            for (int j = 1; j < rows - 1; j++) {
                double[] rr = resid(j, Fkre, Fkim);
                maxResid = Math.max(maxResid, Math.hypot(rr[0], rr[1]));
                maxQ = Math.max(maxQ, Math.hypot(Fkre[j], Fkim[j]));
            }
        }
        lastResidual = maxResid;
        lastForcing = maxQ;
        // ---- 不动点迭代的收敛判定 ----
        if (LOOP_RELAX < 1.0 && prevSol != null)
            for (int t = 0; t < acc.length; t++) acc[t] = prevSol[t] + LOOP_RELAX * (acc[t] - prevSol[t]);
        lastLoopChange = relChange(prevSol, acc);
        // §352 第 4 门：环路增益 = 本轮增量 / 上轮增量
        double[] delta = new double[acc.length];
        for (int t = 0; t < acc.length; t++) delta[t] = acc[t] - (prevSol == null ? 0.0 : prevSol[t]);
        if (prevDelta != null) {
            double den = maxAbs(prevDelta);
            lastLoopGain = den <= 0 ? 0.0 : maxAbs(delta) / den;
        } else lastLoopGain = -1;
        prevDelta = delta;
        prevSol = acc;
        divPrevField = acc;                        // 下一轮建强迫时读它（仅求解线程可见）
        paccOut = pacc;
        if (lastLoopChange < LOOP_TOL) break;
        }
        lastIterations = iters > itMax ? itMax : iters;
        divCache = prevSol;                        // ★ 收敛后才对外发布
        pGrid = paccOut;
        cacheKey = key;
        solveCount++;
        SOLVE_NANOS += System.nanoTime() - t0;
        } finally { IN_SOLVE.set(Boolean.FALSE); }
    }

    /**
     * z -> 纬度栅格行号（0..rows-1）。
     * ★⚠ {@code WorldContract.latOf} 返回的是【弧度】，不是度（这是 WorldContract 的约定，
     *   Atmosphere 全链路都按弧度用）。这里曾经多写了一次 {@code Math.toRadians} ——
     *   E128（2026-09 抓到）：那让纬度缩小 57.3 倍，于是【所有中高纬查询都读到赤道那一行】，
     *   而输出看起来"平滑合理"，没有任何异常。抽成函数 + {@link #selfCheckMapping()} 是唯一守卫。
     */
    private static double fjOf(int z) {
        double latRad = com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(z);
        return (latRad + Math.PI / 2) / Math.PI * (rows - 1);
    }

    /** x -> 经度栅格列号（0..NX-1，线性，可越界；调用方取模）。 */
    private static double fxOf(int x) {
        return x / (2 * Math.PI * R_EFF) * NX;
    }

    /**
     * 自证：把求解器网格的每个节点 (j,i) 换成世界坐标 (x,z)，再经 {@link #fjOf}/{@link #fxOf}
     * 映射回来，必须还原成 (j,i)。返回最大偏差（单位：格）。健康值 &lt; 0.01；
     * E128 那种量纲错误会让它变成几百甚至上千。
     */
    public static double selfCheckMapping() {
        if (rows <= 0) return -1;
        double lx = 2 * Math.PI * R_EFF, worst = 0;
        for (int j = 1; j < rows - 1; j++) {
            double phiDeg = Math.toDegrees(-Math.PI / 2 + Math.PI * j / (rows - 1));
            int z = com.EyeOfHarmonyBuffer.sim.world.WorldContract.zOfLat(phiDeg);
            worst = Math.max(worst, Math.abs(fjOf(z) - j));
            for (int i = 0; i < NX; i++) {
                int x = (int) Math.round(lx * i / NX);
                double d = Math.abs(fxOf(x) - i);
                d = Math.min(d, Math.abs(d - NX));
                worst = Math.max(worst, d);
            }
        }
        return worst;
    }

    // 该点的定常波辐合 div(V)（1/s）。未启用或未解时返回 0。
    public static double divAt(int x, int z) {
        // ★ 求解期间（含闭环建强迫的那一段）必须读【上一次迭代】的场：
        //   读 divCache 就成了自指，读 0 就退回开环。
        double[] src = IN_SOLVE.get() ? divPrevField : divCache;
        if (!ENABLED || src == null) return 0.0;
        double fx = fxOf(x);
        double fj = fjOf(z);
        int i0 = (int) Math.floor(fx), j0 = (int) Math.floor(fj);
        double tx = fx - i0, tj = fj - j0;
        int i0m = ((i0 % NX) + NX) % NX, i1m = (((i0 + 1) % NX) + NX) % NX;
        int j0m = j0 < 0 ? 0 : (j0 > rows - 2 ? rows - 2 : j0);
        int j1m = j0m + 1;
        double v00 = src[j0m * NX + i0m], v10 = src[j0m * NX + i1m];
        double v01 = src[j1m * NX + i0m], v11 = src[j1m * NX + i1m];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tj) + (v01 * (1 - tx) + v11 * tx) * tj;
    }

    public static synchronized void invalidate() { cacheKey = Long.MIN_VALUE; divCache = null; divPrevField = null; }
}
