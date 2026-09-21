package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.util.HashMap;
import java.util.Map;

/**
 * ★ S3 完整形态：**Manabe 土壤湿度桶**（有记忆），给出逐点表面湿润度 {@code beta}。
 *
 * <p><b>为什么必须有它</b>（§404/§405）：{@code moisture() = RH_SEA * qSat(T) * depl}，
 * {@code RH_SEA = 0.80} 是**全球常数** ⇒ 模型里没有「干燥」这个状态。
 * 实测（P601）：撒哈拉 JJA 的 {@code q = 0.018729}，与亚洲的 {@code 0.017259} 几乎一样
 * ⇒ 撒哈拉与亚洲一样湿（观测上差 74 倍）。
 *
 * <p><b>为什么不能用 {@code beta = min(1, P/E_p)}</b>（§357 的循环，P604 直接证实）：
 * 那个 {@code beta} 从本点降水 {@code P} 算，而 {@code P ∝ q·wEff} 本身就要靠 {@code beta} 修
 * ⇒ 循环。实测撒哈拉 {@code beta = 0.4576} **反而高于**亚洲 {@code 0.3991}，完全反了。
 *
 * <p><b>本类怎么破环</b>：{@code beta} 由**土壤湿度 W 的状态**决定，而 W 由
 * {@code dW/dt = P - E - R} 的**积分**决定 ⇒ {@code t} 时刻的 {@code beta} 来自**过去**的 P，不是当前的 P。
 *
 * <p><b>★ 架构关键：记忆是【解出来的】，不是【带在身上】的。</b>
 * 该 ODE 逐列独立、强迫按季节周期 ⇒ 它的**周期稳态解**是一个确定的静态函数
 * {@code W(x, z, theta)}。所以本类**不需要任何可变全局状态**：把 W(θ) 解出来并按列缓存即可。
 * 这拿到「时间步进桶」的全部物理，却不破 §348 的「静态纯函数」契约
 * （可任意顺序调用、多线程安全、跨 seed 无残留）。
 *
 * <p><b>方程（Manabe 1969；与模型已有的常量对齐，零新拟合参数）</b>：
 * <pre>
 *   W' = P - E - R,   W in [0, W_FC]        （W_FC = 田间持水，mm）
 *   R  = 超出 W_FC 的部分（径流）
 *   E  = beta * E_p                          （Manabe：beta 乘在【通量】上）
 *   beta = min(1, W / (WK_OVER_WFC * W_FC))  （Manabe eq.20；WK_OVER_WFC = 0.75 模型已有）
 * </pre>
 *
 * <p><b>为什么要改 q 而不只是改 E</b>：本模型的降水是 {@code P = EPS_C*rho*q*wEff/rho_w}
 * ⇒ 只改 E 改不了 P，必须让近地面 q 也响应表面干湿。物理上近地面空气的水汽由
 * 「局地蒸发 + 平流带来」两部分组成，所以
 * {@code RH_eff = RH_SEA*beta + RH_DRY*(1-beta)}。
 * {@code RH_DRY} 是「完全干表面之上的近地面相对湿度」，沙漠边界层观测值 10~30%（取 0.20）。
 *
 * <p><b>默认 ENABLED=false ⇒ 逐位不变。</b>
 */
public final class SoilMoisture {

