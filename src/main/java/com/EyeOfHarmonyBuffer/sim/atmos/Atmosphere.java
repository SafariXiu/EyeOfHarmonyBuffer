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

    /** 海平面气压（Pa）= 外生纬向平均（随直射点迁移）+ 热力距平。 */
    public static double seaLevelPressure(int x, int z, long seed, int cell, double theta) {
        double lat = WorldContract.latOf(z);
        double shifted = Math.toDegrees(lat - subsolarLat(theta));
        return ZonalTables.pRef(shifted) + pressureAnomaly(x, z, seed, cell, theta);
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
        double pzRef = ZonalTables.pRefSlopePerRad(Math.toDegrees(latRad)) / WorldContract.R_EFF;
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
    public static double oceanBaseK(double latRad) { return ZonalTables.tOceanK(latRad); }

    /**
     * **陆地相对海洋的海平面年均温差（K）**：{@code T_LAND_SL − T_OCEAN}。
     *
     * <p>为什么要把陆地那一支折到海平面：观测的 {@code T_LAND} **已经含了地球自己的海拔**
     * （陆面平均约 840 m）。而本模型另外还要减一次 {@code Γ*h}（每列自己的精确高程）
     * ⇒ 不剥出来就是**同一份海拔减两次**（在 75 度能差 10 K 以上）。
     * 剥出来之后，「海平面上的陆地」比海洋暖/冷这么多，模型的 {@code -Γ*h*κ} 再把它降到位。
     */
    public static double landMinusOceanSLK(double latRad) {
        return ZonalTables.tLandK(latRad) + GAMMA * ZonalTables.landMeanElev(latRad)
             - ZonalTables.tOceanK(latRad);
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
     * **本世界的纬向平均海平面气温（K）**：取 κ = {@link #KAPPA_MEAN} 的 {@link #annualSeaLevelTemp}。
     *
     * <p>⚠ 与 {@link #tZonalMean} **不是**同一个量：{@code tZonalMean} 是**地球**在那个纬度的
     * 全表面年均气温（含地球自己的陆地和海拔），本方法是**本世界**在各种 κ 混合下的纬向平均。
     * 需要「本世界自己的纬向平均」时一律用本方法（降水的水汽源、airT 的参考、海洋的热成风）。
     */
    public static double zonalMeanSeaLevelK(double latRad) {
        return annualSeaLevelTemp(latRad, KAPPA_MEAN, 0.0);
    }

    /**
     * 季节性温度异常（K）—— 用**连续大陆度**插值振幅与相位。
     *
     * <p>这是让 p' 连续的关键：海陆的 A 与 psi 不再在海岸线上跳变，
     * 而是在 COAST_BLEND（800 km）尺度上平滑过渡。
     * 南半球自动反相（相位移 pi），不需要任何开关。
     */
    public static double seasonalAnomaly(double latRad, double kappa, double theta) {
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
    public static double seasonalAnomalyZonal(double latRad, double theta) {
        double amp = ZonalTables.aZonalMean(Math.toDegrees(latRad));
        double psiDays = PSI_SEA_DAYS + (PSI_LAND_DAYS - PSI_SEA_DAYS) * KAPPA_MEAN;
        double psi = 2.0 * Math.PI * psiDays / WorldContract.DAYS_PER_YEAR;
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
    public static boolean SEASON_SHAPE_FROM_OBS = false;

    /** 海温异常提供者（由 M2 的洋流给出：西暖东冷）。null = 无异常（默认）。 */
    public interface SstProvider { double anomalyAt(int x, int z); }
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
     * <p>⚠ <b>不要把它换成「按纬度的大陆度表」</b>：`PlateField` 全链
     * （contScore / 抖动 Voronoi / 造山 / 岛弧限制器）是 (x,z) 统计均匀且各向同性的，
     * **没有任何一处引用纬度** ⇒ ⟨κ⟩(φ) 实测是平的（19 个纬度全在 0.304~0.341，**无一超过 2σ**）。
     * 「65 度 0.71」是单条 z 线 × 6.7 个板块格的**抽样噪声**（同纬度换窗口给 0.054，见 §90）。
     *
     * <p>⚠ `PLATE_CELL` 一旦改变，⟨κ⟩ **必须重测**（P420 应进回归集）。
     */
    public static double KAPPA_MEAN = 0.328;

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

    public static double cellPressure(double latRad, double kappa, double theta) {
        double shifted = Math.toDegrees(latRad - CELL_MIGRATION * Math.cos(theta - CELL_LAG));
        return CELL_GAIN * ZonalTables.carrier(shifted) * tropicGate(latRad) * (KAPPA_MEAN - clamp01(kappa));
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
        return annualSeaLevelTemp(lat, k, sstAnom(x, z))
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
        return -K_P * CHI * (tSfc + GAMMA * h - tRef)
             + cellPressure(lat, k, theta);
    }
}