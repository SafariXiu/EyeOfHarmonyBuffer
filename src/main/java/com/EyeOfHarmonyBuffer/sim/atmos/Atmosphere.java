package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * M3 大气层 —— **第一片纵向切片：温度场 + 海陆热力异常 + 地面气压异常**。
 *
 * <p>设计约束（M3 调研报告的结论）：**零预报量**。所有量都是
 * <code>(seed, x, z, Theta)</code> 的纯函数，Theta 是唯一的全局标量（季节相位）。
 * 这样做是因为 X 无限 ⇒ 纬向平均无法定义 ⇒ 纬向平均态必须外生给定。
 *
 * <h3>温度</h3>
 * <pre>
 *   T_zm(phi) = T0 + T2*P2(sin phi) + T4*P4(sin phi)          纬向平均地表气温
 *   A(phi)    = A_PEAK * sin^2(phi)                            季节性振幅
 *   T_sl      = T_zm + A*cos(Theta - psi - [南半球 ? pi : 0])   海陆各自的热惯性
 *   T_sfc     = 陆地 ? T_sl - GAMMA*h : T_zm + A_sea*cos(...)   海拔递减率
 * </pre>
 * 勒让德系数锚定地球观测三点（赤道 299 K / 45 度 284 K / 极 255 K），
 * 三个锚点在本实现下逐位成立（见 P258 的自检）。
 */
public final class Atmosphere {

    private Atmosphere() {}

    // ---- 纬向平均地表气温：2 项勒让德拟合 ----
    public static final double T0 = 288.15;
    public static final double T2 = -26.71;
    public static final double T4 = -6.29;

    /** 标准大气递减率（K/m）。 */
    public static final double GAMMA = 6.5e-3;
    /** 黄赤交角（弧度）。 */
    public static final double OBLIQUITY = Math.toRadians(23.44);

    // 季节振幅 A(phi) 已改成**观测表**：见 ZonalTables.A_LAND_K / A_SEA_K（§80）。
    // 旧的常量 A_LAND_PEAK=26 / A_SEA_PEAK=10 + sin^2(phi) 已被 ERA5 实测否证
    // （20 度偏低 2.2 倍、70 度偏高 3.2 倍），不要再用。
    /** 相位滞后（天）——海洋热惯性大，所以滞后更多。 */
    public static final double PSI_LAND_DAYS = 30.0;
    public static final double PSI_SEA_DAYS = 60.0;

    /**
     * 热力异常换算成地面气压异常（Pa/K）。
     *
     * <p>K_P = gamma_p * rho0 * g * H_eff / T0。**H_eff 是对流层厚度（12,000 m），不是边界层（3,000 m）** ——
     * 季风尺度的加热贯穿整个对流层。§59 实测：这样得到的 30 度海陆气压差 = **6.1 hPa**，
     * 与观测的夏季海陆气压差 10~15 hPa 同量级 ✓（原来的 3,000 m 只有 1.5 hPa，弱 4 倍）。
     */
    public static final double H_EFF = 12_000.0;
    // ⚠ 2026-09-13 修正（审计 D5）：原来这里把四个量写成了**字面量**
    //     K_P = 0.55 * 1.225 * 9.807 * H_EFF / 288.15
    // —— 而 CHI / RHO_AIR / T0 就在同一个类里、G_ACC 在 PrecipField 里，
    // 三处各有一份「密度 / 温度 / 重力」⇒ 改 RHO_AIR 不会改 K_P，静默失配。
    // 现在改成对同名字段的引用：**数值逐位不变**（下面四个字面量与四个字段一一相等），
    // 但以后任何一个字段被改，K_P 会自动跟着走。
    // ⚠ E11（我自己的错，编译期就暴露了）：第一版写成简单名 CHI / RHO_AIR / T0，
    // 但它们**声明在后面** ⇒ JLS §8.3.3「非法前向引用」。**限定名不受该禁令约束**，
    // 且这几个字段都是编译期常量 ⇒ 值仍然是内联的、**数值逐位不变**。
    public static final double K_P =
        Atmosphere.CHI * Atmosphere.RHO_AIR * PrecipField.G_ACC * H_EFF / Atmosphere.T0;   // = 276 Pa/K
    /** 把 T_sfc 折成「对纬向平均的偏离」的归一系数。 */
    public static final double CHI = 0.55;

    /**
     * **高原放大系数（默认关闭 = 1.0）**。
     *
     * <p>⚠ 设计报告说「T'_c 里的 +GAMMA*h 保证高原是暖的」，但把 T_sfc 的 -GAMMA*h 代进去
     * <b>恰好抵消</b> ⇒ 按设计原式，高原的**地面气压异常与同纬低地完全相同**。
     * 真实世界的高原（青藏）之所以强化季风，是因为它把热量加到**中层大气**，
     * 这是三维效应，单层表达不出来，只能显式参数化。
     * 本切片**照设计原式实现**（=1.0），把差距留成可测的开关。
     */
    public static double PLATEAU_AMP = 1.0;
    /** PLATEAU_AMP 的参考高度（m）。 */
    public static final double PLATEAU_H_REF = 3000.0;

    // ================= 风 =================

    public static final double RHO_AIR = 1.225;
    /** 摩擦系数（1/s）。越等压线角 arctan(gamma/f)：海 13.6 度、陆 30.2 度 @45 度。 */
    public static final double GAMMA_OCN = 2.5e-5;
    public static final double GAMMA_LND = 6.0e-5;
    /** 拖曳系数。 */
    public static final double CD_OCN = 1.3e-3;
    public static final double CD_LND = 3.0e-3;
    /** 边界层厚度（m），gamma_turb = C_D*|U|/H_BL 用。 */
    public static final double H_BL = 1000.0;
    /** 地面风速硬上限（m/s）。 */
    public static final double U_MAX = 25.0;
    /**
     * @deprecated 旧的「整体平移 Δφ(θ)」幅度。§83 换成 U_ZM_JAN/U_ZM_JUL 两表 + 按 cos(Theta) 插值。
     *
     * <p>⚠ 2026-09-13（审计 D4）：本注释原来写「**模型已不再使用**」，但那是**错的** ——
     * {@code PrecipField.stormGate} 当时仍在生产路径上用它。现已把那一处改用
     * {@code ZonalTables.uZm(latDeg, theta)}，**本常量现在真的只剩探针在引用了**。
     * 保留它只为 {@code tools/talos-probe} 的旧探针（P282/P283/P284 的 {@code zmShift}）。
     */
    @Deprecated
    public static final double DELTA_PHI0 = Math.toRadians(6.0);

    /**
     * ⚠ **相位约定**：本实现的 Theta = 0 是**北半球夏至**（与设计报告的 Theta 原点=春分不同）。
     * 所以太阳直射点纬度是 <code>delta = eps*cos(Theta)</code>，不是 sin。
     */
    public static double subsolarLat(double theta) { return OBLIQUITY * Math.cos(theta); }


    /**
     * ★★★ **§451：`pzRef` 进 `v` 的替代方案（§422 选项 D）。**
     *
     * <pre>
     * 0 = 现状：v = (f*px - gam*(pz + pzRef))/den          （pzRef 直接驱动 v）
     * 1 = 选项 D：v 的纬向平均部分改由【w_zm 经质量连续性导出】，pzRef 不再进 v
     * </pre>
     *
     * <p><b>为什么必须换</b>（§422 已确证、但修法一直没落地）：`f→0` 时 `den → rho*gam²`，
     * 于是 `v = -pzRef/(rho*gam)` —— `gam≈2.5e-5`、`pzRef≈3.6e-4 Pa/m` ⇒ **v ≈ -12 m/s**，
     * 实测赤道纬向平均 `divU` 因此**大 14 倍**，且 **25~30N 符号翻**。
     * 物理：赤道处经向气压梯度受**质量连续性**约束，不是「纯摩擦平衡」。
     *
     * <p><b>导出的式子</b>（球面纬向平均质量连续性，`V = ∫v dz`）：
     * <pre>
     *   (1/(a cosφ)) ∂(V cosφ)/∂φ = -w_zm(φ)
     *   ⇒ V(φ) = -a * g(φ)/cosφ ,  g(φ) = ∫_0^φ w_zm cosφ' dφ' - wbar_c*sinφ
     *   ⇒ v_zm(φ) = V(φ)/H_EFF
     * </pre>
     * `wbar_c = ∫_0^{π/2} w_zm cosφ dφ / ∫_0^{π/2} cosφ dφ` 是 `w_zm` 的余弦加权均值：
     * **一个流函数要求净质量输送为零**（否则极点处 `V→∞`）⇒ 这一项是**推导出来的，不是拟合的**。
     * 它同时给出 `v_zm(90°)=0`（`g` 与 `cosφ` 同阶趋于 0）与 `div(v_zm) = -(w_zm - wbar_c)/H_EFF`。
     *
     * <p>表源口径：`ZonalTables.wZm`（生产口径，`WZM_FROM_TABLE=false`）。
     * ⚠ 若将来把 `WZM_FROM_TABLE` 定为 true，**这里必须同步换表**（已进 CALIBERS 未对账清单）。
     */
    public static int PZREF_VZ_MODE = 1;   // §487：默认改为 1 —— 模式 0 是【已确证的缺陷】
    //  为什么改默认（用户裁决「按照最物理最正确的方向做」）：模式 0 下 f→0 时 den → rho*gam²，
    //  v = -pzRef/(rho*gam) ≈ -12 m/s ⇒ 赤道纬向平均 divU 大 14 倍、25~30N 符号翻（§422 实测）。
    //  模式 1 用 w_zm 经质量连续性导出的 v_zm（wbar_c 是推导出的流函数闭合项，§451 实测）。
    //  ⚠ 这【会改变生产行为】：divU 量级回到 ~1e-6，验收基线需要重捕。