    private SoilMoisture() {}

    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【6 份】，语义各不相同、默认值也不一致
     * （4 个 false：HadleyCell/SoilMoisture/StationaryWave/Vegetation；2 个 true：OceanField/SimTerrain）。
     * **本份的含义是：SoilMoisture（土壤湿度桶 / S3）。** 引用时务必写全类名（如 `SoilMoisture.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     */
    // ★★★ §574：默认改为 true（用户裁决）。
    //   文献锚：Manabe 1969 的桶（W_FC=15cm, W_K=0.75·W_FC）与 Isca GMD 2018 §5.4 逐字
    //   「evaporation is proportional to the bucket depth as a fraction of the field capacity」。
    //   为什么必须开：四个参考模式（Isca / SpeedyWeather.jl / PlaSim / MITgcm）一致指出，
    //   陆地上的「沙漠级」干旱只靠两件事 —— (i) 一个会归零的土壤水库，(ii) 陆地反照率高于海洋。
    //   关着时 beta 恒为 1（潜在蒸发）⇒ 源码自己的注释写着「模型里没有『干燥』这个状态」。
    //   代价：每个点都要自旋一次桶（见 SPIN_TOL_REL），会显著变慢。
    public static boolean ENABLED = true;

    /** 田间持水（mm）—— Manabe 桶的标准取值。 */
    public static double W_FC = 150.0;
    /** 「完全干表面之上」的近地面相对湿度（沙漠边界层观测 10~30%）。 */
    public static double RH_DRY = 0.20;

    /**
     * ★★★ **§440：表面阻力（canopy / surface resistance）**，单位 s/m。默认 **0**（无阻力）。
     *
     * <p>物理：现在 `E_p` 只有**空气动力学阻力** `r_a = 1/chv`，没有表面阻力。Monteith (1965) 的
     * 标准形式是 `E = rho*(q_sat(T_s) - q_a)/(r_a + r_s)`，即把 `chv` 换成
     * <pre>
     *   chv_eff = chv / (1 + chv*r_s)
     * </pre>
     * 典型量级：`r_a ≈ 80~120 s/m`（|V| = 8 m/s、C_H = 1.3e-3）；完全植被覆盖 50~150 s/m、
     * 稀疏植被 200~500、裸土/结皮 1000~4000。**⇒ 这个乘子能给出 2~25 倍。**
     *
     * <p><b>为什么需要它（§439 的解析结论）</b>：桶的稳态是 `beta = F(beta)`，
     * `F(beta) = A*rh/(e1 - e2*rh)`，`rh = RH_DRY + (RH_SEA-RH_DRY)*beta`。
     * `F` 单调增且**凹**，`F(0) = 0.20A/e1 > 0`。**双稳（沙漠 / 草原两条支）的必要条件是
     * `F(1) > 1`，即 `P(beta=1) > E_p(beta=1)`**；否则 `F - beta` 从正单调落到负，只有一个交点。
     * 实测（P652）：亚洲 `E_p(1)/P(1) = 4.05`、撒哈拉 `3.15` ⇒ **`F(1) = 0.25 / 0.32 << 1` ⇒ 单稳。**
     * 把 `E_p` 整体乘 `1/(1+chv*r_s)` 之后 `F(1)` 就乘上同一个因子 ⇒ **需要 3~4 倍。**
     *
     * <p>⚠ 记账：本量**等于 0 时逐位不变**；一旦非 0 就是**一个新常数**，取值必须落在文献区间内。
     * 另外它**是否与桶自己的 beta 重复计数**（beta 已经是土壤水分胁迫）需要在下结论时说清：
     * 本项代表的是**表面/冠层**的阻力，与土壤供水胁迫是两个物理环节。
     */
    public static double RS_SURF = 0.0;

    /** 一年离散成多少个 theta（沿季节积分）。 */
    public static int NTHETA = 24;
    /** 自旋上限（年）。周期稳态通常 1~3 年就够。 */
    public static int MAX_YEARS = 60;
    /** 收敛判据：|W(2pi) - W(0)| / W_FC。 */
    public static double SPIN_TOL = 1.0e-4;
    /**
     * ★★★ §573：<b>无量纲</b>自旋收敛判据 = 年际差 / 年振幅。
     *
     * <p><b>为什么必须有无量纲判据</b>：强季风气候的土壤桶是【极限环】不是不动点 ——
     * 它有巨大的季节循环，年际差永远到不了 {@link #SPIN_TOL} 那个绝对值。
     * 实测（P905）：亚洲在 {@code MAX_YEARS=60} 撞顶，resid = 1.06e-1，
     * 换算成水量是 0.016 m × 1.06e-1 ≈ 1.7 mm/yr 的年际差 —— 物理上早已可忽略。
     *
     * <p>⚠ 这是<b>数值</b>判据、<b>不是物理常数</b>（同 {@code BLQ_SMOOTH_K} 的先例）。
     * 取 1e-2 = 年际差小于年振幅的 1%。
     */
    public static double SPIN_TOL_REL = 1.0e-2;
    /** 自旋初值（相对 W_FC）。 */
    public static double W_INIT_FRAC = 0.5;

    /** 诊断。 */
    public static long spinupCount = 0, evalCount = 0, SPINUP_NANOS = 0;
    /** §466：自旋返回的年均植被盖度 V̄（ENABLED=false 时恒为 1.0）。 */
    public static double lastV = 1.0;
    /** §466：两 pass 之间 V̄ 的变化量（<0 = 只有一遍）。这是【准静态一拍滞后】的度量。 */
    public static double lastVResid = -1.0;
    /** §466：pass 1 实际被用到的次数（= rsTot 真的被 V 改过的次数）。 */
    public static long refreshUsed = 0;
    /** §467：每个 pass 的 V̄ 序列（诊断双稳 vs 唯一不动点）。 */
    public static final double[] V_HIST = new double[12];
    public static int vHistN = 0;
    public static double lastSpinYears = -1, lastSpinResid = -1;
    /** 诊断：最近一次自旋的【归一化】残差 = 年际差 / 年振幅（无量纲）。 */
    public static double lastSpinRelResid = -1;

    private static final int MEMO_MAX = 1_000_000;
    /** (x,z) -> W(θ) 的 NTHETA 个采样（相对 W_FC）。 */
    private static final ThreadLocal<HashMap<Long, double[]>> MEMO =
            ThreadLocal.withInitial(HashMap::new);
    /** 自旋中标志：切断「解 W -> 调 mmPerDay -> 又要 beta -> 再解 W」的递归。 */
    private static final ThreadLocal<Boolean> IN_SPINUP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isSpinningUp() { return IN_SPINUP.get(); }

    public static void clearMemo() { MEMO.get().clear(); }
    public static int memoSize() { return MEMO.get().size(); }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    /**
     * ⚠⚠ **§440：清空 beta 记忆。**
     *
     * <p><b>为什么必须有</b>：`MEMO` 原来只按 `(x, z)` 作键，且**没有任何失效入口**。
     * 我第一版把 `RS_SURF` 接进 `spinup` 后，A/B（P653）在 r_s = 0/50/100/200/400 上给出**完全相同**的
     * beta 与 P —— 因为第一次自旋的结果被无条件复用了。**这正是 D58 那条教训的又一次重演**
     * （「改了结果就必须让缓存失效」），而且这次是**我自己**犯的。
     */
    public static void invalidate() { MEMO.get().clear(); }

    /** beta = min(1, W / (WK_OVER_WFC * W_FC))（Manabe eq.20）。W 与返回均为相对 W_FC 的分数。 */
    public static double betaOfFrac(double wFrac) {
        double wk = Radiation.WK_OVER_WFC;
        if (wk <= 0) return 1.0;
        double b = wFrac / wk;
        return b < 0.0 ? 0.0 : (b > 1.0 ? 1.0 : b);
    }

    /** 近地面有效相对湿度：局地蒸发（beta 权重）+ 平流（1-beta 权重）。 */
    public static double rhEff(double beta) {
        return PrecipField.RH_SEA * beta + RH_DRY * (1.0 - beta);
    }

    /**
     * beta 与大陆度连续混合：海洋（kappa=0）恒 beta=1，深海内陆（kappa=1）取桶值。
     *
     * 为什么必须有：Manabe 桶是【陆地】参数化。第一版把 beta 用到海洋点上，
     * 实测北太平洋 beta=0.000（本该 1）—— 因为海洋上 E_p 大、P 小，桶自己把自己抽干了。
     * 用模型已有的惯用写法（与 annualSeaLevelTemp 的 (1-k)*sstAnom 同构），零新常数。
     */
    public static double blend(double betaBucket, double kappa) {
        double k = Atmosphere.clamp01(kappa);
        return 1.0 - k * (1.0 - betaBucket);
    }

    /**
     * 解一列的周期稳态 {@code W(θ)}（返回相对 W_FC 的分数，长度 NTHETA）。
     *
     * <p>做法：按 theta 等距步进一年，重复多年直到 W 的年周期重复。
     * 每一步都用【当前 W】给出的 beta 去算 P 与 E ⇒ 记忆天然破环。
     */
    private static double[] spinup(int x, int z, long seed, int cell, int gradStep) {
        int N = NTHETA;
        // ---------- 第一步：每季一次性预计算，之后全是代数 ----------
        // 关键：P 与 E_p 都对 rhEff(beta) 【仿射】，而 A / e1 / e2 与 beta 无关：
        //   P   = EPS_C*rho*qSat(tQ)*depl/rho_w * wEff * rhEff  ==  A  * rhEff
        //   E_p = chv*(qSat(ts) - rhEff*qSat(ta)*depl)/rho_w    ==  e1 - e2*rhEff
        // => 每列只需 N 次昂贵求值（windAt/kappaMemo），自旋的年循环是纯标量代数。
        //    （第一版每季都重跑 mmPerDay，18.1 ms/列且 6 年都收不敛 —— 那是白花的。）
        double[] A = new double[N], e1 = new double[N], e2 = new double[N];
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaMemo(x, z, seed, cell);
        double depl = 1.0;                       // 由 upwindElev 给出，见下
        double hUp = 0.0;
        // ★ §466：pass 循环。Vegetation 关闭（或 RS_BARE=0）时 passes=1 且 rsTot==RS_SURF
        //   ⇒ 下面每一句算式都与接线前【逐位相同】。
        //   为什么是 pass 而不是迭代：§464 实测一次桶自旋 4.378 ms/点，迭代会被性能否证；
        //   这里 pass=1 只多算一次 A/e1/e2 块（evalCount +N），spinupCount【不变】。
        double rsTot = RS_SURF;
        int passes = 1;
        if (Vegetation.ENABLED && Vegetation.RS_BARE > 0.0) passes = 1 + Math.max(0, Vegetation.RS_PASS);
        double[] w = null;
        double resid = Double.NaN;
        int years = 0;
        double vBarFirst = -1.0, vBar = 1.0;
        for (int pass = 0; pass < passes; pass++) {
        for (int kk = 0; kk < N; kk++) {
            double theta = 2.0 * Math.PI * kk / (double) N;
            double[] u = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
            double sp = Math.hypot(u[0], u[1]);
            hUp = PrecipField.upwindElev(x, z, seed, cell, u[0], u[1]);
            depl = PrecipField.depletion(hUp, k);
            double tSl = Atmosphere.annualSeaLevelTemp(lat, k, 0.0)
                       + Atmosphere.seasonalAnomaly(lat, k, theta);
            double divU = diverge(x, z, seed, cell, theta, gradStep);
            double wEff = PrecipField.wEff(lat, theta, divU);
            double qsTQ = PrecipField.qSat(tSl);          // tQ == tSl（Q_AT_SURFACE_TEMP 默认关）
            double chv = Radiation.bulkCoeff(k, sp);
            // ★ E_p 必须在【解出的皮温】上求值 —— 那才是能量限制（Radiation 原文如此）。
            //   第一版直接用 tSl，等于只有空气动力学需求、没有能量上限 => E_p 偏大
            //   => 桶把陆地全抽干（实测亚洲 beta=0.113，解析式 0.2A/(Ep-0.6A)=0.103 吻合）。
            //   E_p 取 beta=1（潜在表面）=> 与 beta 无关 => 下面的仿射分解仍然成立。
            double qa1 = PrecipField.RH_SEA * qsTQ * depl;
            double dec = Atmosphere.subsolarLat(theta);
            double absSolar = Radiation.insolation(lat, dec)
                            * (1.0 - Radiation.albedo(k > 0.5, tSl));
            // §440：Monteith 表面阻力 —— chv -> chv/(1+chv*r_s)。RS_SURF = 0 时 chvE == chv（逐位不变）。
            // §466：状态依赖的裸地阻力 —— rsTot = RS_SURF + RS_BARE*(1-V̄)，V̄ 由【上一 pass】给出。
            //   RS_BARE=0（默认）⇒ rsTot==RS_SURF ⇒ 逐位不变。
            double chvE = (rsTot > 0.0) ? chv / (1.0 + chv * rsTot) : chv;
            double tsPot = Radiation.skinTempLand(absSolar, tSl, qa1, chvE, 1.0);
            double qsTs = PrecipField.qSat(tsPot);
            A[kk]  = (wEff <= 0.0) ? 0.0
                   : PrecipField.EPS_C * Atmosphere.RHO_AIR * qsTQ * depl / PrecipField.RHO_WATER
                     * wEff * 86400.0 * 1000.0;       // mm/day per unit rhEff
            e1[kk] = chvE * qsTs / PrecipField.RHO_WATER * 86400.0 * 1000.0;
            e2[kk] = chvE * qsTQ * depl / PrecipField.RHO_WATER * 86400.0 * 1000.0;
            evalCount++;
        }
        // ---------- 第二步：自旋（纯代数） ----------
        w = new double[N];
        for (int kk = 0; kk < N; kk++) w[kk] = W_INIT_FRAC;
        double dtDays = WorldContract.DAYS_PER_YEAR / (double) N;
        double relResid = -1;
        for (; years < MAX_YEARS; years++) {
            double[] prev = w.clone();
            double cur = w[0];
            for (int kk = 0; kk < N; kk++) {
                double beta = betaOfFrac(cur);
                double rh = PrecipField.RH_SEA * beta + RH_DRY * (1.0 - beta);
                double pMm = A[kk] * rh;
                double epMm = Math.max(0.0, e1[kk] - e2[kk] * rh);
                double dW = (pMm - beta * epMm) * dtDays / W_FC;
                cur += dW;
                if (cur < 0.0) cur = 0.0;
                if (cur > 1.0) cur = 1.0;                 // 超出 W_FC = 径流
                w[kk] = cur;
            }
            // ★ 收敛判据看【整年】的最大变化，不是只看 theta=0（第一版只看 w[0]，会假收敛）
            resid = 0;
            double wMax = -1.0e30, wMin = 1.0e30;
            for (int kk = 0; kk < N; kk++) {
                resid = Math.max(resid, Math.abs(w[kk] - prev[kk]));
                if (w[kk] > wMax) wMax = w[kk];
                if (w[kk] < wMin) wMin = w[kk];
            }
            // ★★★ §573：改用【无量纲】判据。强季风气候是极限环 ⇒ 绝对残差永不收敛。
            //   振幅退化（恒定桶）时 relResid 回落到 resid 本身 ⇒ 与旧行为一致。
            double amp = wMax - wMin;
            relResid = (amp > 1.0e-6) ? (resid / amp) : resid;
            if (resid < SPIN_TOL || relResid < SPIN_TOL_REL) { years++; break; }
        }
        // ★ §466：用【收敛后的状态】算 V —— 输入 P 就用年循环里已经算出来的那个量（§465）。
        //   零额外自旋：这里只是把 A[kk]*rh 与闭式解再走一遍（纯标量）。
        if (Vegetation.ENABLED) {
            double sv = 0;
            for (int kk = 0; kk < N; kk++) {
                double beta = betaOfFrac(w[kk]);
                double rh = PrecipField.RH_SEA * beta + RH_DRY * (1.0 - beta);
                sv += Vegetation.vStarClosed(A[kk] * rh);
            }
            vBar = sv / N;
            if (vBarFirst < 0.0) vBarFirst = vBar;
            if (pass < V_HIST.length) V_HIST[pass] = vBar;
            if (pass + 1 > vHistN) vHistN = pass + 1;
            if (pass < passes - 1) {
                rsTot = RS_SURF + Vegetation.RS_BARE * (1.0 - vBar);
                refreshUsed++;
            }
        }
        lastSpinYears = years;
        lastSpinResid = resid;
        lastSpinRelResid = relResid;
        }   // end pass（§466）
        lastV = vBar;
        lastVResid = (vBarFirst < 0.0) ? -1.0 : Math.abs(vBar - vBarFirst);
        return w;
    }

    /** 与 {@code PrecipField.mmPerDay} 同源的散度（中央差分，步长同 UPWIND 口径）。 */
    private static double diverge(int x, int z, long seed, int cell, double theta, int gradStep) {
        double[] ux = Atmosphere.windAt(x + gradStep, z, seed, cell, theta, gradStep);
        double[] uw = Atmosphere.windAt(x - gradStep, z, seed, cell, theta, gradStep);
        double[] un = Atmosphere.windAt(x, z + gradStep, seed, cell, theta, gradStep);
        double[] us = Atmosphere.windAt(x, z - gradStep, seed, cell, theta, gradStep);
        return (ux[0] - uw[0]) / (2.0 * gradStep) + (un[1] - us[1]) / (2.0 * gradStep);
    }

    /**
     * 潜在蒸发 {@code E_p}（mm/day）—— 用**皮温 = 海平面等效温度**的空气动力学式
     * {@code E_p = rho*C_D(k)*|V|*(qSat(T_s) - q_a)/rho_w}。
     *
     * <p>注意：这里用解析式而**不是** {@code Radiation.skinTempLand} 的二分皮温 ——
     * 自旋里每列要算 NTHETA*年数 次，二分 60 次的代价不可接受；
     * 而 {@code SKIN_TEMP_FROM_ENERGY_BALANCE} 默认关时生产本来就用诊断温度。
     */
    static double potentialEvapMmDay(int x, int z, long seed, int cell, double theta, int gradStep) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaMemo(x, z, seed, cell);
        double qa = PrecipField.moisture(Atmosphere.annualSeaLevelTemp(lat, k, 0.0)
                    + Atmosphere.seasonalAnomaly(lat, k, theta), 0.0, k);
        double[] u = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double sp = Math.hypot(u[0], u[1]);
        double chv = Radiation.bulkCoeff(k, sp);
        double ts = Atmosphere.annualSeaLevelTemp(lat, k, 0.0) + Atmosphere.seasonalAnomaly(lat, k, theta);
        return Radiation.potentialEvapMmDay(ts, qa, chv);
    }

    /**
     * 逐点表面湿润度 {@code beta}。首次访问该列时解周期稳态并缓存。
     * <b>自旋中调用会直接返回 NaN</b>（调用方应当已经设好 BETA_OVERRIDE）。
     */
    public static double betaAt(int x, int z, long seed, int cell, double theta, int gradStep) {
        if (!ENABLED || isSpinningUp()) return 1.0;
        HashMap<Long, double[]> m = MEMO.get();
        // §440：把 RS_SURF 折进键（RS_SURF = 0 时 doubleToLongBits(0.0) = 0 ⇒ 键与历史【逐位相同】）。
        long kk = key(x, z) ^ (Double.doubleToLongBits(RS_SURF) * 0x9E3779B97F4A7C15L);
        if (m.size() > MEMO_MAX) m.clear();
        double[] w = m.get(kk);
        if (w == null) {
            long t0 = System.nanoTime();
            IN_SPINUP.set(Boolean.TRUE);
            try {
                w = spinup(x, z, seed, cell, gradStep);
            } finally {
                IN_SPINUP.set(Boolean.FALSE);
            }
            SPINUP_NANOS += System.nanoTime() - t0;
            spinupCount++;
            m.put(kk, w);
        }
        // 线性插值到连续 theta
        double ph = theta / (2.0 * Math.PI);
        ph -= Math.floor(ph);
        double f = ph * NTHETA;
        int i0 = (int) Math.floor(f);
        double t = f - i0;
        int i1 = (i0 + 1) % NTHETA;
        double wv = w[i0] * (1.0 - t) + w[i1] * t;
        return blend(betaOfFrac(wv), Atmosphere.kappaMemo(x, z, seed, cell));
    }
}
