package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 静态气候解流水线：LandV3 -> Stommel -> T/S 平流扩散 -> 结果。
 *
 * <p>★ 2026-11-02（甲：季节循环）：不再只解一个相位。按 {@link #SEASONAL_N} 个相位
 * 依次求稳态（热启动：保留上一季的 T/S/h），最后输出**年平均场**。
 * <p>★ 2026-11-02（KT + 多年）：h 由 Kraus–Turner 卷吸方程时间积分（见
 * {@code AdvectionDiffusionSolver}），不再用"B&lt;0 就 h=hLimit"的瞬时整柱对流；
 * 并按年循环至 {@link #SEASONAL_CONV_K} 收敛（{@link #SEASONAL_YEARS} 为上限）。
 *
 * <p>生产默认不调用；只生成一次，后续走缓存。
 */
public final class StaticOceanSolver {

    /**
     * 缓存换代：v4 季节循环；v5 稳态短波源 + 海冰；v6 KT 卷吸 + 多年循环 + 分层 KV；
     * v7 prognostic S/T + C 网格投影；v8（2026-11-03）域宽 2 圈 -> 1 圈（40,000 km）+ 旋度配对修正。
     */
    public static final int SOLVER_VERSION = 9;   // ★ 2026-11-04（A6）：干格层判、MOC 口径、内部冰点、海气耦合

    /**
     * 季节采样数：4 = 季（默认）；12 = 月。
     * <p>⚠ 2026-11-02 实测：12 月 + 3D 盐度 + 冰盐析会累积到 S=215 psu / MOC=1272 Sv；
     * 根因是"整柱对流 → 单季结冰 3 m → 盐析通量过大"。在修好这个链之前，默认用 4 季。
     */
    public static int SEASONAL_N = 4;

    /**
     * ★ 2026-11-12（交接文档 §11.3 B1，用户批准）：收尾时把 {@code iceFraction} 与冰厚统一到年平口径。
     *
     * <p>问题：收尾循环把 {@code iceThickness} 换成年平均，但 {@code iceFraction} 停在最后一季
     * ⟹ 存出去的状态混两个时间口径，而世界（{@code Atmosphere.ICE_PROVIDER}）读的是 iceFraction。
     *
     * <p>实测（NICECAL，1 年、全湿格 6466）：只有 **2 格**不同 —— 冰厚普遍 > H_ICE_REF=0.5 m
     * ⟹ 分数早已饱和成 1，所以这是口径修复而不是改解。
     *
     * <p><b>默认 true</b>（用户 2026-11-12 批准改默认）；置 false 逐位回到旧口径。
     * 会改变世界输出 ⟹ 已折进 {@code solveFlagsHash()}（A3-⑤），旧缓存不会命中。
     */
    public static boolean ICE_FRACTION_FROM_MEAN = Boolean.parseBoolean(
            System.getProperty("eoh.icefrommean", "true"));
    /** Route B: annual iceFraction is an independent area mean; default off. */
    public static boolean ROUTE_B_AREA_ON = false;
    /**
     * ★ 2026-11-23（BZ）：**已删除 `PIC_FIRST`**。
     *
     * <p>原值 2，只在「run 的第一年第一季」生效（其余季用 {@link #PIC_WARM}=1）。
     * 实测后果：同一状态出发，**run 的第 2 年**与**新 run 第 1 年**差 **2.13 K** ——
     * 年映射因此不是状态的函数，平衡态求解（M(x)=x）无法建立。
     * 删掉特判后两次运行**逐位相同**（max|dT| = 0）。
     * <p>⚠ 这会改变全部气候结果（首季少一次 Picard）。
     */
    /** 之后每季的 Picard 次数（热启动）。 */
    public static int PIC_WARM = 1;

    /**
     * 多年循环的最大年数。
     * <p>★ 2026-11-02 实测（seed 0，4 季/年，KT + 分层 KV + 冰）：
     * 跑 2 年会进入**雪球态**——T=270.65..287.29 K、冰盖 6636/7946（83.5%）、
     * 平均冰厚 22.45 m、h 均值 1008 m、MOC=359.9 Sv。
     * 机理：深层仍可整柱不稳定 → KT 的 ΔT 被 0.05 下限放大 → h 仍会跳到 hLimit；
     *      冰反照率正反馈再把整个海洋冻住。这是**真发现**，但默认不能跑成雪球。
     * ⟹ 默认 1 年；2+ 年作为诊断开关（SEASONAL_YEARS=2/3）。
     */
    /**
     * ★★★★★ 2026-11-23（平衡解改造）：多年循环的**安全上限**（不再是"跑满 N 年"）。
     *
     * <p>真正的停止由 {@link #SEASONAL_RMS_TOL} 决定：面积加权 RMS_T < 1e-3 K/年。
     * 实测从初值 0.375 K/年出发、比值 ≈0.90/年 ⟹ 约 **60 年**收敛；
     * 上限只是防止病态世界无限跑（150 年是 2.5 倍余量）。
     *
     * <p>历史：2026-11-20 曾把 20 当"上限"，但当时用的是**不会收敛的判据**
     * （逐点 maxDT 停在 1.8 K、全球热含量停在 1.4e-4）⟹ 每次都撞 20 年帽。
     */
    public static int SEASONAL_YEARS = 20;
    //
    // ★ 2026-11-23（CD 报告，重要）：判据已改成面积加权 RMS_T（见 SEASONAL_RMS_TOL），
    //   但实测它**到不了 1e-3**：RMS_T 从 0.375 降到 y=20 的 0.104 之后**回升并平台在 0.11**。
    //   ⟹ 这套模型**没有年映射的不动点**（年际变率下限约 0.11 K RMS），
    //   所以"迭代到判据"不成立，20 年仍是**临时**上限（与旧成本相同，不是回归）。
    //   下一步必须先定位这个 0.11 K 的年际振荡来源，再谈真正的稳态。

    /**
     * ★ 2026-11-17（B1）：深层稳态 —— 每年结束后，对 {@code zCenter >= DEEP_STEADY_Z0} 的层
     * 解【稳态平流-扩散】BVP（逐列），把解写回状态。**默认关；关时逐位不变**（不进
     * {@code solveFlagsHash}，因为开关只在 {@link #solve} 的年循环末尾执行，且 ON 时也会改解）。
     *
     * <p>方程（逐列、定常、无源）：{@code d/dz(w·T − K·dT/dz) = 0}，用模型自己的
     * {@code w}（迎风）与 {@code K = AdvectionDiffusionSolver.kvAt}，不引任何新常数。
     * 上边界取 z0 以上那一层的年均值（Dirichlet），海底零通量。
     *
     * <p>物理含义（必须知道）：零通量边界 + 无源 ⟹ 稳态通量恒为 0 ⟹
     * {@code w·T = K·dT/dz}，即"平流-扩散平衡"的指数剖面；若深层 w→0，解退化为**等温**
     * （温度 = 上边界值）。这不是 bug，是方程的解。
     *
     * <p>⛔ <b>2026-11-17 实测否决（交接文档 §25）：两条验收门都 FAIL，且错在前提而非实现。</b>
     * 单元测试（合成柱、w=0）给出等温解到 5.7e-14 ⟹ 求解器正确；但真实状态下
     * 等温值是 z0 以上那层（≈280.7 K），比时间步进的冷深层（274.8 K）**暖 5.9 K**，
     * 写回后反而让深层漂移从 0.027 放大到 <b>0.209 K/yr</b>，且 68% 的列矩阵病态被跳过。
     * ⟹ 本方法**保留为可复现该否定的仪器**，默认 false，不作为生产路径；
     * 根因见 §24（本模型没有深水形成）与 §25.5。
     */
    public static boolean DEEP_STEADY_ON = false;

    /**
     * ★ 2026-11-23（稳态改造，第 2 步）：**把深层稳态当【初值】做一次**，而不是每年补丁。
     *
     * <p>为什么：实测 ¤DEEP_STEADY_ON=true¤（每年结束后替换深层）会让第 2 年 ¤maxDT¤
     * 从 5.7 K 突跳到 **32.2 K**，而深层漂移只从 3.4%/年 提到 5%/年。硬替换的时机错了。
     *
     * <p>本开关在**进入年循环之前**调用一次 {@link #applyDeepSteady}：
     * 深层从一个定常解出发，之后由正常时间推进接管 ⟹ 不会有突跳。
     * 默认 **false**（不改变现有结果）；用探针打开验证后再决定是否翻默认。
     */
    public static boolean DEEP_STEADY_INIT_ON = false;

    /**
     * ★ 2026-11-23（平衡解改造，第 3 步的钩子）：**年映射实验用的初值**。
     *
     * <p>非 null 时 {@code solve} 在冷启动/父热启动之后用它覆盖 T,S —— 
     * 目的是验证「一年映射 M(x) 是不是状态的纯函数」。
     * 默认 null ⟹ 生产行为完全不变。
     */
    public static OceanState MAP_SEED = null;

    /**
     * 年映射实验：在 solve **开始时**清掉派生量缓存（土壤/植被/驻波/EBM/Hadley/...）。
     * 默认 false ⟹ 生产行为不变。A/B 两次对照必须**同时**打开或同时关闭。
     */
    public static boolean CLEAR_CACHES_AT_SOLVE_START = false;

    /** 年映射实验用的快照容器：每年【4 个季节走完、年度平均之前】存一份预后场全量拷贝。null = 不存。 */
    public static java.util.List<OceanState> MAP_CAPTURE = null;

    /** 年映射实验用的**逐季**快照（每个季节走完抓一份，用来二分定位分叉点）。null = 不存。 */
    public static java.util.List<OceanState> MAP_SEASON_CAPTURE = null;

    /**
     * 把 src 的**预后场**拷进 dst（年映射的状态向量）。
     *
     * <p>纳入：t / s / 冰厚 / 冰密集度 / 雪厚 / 混合层深 / eta / psi。
     * 其余（u,v,w,uF,vF,wF,冰速,应力）在模型里每季由 T,S 与冰**诊断**得到，不进状态向量。
     * 若实测证明还需要它们，再往这里加（下一轮就靠这个测试决定）。
     */
    public static void copyPrognostic(OceanState src, OceanState dst) {
        System.arraycopy(src.t, 0, dst.t, 0, src.t.length);
        System.arraycopy(src.s, 0, dst.s, 0, src.s.length);
        int n2 = Math.min(src.iceThickness.length, dst.iceThickness.length);
        System.arraycopy(src.iceThickness, 0, dst.iceThickness, 0, n2);
        System.arraycopy(src.iceFraction, 0, dst.iceFraction, 0, n2);
        System.arraycopy(src.snowThickness, 0, dst.snowThickness, 0, n2);
        System.arraycopy(src.mixedLayerDepth, 0, dst.mixedLayerDepth, 0, n2);
        System.arraycopy(src.eta, 0, dst.eta, 0, n2);
        int np = Math.min(src.psi.length, dst.psi.length);
        System.arraycopy(src.psi, 0, dst.psi, 0, np);
        // ★ 2026-11-23（实测发现）：**冰的分布状态也必须进状态向量** ——
        //   第一版只拷 t,s,冰厚/密集度,MLD,eta,psi，一年映射仍不纯（max|diff|=150）。
        //   ITD/FSD 是逐类别数组，是冰的"记忆"，漏了它们映射必然不是函数。
        int idt = Math.min(src.itd.area.length, dst.itd.area.length);
        for (int c = 0; c < idt; c++) {
            System.arraycopy(src.itd.area[c], 0, dst.itd.area[c], 0, src.itd.area[c].length);
            System.arraycopy(src.itd.volume[c], 0, dst.itd.volume[c], 0, src.itd.volume[c].length);
            System.arraycopy(src.itd.snow[c], 0, dst.itd.snow[c], 0, src.itd.snow[c].length);
        }
        dst.itd.initialized = src.itd.initialized;
        int ifs = Math.min(src.fsd.area.length, dst.fsd.area.length);
        for (int c = 0; c < ifs; c++)
            System.arraycopy(src.fsd.area[c], 0, dst.fsd.area[c], 0, src.fsd.area[c].length);
        dst.fsd.initialized = src.fsd.initialized;
        // ★ 2026-11-23（补全）：**所有**逐格数组都拷 —— 尤其是混合层卷吸的历史项
        //   hTOld/hTNew（实测 MLD 差 150 m 就是它）以及 surfaceLayer / 冰速 / 应力。
        //   只拷"看起来重要"的字段是错的：状态的定义应当由【代码是否读它跨步】决定。
        int nn = Math.min(src.t.length, dst.t.length);
        System.arraycopy(src.u, 0, dst.u, 0, nn);
        System.arraycopy(src.v, 0, dst.v, 0, nn);
        System.arraycopy(src.w, 0, dst.w, 0, nn);
        System.arraycopy(src.uBaroclinic, 0, dst.uBaroclinic, 0, nn);
        System.arraycopy(src.vBaroclinic, 0, dst.vBaroclinic, 0, nn);
        System.arraycopy(src.uF, 0, dst.uF, 0, nn);
        System.arraycopy(src.vF, 0, dst.vF, 0, nn);
        System.arraycopy(src.wF, 0, dst.wF, 0, nn);
        int m2 = Math.min(src.eta.length, dst.eta.length);
        System.arraycopy(src.surfaceLayer, 0, dst.surfaceLayer, 0, m2);
        System.arraycopy(src.hTOld, 0, dst.hTOld, 0, m2);
        System.arraycopy(src.hTNew, 0, dst.hTNew, 0, m2);
        System.arraycopy(src.meltPondArea, 0, dst.meltPondArea, 0, m2);
        System.arraycopy(src.meltPondDepth, 0, dst.meltPondDepth, 0, m2);
        System.arraycopy(src.iceSurfaceTemp, 0, dst.iceSurfaceTemp, 0, m2);
        System.arraycopy(src.iceU, 0, dst.iceU, 0, m2);
        System.arraycopy(src.iceV, 0, dst.iceV, 0, m2);
        System.arraycopy(src.sig1, 0, dst.sig1, 0, m2);
        System.arraycopy(src.sig2, 0, dst.sig2, 0, m2);
        System.arraycopy(src.sig12, 0, dst.sig12, 0, m2);
        System.arraycopy(src.iceSaltFlux, 0, dst.iceSaltFlux, 0, m2);
        System.arraycopy(src.snowMeltFlux, 0, dst.snowMeltFlux, 0, m2);
    }
    /** 深层稳态的起始深度（m）；该深度以上不动。 */
    public static double DEEP_STEADY_Z0 = 1000.0;
    /** 诊断：最近一次 applyDeepSteady 成功写入的列数 / 因病态被跳过的列数。 */
    public static int LAST_DEEP_COLS = 0, LAST_DEEP_SKIP = 0;

    /**
     * ★ 2026-11-17（A）：逐季快照诊断（**默认关**）。每个季节记录每纬度带的
     * 表层 σ / 500 m σ / 冰厚 / 该季冰析盐 [kg/m²]，用于判"冬季卤水脉冲够不够"。
     * 关时不分配、不采样、不影响任何数值。
     */
    public static boolean SEASON_SNAP_ON = false;
    /** [nseas][3]：表层位密；未开启时为 null。 */
    public static double[][] LAST_SEAS_SIGMA = null;
    /** [nseas][3]：500 m 位密。 */
    public static double[][] LAST_SEAS_SIGMA500 = null;
    /** [nseas][3]：平均冰厚（m）。 */
    public static double[][] LAST_SEAS_ICE = null;
    /** [nseas][3]：该季冰析盐（虚拟淡水通量积分，kg/m²；负 = 析盐加盐）。 */
    public static double[][] LAST_SEAS_BRINE = null;

    /** 采一个季节的快照（只在 SEASON_SNAP_ON 时由季循环调用）。 */
    static void snapshotSeason(OceanGrid g, OceanState st, int si, int nseas, double seasonSeconds) {
        if (!SEASON_SNAP_ON) return;
        if (LAST_SEAS_SIGMA == null || LAST_SEAS_SIGMA.length != nseas) {
            LAST_SEAS_SIGMA = new double[nseas][3];
            LAST_SEAS_SIGMA500 = new double[nseas][3];
            LAST_SEAS_ICE = new double[nseas][3];
            LAST_SEAS_BRINE = new double[nseas][3];
        }
        int nx = g.nx, ny = g.ny;
        double[] cnt = new double[3];
        for (int j = 1; j < ny - 1; j++) {
            double al = Math.abs(Math.toDegrees(g.latAt(j)));
            int b = al < 30.0 ? 0 : al < 60.0 ? 1 : 2;
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(g.nz, g.wetLayers[j * nx + i]);
                if (wl < 3) continue;
                int js = j * nx + i;
                int k5 = 0;
                while (k5 < wl - 1 && g.zCenter[k5] < 500.0) k5++;
                int q0 = st.idx(i, j, 0);
                LAST_SEAS_SIGMA[si][b] += 1027.8 * (1.0 - com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.ALPHA * (st.t[q0] - 285.0)
                        + com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.BETA * (st.s[q0] - 35.0));
                int q5 = st.idx(i, j, k5);
                LAST_SEAS_SIGMA500[si][b] += 1027.8 * (1.0 - com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.ALPHA * (st.t[q5] - 285.0)
                        + com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline.BETA * (st.s[q5] - 35.0));
                LAST_SEAS_ICE[si][b] += st.iceThickness[js];
                LAST_SEAS_BRINE[si][b] += st.iceSaltFlux[js] * seasonSeconds;
                cnt[b]++;
            }
        }
        for (int b = 0; b < 3; b++) {
            if (cnt[b] <= 0) continue;
            LAST_SEAS_SIGMA[si][b] /= cnt[b];
            LAST_SEAS_SIGMA500[si][b] /= cnt[b];
            LAST_SEAS_ICE[si][b] /= cnt[b];
            LAST_SEAS_BRINE[si][b] /= cnt[b];
        }
    }

    /**
     * ★ 2026-11-03：S 初值用物理纬向剖面（高纬淡、副热带咸）。
     * 默认关；A/B 用 NSINIT=1。这不是拟合：高纬表层淡（~34）是海洋气候学的基本事实，
     * 缺了它高纬盐度层结为零，冬季整柱对流（h=1739 m）。
     */
    public static boolean S_PROFILE_INIT_ON = true;
    /** 调试：逐季打印高纬（|lat|>60）平均 h。 */
    public static boolean DEBUG_SEASON_H = false;

    /** 表层盐度纬向剖面（psu）：热带 34.8 / 副热带 35.5 / 中纬 35.0 / 高纬 34.3–34.0。 */
    static double sProfile(double latDeg) {
        double a = Math.abs(latDeg);
        if (a < 15.0) return 34.8;
        if (a < 30.0) return 35.5;
        if (a < 45.0) return 35.0;
        if (a < 60.0) return 34.3;
        return 34.0;
    }
    /** 收敛判据：相邻两年年平均 T 的最大差 [K]。 */
    public static double SEASONAL_CONV_K = 0.05;
    /** ★ 2026-11-07（R3 诊断）：最近一次 solve 的年际 max|dT| 与实际跑了几年。 */
    public static double LAST_MAX_DT = 0.0;
    /**
     * ★ 2026-11-20（收敛判据，用户问题后的结论）：0 = 旧判据（逐点 maxDT < {@link #SEASONAL_CONV_K}）；
     * 1 = **统计判据**：全球热含量年变化率 `|dH/H| < SEASONAL_CONV_REL`。
     *
     * <p>为什么需要：40 年实测（`NLONG40`）显示赤道海洋存在 **1.8–3.2 K 的年际振荡**（极限环），
     * 逐点 `maxDT → 0` 永远不成立；但**全球热含量**是振荡不敏感的量，用它才能判定「统计稳态」。
     */
    public static int SEASONAL_CONV_MODE = 1;   // ★ 2026-11-20 用户裁决：生产用统计判据
    /** 统计判据的相对阈值（每年 |dH/H|）。1e-4 对应全球热含量 1e4 年才翻倍量级。 */
    public static double SEASONAL_CONV_REL = 1.0e-4;

    /**
     * ★★★★★ 2026-11-23（平衡解改造）：**稳态判据 = 面积加权 RMS_T < 本值（K/年）**。
     *
     * <p>实测（CB/CA 报告）：初值 0.375 K/年 → 14 年 0.111 → 比值 ≈0.90/年；
     * 到 1e-3 约需 60 年（≈18 min）。这取代了旧的"跑满 N 年"上限。
     */
    public static double SEASONAL_RMS_TOL = 1.0e-3;
    /** 最近一次的年映射残差 RMS_T（K/年）。 */
    public static double LAST_RMS_T = Double.NaN;
    /** 年际 max|ΔT| 所在格点（q 索引；-1 = 未计算）。用于定位漂移源。 */
    public static int LAST_MAX_DT_Q = -1;
    /** 最近一年的逐格 |ΔT|（年际残差；诊断用）。 */
    public static double[] LAST_RESID = new double[0];
    public static int LAST_YEARS_RUN = 0;
    /** ★ 2026-11-09：年际 max|dS|。 */
    public static double LAST_MAX_DS = 0.0;

    /**
     * ★ 2026-11-09（按用户裁决）：把外层年循环当成**不动点迭代**来解，而不是"跑很多年"。
     *
     * <p>为什么要这个：本类的外层是
     * 稳态动量 -> 30 天示踪平流 -> 稳态动量 -> ...，本质是 Picard 迭代。
     * 我们只需要一个**稳定解**（算一次、缓存、世界直接用），所以判据应该是
     * "映射残差是否降到零"，而不是"跑了几年"。
     *
     * <p>FP_SOLVER_ON=true 时：每年把年平均 T/S 与上一年做**欠松弛**
     * (1-lambda)*prev + lambda*mean，lambda 自适应；并打印/记录残差序列。
     * 默认 false = 逐位不变。
     */
    public static boolean FP_SOLVER_ON = false;
    public static double FP_RELAX = 0.5;
    public static double FP_RELAX_MIN = 0.05;
    public static double LAST_RESID_T = 0.0, LAST_RESID_S = 0.0;
    /** 最近一年的全球热含量相对变化（{@link #SEASONAL_CONV_MODE}=1 时用）。 */
    public static double LAST_CONV_REL = Double.NaN;
    /** Dimensionless year-over-year state residual: maxDT / annual amplitude. */
    public static double LAST_REL_DT = 0.0;
    public static int LAST_REL_DT_Q = -1;
    /** Diagnostic annual-map hooks; null = production cold start. */
    public static OceanState INITIAL_STATE_OVERRIDE = null;
    public static OceanState LAST_STATE = null;
    /** 每年残差序列（T），长度 = 实际跑的年数。 */
    public static final java.util.ArrayList<Double> FP_RESID_T = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> FP_RESID_S = new java.util.ArrayList<>();
    /** 逐年年平均 T（气候平均门用；每项长度 n3）。 */
    public static final java.util.ArrayList<double[]> YEAR_T_MEAN = new java.util.ArrayList<>();
    /** Per-year trace of PrognosticT terms at PrognosticT.TRACE_Q (diagnostic). */
    public static final java.util.ArrayList<double[]> YEAR_TRACE = new java.util.ArrayList<>();
    /** 逐年全球热/盐/冰总量（面积加权；收支诊断用）。 */
    public static final java.util.ArrayList<Double> YEAR_HEAT = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_SALT = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_ICE = new java.util.ArrayList<>();
    /** 逐年上层（z<2000 m）与深层（z>=2000 m）的热量（面积加权；深层慢漂诊断）。 */
    public static final java.util.ArrayList<Double> YEAR_HEAT_UP = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_HEAT_DEEP = new java.util.ArrayList<>();
    /** 逐年 |ΔT| 的年际残差分位数（p50/p90/p99；气候定常的稳健判据）。 */
    public static final java.util.ArrayList<Double> YEAR_RESID_P50 = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_RESID_P90 = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_RESID_P99 = new java.util.ArrayList<>();

    /** 表面通量边界（A）；默认 true。 */
    public static boolean FLUX_BOUNDARY_ON = true;
    /** 诊断：每季末检查 T/S/u/v/w/h/ice 是否有限（默认关，零开销路径不变）。 */
    public static boolean DEBUG_FINITE = false;

    /**
     * ★ 2026-11-02（方案 B）：S 用季节内 prognostic flux-form（30 天 / 30 步），
     * 替代稳态 SOR+全局总盐 corr。默认关，A/B 用环境变量 NPROGS=1。
     */
    public static boolean PROGNOSTIC_S_ON = true;

    private StaticOceanSolver() {}

    /**
     * ★ 2026-11-07（R3）：动量来源二选一。
     * <p>R3 = 稳态 3D 动量平衡（Coriolis + 压力梯度 + A_V），给出局地斜压 u/v；
     * 关 = v1 的 Stommel + 行均匀热成风（逐位不变）。
     */
    static void applyMomentum(OceanGrid g, OceanState st) {
        if (R3Momentum.ON) R3Momentum.applyToState(g, st);
        else BaroclinicFlow.apply(g, st);
    }

    public static OceanSolution solve(int worldSeed, int cell, int x0, int z0,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season) {
        return solve(worldSeed, cell, x0, z0, forcing, theta, landVersion, season, 50000);
    }

    public static OceanSolution solve(int worldSeed, int cell, int x0, int z0,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter) {
        return solve(OceanGrid.landV3(worldSeed, cell, x0, z0), worldSeed, forcing, theta,
            landVersion, season, advMaxIter);
    }

    /**
     * ★ 2026-11-03（阶段 2）：在【任意域】上求解 —— 区域 patch 的入口。
     *
     * <p>域由 {@code grid} 给出：绝对 x/z、真实 LandV3（不折叠）。默认路径
     * {@link #solve(int,int,int,int,SeaSurfaceForcing,double,int,int,int)} 只是用它构造
     * 40,000 km 全球箱并转调本方法，所以旧路径逐位不变。
     */
    public static OceanSolution solve(OceanGrid grid, int worldSeed,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter) {
        return solve(grid, worldSeed, forcing, theta, landVersion, season, advMaxIter, null, 0);
    }

    /**
     * ★ 2026-11-03（阶段 2b）：带父状态 + sponge 的 patch 入口。
     *
     * <p>{@code parent == null} 时与旧路径逐位相同。sponge 只改外圈 {@code spongeCells}
     * 个湿格（y 两端；x 侧等阶段 2c 打开开边界后再生效），中心区完全由本域方程决定。
     */
    public static OceanSolution solve(OceanGrid grid, int worldSeed,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter,
                                      OceanParent parent, int spongeCells) {
        return solve(grid, worldSeed, forcing, theta, landVersion, season, advMaxIter,
            parent, spongeCells, null);
    }

    /**
     * ★ 2026-11-04（阶段 3）：深层源水团初始化（两段式的第二段）。
     *
     * <p>物理依据：深水在高纬形成；深层 T/S 应由【源水团】决定，而不是由局地表面线性降到冰点。
     * {@code hHL} 用模型自己的高纬平均混合层深度（不是新常数）。
     */
    public static final class DeepInit {
        public final double tDeep, sDeep, hHL;
        public DeepInit(double tDeep, double sDeep, double hHL) {
            this.tDeep = tDeep;
            this.sDeep = sDeep;
            this.hHL = Math.max(1.0, hHL);
        }
    }

    public static OceanSolution solve(OceanGrid grid, int worldSeed,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter,
                                      OceanParent parent, int spongeCells, DeepInit deepInit) {
        VelocityProjection.OPEN_PARENT = parent;
        SeaSurfaceForcing periodic = new PeriodicSeaSurfaceForcing(forcing);
        int nx = grid.nx, ny = grid.ny, nz = grid.nz;
        int n3 = nx * ny * nz, n2 = nx * ny;

        double[] fallback = new double[n2];
        for (int j = 0; j < ny; j++) {
            double lat = grid.latAt(j);
            for (int i = 0; i < nx; i++) fallback[j * nx + i] = Atmosphere.oceanBaseK(lat);
        }
        double[] surfaceT = fallback.clone();

        OceanState st = new OceanState(grid);
        // ★ 2026-11-20（D1b/B1）：把本季 SST 的纬向梯度交给大气，供「沃克环流」自算赤道风。
        //   直接读 st（不经过 SST_PROVIDER —— 求解时它是被抑制的，否则递归）。
        if (Atmosphere.EQ_WALKER_ON) {
            final OceanGrid gg = grid;
            final OceanState ss = st;
            Atmosphere.WALKER_PROVIDER = new Atmosphere.WalkerSstProvider() {
                @Override public double dSstDxAt(int x, int z) {
                    int i = gg.iOf(x), j = gg.jOf(z);
                    if (!gg.isWet(i, j)) return 0.0;
                    // 2026-11-20（D1b 标定步 1）：用 ±2 格的宽差分代替 ±1（纯数值平滑，无新常数）。
                    int im = (i - 2 + 2 * gg.nx) % gg.nx, ip = (i + 2) % gg.nx;
                    int q0 = ss.idx(i, j, 0);
                    double tm = gg.isWet(im, j) ? ss.t[ss.idx(im, j, 0)] : ss.t[q0];
                    double tp = gg.isWet(ip, j) ? ss.t[ss.idx(ip, j, 0)] : ss.t[q0];
                    return (tp - tm) / (4.0 * gg.dx);
                }
            };
        }
        // ★ 2026-11-23（BY 报告）：MAP_SEED 的作用点必须放在**冷启动之后**，
        // ★ 2026-11-11（深层收支仪器）：清零并算体积权重；ON=false 时只是几个空数组。
        DeepBudget.begin(grid);
        // 冷启动：★ 2026-11-02 修正输入 —— 深层不能也取表层气候值。
        // 旧版把 oceanBaseK 赋给所有深度 ⟹ 深层天生是暖的 ⟹ 高纬表层(冷)比深层密 ⟹
        // 密度判据永远找不到稳定界面 ⟹ h 顶到全深度；OHT 的深层暖支也把符号带反。
        // 这里用一条**无自由参数**的线性剖面：表层 = oceanBaseK，底层 = 海水冰点。
        // 冰点是物理量（FreezingPoint，UNESCO 1983），不是调参。
        double deepT = com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint.referenceKelvin();
        double hTotal = 0.0;
        for (int k = 0; k < nz; k++) hTotal += grid.dz[k];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                double ts = fallback[j * nx + i];
                double dep = 0.0;
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    double frac = Math.min(1.0, (dep + 0.5 * grid.dz[k]) / hTotal);
                    st.t[q] = ts + (deepT - ts) * frac;
                    if (st.s[q] == 0.0) {
                        if (S_PROFILE_INIT_ON) {
                            double latDeg = Math.toDegrees(grid.latAt(j));
                            double sSurf = sProfile(latDeg);
                            st.s[q] = sSurf + (34.9 - sSurf) * frac;
                        } else {
                            st.s[q] = 35.0;
                        }
                    }
                    dep += grid.dz[k];
                }
            }

        // ★ 2026-11-03（阶段 2b）：有父状态时用【父】热启动。
        //   区域模式的标准做法：边界与初值都取父，避免 patch 从解析剖面冷启动
        //   产生的 1 年瞬态污染 P2 收敛测试。不存在自造参数：只是换初值。
        if (parent != null) {
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    double x = grid.xOf(i), z = grid.zOf(j);
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double pt = parent.temperatureK(x, z, k);
                        if (Double.isFinite(pt)) st.t[q] = pt;
                        double ps = parent.salinityPsu(x, z, k);
                        if (Double.isFinite(ps)) st.s[q] = ps;
                        double pu = parent.uMs(x, z, k);
                        if (Double.isFinite(pu)) st.u[q] = pu;
                        double pv = parent.vMs(x, z, k);
                        if (Double.isFinite(pv)) st.v[q] = pv;
                    }
                }
        }

        // ★ 2026-11-23（年映射实验）：用外部给定的状态覆盖初值 —— **必须放在冷启动/父热启动之后**。
        //   （第一版放在冷启动之前，结果被冷启动覆盖 ⟹ B 其实一直是冷启动，
        //     因此前几轮的"映射不纯"结论全部作废，见 BZ 报告。）
        if (CLEAR_CACHES_AT_SOLVE_START) StaticOceanProduction.clearDerivedCaches();
        if (MAP_SEED != null) copyPrognostic(MAP_SEED, st);

        // ★ 2026-11-04（阶段 3）：深层源水团初始化（在父热启动之后应用；只由调用方显式开启）。
        if (deepInit != null) {
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int wl = grid.wetLayers[grid.idx(i, j)];
                    double ts = st.t[st.idx(i, j, 0)];
                    double ss = st.s[st.idx(i, j, 0)];
                    double dep = 0.0;
                    for (int k = 0; k < wl; k++) {
                        double zc = dep + 0.5 * grid.dz[k];
                        double frac = Math.min(1.0, zc / deepInit.hHL);
                        int q = st.idx(i, j, k);
                        st.t[q] = ts + (deepInit.tDeep - ts) * frac;
                        st.s[q] = ss + (deepInit.sDeep - ss) * frac;
                        dep += grid.dz[k];
                    }
                }
            st.fillDryGhosts();
        }

        if (INITIAL_STATE_OVERRIDE != null) copyState(INITIAL_STATE_OVERRIDE, st);
        if (AdvectionDiffusionSolver.SURFACE_FLUX_COUPLED) {
            // ★ 2026-11-20（P2）：液态海水不得低于冰点。
            //   冷启动剖面与父热启动都可能给出过冷水（实测 patch 极点初值 252.5 K = 冰点下 18.8 K），
            //   耦合表面步会把它一次性折成 12.7 m 冰 —— 那是初值伪影，不是物理。
            //   默认关 ⟹ 逐位不变。
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int wl = grid.wetLayers[grid.idx(i, j)];
                    for (int k = 0; k < wl; k++) {
                        int q = st.idx(i, j, k);
                        double tfq = com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint.kelvin(st.s[q]);
                        if (st.t[q] < tfq) st.t[q] = tfq;
                    }
                }
        }
        int nseas = Math.max(1, SEASONAL_N);
        int years = Math.max(1, SEASONAL_YEARS);
        // ★ 2026-11-09（阶段 3 / A5）：一季 = 一年/nseas，而不是固定 30 天。
        //   旧版 4×30=120 天/年，热/盐/冰年少积分 3.04×。
        AdvectionDiffusionSolver.SEASON_SECONDS =
                WorldContract.DAYS_PER_YEAR * 86400.0 / nseas;
        AdvectionDiffusionSolver.SUBSTEPS = AdvectionDiffusionSolver.substepsFor(nseas);
        FP_RESID_T.clear();
        FP_RESID_S.clear();
        YEAR_T_MEAN.clear();
        YEAR_TRACE.clear();
        YEAR_HEAT.clear();
        YEAR_SALT.clear();
        YEAR_ICE.clear();
        YEAR_HEAT_UP.clear();
        YEAR_HEAT_DEEP.clear();
        YEAR_RESID_P50.clear();
        YEAR_RESID_P90.clear();
        YEAR_RESID_P99.clear();
        // ★★ 2026-11-06（用户裁决：开分季 SST 出口）：保留**最后一年**的逐季表层 T。
        //   为什么必需：P292 的自洽判据要问“上升流”，而上升流是**季节**现象；
        //   原来 OceanSolution 只存年均 SST'，季节信号被抹平（NUPWELL 四版实测 corr≈0）。
        //   只存字段、不参与任何计算 ⇒ 年均路径逐位不变。
        double[][] seasSurf = new double[nseas][];
        double[] tSum = new double[n3], sSum = new double[n3];
        double[] tMinYear = new double[n3], tMaxYear = new double[n3];
        double[] hSum = new double[n2], etaSum = new double[n2], iceSum = new double[n2];
        double[] iceAreaSum = new double[n2];
        double[] tMean = new double[n3], sMean = new double[n3], prevT = new double[n3], prevS = new double[n3];
        /** 年映射平衡判据用：上一年年初的 T（算 RMS_T = 面积加权均方根的年际变化）。 */
        double[] yearT0 = st.t.clone();
        double[] hMean = new double[n2], etaMean = new double[n2], iceMean = new double[n2];
        double[] iceAreaMean = new double[n2];

        AdvectionDiffusionSolver.Result adv = null;
        double sBudget = 0.0;
        BarotropicSolver.Result baro = null;
        double maxDT = 0.0;
        int yearsRun = 0;
        // ★ 2026-11-23（稳态改造第 2 步）：深层稳态当**初值**做一次。
        //   放在年循环之前 ⟹ 之后由正常推进接管，不会像「年度补丁」那样造成 32 K 突跳。
        if (DEEP_STEADY_INIT_ON) {
            applyDeepSteady(grid, st);
            System.out.println("DEEP_STEADY_INIT cols=" + LAST_DEEP_COLS + " skip=" + LAST_DEEP_SKIP);
        }

        // ★ 2026-11-02（KT + 多年）：h 现在是卷吸方程的时间积分，深层通风是多年过程。
        // 单跑一年到不了气候平衡；按年循环，直到相邻两年的年平均 T 收敛。
        for (int yr = 0; yr < years; yr++) {
            java.util.Arrays.fill(tSum, 0.0);
            java.util.Arrays.fill(sSum, 0.0);
            java.util.Arrays.fill(tMinYear, Double.POSITIVE_INFINITY);
            java.util.Arrays.fill(tMaxYear, Double.NEGATIVE_INFINITY);
            PrognosticT.traceReset();
            java.util.Arrays.fill(hSum, 0.0);
            java.util.Arrays.fill(etaSum, 0.0);
            java.util.Arrays.fill(iceSum, 0.0);
            java.util.Arrays.fill(iceAreaSum, 0.0);
            for (int si = 0; si < nseas; si++) {
                double th = (nseas == 1) ? theta : (2.0 * Math.PI * si / nseas);
                baro = BarotropicSolver.update(grid, st, periodic, th, parent);
                if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE baro yr=" + yr + " si=" + si + " " + b); }
                // update() 覆盖了 u/v，但 vBaroclinic/w 仍是上一季的 ⟹ 先清掉。
                java.util.Arrays.fill(st.vBaroclinic, 0.0);
                java.util.Arrays.fill(st.w, 0.0);
                if (parent != null) OceanSponge.apply(grid, st, parent, spongeCells, !OceanSponge.OPEN_X);
                st.fillDryGhosts();
                SteadyFreeSurfaceSolver.solve(grid, st, periodic, th);
                if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE freeSurf yr=" + yr + " si=" + si + " " + b); }
                // ★ 2026-11-23（BZ 报告，平衡解改造）：**删除"第一年第一季多做一次 Picard"的特判**。
                //   实测：这个特判让年映射不是状态的纯函数 —— 从同一状态出发，
                //   "run 的第 2 年"与"新 run 第 1 年"的 T 差 2.13 K；把两者设为相等后
                //   两次运行**逐位相同**（max|dT| = 0）。
                //   平衡解求解要求 M(x) 只依赖 x，所以迭代次数必须由状态/判据决定，不能由年份决定。
                int pics = PIC_WARM;
                for (int pic = 0; pic < pics; pic++) {
                    if (FLUX_BOUNDARY_ON) {
                        // ★★★ 2026-11-20（审计修复）：**平流之前**闭合经向输运 ⟹ 让 T/S 的输运用的是闭合速度场。
                        if (com.EyeOfHarmonyBuffer.sim.ocean.staticcore.VelocityProjection.CLOSE_MERIDIONAL) {
                            com.EyeOfHarmonyBuffer.sim.ocean.staticcore.VelocityProjection.closeMeridional(grid, st);
                        }
                        adv = AdvectionDiffusionSolver.solveFluxBoundary(grid, st, periodic, th,
                            advMaxIter, AdvectionDiffusionSolver.OUTER_MAX);
                        for (int j = 0; j < ny; j++)
                            for (int i = 0; i < nx; i++)
                                if (grid.isWet(i, j)) surfaceT[j * nx + i] = st.t[st.idx(i, j, 0)];
                        // ★ 2026-11-17（A）：快照必须在【盐度步消费 iceSaltFlux 之前】。
                        if (SEASON_SNAP_ON) snapshotSeason(grid, st, si, nseas,
                                AdvectionDiffusionSolver.SEASON_SECONDS);
                    } else {
                        surfaceT = SurfaceTemperatureSolver.solve(grid, periodic, th, fallback, st);
                        AdvectionDiffusionSolver.init(grid, st, surfaceT);
                        double tRes = BiCGSTABT.solve(grid, st, surfaceT, advMaxIter, 1.0e-6);
                        if (!Double.isFinite(tRes) || tRes > 1.0) {
                            adv = AdvectionDiffusionSolver.solve(grid, st, surfaceT,
                                Math.max(advMaxIter, 5000));
                            tRes = adv.residual;
                        } else {
                            adv = new AdvectionDiffusionSolver.Result(0, tRes);
                        }
                    }
                    // 3D 盐度：默认稳态 SOR；PROGNOSTIC_S_ON 时走季节内 flux-form 时间步。
                    AdvectionDiffusionSolver.Result sR;
                    if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE T yr=" + yr + " si=" + si + " " + b); }
                    if (PROGNOSTIC_S_ON) {
                        double dSalt = PrognosticSalinity.run(grid, st, periodic, th,
                                AdvectionDiffusionSolver.SEASON_SECONDS, AdvectionDiffusionSolver.SUBSTEPS);
                        sR = new AdvectionDiffusionSolver.Result(0, Math.abs(dSalt));
                    } else {
                        sR = AdvectionDiffusionSolver.solveSalinity(grid, st,
                            periodic, th, Math.min(advMaxIter, 80));
                    }
                    sBudget = sR.residual;
                    // ★ 2026-11-18（S4）：密度水沿坡下沉（Campin & Goosse 1999）。
                    //   必须在 T/S 都更新之后、且用当季时长；ON=false 时这段是零副作用。
                    if (DenseOverflow.ON) {
                        DenseOverflow.apply(grid, st, AdvectionDiffusionSolver.SEASON_SECONDS);
                    }
                    if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE S yr=" + yr + " si=" + si + " " + b); }
                    // ★ 2026-11-03（stage 3 步序）：盐度改变了 S ⟹ 表层 Tf 变了，重钳。
                    if (AdvectionDiffusionSolver.FREEZE_SURFACE_ON) {
                        for (int j = 1; j < ny - 1; j++)
                            for (int i = 0; i < nx; i++) {
                                if (!grid.isWet(i, j)) continue;
                                int q0 = st.idx(i, j, 0);
                                double tf = FreezingPoint.kelvin(st.s[q0]);
                                // ★ 2026-11-18（A3）：这一句**把水加热到冰点却从不记账** ⟹ 账本少一笔正项。
                                double tOld = st.t[q0];
                                if (tOld < tf) {
                                    st.t[q0] = tf;
                                    if (DeepBudget.ON) DeepBudget.add(DeepBudget.band(grid, j), DeepBudget.T_ML, 0,
                                            (tf - tOld) * grid.dz[0] * Math.max(0.05, grid.cosLat(j)));
                                }
                            }
                    }
                    applyMomentum(grid, st);
                    if (parent != null) OceanSponge.apply(grid, st, parent, spongeCells, !OceanSponge.OPEN_X);
                    st.fillDryGhosts();
                }
                if (DEBUG_FINITE) {
                    String bad = firstNonFinite(grid, st);
                    if (bad != null) System.out.println("NONFINITE yr=" + yr + " si=" + si + " " + bad);
                }
                if (DEBUG_SEASON_H) {
                    double hh = 0.0;
                    int hn = 0;
                    for (int j = 1; j < ny - 1; j++) {
                        double lat = Math.toDegrees(grid.latAt(j));
                        if (Math.abs(lat) < 60.0) continue;
                        for (int i = 0; i < nx; i++) {
                            if (!grid.isWet(i, j)) continue;
                            hh += st.mixedLayerDepth[j * nx + i];
                            hn++;
                        }
                    }
                    System.out.println("SEASONH si=" + si + " hiLatH=" + (hn > 0 ? hh / hn : 0.0));
                }
                // ★★★ 2026-11-20（审计修复，默认 ON）：**季末恢复经向体积闭合**。
                //   投影/free-surface 都会给每个纬度圈加进一条净经向输运（实测 +31.3° = +140.7 Sv）；
                //   闭合海盆里它必须为 0 ⟹ 在本季所有速度修改之后逐行扣掉它的纬向平均。
                if (com.EyeOfHarmonyBuffer.sim.ocean.staticcore.VelocityProjection.CLOSE_MERIDIONAL) {
                    com.EyeOfHarmonyBuffer.sim.ocean.staticcore.VelocityProjection.closeMeridional(grid, st);
                }
                // ★ 2026-11-06：存下**本季**的表层 T（每年覆盖 ⇒ 最后年的值即收敛后的年循环）。
                {
                    double[] snap = new double[n2];
                    for (int j = 0; j < ny; j++)
                        for (int i = 0; i < nx; i++)
                            if (grid.isWet(i, j)) snap[j * nx + i] = st.t[st.idx(i, j, 0)];
                    if (AdvectionDiffusionSolver.ICE_TRACE) {
                        for (int jj = 1; jj < ny - 1; jj++) {
                            if (Math.abs(Math.toDegrees(grid.latAt(jj))
                                    - AdvectionDiffusionSolver.ICE_TRACE_LAT) >= 1.0) continue;
                            for (int ii = 0; ii < nx; ii++) {
                                if (!grid.isWet(ii, jj)) continue;
                                int js2 = jj * nx + ii;
                                if (st.iceThickness[js2] <= 0.0) continue;
                                System.out.println(String.format(java.util.Locale.ROOT,
                                    "ICESNAP si=%d yr=%d i=%d T0=%.3f ice=%.3f hML=%.1f",
                                    si, yr, ii, snap[js2], st.iceThickness[js2], st.mixedLayerDepth[js2]));
                            }
                        }
                    }
                    seasSurf[si] = snap;
                    // ★ 2026-11-23（年映射二分）：每季末抓一份预后场快照。
                    if (MAP_SEASON_CAPTURE != null) {
                        OceanState ss2 = new OceanState(grid);
                        copyPrognostic(st, ss2);
                        MAP_SEASON_CAPTURE.add(ss2);
                    }
                }
                // 年度累加
                for (int j = 0; j < ny; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!grid.isWet(i, j)) continue;
                        int js = j * nx + i;
                        hSum[js] += st.mixedLayerDepth[js];
                        etaSum[js] += st.eta[js];
                        iceSum[js] += st.iceThickness[js];
                        iceAreaSum[js] += st.iceFraction[js];
                        for (int k = 0; k < nz; k++) {
                            int q = st.idx(i, j, k);
                            double tv = st.t[q];
                            tSum[q] += tv;
                            sSum[q] += st.s[q];
                            if (tv < tMinYear[q]) tMinYear[q] = tv;
                            if (tv > tMaxYear[q]) tMaxYear[q] = tv;
                        }
                    }
            }
            // ★ 2026-11-23（年映射实验）：此刻 = 下一年的年初状态（4 个季节刚走完、年度平均之前）。
            if (MAP_CAPTURE != null) {
                OceanState snap = new OceanState(grid);
                copyPrognostic(st, snap);
                MAP_CAPTURE.add(snap);
            }
            // 年度平均
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int js = j * nx + i;
                    hMean[js] = hSum[js] / nseas;
                    etaMean[js] = etaSum[js] / nseas;
                    iceMean[js] = iceSum[js] / nseas;
                    iceAreaMean[js] = iceAreaSum[js] / nseas;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        tMean[q] = tSum[q] / nseas;
                        sMean[q] = sSum[q] / nseas;
                    }
                }
            if (yr > 0) {
                double maxRel = 0.0;
                int maxRelQ = -1;
                for (int q = 0; q < n3; q++) {
                    double diff = Math.abs(tMean[q] - prevT[q]);
                    double amp = tMaxYear[q] - tMinYear[q];
                    double rel = amp > 1.0e-6 ? diff / amp : diff;
                    if (rel > maxRel) { maxRel = rel; maxRelQ = q; }
                }
                LAST_REL_DT = maxRel;
                LAST_REL_DT_Q = maxRelQ;
            }
            // ★ 2026-11-17（B1）：每年的年均算完之后，可选地把深层替换成稳态解。
            if (DEEP_STEADY_ON) applyDeepSteady(grid, st);
            YEAR_T_MEAN.add(tMean.clone());
            double[] tr = PrognosticT.TRACE_SUM.clone();
            double[] trMl = {PrognosticT.TRACE_ML};
            YEAR_TRACE.add(new double[]{tr[0], tr[1], tr[2], tr[3], tr[4], tr[5], trMl[0]});
            double yearHeat = 0.0, yearSalt = 0.0, yearIce = 0.0;
            double yearHeatUp = 0.0, yearHeatDeep = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int js = j * nx + i;
                    double w = Math.max(0.05, Math.cos(grid.latAt(j)));
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double hq = tMean[q] * grid.dz[k] * w;
                        yearHeat += hq;
                        yearSalt += sMean[q] * grid.dz[k] * w;
                        if (grid.zCenter[k] < 2000.0) yearHeatUp += hq; else yearHeatDeep += hq;
                    }
                    yearIce += iceMean[js] * w;
                }
            YEAR_HEAT.add(yearHeat);
            YEAR_HEAT_UP.add(yearHeatUp);
            YEAR_HEAT_DEEP.add(yearHeatDeep);
            YEAR_SALT.add(yearSalt);
            YEAR_ICE.add(yearIce);
            maxDT = 0.0;
            double maxDS = 0.0;
            LAST_MAX_DT_Q = -1;
            if (yr > 0) {
                double[] dts = new double[n3];
                LAST_RESID = new double[n3];
                int m = 0;
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!grid.isWet(i, j)) continue;
                        for (int k = 0; k < nz; k++) {
                            int q = st.idx(i, j, k);
                            double dT = Math.abs(tMean[q] - prevT[q]);
                            dts[m++] = dT;
                            LAST_RESID[q] = dT;
                            if (dT > maxDT) { maxDT = dT; LAST_MAX_DT_Q = q; }
                            maxDS = Math.max(maxDS, Math.abs(sMean[q] - prevS[q]));
                        }
                    }
                java.util.Arrays.sort(dts, 0, m);
                YEAR_RESID_P50.add(m > 0 ? dts[(int) (0.50 * (m - 1))] : 0.0);
                YEAR_RESID_P90.add(m > 0 ? dts[(int) (0.90 * (m - 1))] : 0.0);
                YEAR_RESID_P99.add(m > 0 ? dts[(int) (0.99 * (m - 1))] : 0.0);
            }
            yearsRun = yr + 1;
            // ★ 2026-11-11：封存这一年的逐项热量账（默认关时是空操作）。
            //   必须在 yr==0 也执行，否则 1 年跑不出账本。
            DeepBudget.closeYear(grid, st);
            // ★ 2026-11-23（物理自洽第一诊断）：账本预测的温度变化 vs 实际变化。
            //   账本 = 逐项热收支（K·m/年），除以体积即「按账本这一年该变多少 K」；
            //   实际 = tAll 的年际差。两者相等 ⟹ 离散收支闭合（没有未记账的热源）。
            //   纯诊断：DeepBudget.ON=false 时一行都不执行。
            if (DeepBudget.ON) {
                int yl = DeepBudget.years() - 1;
                double totVol = 0.0;
                for (int bb = 0; bb < DeepBudget.NB; bb++)
                    for (int kk2 = 0; kk2 < grid.nz; kk2++) totVol += DeepBudget.volume(bb, kk2);
                double predT = totVol > 0.0 ? DeepBudget.residualSum(yl) / totVol : Double.NaN;
                double actT = yl > 0 ? DeepBudget.tAll(yl) - DeepBudget.tAll(yl - 1) : Double.NaN;
                System.out.println("DEEPBUDGET y=" + yearsRun
                        + " predT=" + predT + " actT=" + actT
                        + " tDeep=" + DeepBudget.tDeep(yl) + " tAll=" + DeepBudget.tAll(yl));
            }
            LAST_MAX_DT = maxDT;
            LAST_MAX_DS = maxDS;
            if (yr > 0 && Boolean.getBoolean("eoh.debugresid") && LAST_MAX_DT_Q >= 0) {
                int q = LAST_MAX_DT_Q;
                int kk = q / n2, rr = q % n2, jj = rr / grid.nx, ii = rr % grid.nx;
                int qs = jj * grid.nx + ii;
                System.out.println("RESID_Q y=" + (yr + 1) + " q=" + q
                        + " i=" + ii + " j=" + jj + " k=" + kk
                        + " lat=" + grid.latAt(jj) + " T=" + st.t[q] + " S=" + st.s[q]
                        + " iceA=" + st.iceFraction[qs]
                        + " h=" + st.iceActualThickness(ii, jj)
                        + " snow=" + st.snowThickness[qs]
                        + " Tprev=" + prevT[q] + " Tnow=" + tMean[q]
                        + " Sprev=" + prevS[q] + " Snow=" + sMean[q]
                        + " mld=" + st.mixedLayerDepth[qs]
                        + " wF=" + st.wF[q] + " uF=" + st.uF[q] + " vF=" + st.vF[q]);
            }
            // ★ 2026-11-10（套件并入 DEEPGATE）：12 年 spin-up 中途不打印会被
            //   rerun_acceptance.ps1 的 1800 s 空闲看门狗判 TIMEOUT。逐年一行 ~4 分钟。
            if (SEASONAL_YEARS > 1) {
                // ★ 2026-11-23（C 项，纯诊断）：把四个收敛指标一起打出来 ——
                //   convRel  = |ΔH/H|          （生产判据，全球总量，太松）
                //   relDT    = max|ΔT年均| / 季节振幅   （**物理上最像"稳态"的那个**，已在算但没用于收敛）
                //   upRel / deepRel = 上层(z<2000m) / 深层(z>=2000m) 热含量的年相对变化
                //   用来回答"到底哪一层在漂"。此行**不改变任何计算**。
                double upRel = Double.NaN, deepRel = Double.NaN;
                int nh = YEAR_HEAT_UP.size();
                if (nh >= 2) {
                    double u1 = YEAR_HEAT_UP.get(nh - 1), u0 = YEAR_HEAT_UP.get(nh - 2);
                    upRel = Math.abs(u1 - u0) / Math.max(1.0e-12, Math.abs(u1));
                    double d1 = YEAR_HEAT_DEEP.get(nh - 1), d0 = YEAR_HEAT_DEEP.get(nh - 2);
                    deepRel = Math.abs(d1 - d0) / Math.max(1.0e-12, Math.abs(d1));
                }
                System.out.println("YEAR_PROGRESS y=" + yearsRun
                        + " maxDT=" + maxDT + " maxDS=" + maxDS
                        + " convRel=" + LAST_CONV_REL
                        + " relDT=" + LAST_REL_DT
                        + " rmsT=" + LAST_RMS_T
                        + " upRel=" + upRel
                        + " deepRel=" + deepRel);
            }
            LAST_RESID_T = maxDT;
            LAST_RESID_S = maxDS;
            LAST_YEARS_RUN = yearsRun;
            if (yr > 0) { FP_RESID_T.add(maxDT); FP_RESID_S.add(maxDS); }
            if (FP_SOLVER_ON && yr > 0) {
                // damped fixed-point update on the annual mean
                for (int q = 0; q < n3; q++) {
                    double oldT = st.t[q];
                    st.t[q] = (1.0 - FP_RELAX) * prevT[q] + FP_RELAX * tMean[q];
                    st.s[q] = (1.0 - FP_RELAX) * prevS[q] + FP_RELAX * sMean[q];
                    if (DeepBudget.ON) {
                        int kk = q / (nx * ny);
                        int rr = q % (nx * ny);
                        int jj = rr / nx;
                        DeepBudget.add(DeepBudget.band(grid, jj), DeepBudget.T_FP, kk,
                                (st.t[q] - oldT) * grid.dz[kk]
                                        * Math.max(0.05, grid.cosLat(jj)));
                    }
                }
                System.arraycopy(st.t, 0, tMean, 0, n3);
                System.arraycopy(st.s, 0, sMean, 0, n3);
                // FP: keep h/eta/ice on the same annual-mean clock as T/S.
                // Without this, the next year starts with damped T/S but last-season h/ice.
                for (int j = 0; j < ny; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!grid.isWet(i, j)) continue;
                        int js = j * nx + i;
                        st.mixedLayerDepth[js] = hMean[js];
                        st.eta[js] = etaMean[js];
                        st.iceThickness[js] = iceMean[js];
                        if (ROUTE_B_AREA_ON) {
                            st.iceFraction[js] = Math.min(1.0, Math.max(0.0, iceAreaMean[js]));
                        } else if (ICE_FRACTION_FROM_MEAN) {
                            st.iceFraction[js] = Math.min(1.0, Math.max(0.0, iceMean[js])
                                    / com.EyeOfHarmonyBuffer.sim.atmos.Radiation.H_ICE_REF);
                        }
                    }
                // Annual-mean T/S/h must be re-diagnosed before the next year.
                AdvectionDiffusionSolver.convectiveAdjust(grid, st);
                // ★ 2026-11-23（CG 报告）：**实测否证了"阈值诊断是跳变源"的假设** ——
                //   把这两处 diagnoseMixedLayer 注掉后，第 31/32 年那组 130/210 m 跳变**逐位不变**。
                //   跳变来自**预报式 KT 混合层（hNew）本身**，不是诊断。此处恢复原调用。
                AdvectionDiffusionSolver.diagnoseMixedLayer(grid, st);
            }
            System.arraycopy(tMean, 0, prevT, 0, n3);
            System.arraycopy(sMean, 0, prevS, 0, n3);
            // ★★★★★ 2026-11-23（平衡解改造，A 路线）：**判据换成面积加权 RMS_T**。
            //
            //   为什么不用旧的两种判据：
            //   · 逐点 maxDT：被少数年际振荡格点支配（实测长期停在 1.8 K），永远不收敛；
            //   · 全球热含量 |dH/H|：被深层拖着，平台在 1.4e-4（要 ~50 年），且对局部不敏感。
            //   · 面积加权 RMS_T = sqrt( Σ w·(ΔT)² / Σ w )：与"整体是否稳态"一致，
            //     实测从 0.375 K 稳定降到 0.111 K（14 年），是本世界真正会收敛的量。
            double rms = 0.0, sw = 0.0;
            for (int j = 1; j < ny - 1; j++) {
                double w = Math.max(0.05, grid.cosLat(j));
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double d = st.t[q] - yearT0[q];
                        rms += w * d * d;
                        sw += w;
                    }
                }
            }
            rms = sw > 0.0 ? Math.sqrt(rms / sw) : 0.0;
            LAST_RMS_T = rms;
            System.arraycopy(st.t, 0, yearT0, 0, n3);   // 下一年的参照 = 今年年末状态
            if (yr >= 1 && rms < SEASONAL_RMS_TOL) {
                LAST_YEARS_RUN = yearsRun;
                break;
            }
        }

        // 最后一年平均写入状态
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                int js = j * nx + i;
                st.mixedLayerDepth[js] = hMean[js];
                st.eta[js] = etaMean[js];
                // ★ 2026-11-14（C1b 回滚）：状态 = 年平均（与 T/S 同口径），否则 iceFraction 与冰厚不自洽（P484 实测 FAIL）。
                //   年均不是「丢冰」：它是把耦合轨迹做年平均；真正的缺口是【融冰没记淡水 + 表面融冰没触发】（C1a/C0）。
                st.iceThickness[js] = iceMean[js];
                if (ROUTE_B_AREA_ON) {
                    st.iceFraction[js] = Math.min(1.0, Math.max(0.0, iceAreaMean[js]));
                } else if (ICE_FRACTION_FROM_MEAN) {
                    st.iceFraction[js] = Math.min(1.0, Math.max(0.0, iceMean[js])
                            / com.EyeOfHarmonyBuffer.sim.atmos.Radiation.H_ICE_REF);
                }
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    st.t[q] = tMean[q];
                    st.s[q] = sMean[q];
                }
            }
        // ★ 2026-11-02：h 用最终年平均 T/S 重诊（逐季平均的 h 与 T/S 不自洽）。
        // ★ 2026-11-11：收尾先做一次对流调整，再重诊 h（保证报出的状态静力稳定）。
        AdvectionDiffusionSolver.convectiveAdjust(grid, st);
        AdvectionDiffusionSolver.diagnoseMixedLayer(grid, st);
        // 用年平均 T/S 重算流场：先清零斜压场，再把 u/v 重置为正压基态
        java.util.Arrays.fill(st.vBaroclinic, 0.0);
        java.util.Arrays.fill(st.w, 0.0);
        baro = BarotropicSolver.update(grid, st, periodic, (nseas == 1) ? theta : 0.0, parent);
        if (AdvectionDiffusionSolver.FREEZE_SURFACE_ON) {
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0);
                    double tf = FreezingPoint.kelvin(st.s[q0]);
                    // ★ 2026-11-18（A3）：年末这次"加热到冰点"同样没记账，一并补上（只动账本）。
                    double tOld = st.t[q0];
                    if (tOld < tf) {
                        st.t[q0] = tf;
                        if (DeepBudget.ON) DeepBudget.add(DeepBudget.band(grid, j), DeepBudget.T_ML, 0,
                                (tf - tOld) * grid.dz[0] * Math.max(0.05, grid.cosLat(j)));
                    }
                }
        }
        applyMomentum(grid, st);
        if (AdvectionDiffusionSolver.EKMAN_UPWELL_ON) {
            AdvectionDiffusionSolver.applyEkmanFlow(grid, st, periodic, 0.0);
        }
        if (parent != null) OceanSponge.apply(grid, st, parent, spongeCells, !OceanSponge.OPEN_X);
        st.fillDryGhosts();

        // ★ 2026-11-04（A6）：内部冰点投影进生产路径。
        //   球面+地形生产配置实测有 19 个格点在年平均后仍低于 Tf（GATE_NOHT_T_GE_TF=FAIL）——
        //   与 A3 在稳态路径收掉的 4 格同类：PrognosticT 子步内钳制，但年平均/后续 S 更新会留下残差。
        //   投影只钉温度并记账（INTERIOR_FREEZE_ICE_ON 默认关：把亏损记成表面冰会失控，A3 已实测否决）。
        if (AdvectionDiffusionSolver.INTERIOR_FREEZE_ON) {
            AdvectionDiffusionSolver.applyInteriorFreezing(grid, st);
            st.fillDryGhosts();   // 投影改了海底格 ⟹ 干格拷贝同步，避免读到投影前的旧值
        }

        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                if (grid.isWet(i, j)) surfaceT[j * nx + i] = st.t[st.idx(i, j, 0)];
        // 2026-11-20（D1b/B1）：解完立刻撤掉沃克入口，避免影响后续世界侧调用。
        Atmosphere.WALKER_PROVIDER = null;
        // ★★★ 2026-11-20（审计修复，最后一道）：**返回前再闭合一次**。
        //   季末那次会被之后某一步重写（待定位），这里保证**返回的状态**净经向输运为 0。
        if (com.EyeOfHarmonyBuffer.sim.ocean.staticcore.VelocityProjection.CLOSE_MERIDIONAL) {
            com.EyeOfHarmonyBuffer.sim.ocean.staticcore.VelocityProjection.closeMeridional(grid, st);
        }

        // ★ 2026-11-04（A6）：键带网格身份（sphere/维度/bathyMask）——换网格不再命中旧解。
        OceanCacheKey key = OceanCacheKey.of(grid, worldSeed, landVersion, SOLVER_VERSION, season);
        double psiMin = 0.0, psiMax = 0.0;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                double p = st.psi[grid.idx(i, j)];
                if (p < psiMin) psiMin = p;
                if (p > psiMax) psiMax = p;
            }
        }
        // ★ 2026-11-03：口径统一——MOC = max|ψ(y,z)| × 湿段宽度（BaroclinicFlow.LAST_MOC）。
        // 旧式用【正压】2D ψ 的 (max−min)/1e6，漏了湿宽且混用了正压/斜压两套 ψ ⟹ 报告 0.1 Sv（实际 141）。
        // ★ 2026-11-04（A2）：宽度改 cos 加权 + 按【最宽盆地】取（BaroclinicFlow 里有完整说明）。
        // 同相上界见 LAST_MOC_ZONAL；盆地无关的 ψ 峰值见 LAST_PSI_MAX。
        double mocSv = R3Momentum.ON ? R3Momentum.LAST_MOC_SV : BaroclinicFlow.LAST_MOC;
        LAST_STATE = st;
        return new OceanSolution(key, st, surfaceT,
            baro.iterations, adv == null ? 0 : adv.iterations,
            baro.residual, adv == null ? 0.0 : adv.residual,
            psiMin, psiMax, mocSv,
            sBudget, sBudget, seasSurf);
    }


    /** Copy a full OceanState into another state on the same grid (diagnostic annual map). */
    public static void copyState(OceanState src, OceanState dst) {
        if (src.grid.nx != dst.grid.nx || src.grid.ny != dst.grid.ny || src.grid.nz != dst.grid.nz) {
            throw new IllegalArgumentException("state grid mismatch");
        }
        System.arraycopy(src.t, 0, dst.t, 0, src.t.length);
        System.arraycopy(src.s, 0, dst.s, 0, src.s.length);
        System.arraycopy(src.u, 0, dst.u, 0, src.u.length);
        System.arraycopy(src.v, 0, dst.v, 0, src.v.length);
        System.arraycopy(src.w, 0, dst.w, 0, src.w.length);
        System.arraycopy(src.vBaroclinic, 0, dst.vBaroclinic, 0, src.vBaroclinic.length);
        System.arraycopy(src.uBaroclinic, 0, dst.uBaroclinic, 0, src.uBaroclinic.length);
        System.arraycopy(src.eta, 0, dst.eta, 0, src.eta.length);
        System.arraycopy(src.surfaceLayer, 0, dst.surfaceLayer, 0, src.surfaceLayer.length);
        System.arraycopy(src.mixedLayerDepth, 0, dst.mixedLayerDepth, 0, src.mixedLayerDepth.length);
        System.arraycopy(src.hTOld, 0, dst.hTOld, 0, src.hTOld.length);
        System.arraycopy(src.hTNew, 0, dst.hTNew, 0, src.hTNew.length);
        System.arraycopy(src.iceFraction, 0, dst.iceFraction, 0, src.iceFraction.length);
        System.arraycopy(src.iceThickness, 0, dst.iceThickness, 0, src.iceThickness.length);
        System.arraycopy(src.snowThickness, 0, dst.snowThickness, 0, src.snowThickness.length);
        System.arraycopy(src.iceU, 0, dst.iceU, 0, src.iceU.length);
        System.arraycopy(src.iceV, 0, dst.iceV, 0, src.iceV.length);
        System.arraycopy(src.sig1, 0, dst.sig1, 0, src.sig1.length);
        System.arraycopy(src.sig2, 0, dst.sig2, 0, src.sig2.length);
        System.arraycopy(src.sig12, 0, dst.sig12, 0, src.sig12.length);
        System.arraycopy(src.iceSaltFlux, 0, dst.iceSaltFlux, 0, src.iceSaltFlux.length);
        System.arraycopy(src.snowMeltFlux, 0, dst.snowMeltFlux, 0, src.snowMeltFlux.length);
        System.arraycopy(src.psi, 0, dst.psi, 0, src.psi.length);
        System.arraycopy(src.uF, 0, dst.uF, 0, src.uF.length);
        System.arraycopy(src.vF, 0, dst.vF, 0, src.vF.length);
        System.arraycopy(src.wF, 0, dst.wF, 0, src.wF.length);
        for (int c = 0; c < IceThicknessDistribution.NCAT; c++) {
            System.arraycopy(src.itd.area[c], 0, dst.itd.area[c], 0, src.itd.area[c].length);
            System.arraycopy(src.itd.volume[c], 0, dst.itd.volume[c], 0, src.itd.volume[c].length);
            System.arraycopy(src.itd.snow[c], 0, dst.itd.snow[c], 0, src.itd.snow[c].length);
        }
        dst.itd.initialized = src.itd.initialized;
    }

    /** 定位第一个非有限格（DEBUG_FINITE 用；默认路径零开销）。 */
    // ==================================================================================
    // B1：深层稳态（逐列、定常、平流-扩散；见 DEEP_STEADY_ON 的 javadoc）
    // ==================================================================================
    /** 公开给探针（与 {@code zonalGrad} 同理：让探针测的就是生产用的那份实现）。 */
    public static void applyDeepSteady(OceanGrid g, OceanState st) {
        LAST_DEEP_COLS = 0;
        LAST_DEEP_SKIP = 0;
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] A = new double[nz * nz];
        double[] b = new double[nz];
        double[] x = new double[nz];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                int k0 = -1;
                for (int k = 0; k < wl; k++) if (g.zCenter[k] >= DEEP_STEADY_Z0) { k0 = k; break; }
                if (k0 <= 0 || wl - k0 < 2) continue;
                int m = wl - k0;
                java.util.Arrays.fill(A, 0.0);
                java.util.Arrays.fill(b, 0.0);
                for (int a = 0; a < m; a++) {
                    int k = k0 + a;
                    addFace(A, b, m, a, +1.0, g, st, i, j, k - 1, k, k0);              // F_top
                    addFace(A, b, m, a, -1.0, g, st, i, j, k, (k + 1 < wl) ? k + 1 : -1, k0); // -F_bot
                }
                if (!solveDense(A, b, x, m)) { LAST_DEEP_SKIP++; continue; }
                boolean ok = true;
                for (int a = 0; a < m; a++) if (!Double.isFinite(x[a]) || x[a] < 200.0 || x[a] > 400.0) ok = false;
                if (!ok) { LAST_DEEP_SKIP++; continue; }      // 病态列：保持原值，不污染状态
                for (int a = 0; a < m; a++) {
                    int k = k0 + a;
                    double tf = com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint.kelvin(st.s[st.idx(i, j, k)]);
                    st.t[st.idx(i, j, k)] = Math.max(tf, x[a]);
                }
                LAST_DEEP_COLS++;
            }
    }

    /**
     * 把一个面（cell lo 与 hi 之间）的【向上】通量加入平衡式：F = w·T_up − K·(T_lo−T_hi)/dzd。
     * hi &lt; 0 表示海底面（绝热 ⟹ 通量为 0）。lo &lt; k0 时 T_lo 是 Dirichlet，移到右端项。
     */
    private static void addFace(double[] A, double[] b, int m, int row, double sign,
                                OceanGrid g, OceanState st, int i, int j, int lo, int hi, int k0) {
        if (hi < 0) return;
        if (lo < 0) return;
        double wlo = st.w[st.idx(i, j, lo)], whi = st.w[st.idx(i, j, hi)];
        double w = 0.5 * (wlo + whi);
        double dzd = 0.5 * (g.dz[lo] + g.dz[hi]);
        double kk = 0.5 * (AdvectionDiffusionSolver.kvAt(g, st, i, j, lo)
                         + AdvectionDiffusionSolver.kvAt(g, st, i, j, hi));
        double cLo, cHi;
        if (w > 0.0) { cLo = -kk / dzd; cHi = w + kk / dzd; }
        else         { cLo = w - kk / dzd; cHi = kk / dzd; }
        if (lo >= k0) A[row * m + (lo - k0)] += sign * cLo;
        else b[row] -= sign * cLo * st.t[st.idx(i, j, lo)];
        A[row * m + (hi - k0)] += sign * cHi;
    }

    /** 小规模稠密解（带部分主元）；奇异时返回 false，调用方保持原值。 */
    private static boolean solveDense(double[] A, double[] b, double[] x, int m) {
        for (int c = 0; c < m; c++) {
            int piv = c;
            double best = Math.abs(A[c * m + c]);
            for (int r = c + 1; r < m; r++) {
                double v = Math.abs(A[r * m + c]);
                if (v > best) { best = v; piv = r; }
            }
            if (!(best > 1.0e-30)) return false;
            if (piv != c) {
                for (int k2 = 0; k2 < m; k2++) {
                    double tmp = A[c * m + k2]; A[c * m + k2] = A[piv * m + k2]; A[piv * m + k2] = tmp;
                }
                double tb = b[c]; b[c] = b[piv]; b[piv] = tb;
            }
            for (int r = c + 1; r < m; r++) {
                double f = A[r * m + c] / A[c * m + c];
                if (f == 0.0) continue;
                for (int k2 = c; k2 < m; k2++) A[r * m + k2] -= f * A[c * m + k2];
                b[r] -= f * b[c];
            }
        }
        for (int r = m - 1; r >= 0; r--) {
            double s = b[r];
            for (int c = r + 1; c < m; c++) s -= A[r * m + c] * x[c];
            x[r] = s / A[r * m + r];
        }
        return true;
    }

    private static String firstNonFinite(OceanGrid g, OceanState st) {
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    if (!Double.isFinite(st.t[q]) || !Double.isFinite(st.s[q])
                            || !Double.isFinite(st.u[q]) || !Double.isFinite(st.v[q])
                            || !Double.isFinite(st.w[q]))
                        return "i=" + i + " j=" + j + " k=" + k + " wl=" + wl
                                + " t=" + st.t[q] + " s=" + st.s[q]
                                + " u=" + st.u[q] + " v=" + st.v[q] + " w=" + st.w[q]
                                + " h=" + st.mixedLayerDepth[j * g.nx + i];
                }
                int js = j * g.nx + i;
                if (!Double.isFinite(st.mixedLayerDepth[js]) || !Double.isFinite(st.iceThickness[js])
                        || !Double.isFinite(st.eta[js]))
                    return "i=" + i + " j=" + j + " surface h=" + st.mixedLayerDepth[js]
                            + " ice=" + st.iceThickness[js] + " eta=" + st.eta[js];
            }
        return null;
    }
}