    /** `v_zm` 查表步长（度）。 */
    private static final double VZ_STEP = 0.5;
    private static volatile double[] VZ_TAB = null;
    private static volatile double VZ_WBAR_C = 0.0;

    /** `w_zm` 的余弦加权均值（m/s）—— 流函数闭合项。首次调用时随表一起算好。 */
    public static double vzWbarC() { vzTable(); return VZ_WBAR_C; }

    private static double[] vzTable() {
        double[] t = VZ_TAB;
        if (t != null) return t;
        synchronized (Atmosphere.class) {
            if (VZ_TAB != null) return VZ_TAB;
            final int n = (int) Math.round(90.0 / VZ_STEP) + 1;
            double[] acc = new double[n];
            double a = 0.0, prevW = ZonalTables.wZm(0.0);
            for (int i = 1; i < n; i++) {
                double d2 = i * VZ_STEP;
                double curW = ZonalTables.wZm(d2) * Math.cos(Math.toRadians(d2));
                a += 0.5 * (prevW + curW) * Math.toRadians(VZ_STEP);
                acc[i] = a;
                prevW = curW;
            }
            double wbar = acc[n - 1];          // / ∫_0^{π/2} cos = 1
            double[] out = new double[n];
            for (int i = 1; i < n - 1; i++) {
                double d2 = i * VZ_STEP;
                double g = acc[i] - wbar * Math.sin(Math.toRadians(d2));
                out[i] = -(WorldContract.R_EFF * g) / (Math.cos(Math.toRadians(d2)) * H_EFF);
            }
            out[0] = 0.0; out[n - 1] = 0.0;
            VZ_WBAR_C = wbar;
            VZ_TAB = out;
        }
        return VZ_TAB;
    }

    /** §451：由 `w_zm` 经连续性导出的纬向平均经向风（m/s）。南半球取反。 */
    public static double vZmAt(double latDeg) {
        double a = Math.abs(latDeg);
        if (a >= 90.0) return 0.0;
        double[] t = vzTable();
        double fi = a / VZ_STEP;
        int i0 = (int) fi;
        if (i0 >= t.length - 1) return t[t.length - 1];
        double tx = fi - i0;
        double v = t[i0] * (1.0 - tx) + t[i0 + 1] * tx;
        return latDeg >= 0.0 ? v : -v;
    }

    /** §451：prescribed `v_zm` 自身的纬向平均散度（s^-1）= `-(w_zm - wbar_c)/H_EFF`。 */
    public static double divVzmAt(double latDeg) {
        double a = Math.min(90.0, Math.abs(latDeg));
        return -(ZonalTables.wZm(a) - vzWbarC()) / H_EFF;
    }

    /**
     * Ekman-Rayleigh 平衡 + 纬向平均风。**赤道与中纬同一个公式，没有任何分支**。
     *
     * <pre>
     *   u = -( gamma*p_x + f*p_z ) / ( rho0*(gamma^2 + f^2) )
     *   v =  ( f*p_x - gamma*p_z ) / ( rho0*(gamma^2 + f^2) )
     *   U += U_zm(phi - Delta_phi)
     * </pre>
     *
     * f -> 0 时退化成纯下坡（向低压）流 ⇒ 夏季吹向陆地、冬季吹向海洋，**季风反转自动发生**。
     * gamma 做 2 次不动点迭代（gamma_turb = C_D*|U|/H_BL）。
     */
    public static double[] wind(double px, double pz, double kappa, double latRad, double theta) {
        double f = WorldContract.coriolis(latRad);
        double g0 = gammaOf(kappa);
        double cd = cdOf(kappa);
        double u = 0.0, v = 0.0;
        double gam = g0;
        // §62.3-2 / §84：p_ref 的**经向**梯度只进 v。u 不进 —— 纬向平均风已由外生 U_zm 给定，
        // 把 p_ref 的梯度也喂进 u 就是重复计数（P260 实测会造出 40 m/s 的虚假纬向风）。
        // d p_ref/dz = pRefSlopePerRad(latDeg)/R_EFF（sign 已含在方法里 ⇒ 赤道两侧都指向赤道）。
        // §451：PZREF_VZ_MODE >= 1 时 pzRef 不再进 v（改由 w_zm 经连续性导出，见 vZmAt）。
        double pzRef = (PZREF_IN_V && PZREF_VZ_MODE == 0)
                     ? ZonalTables.pRefSlopePerRad(Math.toDegrees(latRad)) / WorldContract.R_EFF : 0.0;
        for (int it = 0; it < 3; it++) {
            double den = RHO_AIR * (gam * gam + f * f);
            u = -(gam * px + f * pz) / den;
            v = (f * px - gam * (pz + pzRef)) / den;
            double gt = cd * Math.hypot(u, v) / H_BL;
            gam = Math.sqrt(g0 * g0 + gt * gt);
        }
        // §83：不再用「整体平移 6 度」，改成观测的 1 月/7 月两表 + 按 cos(Theta) 季节插值。
        // 「洋盆尺度纬向风骨架」：SEA_ONLY_UZM=false 时与原行逐位不变；打开时按 κ 混入洋面口径表。
        u += ZonalTables.uZmBlend(Math.toDegrees(latRad), theta, kappa);
        // §451：纬向平均【经向】风的等价项 —— 与 u_zm 平行地加进来（而不是让 pzRef 通过 Ekman 分母放大 14 倍）。
        if (PZREF_VZ_MODE >= 1) v += vZmAt(Math.toDegrees(latRad));
        double sp = Math.hypot(u, v);
        if (sp > U_MAX) { u *= U_MAX / sp; v *= U_MAX / sp; }
        return new double[]{u, v};
    }

    /**
     * 世界坐标上的风（m/s）。gradStep = 差分步长（m）。
     *
     * <p>⚠ <b>关键修正（P260 实测）</b>：这里的梯度取的是**热力距平 p'** 的梯度，
     * <b>不是</b> p_sl 的全梯度。
     *
     * <p>原因：p_ref(phi) 的经向梯度是「纬向平均环流」的另一种写法，
     * 而纬向平均风已经由外生表 U_zm 给了 ⇒ 再用它驱动一次就是**重复计数**。
     * 实测代价：p_ref 从赤道 1008 到 30 度 1018 hPa，在本世界 30 度纬度只有 **1667 km**
     * ⇒ |grad p| = 6e-3 Pa/m ⇒ 虚假纬向风 **40 m/s**，把 U_zm 完全淹没，
     * 地面风速饱和到 25 m/s 上限、风应力饱和到 1 Pa（真实 0.05~0.15 Pa）。
     *
     * <p>所以：**局地风 = f(热力距平梯度) + U_zm**。p_ref 只负责「气压带在哪里」。
     */
    public static double[] windAt(int x, int z, long seed, int cell, double theta, int gradStep) {
        double px = (pressureAnomaly(x + gradStep, z, seed, cell, theta)
                   - pressureAnomaly(x - gradStep, z, seed, cell, theta)) / (2.0 * gradStep);
        double pz = (pressureAnomaly(x, z + gradStep, seed, cell, theta)
                   - pressureAnomaly(x, z - gradStep, seed, cell, theta)) / (2.0 * gradStep);
        double[] out = wind(px, pz, kappaMemo(x, z, seed, cell), WorldContract.latOf(z), theta);
        if (COAST_WIND_ON) out[1] += coastalAlongshoreV(x, z, seed, cell);
        return out;
    }

    /** 风应力 tau = rho_a * C_D * |U| * U（Pa）。 */
    public static double[] windStress(int x, int z, long seed, int cell, double theta, int gradStep) {
        double[] uv = windAt(x, z, seed, cell, theta, gradStep);
        double cd = cdOf(kappaMemo(x, z, seed, cell));
        double sp = Math.hypot(uv[0], uv[1]);
        double k = RHO_AIR * cd * sp;
        return new double[]{k * uv[0], k * uv[1]};
    }

    public static double p2(double s) { return 0.5 * (3.0 * s * s - 1.0); }

    public static double p4(double s) {
        double x2 = s * s;
        return (35.0 * x2 * x2 - 30.0 * x2 + 3.0) / 8.0;
    }

    /** 季节相位：day = 0 是**北半球夏至**。 */
    public static double theta(double day) {
        return 2.0 * Math.PI * day / WorldContract.DAYS_PER_YEAR;
    }

    /**
     * **纬向平均地表气温（K）** —— 观测表（§216.7 / D74）。
     *
     * <p>⚠ <b>2026-09-16 换表</b>：原来是三点勒让德拟合 {@code T0 + T2*p2(s) + T4*p4(s)}
     * （锚 赤道 299.15 / 45 度 284.03 / 极 255.15 —— 三个锚点只是那个拟合自己的取值，
     * 不是独立观测）。P491 复算：拟合对 ERA5 真实纬向年均剖面的 rms 残差 <b>3.3 K</b>、
     * 极区最大 <b>9.8 K</b>，而且**抓不到极区向极陡降的形状**。
     * 现在直接读 {@link ZonalTables#tZmSym}（5 度观测表，南北对称化），
     * 残差降到 rms 0.4 K 量级。{@link #T0}/{@link #T2}/{@link #T4} 保留**只为
     * K_P 的定义与老探针的对照**，不再是生产温度场的来源。
     *
     * <p>⚠ 它**只**是「那个纬度上地球全表面（陆+海+冰）的年均气温」这个观测事实。
     * <b>不要</b>把它当成模型的年均温度场 —— 模型的场是
     * {@link #annualSeaLevelTemp}（= 洋面基线 + κ 加权的海陆对比）。两者相差
     * {@code (κ - ZF_earth)*Δ + κ*Γ*z_bar}，在高纬可以差 8 K 以上。
     */
    public static double tZonalMean(double latRad) { return ZonalTables.tZmSym(latRad); }

    public static double clamp01(double t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    /** 由**大陆度** kappa 插值的摩擦系数与拖曳系数（连续，不再二选一）。 */
    public static double gammaOf(double kappa) { return GAMMA_OCN + (GAMMA_LND - GAMMA_OCN) * clamp01(kappa); }
    public static double cdOf(double kappa) { return CD_OCN + (CD_LND - CD_OCN) * clamp01(kappa); }

    /** 大陆度 kappa = (landScore+1)/2，0 = 纯海洋，1 = 纯内陆。**连续**。 */
    public static double kappaAt(int x, int z, long seed, int cell) {
        return 0.5 * (PlateField.landScoreWithCell(x, z, seed, cell) + 1.0);
    }

    // ==================== kappa/elev 的**行解记忆化**（§162.16 的代价修复） ====================

    /**
     * 为什么要它：P469 实测 {@code kappaAt} = <b>56.6 us</b>，占 {@code pressureAnomaly}（57.8 us）的
     * <b>97.9%</b>；而 {@link #pressureAnomaly} 与 {@link #kappaAt} 都**与季节相位 theta 无关**，
     * 却在 {@code OceanField.curlAtmos} 的 4 个相位里被**一模一样地重算了 4 遍**。
     * 一次世界预热因此要 20~25 分钟（P470 实测 1489.8 s）。
     *
     * <p><b>为什么这是「物理零改变」而不是「近似」</b>：
     * {@code kappaAt} 与 {@code PlateField.elevationWithCell} 都是文档里写明的**纯函数**
     * （全部入参传入、无静态可变状态），所以「同一组入参算两次」== 「算一次再查表」，
     * <b>逐位相同</b>。P471 会用 {@code Double.doubleToLongBits} 逐点断言这件事，不靠推理。
     *
     * <p><b>为什么用 thread-local</b>：记忆化只在「解一条海洋纬度行」这段临界区里开启。
     * 用全局开关的话，其它线程（区块生成）在这几秒里也会走记忆化路径并竞争同一个 HashMap。
     * thread-local 让记忆化**只对正在解行的那个线程**生效，对其它线程零影响。
     *
     * <p><b>键是精确的</b>：{@code ((long)x << 32) | (z & 0xFFFFFFFFL)} —— 两个 int 拼进一个 long，
     * <b>不存在哈希碰撞</b>（这一点很重要：物理正确性不能建立在「64 位哈希大概不会撞」上）。
     */
    private static final ThreadLocal<java.util.HashMap<Long, double[]>> KE_MEMO = new ThreadLocal<>();
    /** 记忆条目上限（防止极端行把内存吃满；超过就停止记忆化，只影响速度不影响结果）。 */
    public static int MEMO_MAX = 4_000_000;
    /** 诊断计数（P471 用）。 */
    public static long memoHits = 0, memoMisses = 0;

    /**
     * 开启**本线程**的 kappa/elev 记忆化。
     *
     * <p>⚠ <b>可嵌套</b>（E30 的教训：布尔式的「开/关」在内层提前 restore 时会把外层的状态吃掉）：
     * 已经开着时**不重建**、返回 false；调用方必须把它原样传给 {@link #endMemo(boolean)}。
     *
     * <pre>
     *   boolean mc = Atmosphere.beginMemo();
     *   try { ... } finally { Atmosphere.endMemo(mc); }
     * </pre>
     *
     * @return true = 这次调用**创建**了记忆表（因此有责任销毁它）
     */
    public static boolean beginMemo() {
        if (KE_MEMO.get() != null) return false;
        KE_MEMO.set(new java.util.HashMap<Long, double[]>());
        return true;
    }
    /** 关闭本线程的记忆化；只有创建者（{@code created == true}）才真正销毁。 */
    public static void endMemo(boolean created) { if (created) KE_MEMO.remove(); }
    /** 本线程是否正在记忆化。 */
    public static boolean memoOn() { return KE_MEMO.get() != null; }

    /**
     * {@code {kappa, elev}} —— 与「分别调 {@link #kappaAt} 与
     * {@code PlateField.elevationWithCell}」**逐位相同**，只是同一组入参不重算。
     *
     * @return 长度 2 的数组（**共享引用，调用方不得修改**）
     */
    private static double[] kappaElev(int x, int z, long seed, int cell) {
        java.util.HashMap<Long, double[]> m = KE_MEMO.get();
        if (m == null) {
            return new double[]{kappaAt(x, z, seed, cell), PlateField.elevationWithCell(x, z, seed, cell)};
        }
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        double[] v = m.get(key);
        if (v != null) { memoHits++; return v; }
        memoMisses++;
        v = new double[]{kappaAt(x, z, seed, cell), PlateField.elevationWithCell(x, z, seed, cell)};
        if (m.size() < MEMO_MAX) m.put(key, v);
        return v;
    }

    /** 记忆化版的 kappa（与 {@link #kappaAt} 逐位相同）。 */
    public static double kappaMemo(int x, int z, long seed, int cell) { return kappaElev(x, z, seed, cell)[0]; }

    // ================= 海陆**年均**对比（§216.7：D74 / ④(b) / D8-b 的同一条物理补全） =================

    /**
     * **洋面支**：观测的洋面纬向年均气温（K）。**直接的观测量**，不是任何东西的推论。
     *
     * <p>为什么不能只用一个 {@code T_zm}：{@code T_zm = ZF*t2m_land + (1-ZF)*t2m_sea} 逐行精确
     * （P492 在 1 度数据上实测残差 0.0000 K），但那一行里的 {@code ZF} 是**地球的陆地占比**。
     * 本世界在同一个纬度上的陆地占比是 {@code kappa}，与地球无关 ⇒ 只有一个 T_zm 就
     * **回答不了「换一个陆地分布会怎样」**。把它拆成 {@code T_OCEAN} 与 {@code T_LAND} 两个
     * 独立事实之后，用本世界自己的 {@code kappa} 重新混合 —— 这才是能搬运的东西。
     */
    public static double oceanBaseK(double latRad) {
        // ★ §7344/§7345：climlab EBM 稳态解（内生）。默认关 ⇒ 逐位不变。
        //   为什么要有它：本方法的 javadoc 自己指出「只有一个外生 T_zm 就回答不了『换一个陆地分布会怎样』」。
        //   内生化之后纬向温度不再依赖地球的观测表；且 §7345 实测该解与 ERA5 观测支逐点差 ~2 K（最大 5.66 K）。
        return ClimlabEBM.ENABLED ? ClimlabEBM.tOceanK(latRad) : ZonalTables.tOceanK(latRad);
    }

    /**
     * **陆地相对海洋的海平面年均温差（K）**：{@code T_LAND_SL − T_OCEAN}。
     *
     * <p>为什么要把陆地那一支折到海平面：观测的 {@code T_LAND} **已经含了地球自己的海拔**
     * （陆面平均约 840 m）。而本模型另外还要减一次 {@code Γ*h}（每列自己的精确高程）
     * ⇒ 不剥出来就是**同一份海拔减两次**（在 75 度能差 10 K 以上）。
     * 剥出来之后，「海平面上的陆地」比海洋暖/冷这么多，模型的 {@code -Γ*h*κ} 再把它降到位。
     *
     * <p>★★★★★ <b>§7350 口径裁决（<u>推翻 §7347 的相反裁决</u>）：这里的 {@code tOceanK} <u>必须</u>与
     * {@link #oceanBaseK} 走【同一个】开关。</b>
     *
     * <p><b>为什么 §7347 的裁决是错的</b>：当时论证「{@code Δ} 是观测事实，所以保持观测的陆海温差」——
     * 但真正发生的代数不是那样。{@link #annualSeaLevelTemp} 是
     * <pre>  T = oceanBaseK + kappa * Delta + (1-kappa) * sstAnom  </pre>
     * 若 {@code oceanBaseK} 内生而 {@code Delta} 用观测的 {@code tOceanK}，则陆地上（{@code kappa = 1}）
     * <pre>  T_land = oceanBaseK_endo + (tLandK + GAMMA*zbar - tOceanK_obs)  </pre>
     * ⟹ **陆地把「内生海洋相对观测海洋的偏差」整个继承了下来**，而**观测的陆地温度里并没有那个偏差**
     * ⟹ **这才是混口径**（一个分量含内生偏差、另一个不含，却把它们相加）。
     *
     * <p><b>P1086 实测（该偏差逐位精确地传给了陆地）</b>：
     * <pre>
     *   lat   oceanBaseK_endo  tOceanK_obs   偏差      陆地年温 false -> true
     *    15      297.87         298.84      -0.97      302.74 -> 301.78  (-0.97)
     *    25      293.73         295.85      -2.12      300.06 -> 297.94  (-2.12)
     *    30      291.02         293.57      -2.55      296.97 -> 294.42  (-2.55)
     *    90      263.61         260.23      +3.38      237.42 -> 240.81  (+3.38)
     * </pre>
     * 15~30N 平均 −1.775 K ⟹ {@code qSat} 比 0.8518（水汽 −14.8%）
     * ⟹ **P499 的 {@code GATE_MONSOON_IDX} 从 +0.205 掉到 +0.155（−24.4%）** —— 这就是那 −24.4% 的机理。
     *
     * <p><b>正确形式</b>：{@code Delta} 的两个分量必须【同源】。{@code tLandK} 与 {@code tOceanK} 都来自
     * 同一份 ERA5 观测 ⟹ 当海洋支被换成内生解时，{@code Delta} 也必须用同一个内生海洋，
     * 这样陆地上 {@code T_land = tLandK + GAMMA*zbar} —— **陆地保持观测的绝对水平、海洋用内生**，
     * 两个分量各自与自己的来源自洽。
     *
     * <p>★ 附带正确性：{@code Delta = T_LAND_SL − T_OCEAN} 因此变大（极地尤其），
     * 而 §7349 实测「陆面能量平衡只给出观测陆海温差的 ~60%」⟹ 这个方向是对的。
     */
    public static double landMinusOceanSLK(double latRad) {
        // ★ §7350：与 oceanBaseK 走【同一个】开关（两个分量必须同源）—— 见上方口径裁决。
        double tOcean = ClimlabEBM.ENABLED ? ClimlabEBM.tOceanK(latRad) : ZonalTables.tOceanK(latRad);
        return ZonalTables.tLandK(latRad) + GAMMA * ZonalTables.landMeanElev(latRad) - tOcean;
    }

    /**
     * **海陆年均温度对比项（K）**，按**连续**大陆度 κ 混合：
     * κ=0（纯海洋）给 0，κ=1（纯内陆）给 {@link #landMinusOceanSLK}。
     *
     * <p><b>这一项是 ④(b) / D74 / D8-b 三条缺陷的共同解</b>：
     * <ol>
     *   <li><b>④(b) 极区不对称</b>：这一项让「陆地」与「海洋」在同纬度上年均温度不同 ⇒
     *       本世界北极是陆地就自动变冷、南极是海洋就自动变暖，<b>不对称来自本世界自己的 κ 场</b>，
     *       一行也没有从地球搬（P491：本世界的纬向平均海陆南北对称 ⇒ 结果自然对称）；</li>
     *   <li><b>D74</b>：模型原来没有「海温 vs 气温」之分 —— 现在海洋有独立的 {@link #oceanBaseK}；</li>
     *   <li><b>D8-b</b>：{@code airT = (tSea - tzm)/AIRT_SCALE} 原来恒为浮点噪声
     *       （{@code tSea == T_zm}）；现在 {@code tSea - tzm} 有了真实的海陆热力内容。</li>
     * </ol>
     *
     * <p>⚠ 它**不进 p'**（见 {@link #pressureAnomaly}）：海陆年均气压差已由
     * {@link #cellPressure} 独立锚定在**观测的 7 hPa @30 度**上，两项都进就是重复计数。
     */
    public static double landSeaAnnualAnomaly(double latRad, double kappa) {
        return clamp01(kappa) * landMinusOceanSLK(latRad);
    }

    /**
     * **海平面等效的年均地表温度（K）** —— 全模型唯一的年均温度入口。
     *
     * <pre>
     *   T_ann = T_ocean(|phi|) + kappa * (DELTA(|phi|) + GAMMA*z_bar(|phi|)) + (1-kappa) * SST'
     * </pre>
     *
     * <p>它是恒等式的逐点版本：带内平均（陆地占比 ZF_our）= {@code T_ocean + ZF_our*DELTA_SL}，
     * 而地球在同纬度是 {@code T_ocean + ZF_earth*DELTA} —— 两者的差
     * {@code (ZF_our - ZF_earth)*Δ} 正是「这个纬度上有多少陆地」造成的差异。
     * <b>不需要任何沿 x 的窗口平均</b>（X 无限 ⇒ 那种平均本来也没有定义）。
     */
    public static double annualSeaLevelTemp(double latRad, double kappa, double sstAnom) {
        double k = clamp01(kappa);
        return oceanBaseK(latRad) + k * landMinusOceanSLK(latRad) + (1.0 - k) * sstAnom;
    }

    /**
     * **本世界的纬向平均海平面气温（K）** —— 锚在观测的海平面年表上，只加「本世界陆地占比」那一项差。
     *
     * <pre>
     *   T_ann(φ, κ) = T_ZM_SL_ANN(φ) + (κ − ZF_earth(φ)) · DELTA_SL(φ)
     * </pre>
     *
     * <p>κ = {@link ZonalTables#zfEarth} 时**逐位等于观测表** ⇒ 恒等式按构造成立。
     *
     * <p>⚠ 与 {@link #tZonalMean} **不是**同一个量：{@code tZonalMean} 是**地球**在那个纬度的
     * 全表面年均气温（含地球自己的陆地和海拔），本方法是**本世界**在各种 κ 混合下的纬向平均。
     * 需要「本世界自己的纬向平均」时一律用本方法（降水的水汽源、airT 的参考、海洋的热成风）。
     *
     * <p><b>为什么不再写 {@code annualSeaLevelTemp(latRad, KAPPA_MEAN, 0)}</b>（P943 实测，2026-09-21）：
     * 那条式子等价于 {@code T_OCEAN + KAPPA_MEAN·DELTA_SL}，即假定重构式
     * {@code T_ZM_SL = T_OCEAN + ZF_earth·DELTA_SL} 成立。它**在 25~65 度成立到 ≤1.5 K，
     * 在 70~85 度崩到 +5.2/+8.0/+9.7/+8.0 K**，而崩的位置精确对应 {@link ZonalTables#landMeanElev}
     * 的跳升（245 → 444 → 1095 → 1638 → 1447 m）。
     * 原因是**折算口径不一致**：{@code T_ZM_SL_ANN} 是上游那张表自己的海平面折算，
     * 而重构式用 {@link #GAMMA}（6.5 K/km）乘**纬向平均**高程 —— 两者在高纬（陆地集中、
     * 高程大）必然分叉。这个分叉被 {@code (KAPPA_MEAN − ZF_earth)} 放大成一支**虚假的极地冷偏差**：
     * 80 度上 {@code zonalMeanSeaLevelK − T_ZM_SL_ANN} = <b>−8.83 K</b>，与 P940 实测的
     * {@code zonalSlTemp − tZmSlMonth} = −8.69 K 同源（季节项无辜：A_ZM 13.36/13.36、
     * 相位逐行相同）。
     *
     * <p>改成上式后：κ = ZF_earth 处恒等；25~65 度只动 ≤1.5 K；70~85 度消掉那 −4.4…−8.8 K。
     * **零新常数**（{@code T_ZM_SL_ANN} / {@code ZF_EARTH} / {@code DELTA_SL} 全部已在册），
     * 且 {@code interp5} 取 {@code abs(lat)} ⇒ 南北对称不破。
     */
    /**
     * <b>P944 归因开关</b>（默认 false = 生产）。true 时 {@link #zonalMeanSeaLevelK} 走
     * {@link #zmslkLegacy}，用于在同一次 JVM 内把「直接通路（dTdz）」与
     * 「间接通路（airT 到 风 到 tauS 到 vJet）」分开量。它折进 {@code SimClimate.configStamp()}，
     * 因此翻它会作废气候瓦片缓存，并经 {@code OceanField} 的 stamp 作废 ANOM 缓存。
     */
    public static boolean ZMSLK_LEGACY = false;

    /** 新式：锚在观测海平面年表上，只加本世界陆地占比那一项差。 */
    public static double zmslkNew(double latRad) {
        return ZonalTables.tZmSlAnnual(latRad)
             + (KAPPA_MEAN - ZonalTables.zfEarth(latRad)) * landMinusOceanSLK(latRad);
    }

    /** 旧式（重构式）：{@code T_OCEAN + KAPPA_MEAN*DELTA_SL}。仅为归因保留，不是生产路径。 */
    public static double zmslkLegacy(double latRad) {
        return annualSeaLevelTemp(latRad, KAPPA_MEAN, 0.0);
    }

    public static double zonalMeanSeaLevelK(double latRad) {
        return ZMSLK_LEGACY ? zmslkLegacy(latRad) : zmslkNew(latRad);
    }

    /**
     * 季节性温度异常（K）—— 用**连续大陆度**插值振幅与相位。
     *
     * <p>这是让 p' 连续的关键：海陆的 A 与 psi 不再在海岸线上跳变，
     * 而是在 COAST_BLEND（800 km）尺度上平滑过渡。
     * 南半球自动反相（相位移 pi），不需要任何开关。
     */
    // ================= §407 陆海热容对比（平板模型） =================

    /**
     * ★★★ §407：季节项改由【平板热容模型】推导，取代地球振幅表（`A_LAND_K`/`A_SEA_K`）。
     * **默认 false => 逐位不变。**
     *
     * <p><b>物理</b>：`C*dT'/dt = F'(t) - lambda*T'`，周期强迫下的稳态解
     * <pre>
     *   A   = (F0/lambda) / sqrt(1 + (omega*tau)^2),   tau = C/lambda
     *   phi = atan(omega*tau)
     * </pre>
     * **陆海差异全部来自 `C`（差 133 倍）与 `lambda`（蒸发反馈在干表面被 beta 压掉）。**
     *
     * <p><b>lambda 零新拟合常数</b>：`lambda = 4*EPS*sigma*T^3 + beta*chv*LV*dq_sat/dT`
     * —— 长波反馈用模型自己的 OLR 参数化的导数，蒸发反馈用模型自己的 `qSat`。
     *
     * <p><b>实测（P607，25N）</b>：海洋振幅 2.09 K（表 3.20）、陆地 8.93 K（表 8.70）、
     * 陆海比 4.27（表 2.72）、海洋滞后 52.5 天（表 60）、**陆地滞后 1.5 天（表 30）**。
     * ⇒ **陆地滞后表差 20 倍，那是本开关要修的主要东西。**
     */
    public static boolean SEASON_FROM_HEAT_CAPACITY = false;
    /** 海洋混合层热容 J/(m^2 K)：rho_w*c_w*h = 1000*4000*50 m。 */
    public static double C_SEA = 1000.0 * 4000.0 * 50.0;
    /** 陆地土壤热容 J/(m^2 K)：rho_s*c_s*h = 1500*1000*1 m。 */
    public static double C_LAND = 1500.0 * 1000.0 * 1.0;
    /** 参考风速耦合系数 rho*C_D*|V|（kg/(m^2 s)）。 */
    public static double CHV_REF = 1.2 * 1.3e-3 * 6.0;
    /** 陆地上的参考表面湿润度（§387 实测值，用于压制蒸发反馈）。 */
    public static double BETA_LAND_REF = 0.30;

    /** 平板模型的反馈系数 lambda（W/(m^2 K)）。 */
    public static double slabLambda(double latRad, double kappa) {
        double t = annualSeaLevelTemp(latRad, 0.5, 0.0);
        double lamLong = 4.0 * Radiation.EPS * Radiation.SIGMA * t * t * t;
        double dq = (PrecipField.qSat(t + 1.0) - PrecipField.qSat(t - 1.0)) / 2.0;
        double lamEvapRef = CHV_REF * Radiation.LV * dq;
        double k = clamp01(kappa);
        double betaEff = 1.0 - k * (1.0 - BETA_LAND_REF);
        return lamLong + betaEff * lamEvapRef;
    }

    /** 平板模型的时间常数（秒）。 */
    public static double slabTau(double latRad, double kappa) {
        double k = clamp01(kappa);
        double c = (1.0 - k) * C_SEA + k * C_LAND;
        return c / slabLambda(latRad, kappa);
    }

    private static double slabOmega() {
        return 2.0 * Math.PI / (WorldContract.DAYS_PER_YEAR * 86400.0);
    }

    /** 吸收太阳的季节振幅（W/m^2）：用二至日的日照差。 */
    public static double seasonalSolarAmp(double latRad, double kappa) {
        double a = Radiation.insolation(latRad, OBLIQUITY);
        double b = Radiation.insolation(latRad, -OBLIQUITY);
        return Math.abs(a - b) / 2.0 * (1.0 - Radiation.ALB_LAND);
    }

    /** 平板模型给出的季节振幅（K）。 */
    public static double slabAmpK(double latRad, double kappa) {
        double lam = slabLambda(latRad, kappa);
        double tau = slabTau(latRad, kappa);
        double w = slabOmega();
        return (seasonalSolarAmp(latRad, kappa) / lam) / Math.sqrt(1.0 + w * tau * w * tau);
    }

    /** 平板模型给出的滞后相位（弧度）。 */
    public static double slabPhaseRad(double latRad, double kappa) {
        return Math.atan(slabOmega() * slabTau(latRad, kappa));
    }

    public static double seasonalAnomaly(double latRad, double kappa, double theta) {
        if (SEASON_FROM_HEAT_CAPACITY) {
            double hemi0 = latRad >= 0.0 ? 0.0 : Math.PI;
            return slabAmpK(latRad, kappa) * Math.cos(theta - slabPhaseRad(latRad, kappa) - hemi0);
        }
        double latDeg = Math.toDegrees(latRad);
        double k = clamp01(kappa);
        double aSea = ZonalTables.aSea(latDeg);
        double amp = aSea + (ZonalTables.aLand(latDeg) - aSea) * k;
        // 纬向形状已被观测表 A(phi) 吸收 ⇒ 这里不再乘 sin^2(phi)（§80）
        if (SEASON_SHAPE_FROM_OBS) {
            // D79 路线 C″（§229.3）：**观测剖面的季节差值** × 海陆振幅比。
            //   · κ = ⟨κ⟩ 时比值 = 1 ⇒ 纬向平均**恰好是观测剖面** ⇒ 真剖面能驱动的迁移被保留；
            //   · 比值把海陆季节振幅差（判据 A5 量的那个）**原样保留**；
            //   · 赤道：amp(0) = ampRef(0) = 0 ⇒ 提前返回 0，硬约束不受影响。
            double ampRef = aSea + (ZonalTables.aLand(latDeg) - aSea) * KAPPA_MEAN;
            if (ampRef < 1.0e-9) return 0.0;
            double obs = ZonalTables.tZmSlMonth(latRad, theta) - ZonalTables.tZmSlAnnual(latRad);
            return obs * (amp / ampRef);
        }
        double psiDays = PSI_SEA_DAYS + (PSI_LAND_DAYS - PSI_SEA_DAYS) * k;
        double psi = 2.0 * Math.PI * psiDays / WorldContract.DAYS_PER_YEAR;
        double hemi = latRad >= 0.0 ? 0.0 : Math.PI;
        return amp * Math.cos(theta - psi - hemi);
    }

    /**
     * **纬向平均口径的季节温度异常（K）** —— 涡动链专用（D79 **步骤 0**，设计冻结 §231.4）。
     *
     * <p><b>为什么要单开一个方法</b>：{@link #seasonalAnomaly} 有两个主人，要的口径**不同**：
     * <ul>
     *   <li>{@code surfaceTemp}（→ 群系 / 雪线 / 海冰）要**逐点**口径 —— 该点自己的下垫面，
     *       由局部 κ 混合 {@code aLand}/{@code aSea}；</li>
     *   <li>{@code PrecipField.zonalSlTemp}（→ {@code eadyGrowth} / {@code columnWater} / {@code eddyMfc}）
     *       要**纬向平均**口径。</li>
     * </ul>
     * 两者共用一张表时，修任何一侧都会污染另一侧。**实测代价**：D83 的落地版把逐点口径修对
     * （aSea 高纬 ×4~17），纬向平均那一侧跟着变（70° 的混合振幅 8.02 → 13.49 K），
     * 于是 B2.a 从「达标」翻成「未达标」（§231.2）。
     *
     * <p>现在两边**分表**：本方法读 {@link ZonalTables#aZonalMean}（表 {@code A_ZM_K}），
     * {@link #seasonalAnomaly} 读 {@code aLand}/{@code aSea}。**播种值等于旧的混合值** ⇒ 步骤 0 是纯拆分。
     * **决定 2 会把 {@code A_ZM_K} 换成推导出来的机制。**
     */
    /**
     * 2026-09-21 (S615): PHASE FROM OBSERVATION TABLE instead of the pointwise constant.
     * true  => phase = first-harmonic phase of T_ZM_SL_MONTH, per latitude
     * false => old path (a single constant psi from PSI_SEA_DAYS/PSI_LAND_DAYS), bit-identical.
     * WHY (S614, P938): those two constants were calibrated for the POINTWISE sea-surface
     * caliber (thermal inertia). Used on the ZONAL MEAN they give a 50.16-day lag, while the
     * observed table first harmonic is 0..-6 days -- nearly an order of magnitude apart.
     * The amplitude (A_ZM_K) was already re-derived from the same table in S607, so the phase
     * must come from the same source too.
     */
    public static boolean PHASE_FROM_OBS = true;   // S615: P939 self-check passed, lags match P938 (0.1..-6.5 d vs old +50.16 d)

    private static volatile double[] PHI_ZM = null;

    /** first-harmonic phase (rad, theta origin) at the 19 nodes of T_ZM_SL_MONTH. Lazy. */
    public static double[] phiZmNodes() {
        double[] t = PHI_ZM;
        if (t != null) return t;
        t = new double[19];
        double p0 = 2.0 * Math.PI * ZonalTables.SEASON_SHAPE_PHI0;
        for (int j = 0; j < 19; j++) {
            double sc = 0.0, ss = 0.0;
            for (int m = 0; m < 12; m++) {
                double v = ZonalTables.T_ZM_SL_MONTH[m * 19 + j];
                double ang = 2.0 * Math.PI * m / 12.0;
                sc += v * Math.cos(ang);
                ss += v * Math.sin(ang);
            }
            t[j] = Math.atan2(ss, sc) + p0;
        }
        return PHI_ZM = t;
    }

    /** zonal-mean annual-cycle phase (rad, theta origin; theta=0 is NH summer solstice). */
    public static double phiZonalMean(double latDeg) {
        double[] t = phiZmNodes();
        double a = Math.abs(latDeg);
        if (a >= 90.0) return t[18];
        int i = (int) (a / 5.0);
        if (i > 17) i = 17;
        double f = a / 5.0 - i;
        return t[i] * (1.0 - f) + t[i + 1] * f;
    }

    public static double seasonalAnomalyZonal(double latRad, double theta) {
        double amp = ZonalTables.aZonalMean(Math.toDegrees(latRad));
        double psi;
        if (PHASE_FROM_OBS) {
            psi = phiZonalMean(Math.toDegrees(latRad));
        } else {
            double psiDays = PSI_SEA_DAYS + (PSI_LAND_DAYS - PSI_SEA_DAYS) * KAPPA_MEAN;
            psi = 2.0 * Math.PI * psiDays / WorldContract.DAYS_PER_YEAR;
        }
        double hemi = latRad >= 0.0 ? 0.0 : Math.PI;
        return amp * Math.cos(theta - psi - hemi);
    }

    /**
     * **D79 的 A/B 开关**：季节项的形状用「单一全年谐波」还是「观测年循环形状」。
     *
     * <p>{@code false}（默认）⇒ 与换表之前**逐位相同**（那一支的表达式一字未改）。
     * {@code true} ⇒ 形状取 {@link ZonalTables#seasonShape}（ERA5 月平均 t2m 的北半球剖面，
     * 零年均、单位半振幅，19 纬 x 12 月）。
     *
     * <p><b>为什么需要它</b>（§220 实测）：真实纬向平均温度的年循环含显著高次谐波，
     * 斜压性最大值会随季节迁移 <b>7.0 度</b>；而单一余弦只能给出 <b>0.0 度</b>。
     * 缺了迁移，B2.b 判据红着（D79）。
     *
     * <p>⚠ <b>翻之前必须先量</b>：{@code amp(phi,kappa)} 是**本世界自己的**海陆混合振幅，
     * 而形状表来自**地球**的纬向剖面（65 度上是 75% 陆地）。两者口径不同源，
     * 直接组合可能**过度迁移** —— 这正是 P493 要量的东西。它进 {@code configStamp()}。
     */
    public static boolean SEASON_SHAPE_FROM_OBS = false;   // S610 A/B: reverted, P293+P683 regressed, phase not fixed

    /** 海温异常提供者（由 M2 的洋流给出：西暖东冷）。null = 无异常（默认）。 */
    public interface SstProvider {
        double anomalyAt(int x, int z);
        /**
         * ★★★ **§447：带相位的重载**（默认回落到年平 ⇒ 既有提供者不必改）。
         *
         * <p>为什么需要：SST 的**区域季节循环**在旧接口里根本表达不出来（`anomalyAt(int,int)` 没有 theta），
         * 模型的季节 SST 只能来自 `seasonalAnomaly(lat,kappa,theta)` —— 那是**纬向**的。
         * 而季风的水汽源带**区域**季节循环。观测锚（COBE-SST2 月气候 1991-2020，
     * `refs/gen_sst_box_cycle.py`）：孟加拉湾盒 JJA − 年 = **+1.13 K**、南海 **+1.30 K**、
     * 阿拉伯海 **+0.04 K**、索马里外海 **−1.01 K**。
     * ⚠ §445 三 里那个「JJA 比年均暖 4~5 K」是**没有出处的估值**，§448 已用实测改正。
         */
        default double anomalyAt(int x, int z, double theta) { return anomalyAt(x, z); }
    }
    public static SstProvider SST_PROVIDER = null;

    /**
     * **仅本线程**的 SST 抑制开关（防重入；§162 接线）。
     *
     * <p>解一条海洋纬度行时会调 {@link #windStress} → {@link #pressureAnomaly} → {@link #sstAnom}
     * → 提供者 → 又要解这条行 ⇒ 无限递归。原来的做法是把 {@code SST_PROVIDER} 这个**全局**
     * 引用临时置 null 再还原 —— 那在**多线程**下是错的：后台预热线程解行的那几秒里，
     * 服主线程读到的 SST 会**随缘变成 0**（温度场出现一闪一闪的假冷/假暖）。
     * 改成 thread-local 之后，抑制只对本线程生效，全局引用**全程不动**
     * ⇒ 顺带还消掉了「解行期间 {@code SimClimate.configStamp()} 抖动 ⇒ 瓦片缓存被反复清空」。
     */
    private static final ThreadLocal<Integer> SST_SUPPRESS = new ThreadLocal<>();
    /**
     * 本线程内抑制/恢复 SST 注入。**必须 try/finally 成对使用，且支持嵌套**（引用计数）。
     *
     * <p>⚠ 为什么必须是计数器而不是布尔：{@code OceanField.solveRow} 会把**整段**解算包在
     * 一次 suppress/restore 里（见 E30），一旦将来有人在里面再嵌一层，
     * 用布尔的话内层的 {@code remove()} 会把外层的抑制**提前关掉**，
     * 于是又回到「无限重入」。计数器让内层的 restore 只减一层。
     */
    public static void suppressSst(boolean on) {
        Integer cur = SST_SUPPRESS.get();
        int v = cur == null ? 0 : cur;
        if (on) SST_SUPPRESS.set(v + 1);
        else if (v <= 1) SST_SUPPRESS.remove();
        else SST_SUPPRESS.set(v - 1);
    }
    /** 本线程当前是否处于 SST 抑制状态（探针/自检用）。 */
    public static boolean sstSuppressed() {
        Integer s = SST_SUPPRESS.get();
        return s != null && s > 0;
    }

    // ================= 副热带高压 cell（纬向不对称的种子） =================

    /**
     * cell 增益：把 p_ref 的**纬向平均**振幅放大成「洋盆 vs 大陆」的差。
     *
     * <p>锚点 = **观测的年平均海陆气压差 ~7 hPa**（30 度：北太平洋高压 1018~1020 vs 亚洲大陆 1012）。
     * p_ref 在 30 度相对基准是 +500 Pa ⇒ CELL_GAIN·500 = 700 Pa ⇒ 1.4。
     *
     * <p>符号自动正确：副热带 carrier>0 ⇒ 海洋高压 / 大陆低压；
     * 副极地 carrier<0 ⇒ 海洋低压（冰岛/阿留申） / 大陆高压 ✓ 两端都对。
     */
    public static double CELL_GAIN = 2.8;
    /**
     * **本世界自己的大陆度平均值 ⟨κ⟩** —— cell 项「零纬向平均」的参考值，
     * 也是 {@link PrecipField} 里纬向平均季节振幅用的大陆度（**现场直读本字段**，
     * 不再有静态拷贝 —— 见审计 D6）。
     *
     * <p><b>0.15 -> 0.328</b>（§94.3；探针 P420：x 跨度 40,000 km × 8 个互不重叠窗口 × 6 条 z 线
     * × 2001 点/线 = 每纬线 96,048 点）：面积加权全球 ⟨κ⟩ = <b>0.3280</b>（cos 权重、19 个纬度），
     * 全球均值的 SE ≈ <b>±0.007</b>，且**三个独立口径互证**
     * （κ 场 0.3280 / isLand 面积加权陆地占比 32.80 % / hypsometry 32.855 %）。
     * 旧值 0.15 的问题是<b>数值错了 2.2 倍</b>（残留 +252 Pa 的假高压）。
     *
     * <p>⚠ <b>不要把它换成「按纬度的大陆度表」</b>：地形链（§567 之前是 `PlateField` 的
     * contScore / 抖动 Voronoi / 造山 / 岛弧限制器；§567 之后是 `TalosField`）都只依赖 (x,z)，
     * **没有任何一处引用纬度** ⇒ ⟨κ⟩(φ) 实测是平的（19 个纬度全在 0.304~0.341，**无一超过 2σ**；
     * ⚠ 该实测是在**旧地形**上做的，本常量在 TalosField 上应重测 —— 见设计冻结 §567 的重捕清单）。
     * 「65 度 0.71」是单条 z 线 × 6.7 个板块格的**抽样噪声**（同纬度换窗口给 0.054，见 §90）。
     *
     * <p>⚠ `PLATE_CELL` 一旦改变，⟨κ⟩ **必须重测**（P420 应进回归集）。
     */
    public static double KAPPA_MEAN = 0.328;   // ★ 见下方 S624：0.575 已【测出但未采用】


    // S624 (P947, 2026-09-22): this constant was RE-MEASURED.  Result: MEASURED BUT NOT ADOPTED
    // (the A/B is recorded at the end of this block).  The production value is unchanged at 0.328.
    // 0.3280/0.3196 (P420/P491) were measured on the LEGACY terrain; §567 replaced the whole
    // land-sea chain with TalosField, and the javadoc above already recorded this constant as
    // needing re-measurement on it ("见设计冻结 §567 的重捕清单").
    // P947 re-ran P420 protocol verbatim (19 lat x 8 non-overlapping 5,000 km windows x 6 z-lines
    // x 2001 points/line = 96,048 points per latitude, cos-area-weighted):
    //   (1) kappa field  <kappa> = 0.5749   <-- ARTIFACT (see the correction at the end of this block)
    //   (2) isLand area-weighted land fraction = 0.5729     (two independent calibers agree to 0.002)
    //   (3) unweighted simple mean = 0.4746
    // Every estimator is far above 0.328.
    //
    //
    // ★★ P947 WAS UNDER-SAMPLED IN X -- ITS PROFILE IS AN ARTIFACT.  CORRECTED.
    //   P947 covered ONE 40,000 km meridian span (8 windows x 5,000 km inside it).  Its own output
    //   shows why that is not enough: the 8 window means were [0.008, 0.337, 0.663, 0.564, 0.792,
    //   0.923, 0.988, 0.324], SD 0.40 -- the land-sea field is dominated by a few very large
    //   continents, so a single span is ONE realization, not an ensemble.
    //   The PRE-EXISTING measurement refs/kappa_bar_profile.txt (P491: 12 independent 40,000 km
    //   windows x 2000 points, batch means = 480,000 km of x per latitude) gives a nearly flat
    //   profile 0.28~0.37, and its cos-weighted mean is ~0.33 -- i.e. THIS CONSTANT IS CORRECT.
    //   (Process lesson, recorded so it is not repeated: that file already existed in refs/; it
    //    should have been read BEFORE writing a new probe, and a new probe must be checked against
    //    the sampling density of the measurement it intends to replace.)
    //
    //   => The "do not replace it with a per-latitude table" warning above STANDS: kbar(phi) is
    //      flat, so the global mean is the right reference for every consumer, and arm 2
    //      (per-latitude <kappa>) is NOT needed.  zonalMeanSeaLevelK (S620) is likewise fine with
    //      the global constant.
    //
    // ★ A/B RESULT (full 22-gate acceptance suite).  Arms: A0 = 0.328 (dir B97DFF43_805B5532_TALOS),
    //   A1 = 0.575 (dir 7D3A696A_99C31376_TALOS).
    //   GATES: 18/22 -> 18/22, ZERO gate changes -- the battery is insensitive to this constant.
    //   INDEPENDENT GPCP ROWS (P296; the only column never fitted by EDDY_MIX), A0 -> A1:
    //     mid-lat 47.5~62.5   summer +17.2% -> +18.5%    winter +71.2% -> +82.7%
    //     equator  2.5~12.5   summer -41.3% -> -45.8%    winter -61.4% -> -56.8%
    //     subtrop 27.5~37.5   summer -78.3% -> -77.5%    winter -67.8% -> -70.2%
    //   4 of 6 got worse, and the large one-signed equatorial/subtropical deficits did NOT improve.
    //   => REFUTED: "the stale zero-reference explains the tropical/subtropical deficits."
    //
    //   MECHANISM (why a correctly measured value made things worse).  Split kappa into the
    //   latitude own zonal mean and its anomaly:
    //     zonal-mean part = CELL_GAIN*carrier*(KAPPA_MEAN - kbar(phi))   <- non-zero iff KAPPA_MEAN is GLOBAL
    //     land-sea part   = -CELL_GAIN*carrier*(kappa - kbar(phi))       <- INDEPENDENT of KAPPA_MEAN
    //   The land-sea CONTRAST is invariant to this constant; only the zonal MEAN of the term moves.
    //   0.328 -> 0.575 therefore injects a spurious zonal-mean pressure signal proportional to
    //   carrier(phi); it does NOT correct the land-sea difference.  Hence the regression.
    //
    //   => This constant is NOT independently changeable.  The physically correct repair is the
    //      SEMANTIC one (arm 2, NOT YET RUN):  (KAPPA_MEAN - kappa)  ->  (kbar(phi) - kappa),
    //      which makes the zonal-mean part vanish BY CONSTRUCTION and leaves a pure land-sea
    //      contrast.  The sign stays correct (at 30 deg kbar = 0.549: land 0.6 -> -, ocean 0.1 -> +,
    //      and with carrier > 0 that is still "ocean high / land low").
    //      The SAME kbar(phi) is owed by zonalMeanSeaLevelK (S620), whose coefficient currently
    //      takes the global constant instead of the latitude own mean.

    /** 副高 cell 的气压贡献（Pa）。**年平均值**，与季节性热力项的 p' 是两回事。 */
    /**
     * cell 项的迁移幅度（弧度）。**真实副高是准永久的，只南北移 5~10 度**，
     * 不是黄赤交角 ±23.44 度（§62）。早先用 subsolarLat 造成 θ=π 时 30N 被移到 53.4N
     * （那里 carrier 变负 = 副极地低压）⇒ 东岸风在冬季反向 ✗。
     */
    public static double CELL_MIGRATION = Math.toRadians(8.0);
    /** 气压带相对太阳的滞后（弧度）：30 天。 */
    public static final double CELL_LAG = 2.0 * Math.PI * 30.0 / WorldContract.DAYS_PER_YEAR;

    /**
     * **cell 项的赤道侧连续门控宽度（度）**（§84）。
     *
     * <p>为什么需要：cell 项写成 `CELL_GAIN * carrier(φ) * (KAPPA_MEAN - κ)`，
     * 在 20~60 度它的形状与观测的**年平均海陆气压差**吻合（20 度算 8.4 hPa / 观测 6.6；
     * 50 度算 -14 / 观测 -10），但在赤道带 **carrier 是赤道槽（负）** ⇒ 乘上
     * `(KAPPA_MEAN-κ)` 之后**陆地变成 +1190 Pa 的高压**，而真实赤道带的海陆气压差只有 ~1.5 hPa、
     * 而且**符号相反**（陆地是热低压）。这不是振幅问题，是**赤道槽根本不是海陆热力对比造成的**
     * —— 它是 ITCZ 的辐合带，海陆两侧的气压极值相同。
     *
     * <p>所以给 cell 项加一个**连续**的赤道侧门控：0 度权重 0、>= 该宽度权重 1，
     * smoothstep（两端一阶导为 0 ⇒ C1 连续，零阈值、零 if，符合项目纪律）。
     * 25 度 ⇒ 权重 20 度 0.896、25 度以上 1.000（副热带与副极地几乎不动）。
     */
    public static double CELL_TROPIC_GATE_DEG = 30.0;

    /** cell 项的赤道侧连续权重（smoothstep，见 CELL_TROPIC_GATE_DEG）。 */
    public static double tropicGate(double latRad) {
        double t = clamp01(Math.toDegrees(Math.abs(latRad)) / CELL_TROPIC_GATE_DEG);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * 环流侧（§405）：cellPressure 的季节位相改由模型自己的陆海温差驱动。
     *
     * 为什么：原来写死 CELL_MIGRATION*cos(theta - CELL_LAG)（8 度迁移 + 30 天滞后），
     * 那是一个与模型温度场完全无关的正弦。于是「哪个月热低压在哪」不是从物理里长出来的。
     *
     * 诚实的局限：模型现在没有自己的求解温度场（S1a Radiation 与 HadleyCell 都默认关），
     * 所以这里用的是 landSurfaceTemp/seaSurfaceTemp —— 它们本身仍来自 ZonalTables（地球表）。
     * 这严格优于现状（至少位相与模型其余部分同源、且随模型温度变化），
     * 但真正的升级要等 RCE 落地（§396）。
     *
     * 幅度对齐：CELL_MIG_SENS 由首次调用时自标定，使 JJA 在 25N 的迁移量与旧式完全相同
     * => 只换形状，不动幅度（§398 的教训：归一化方式必须说清）。
     */
    // ★★★★★★★ §7462（2026-09-26 采纳）：**默认改为 true** —— 热低压位置的季节位相由模型自己的陆海温差驱动。
    //   为什么采纳（22 支套件 + 生产接口对账，详见 §7461/§7462）：
    //     ① (b) NH AREA 8 -> 9；(c) SH AREA 10 -> 15（升 5，未破「不得降超 2」）；
    //     ② (d) 套件 fail 2 -> 1、FAILED=0、22/22；
    //     ③ (e) 只动一个已有开关，未调任何常数；
    //     ④ 50E 的 dP 从 -0.178 **转正为 +0.127**，而高原对照（95E/105E/90E）**全部保持负** => 判别力保持；
    //     ⑤ 它**正命中**用户 objective 的「**不人为平移**」那一条：替换掉 `CELL_MIGRATION*cos(theta-CELL_LAG)`
    //        这个【与模型温度场完全无关的正弦】（`:908-909` 逐字）。
    //   ⚠ 诚实记账（未满足的那一条）：(a) NH 平均 R **微降**（-0.374 -> -0.401）。
    //     => 但那与 (b) NH AREA 上升并不矛盾：AREA 数的是季节反转的【段数】，R 是【强度均值】。
    //   ⚠ 自标定承诺已验证（P1153）：JJA 在 25N 的 cellPressure 两臂**逐位相同**（-386.21 Pa）。
    //   ⚠ 仍存的局限（`:911` 逐字）：landSeaTempContrast 用的 landSurfaceTemp/seaSurfaceTemp
    //      **仍来自 ZonalTables（地球表）** => 「真正的升级要等 RCE 落地（§396）」仍然成立。
    public static boolean CELL_PHASE_FROM_TEMP = true;
    /** 陆海温差 -> 迁移纬度的灵敏度（度/K）。<=0 时首次调用自标定。 */
    public static double CELL_MIG_SENS = 0.0;
    /** 自标定参考纬度（度）。 */
    public static double CELL_MIG_REF_LAT_DEG = 25.0;

    /** 旧式（硬编码正弦）给出的迁移纬度，度。 */
    public static double cellMigrationDegHardcoded(double theta) {
        return Math.toDegrees(CELL_MIGRATION * Math.cos(theta - CELL_LAG));
    }

    /** 模型自己的陆海温差（K）：陆地（用该纬度平均海拔）减海洋。 */
    public static double landSeaTempContrast(double latRad, double theta) {
        double elev = ZonalTables.landMeanElev(latRad);
        return landSurfaceTemp(latRad, theta, elev) - seaSurfaceTemp(latRad, theta);
    }

    private static double cellMigSensCalib() {
        if (CELL_MIG_SENS > 0.0) return CELL_MIG_SENS;
        double ref = Math.toRadians(CELL_MIG_REF_LAT_DEG);
        double dT = landSeaTempContrast(ref, 0.0);
        double want = cellMigrationDegHardcoded(0.0);
        CELL_MIG_SENS = (Math.abs(dT) < 1.0e-6) ? 0.0 : want / dT;
        return CELL_MIG_SENS;
    }

    public static double cellPressure(double latRad, double kappa, double theta) {
        double migDeg = CELL_PHASE_FROM_TEMP
                ? cellMigSensCalib() * landSeaTempContrast(latRad, theta)
                : cellMigrationDegHardcoded(theta);
        double shifted = Math.toDegrees(latRad) - migDeg;
        double kk = Double.isNaN(PA_FIXED_KAPPA) ? clamp01(kappa) : PA_FIXED_KAPPA;
        return CELL_GAIN * ZonalTables.carrier(shifted) * tropicGate(latRad) * (KAPPA_MEAN - kk);
    }

    // ================= 沿岸风参数化（观测锚点） =================

    /**
     * **沿岸风参数化**：只补「沿岸分量」，不动诊断出来的跨岸分量。
     *
     * <p>为什么需要（设计冻结 §52）：沿岸上升流急流的驱动是**副热带高压 cell 在洋盆东部的纬向不对称**，
     * 而我们的 p_ref 是纬向对称的一维表 ⇒ 诊断链**三次假设全部否证**（§45/§51/§52），
     * 沿岸风的符号只有 39~42% 正确（等于抛硬币）。
     *
     * <p>锚点：**观测的沿岸风应力中位 0.09 Pa**（真实范围 0.05~0.15；加州上升流季）。
     * 由 tau = rho*C_D*|U|^2 反推 |U| = sqrt(0.09/(1.225*1.3e-3)) = **7.5 m/s**。
     *
     * <p>它规定的是**风**（大气的输出），不是洋流 ⇒ 不违反「零规定洋流」。
     * 关闭方式：COAST_WIND_ON = false。
     */
    /**
     * ★★★ A/B 开关（§422）：`p_ref` 的经向斜率是否进 `v`。**默认 true ⇒ 逐位不变。**
     *
     * <p><b>物理疑问</b>：`wind()` 的注释已经指出，`p_ref` 的经向梯度是【纬向平均】气压梯度，
     * 而纬向平均风已由外生 `U_zm` 给定 ⇒ 把它喂进 `u` 就是重复计数（P260 实测造出 40 m/s 虚假纬向风）。
     * **同一个论证对 `v` 也成立**：纬向平均气压梯度的平衡响应就是【纬向平均经向环流】，
     * 而模型已经用 `ZonalTables.wZm`（Hadley 胞表）表示它了。
     *
     * <p><b>实测症状</b>（P629）：赤道 `pzRef = 0`、5N `pzRef = +3.6e-4 Pa/m`，
     * 而 `f=0` 处 `v = -gam*(pz+pzRef)/(rho*(gam^2+f^2))` ⇒ `v` 从赤道的 `+2.04` 跳到 5N 的 `-5.82`，
     * 产生 `dv/dz = -1.5e-05` 的【虚假赤道辐合】—— 比真实热带低层散度（~1e-6）大 13.6 倍。
     */
    public static boolean PZREF_IN_V = true;

    public static boolean COAST_WIND_ON = false;   // 裁决：继续修诊断，不用参数化（§52.2）
    /** 沿岸风速幅值（m/s），由观测的 0.09 Pa 反推。 */
    public static double COAST_WIND_V = 7.5;
    /** 沿岸风的生效宽度（m）：只在离岸这么近的地方补。 */
    public static double COAST_WIND_W = 400_000.0;
    /** 上升流有利纬度的形状（副热带最强）。 */
    public static double SUM_SHAPE(double latRad) {
        double d = (Math.toDegrees(Math.abs(latRad)) - 30.0) / 25.0;
        return Math.exp(-d * d);
    }

    /** 东岸度：+1 = 东岸（西侧是洋、东侧是陆），-1 = 西岸，0 = 两侧同类。 */
    public static double eastness(int x, int z, long seed, int cell) {
        int d = (int) COAST_WIND_W;
        double w = PlateField.landScoreWithCell(x - d, z, seed, cell);
        double e = PlateField.landScoreWithCell(x + d, z, seed, cell);
        double v = (e - w) * 0.5;
        return v > 1 ? 1 : (v < -1 ? -1 : v);
    }

    /** 沿岸风（只给经向分量，m/s）：上升流有利 = 向赤道。 */
    public static double coastalAlongshoreV(int x, int z, long seed, int cell) {
        double en = eastness(x, z, seed, cell);
        if (en <= 0.15) return 0.0;                 // 只补东岸
        double lat = WorldContract.latOf(z);
        double hemi = lat >= 0 ? 1.0 : -1.0;        // 北半球向赤道 = 向南 = v<0
        return -hemi * COAST_WIND_V * SUM_SHAPE(lat) * en;
    }

    /** 该点的海温异常（K）；没有提供者时为 0。 */
    /** SST 距平（K）。SST_PROVIDER=null 时恒为 0。**public 是为了让探针与生产同源**（D40）。 */
    public static double sstAnom(int x, int z) {
        Integer s = SST_SUPPRESS.get();
        if (s != null && s > 0) return 0.0;
        SstProvider p = SST_PROVIDER;      // 先取到局部，避免与 install/uninstall 竞态
        return p == null ? 0.0 : p.anomalyAt(x, z);
    }

    /** §447：**带相位**的 SST 距平（K）。没有支持相位的提供者时回落到年平。 */
    public static double sstAnom(int x, int z, double theta) {
        Integer s = SST_SUPPRESS.get();
        if (s != null && s > 0) return 0.0;
        SstProvider p = SST_PROVIDER;
        return p == null ? 0.0 : p.anomalyAt(x, z, theta);
    }

    /** 兼容重载（离散海陆）。仅用于对照。 */
    public static double seasonalAnomaly(double latRad, boolean land, double theta) {
        return seasonalAnomaly(latRad, land ? 1.0 : 0.0, theta);
    }

    /** 海面温度（K）：洋面基线 + 季节项（**没有**海陆年均对比项，κ=0）。 */
    public static double seaSurfaceTemp(double latRad, double theta) {
        return annualSeaLevelTemp(latRad, 0.0, 0.0) + seasonalAnomaly(latRad, false, theta);
    }

    /** 陆地地表温度（K），含海拔递减率：海平面陆地基线（κ=1）+ 季节项 − Γ*h。 */
    public static double landSurfaceTemp(double latRad, double theta, double elev) {
        return annualSeaLevelTemp(latRad, 1.0, 0.0) + seasonalAnomaly(latRad, true, theta)
             - GAMMA * Math.max(0.0, elev);
    }

    /** 地表温度（K）—— 世界坐标的纯函数。**用连续大陆度**（见 §31.5 那条链）。 */
    public static double surfaceTemp(int x, int z, long seed, int cell, double theta) {
        double lat = WorldContract.latOf(z);
        double[] ke = kappaElev(x, z, seed, cell);
        double k = ke[0];
        double elev = ke[1];
        return annualSeaLevelTemp(lat, k, sstAnom(x, z, theta))
             + seasonalAnomaly(lat, k, theta)
             - GAMMA * Math.max(0.0, elev) * k;
    }

    /**
     * 开关：**海陆年均温度对比要不要进 p'**。
     *
     * <p>{@code false}（默认）⇒ **解析上逐位抵消**，p' 与换基线之前完全相同 ——
     * 因为 {@code tSfc + Γ*h - tRef} 里 {@code tRef} 取的是**同一个 κ** 的年均值，
     * 于是只剩季节项与 SST'（正是原来那两项）。
     *
     * <pre>
     *   tSfc + Γ*h - tRef
     *     = [annualSL(k,SST') + seas - Γ*h*k] + Γ*h*PLATEAU_AMP*k - annualSL(k,0)
     *     = seas + (1-k)*SST' + Γ*h*k*(PLATEAU_AMP - 1)        （PLATEAU_AMP=1 时就是 seas + (1-k)SST'）
     * </pre>
     *
     * <p><b>为什么默认不进</b>：同纬度海陆的**年均气压差**已经由 {@link #cellPressure}
     * 独立锚定在**观测的 ~7 hPa @ 30 度**上。本项若也进 p'，同一份对比就被算两次
     * （30 度处：cellPressure 给 11.2 hPa，本项会再加约 4.6 hPa ⇒ 15.8 hPa，是锚点的 2.3 倍）。
     * 打开它只为**量出**这个重复计数，进 {@code configStamp()}。
     */
    public static boolean LANDS_ANNUAL_IN_PRESSURE = false;

    /**
     * 地面气压异常（Pa）。**纯局部的代数式**，不需要求解任何方程。
     *
     * <pre>
     *   T'_c = CHI * ( T_sfc + GAMMA*h - T_zm )        （h 乘 PLATEAU_AMP）
     *   p'   = -K_P * T'_c
     * </pre>
     */
    // ===== §409 诊断钩子（只给探针用，生产恒为默认值）：把 p 的两项拆开 =====
    public static boolean PA_NO_CELL = false;
    public static boolean PA_NO_THERMAL = false;
    public static double PA_FIXED_KAPPA = Double.NaN;
    /** 打开地表干暖项（§409）：见 pressureAnomaly 内的推导。默认 false => 逐位不变。 */
    public static boolean PA_DRY_WARMTH = false;
    /** 干暖项里取 beta 用的差分步长（与生产同口径）。 */
    public static int SOIL_GRAD_STEP = 500_000;

    public static double pressureAnomaly(int x, int z, long seed, int cell, double theta) {
        double lat = WorldContract.latOf(z);
        double[] ke = kappaElev(x, z, seed, cell);
        double k = ke[0];
        double elev = ke[1];
        // tRef = 「同 κ 的年均海平面温度」⇒ 它把海陆年均对比**解析地**从 T'_c 里减掉
        //（LANDS_ANNUAL_IN_PRESSURE = true 时才换成地球的 T_zm，让那一项真的进 p'）。
        double tRef = LANDS_ANNUAL_IN_PRESSURE ? tZonalMean(lat) : annualSeaLevelTemp(lat, k, 0.0);
        double tSfc = annualSeaLevelTemp(lat, k, sstAnom(x, z))
                    + seasonalAnomaly(lat, k, theta)
                    - GAMMA * Math.max(0.0, elev) * k;
        double h = Math.max(0.0, elev) * PLATEAU_AMP * k;
        // ★★★ §409：地表干暖项 —— 缺蒸发冷却导致的额外升温。
        //   为什么必须有它（P610 实测）：seasonalAnomaly 在亚洲 +6.95 K、撒哈拉 +7.01 K，
        //   【几乎完全一样】，因为它只依赖 kappa。而真实的撒哈拉夏季升温远大于印度，
        //   正是因为【没有蒸发冷却】。缺了这一项，纬向对称的副高场对两地一视同仁，
        //   模型就永远区分不出亚洲与撒哈拉。
        //
        //   零新常数：ΔT_dry = (1-beta) * LE_pot / lambda
        //     LE_pot = chv*LV*(qSat(T) - q_a)      潜在蒸发（beta=1 时的潜热通量）
        //     lambda = 4*EPS*sigma*T^3 + beta*chv*LV*dqSat/dT   线性化反馈
        //   beta=1 => ΔT=0（逐位不变）；beta=0 => 把全部缺失的蒸发冷却转成升温。
        double dryWarmth = 0.0;
        if (PA_DRY_WARMTH && SoilMoisture.ENABLED) {
            double beta = SoilMoisture.betaAt(x, z, seed, cell, theta, SOIL_GRAD_STEP);
            double ta = annualSeaLevelTemp(lat, k, 0.0) + seasonalAnomaly(lat, k, theta);
            double qa = PrecipField.moisture(ta, 0.0, k);
            // ⚠ 这里【绝不能】调 windAt：windAt -> pressureAnomaly -> 本分支 => 无限递归
            //   （实测 P611 第一版 JAVA_EXIT=1，就是这个）。
            //   改用模型已有的【纬向平均风表】作为大尺度风速 —— 零递归、零新常数。
            //   省略了经向分量与瞬变风，属于已文档化的近似。
            double sp = Math.abs(ZonalTables.uZmBlend(Math.toDegrees(lat), theta, k));
            double chv = Radiation.bulkCoeff(k, sp);
            double lePot = chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(ta) - qa);
            double dq = (PrecipField.qSat(ta + 1.0) - PrecipField.qSat(ta - 1.0)) / 2.0;
            double lambda = 4.0 * Radiation.EPS * Radiation.SIGMA * ta * ta * ta
                          + beta * chv * Radiation.LV * dq;
            if (lambda > 1.0e-6) dryWarmth = (1.0 - beta) * lePot / lambda;
        }
        double therm = PA_NO_THERMAL ? 0.0 : -K_P * CHI * (tSfc + dryWarmth + GAMMA * h - tRef);
        double cellTerm = PA_NO_CELL ? 0.0 : cellPressure(lat, k, theta);   // 注意：参数名已经是 cell
        return therm + cellTerm;
    }
}
