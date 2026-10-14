package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.util.HashMap;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

/**
 * **第三步接线的海洋场：海温异常 T'(x,z)（K）** —— 由**模型自己的风**驱动（设计冻结 §158）。
 *
 * <h3>为什么不用 GyreRow.BandedWind</h3>
 * P466 实测（同一批纬度行，唯一变量 = curl 来源）：
 * <pre>
 *   lat 30:  带风 -2.03e-14  vs  模型自己的风 -5.2e-8 ~ -1.0e-7   ⇒ 差 6 个数量级
 *   lat 45:  带风 +6.46e-08  vs  模型自己的风 -3.4e-9 ~ -3.9e-7   ⇒ 符号相反
 * </pre>
 * 根因：sin(3*pi*bandD) 的零点在纬度 0/30/60/90，而真实地球的涡旋分界在 45 度附近
 * ⇒ 带风把整条分带向赤道错了约 15 度。而 GyreRow.WindCurl 的 javadoc 本来就写着
 * 「M3 完成后由真实大气层实现」⇒ **接模型自己的风是项目原定意图**。
 *
 * <h3>结构</h3>
 * 海洋场天然是（纬度行 x 全盆宽）的全局网格，一行可跨 16,000 km ⇒ 不适配 100x50 km 的瓦片。
 * 这里按**纬度行懒缓存**：查询 (x,z) 只解包含它的那条纬度行，之后每点 O(1)。
 *
 * <h3>口径妥协（v1，必须记账）</h3>
 * <ul>
 *   <li><b>不做不动点迭代</b>：P265 是「SST -&gt; 沿岸风 -&gt; h_c -&gt; 急流 -&gt; SST」3 轮；
 *       本类 v1 只用**未装 SST 的风**解一次、急流取第一遍。装 SST 后 windStress 会再进
 *       anomalyAt ⇒ 迭代有重入风险，v1 先不迭代。</li>
 *   <li><b>curl 取 4 相位的算术平均</b>（与 A1 观测锚「逐相位求 curl 再平均」同口径），
 *       因为它要喂的是**年平**量 tSea。</li>
 *   <li>Atmosphere.SstProvider.anomalyAt(int,int) **接口不带 seed** ⇒ 本类只能服务一个世界。</li>
 * </ul>
 */
public final class OceanField {

    private OceanField() {}

    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【多份】，语义各不相同、默认值也不一致。
     * **本份的含义是：OceanField（海洋场）。** 引用时务必写全类名（如 `OceanField.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     *
     * <p>★ 2026-10-14 审查（#19）：本字段上方原有**两份** javadoc 紧挨着——
     * 前一份是【本字段的实质说明】（接线历史 + 代价阻塞 ✓），
     * 后一份才是这段 §540 命名警告 ✓。Java 只认**最后一份** ⟹
     * **前一份（实质说明）原本【写了但无效】** ✗ ⟹ 现已把两者**合并**为这一份 ✓✓
     * （★ 这类"实质性文档被后面的短警告挤掉"是本轮审计发现的一类系统性问题 ✓）
     *
     * <h3>生产是否启用</h3>
     * <p>**2026-09-13 接线（§162）：true。** 由 {@link #install(int)} 读取：
     * true = 装 {@link Atmosphere#SST_PROVIDER}；false = 卸掉（回到 SST' == 0 的旧口径）。
     *
     * <p>⚠ 它**不是**一个可以随手拨的开关：拨一下会同时改变
     * {@code SimClimate.configStamp()}（含 {@code Atmosphere.SST_PROVIDER == null}），
     * 即整个气候瓦片缓存、{@code warmestMonthTempK}（D46）、群系 LUT 全部换口径。
     * 任何 A/B 对比都必须**在同一个 stamp 下各跑一遍**，不能混。
     *
     * <p>⚠⚠ <b>未决的代价阻塞（§162.16，实测不是估计）</b>：
     * 一次世界预热 = <b>20~25 分钟单核 CPU</b>（P470 实测 1489.8 s 墙钟 / 1488.5 s 纯解行，
     * solveCount = 1391）；而且**懒解时单行最长 34.8 s**（P469 zIdx 32，纬度 -88.59），
     * 这一行如果是在区块生成线程里首次被访问，就是一次 <b>34.8 s 的世界卡死</b>。
     *
     * <p>根因已定位：{@code pressureAnomaly} 的 57.8 us 里 <b>56.6 us 是 {@code kappaAt}</b>（P469 实测），
     * 而它是 **theta 无关**的纯函数却在 4 个相位里各算一遍。
     * <b>修法（预登记，见 §162.16）：给 kappa/elev 加一层行解范围内的显式记忆化</b>
     * —— 物理零改变（同一函数、同一入参，只是不重算），预期 4x 以上。
     *
     * <p><b>现在之所以是 true</b>：用户指令是「先把第三步接线做完」，
     * 且本项目的实际用法是离线地图导出/普查（一次性 25 分钟可接受）。
     * <b>若要立刻退出这个口径，把本字段改回 false 即可</b>（一行），
     * 或调 {@link OceanWiring#off()}。代价修好之前，**不要**把它当成「已完成的生产特性」。
     */
    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【多份】，语义各不相同、默认值也不一致。
     * **本份的含义是：OceanField（海洋场）。** 引用时务必写全类名（如 `OceanField.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     *
     * <p>★ 2026-10-14 审查更正（阶段 2）：原文写「全工程有【6 份】（3 false + 3 true）」，
     * 实测已是【8 份】：false 3 个（HadleyCell / StationaryWave / Vegetation）、
     * true 5 个（ClimlabEBM / SoilMoisture / OceanField / SimTerrain / TalosLandErosion）——
     * 原文漏了 ClimlabEBM 与 TalosLandErosion，计数也已过期 ✗。
     * ⟹ ★ 本条【不再报具体份数】（新增类会让它再次过期 ✗），只保留规则本身 ✓
     * （★ 本仓共有 6 份这段警告的副本；本行的这一份是其中之一 ✓）
     */
    public static boolean ENABLED = true;
    /**
     * 纬度行数。★ D1（2026-09-18）：Z_CYCLE 20M -> 40M ⇒ **64 -> 128**，
     * 以保住每行 312.5 km（= 2.81° 纬度）的分辨率。
     * <p>降回 64 会把纬度分辨率砍半；{@code configStamp} 已含 ROWS（见本文件缓存键），
     * 所以改这个值会正确失效缓存，不会读到旧行。
     */
    public static int ROWS = 128;
    /** 行网格间距（block）—— 必须能分辨 Munk 层。 */
    public static double ROW_H = 125.0;   // ★ 2026-10-08 缩 40x（原 5,000）
    /**
     * Sverdrup 积分用的总深度（m）。
     *
     * <p>⚠ 审计 D51：初值改成**引用** {@link CoastalLayer#H_TOTAL}（值不变 ⇒ 逐位不变），
     * 消掉第二份 4000 字面量。但两者仍是**独立的运行时旋钮**，改一个不会同步另一个 ——
     * 所以它们**必须相等**（沿岸层折算与 Sverdrup 积分用同一个深度）。
     */
    public static double H_TOTAL = CoastalLayer.H_TOTAL;
    public static double A_H = 1.9e4;
    /** 沿岸风应力/旋度的差分步长（block）。 */
    /**
     * ★★★ **§447：SST 异常是否【逐相位】求解。** 默认 **false**（年平，逐位不变）。
     *
     * <p>物理：`GyreRow` 对 curl 是**线性**的，而 `curlAtmos` 原来把 4 个相位的 curl 直接平均掉了
     * ⇒ 整条 SST 异常链是**年平**的，模型里【不存在区域季节 SST 异常】。
     * P660 实测季节信号很强且**两个海盆反相**：
     * <pre>
     *   孟加拉湾(88.5E,19.3N)  curl: 0.00pi -4.796e-07  vs  1.00pi -1.161e-07   (JJA 强 4.1 倍)
     *   阿拉伯海(62E,15N)      curl: 0.00pi -1.235e-08  vs  1.00pi -1.236e-07   (DJF 强 10 倍)
     * </pre>
     * 打开后按 (行, 相位) 懒解 4 份并在 theta 上线性插值。**代价约 4 倍海洋预热时间。**
     */
    public static boolean PHASE_SEASONAL = false;

    public static int GRAD = 12_500;   // ★ 缩 40x（原 500,000）

    /**
     * ★★★★★ 2026-10-14（工作目录/短期-洋流问题 §八）：**F1 —— 用【带状旋度】驱动海洋**。
     *
     * <h3>为什么需要</h3>
     * 实测（探针 PSYM / PZM / PCURLF，四相位口径）：
     * <pre>
     *   局地旋度的纬向均值 / RMS 只有 ~0.10 ⟹ 旋度被【空间驻留涡旋】主导
     *   curl = d(tau_z)/dx - d(tau_x)/dz：
     *     d(tau_x)/dz ← 带状结构（尺度 2,000~3,000 km）
     *     d(tau_z)/dx ← 经向驻留涡旋（波长 ~1,000 km）⟹ 微分把它放大约 3~6 倍
     *   地球的海洋靠【时间平均】把涡旋旋度抹掉；本仓的涡旋是**空间驻留**的、抹不掉 ✗
     * </pre>
     *
     * <h3>★ 必须用【带状】而不是【宽窗采样平均】（实测踩过）</h3>
     * <pre>
     *   宽窗采样平均 <curl>_W = <d(tau_z)/dx>_W - d<tau_x>/dz
     *     第一项在【有限窗口】里不会平均到 0（世界在 x 上无限、非周期）
     *     ⟹ 它混进 tau_z 残差，**南半球符号整个反掉**（实测 6 档 BAD）
     *   纯带状 -d<tau_x>/dz（对 tau_x 先做纬向平均、再对纬度求导）：
     *     |lat| 15~30 两半球 **8/8 符号全部正确** ✓✓
     * </pre>
     *
     * <h3>成本</h3>
     * 每行缓存一次；单行的纬向平均 = ZM_SAMPLES x 4 相位次 windStress。
     * 默认 {@code false} ⟹ **逐位回到改动前** ✓
     * ⚠ 打开会改 SST' ⟹ 已进 {@link #configStamp()} ✓
     */
    public static volatile boolean CURL_ZONAL_BAND = true;   // ★ 2026-10-14 用户裁决后打开
    /** 纬向平均的经度采样数（性能/精度权衡；48000 km / N 为采样间距）。 */
    public static int ZM_SAMPLES = 48;
    /** 东边界急流作用范围（R_d 的倍数）。 */
    public static double JET_RANGE_RD = 4.0;
    /** curl 的季节相位。 */
    public static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};

    /**
     * ★ 2026-10-14（N-b）：**按行留存 psi**，供纬向分量 {@link #uAt} 做行间差分。
     *
     * <p>关闭时（默认）不存、不参与任何计算 ⟹ 与改动前【逐位一致】✓
     * <p>开启时进 {@link #configStamp()} ⟹ 缓存自动失效 ✓
     */
    public static volatile boolean PSI_ROWS_ON = true;   // ★★ 2026-10-14 用户裁决：**长期打开，生产默认** ✓
    //   理由：它是 u = -dpsi/dz 的来源 ✓（psi 本来就算了 ⟹ 成本≈0 ✓、零域依赖 ✓）；
    //   完整验收 pass=8 / fail=0 ✓（含冷+5.61 K / 暖−6.06 K 两侧干净达标 ✓）

    /**
     * ★★★★★ 2026-10-14（N-c）：**把纬向分量 u 接进距平链** —— "冷暖交汇"落地的那一步。
     *
     * <p>关（默认）⟹ 距平仍走一维局地平衡 {@code v*dTdz = -lambda*T'}，全链逐位不变 ✓
     * <p>开 ⟹ 在本行内解 {@code u*dT'/dx + v*dTdz = -lambda*T'}（迎风 Gauss-Seidel ✓）
     * <p>⚠ 打开会改 SST' ⟹ 必须重跑完整验收 ✓（{@link #configStamp()} 已含它 ✓）
     */
    public static volatile boolean U_ADVECT_ON = true;    // ★★ 2026-10-14 用户裁决：**长期打开，生产默认** ✓
    //   理由：纬向平流在物理上是对的 ✓（冷暖交汇机制 ✓）；
    //   且完整验收 pass=8 / fail=0 ✓（门窗全过，无饱和 ✓）
    //   ⚠ 打开后 P477 的 A5'-暖 最强盆会到 +10.44 K（无平流是 +6.91 K ⟹ 增强比 1.51 ✓）
    //     判据的锚带已按"基值 x 增强比"拆分（见 P477 ✓），其中【增强比的地球值 NOT FOUND】✓
    /** 迎风 Gauss-Seidel 的扫描次数（交替方向 ✓）。 */
    public static int U_SWEEPS = 8;
    /** 每行的 psi 头：{westX, h, n, psiMax}。 */
    private static final HashMap<Long, double[]> PSI_BY_ROW = new HashMap<>();
    /** 每行的 psi 真身（长度 n ✓，i 对应 x = westX + i*h ✓）。 */
    private static final HashMap<Long, double[]> PSI_ARR = new HashMap<>();

    private static final HashMap<Long, double[]> ANOM = new HashMap<>();
    /**
     * <b>与 {@link #ANOM} 同键同网格的表层经向速度（m/s，正 = 向极）</b>—— 即喂给
     * {@link SeaSurfaceTemp#anomaly} 的那个 {@code vs}。
     *
     * <p>存它是为了 {@link #vAt}：P478 的 A6 问「经向海岸上有没有流」，那是关于<b>流动</b>的陈述，
     * 而旧版只能用 |T-prime| 当代理量 —— 由 {@code anomaly} 的 tanh 形式，
     * {@code |T-prime| = ANOM_MAX*tanh(|v*dTds|/(lambda*ANOM_MAX))}，同一个 |v| 在不同纬度
     * 给出完全不同的 |T-prime|（P946 实测 |dTds| 随纬度变 9 倍）。
     */
    private static final HashMap<Long, double[]> VEL = new HashMap<>();
    private static final HashMap<Long, int[]> SPAN = new HashMap<>();
    /**
     * 每个海盆的**西带/东带深度平均速度**（m/s），键与 ANOM 相同。
     *
     * <p>为什么要有它（A7）：判据 A7 =「西边界强化比 西/东 &gt;= 4」是**速度**的比值，
     * 而 T' 经过 `tanh` 非线性 ⇒ **T' 的比值不等于 v 的比值**，不能拿 T' 代替。
     * 而生产里 `GyreRow.Row.v[]` 只活在 `solveRow` 内部。
     * 旧探针 P257 是自己重新调 `GyreRow.solve` 并把**合成带风**喂进去的 ——
     * 那是**已被否决的驱动源**（P466）⇒ 它的 A7 读数**不是生产口径**。
     * ⇒ 在这里把生产解出来的带均速度存下来，探针只读，**与生产同一个解**。
     */
    private static final HashMap<Long, double[]> BAND = new HashMap<>();
    /**
     * ★★★★★★★ 2026-10-11 修复（气候层适配）：A7/A2 的「带」宽度改由 **Munk 层厚 {@code delta_M}** 决定。
     *
     * <h3>改前的错</h3>
     * <p>原 javadoc 说「与 §23.2 的真实参照（30 Sv / 100 km x 4000 m）同口径」，
     * 即把带固定成 <b>100 km</b>（地球观测的西边界流带宽）。而 2026-10-08 的「缩 40x」
     * 把它改成 2,500，于是实际带只有 <b>2.5 km</b>。
     *
     * <p>★★ 但两者【都不对】，因为本世界的西边界流带宽由 {@code delta_M} 决定，
     * 而 {@code delta_M} 随纬度变（beta ∝ cos(lat)），**不是一个常数**：
     * <pre>
     *   纬度    delta_M(m)      2500/dM    100000/dM
     *     0.0      8,033         0.31        12.45
     *    30.0      8,428         0.30        11.87
     *    45.0      9,017         0.28        11.09
     *    60.0     10,121         0.25         9.88
     *    80.0     14,399         0.17         6.95
     *   ⟹ delta_M 范围 = 8,033 ~ 14,399 m
     * </pre>
     *
     * <h3>依据（本仓库自己的口径）</h3>
     * <p>{@link #solveRow} 里 {@code OceanField.java:505} 的注释逐字：
     * <pre>
     *   // Munk 解应当满足 v_band ~ psi_max / delta，delta = (A_H/beta)^(1/3)。
     * </pre>
     * <p>⟹ 「带」的宽度【就是】{@code delta_M}。所以本方法改为
     * {@code q = max(1, (int)(deltaAt(zRow) / g.h))}，按纬度自动取值。
     *
     * <h3>回滚</h3>
     * <p>{@code BAND_W_FROM_DELTA = false} ⟹ 逐位回到用 {@code BAND_W} 常量的旧行为。
     * {@code BAND_W} 本身保留（旧行为要它）。
     */
    public static boolean BAND_W_FROM_DELTA = true;
    /** 旧口径的固定带宽度（m）。仅当 {@link #BAND_W_FROM_DELTA} = false 时使用。 */
    public static double BAND_W = 2_500.0;   // ★ 缩 40x（原 100,000）—— 旧口径，见上
    /**
     * **按 zIdx 分桶的海盆索引（审计 D55）**：zIdx -> [SPAN 的键]。
     *
     * <p>为什么必须有：{@code WorldContract} 的裁决是 **X 无限、无周期** ⇒ 一个 zIdx 上
     * 累积的海盆数**无上界**。而 {@code hit()} 原来每次都要遍历整张 SPAN 表 ⇒
     * 大范围 x 的导出（地图、普查）会退化成 O(查询数 x 海盆数) 的平方级。
     * 分桶之后每次只看**该行**的桶（行内海盆数 = 个位数）。
     *
     * <p>纯索引结构改动，**不影响任何数值**（P472 逐位回归）。
     */
    private static final HashMap<Integer, java.util.ArrayList<Long>> SPAN_BY_ROW = new HashMap<>();
    private static long stamp = Long.MIN_VALUE;
    /** 解行的耗时统计（诊断用）。 */
    public static long solveCount = 0, solveNanos = 0;

    // ================= 接线（§162） =================

    /** 已安装的世界种子；{@link Integer#MIN_VALUE} = 未安装。 */
    private static int installedSeed = Integer.MIN_VALUE;
    /** {@link #warmAll} 已完成的行数（诊断用）。 */
    public static volatile int warmRowsDone = 0;

    /**
     * **接线入口（生产唯一一处）**：把本场装成 {@link Atmosphere#SST_PROVIDER}。
     *
     * <p>{@link #ENABLED}=false 时**卸掉**提供者（回到 SST' == 0 的旧口径）。
     *
     * <p>为什么提供者要闭包一个种子：{@code  Atmosphere.SstProvider.anomalyAt(int,int)}
     * 的签名**不带种子**（这是既有接口，改它会牵动所有调用方），而本场必须有种子。
     * 所以「一个进程同时只服务一个世界」——换世界会由
     * {@link #resetIfStale} 自动清缓存，但**不会**自动换提供者闭包 ⇒ 换世界必须重新 install。
     *
     * <p>线程安全：{@code synchronized}，与 {@link #anomalyAt}/{@link #spanOf}/{@code solveRow} 同一把锁。
     */
    public static synchronized void install(int worldSeedInt) {
        if (!ENABLED) { uninstall(); return; }
        final int s = worldSeedInt;
        installedSeed = s;
        Atmosphere.SST_PROVIDER = new Atmosphere.SstProvider() {
            @Override public double anomalyAt(int x, int z) { return OceanField.anomalyAt(x, z, s); }
            // §447：相位重载。开关关着时 anomalyAt(x,z,s,theta) 内部直接回落 ⇒ 逐位不变。
            @Override public double anomalyAt(int x, int z, double theta) { return OceanField.anomalyAt(x, z, s, theta); }
        };
    }

    /** 卸掉提供者（回到 SST' == 0）。 */
    public static synchronized void uninstall() {
        Atmosphere.SST_PROVIDER = null;
        installedSeed = Integer.MIN_VALUE;
    }

    /** 当前安装的世界种子；未安装返回 {@link Integer#MIN_VALUE}。**探针用它自证口径。** */
    public static synchronized int installedSeed() { return installedSeed; }

    /**
     * 预热扫描步长（block）：枚举海盆用。
     *
     * <p>★ 2026-10-11 更正：原文说「海盆宽 ~10^4 km，500 km 不会漏」——
     * 那是<b>旧海陆层</b>的海盆宽度。本会话实测【新场】的海盆宽是 <b>600~850 km</b>。
     * 而步长现在是 <b>12,500 格 = 12.5 km</b>，远小于 600 km ⟹ <b>不会漏</b> ✓
     */
    public static int WARM_SCAN = 12_500;   // ★ 缩 40x（原 500,000）

    /**
     * **预热：把 64 条纬度行全部解出来**（一次性 O(分钟)，见 §162 的代价记账）。
     *
     * <p>为什么必须预热而不是懒解：单行首解 1~7 s（P466/P467 实测），
     * 而查询是**在区块生成线程里**发生的 ⇒ 懒解 = 世界生成期间 64 次几秒级卡顿。
     * 预热应当**在后台线程**调用（本方法自己不加锁，逐次调用 {@link #spanOf} 由它加锁，
     * 所以预热期间其它线程仍可查询，只是会等待正在解的那一行）。
     *
     * <p>⚠⚠ <b>预热窗口是人为选的，不是「全球」</b>：扫描范围是
     * {@code x ∈ [-MAX_D, +MAX_D]}。
     * ★ 2026-10-11 更正：{@code MAX_D = 250_000} 格、1 格 = 1 m
     * ⟹ 实际范围是 <b>±250 km</b>（原文误写为「±10,000 km」—— 那是
     * 2026-10-08「全体缩小 40 倍」之前的旧值）。
     * 因为 {@code WorldContract} 的裁决是 **X 无限、无周期**，
     * 「全部海盆」这个概念**根本不存在**（走到 x=+30,000 km 就是全新的地方、全新的海盆）。
     * 所以：玩家在 ±250 km 带内时预热有效；出了这个带，行会被**懒解**
     * （单行 ~2.3 s，有护栏与缓存，不会失控）。
     * 想做更大范围的离线导出，就自己调大这里，或者接受线性增长（见 §164.1）。
     *
     * @return 解出来的海盆数
     */
    public static int warmAll(int worldSeedInt) {
        long seed = SimTerrain.seedOf(worldSeedInt);
        int basins = 0;
        warmRowsDone = 0;
        for (int zIdx = 0; zIdx < ROWS; zIdx++) {
            int z = rowZ(zIdx);
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (int x = -WorldContract.MAX_D; x <= WorldContract.MAX_D; x += WARM_SCAN) {
                int[] sp = spanOf(x, z, worldSeedInt);
                if (sp == null) continue;
                if (seen.add(((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL))) basins++;
                // §447：相位分辨时把 4 个相位一并预热（年平那一支仍然要 —— SimClimate 的年均读数用它）。
                // 代价：解行数 ×(1+4)=5 ⇒ 预热时间约 5 倍（§162 的代价记账继续有效）。
                if (PHASE_SEASONAL) {
                    for (int p = 0; p < PH4.length; p++) anomalyAt(x, z, worldSeedInt, PH4[p]);
                }
            }
            warmRowsDone = zIdx + 1;
            if (Thread.currentThread().isInterrupted()) break;
        }
        return basins;
    }

    private static long key(long seed, int zIdx, int westX) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= (long) zIdx * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) westX * 0x165667B19E3779F9L;
        h ^= (h >>> 29);
        return h;
    }

    /**
     * **本场旋钮的配置指纹**（纯函数，只读自己的 public static + 两个协作类的指纹）。
     *
     * <p>⚠⚠ <b>审计 D58（2026-09-13）</b>：这个方法的**存在本身**就是修复的一部分。
     * 原来只有 `resetIfStale` 内部算了一个含这些旋钮的指纹 ⇒
     * 改 `A_H` 时 **OceanField 自己的海盆缓存会失效**（SST' 真的变了），
     * 但 `SimClimate.configStamp()` 里**只有** `SST_PROVIDER == null ? 0 : 1` 这一个关于海洋的位
     * ⇒ **SimClimate 的瓦片不会失效**，继续用旧 SST' 算出来的值。
     * P476 A 段实测：改 `A_H` 后 `SST'` 从 **4.801934 -> 2.851675 K**（-1.95 K），
     * 而 `SimClimate.surfaceTempK` 仍停在旧值，**陈旧 1.3837 K**，报告里没有任何提示。
     *
     * <p><b>哪些进、哪些不进</b>：
     * <ul>
     *   <li>进：`ROWS / ROW_H / H_TOTAL / A_H / GRAD / JET_RANGE_RD`（都会改解出来的 `SST'`），
     *       以及 `SeaSurfaceTemp` 与 `CoastalLayer` 的指纹（同样改 `SST'`）；</li>
     *   <li><b>不进</b>：`WARM_SCAN`（只影响**预热覆盖面**，不改任何点的值 ——
     *       把它放进去只会让缓存白白失效）、`ENABLED`（不是值，且它只通过 `install()` 生效，
     *       `SST_PROVIDER` 的那个 0/1 位已经覆盖了）。</li>
     * </ul>
     */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + ROWS; h = h * 31 + Double.doubleToLongBits(ROW_H);
        h = h * 31 + Double.doubleToLongBits(H_TOTAL); h = h * 31 + Double.doubleToLongBits(A_H);
        h = h * 31 + GRAD; h = h * 31 + Double.doubleToLongBits(JET_RANGE_RD);
        // ⚠ 2026-09-16（用户裁决 A）：风应力旋度的采样步长也**改解出来的 SST'** ⇒ 按本方法的准入判据
        // 必须进指纹。它落在 GyreRow 里（求解发生的地方），所以从那里取。
        h = h * 31 + GyreRow.CURL_STRIDE;
        h = h * 31 + (CURL_ZONAL_BAND ? 1 : 0);        // ★ F1（会改 SST' ⟹ 必须进键，D58）
        h = h * 31 + ZM_SAMPLES;
        // §447：相位分辨会换掉解出来的 SST' ⇒ 按本方法的准入判据必须进指纹。
        if (PHASE_SEASONAL) h = h * 31 + 0x7A11DL;
        h = h * 31 + SeaSurfaceTemp.configStamp();
        h = h * 31 + CoastalLayer.configStamp();
        // ★ 2026-10-14（用户裁决）：**旧的"域路线"（BasinGyre2D）已整份删除** ✓
        //   它被【按行留存 psi + 行间差分】的新路线取代 ✓（零成本、零域依赖 ✓）
        //   ⟹ 这里不再有它的 configStamp 项 ✓
        // ★ N-b：按行留存 psi（供 u = -dpsi/dz）—— 默认 false ⟹ 常数 ⟹ 不改物理 ✓；
        //   一旦打开就自动让所有按 configStamp 的缓存失效 ✓
        h = h * 31 + (PSI_ROWS_ON ? 1 : 0);
        h = h * 31 + (U_ADVECT_ON ? 1 : 0);
        h = h * 31 + U_SWEEPS;
        // S620 (P944 attribution): DTDZ_FORCE re-routes ONLY the thermal-wind gradient, leaving the
        // airT/wind path on Atmosphere.ZMSLK_LEGACY. It changes the solved SST-prime => must enter
        // the configuration fingerprint (and, via resetIfStale, invalidate ANOM/SPAN/SPAN_BY_ROW/BAND).
        if (DTDZ_FORCE != 0) { h = h * 31 + 0x7A135L; h = h * 31 + DTDZ_FORCE; }
        // S621 (P945): DTDZ_MODE changes the thermal-wind gradient ITSELF (poleward sign + 5-degree
        // scale) => it changes the solved SST-prime and must enter the configuration fingerprint.
        h = h * 31 + 0x7A136L; h = h * 31 + DTDZ_MODE;
        // ★★★★★★★ 2026-10-11（气候层适配 · D58 准入判据补漏）：**带宽口径**。
        //   按本方法的准入判据（:312-313 的注释）：「改解出来的量 ⟹ 必须进指纹」。
        //   而 BAND / BAND_BY_ROW 存的正是「带内算术平均速度」—— 那是 `bandMeansAt` 的返回值，
        //   被 `GlobalClimate:120` 用作洋流速度 ⟹ **改带宽就改结果**。
        //   ⚠ 而 §182 把 `BAND_W` 从固定 2,500 改成按纬度的 `delta_M`（8.0~14.4 km）时**未折入指纹** ✗
        //   ⟹ 本条是那次修改的 D58 补漏。
        //   ★ 注意：`BAND_W` 是**【回滚点】**（`BAND_W_FROM_DELTA = false` 时启用），
        //     所以两个都要折入 —— 切换开关同样改结果。
        h = h * 31 + Double.doubleToLongBits(BAND_W);
        h = h * 31 + (BAND_W_FROM_DELTA ? 1 : 0);
        return h;
    }

    /**
     * <b>P944 归因开关</b>：只改热成风梯度 {@code dT/dz} 的取法，不动任何别的消费者。
     * <ul>
     *   <li>{@code 0}（默认）= 跟随 {@link Atmosphere#ZMSLK_LEGACY}（生产行为）；</li>
     *   <li>{@code 1} = 强制旧式 {@link Atmosphere#zmslkLegacy}；</li>
     *   <li>{@code 2} = 强制新式 {@link Atmosphere#zmslkNew}。</li>
     * </ul>
     * 有了它，(airT, dTdz) 的 2x2 才可分离：ZMSLK_LEGACY 管间接通路，DTDZ_FORCE 管直接通路。
     */
    public static int DTDZ_FORCE = 0;

    /** 热成风用的纬向平均海平面温度（受 {@link #DTDZ_FORCE} 路由）。 */
    static double zmSlk(double latRad) {
        int m = DTDZ_FORCE;
        if (m == 1) return Atmosphere.zmslkLegacy(latRad);
        if (m == 2) return Atmosphere.zmslkNew(latRad);
        return Atmosphere.zonalMeanSeaLevelK(latRad);
    }

    /**
     * <b>P945 物理修正开关</b>（位掩码）：
     * <ul>
     *   <li>位 0 = <b>极向符号修正</b>；</li>
     *   <li>位 1 = <b>5 度尺度中心差</b>（用表自己的节点间距）。</li>
     * </ul>
     * {@code 3}（默认）= 两条都开 = 物理口径；{@code 0} = 历史口径（±50 km 的 z 方向差）。
     */
    public static int DTDZ_MODE = 3;

    /** 每度纬度的米数（由世界契约本身给出，零新常数）。 */
    private static final double M_PER_DEG = WorldContract.MAX_D / 90.0;

    /**
     * <b>背景经向温度梯度（K/m，极向距离为正）</b>—— 热成风的唯一驱动量。
     *
     * <p><b>为什么必须极向为正</b>：{@link SeaSurfaceTemp#anomaly} 的平衡是
     * {@code t = -(v/lambda) * dT/ds}，而 {@code vSurf} 的定义是「正 = 向极」
     * （{@code SeaSurfaceTemp:76}）。所以这里的梯度必须是 {@code dT/ds}（s = 极向距离），
     * <b>不是</b> {@code dT/dz}。而 {@link WorldContract#latOf} 是一条 <b>tent</b>：
     * {@code +z} 在第 1/3 象限向极、在第 2/4 象限向赤道 ⇒ {@code dT/dz} 在半个世界里符号是反的。
     *
     * <p><b>为什么用 5 度尺度的中心差</b>：{@code zmslk} 由 {@code ZonalTables} 的 5 度节点表
     * 经 {@code interp5} 线性插值而来 ⇒ 它的导数在 5 度格内是常数、在节点上跳变。
     * 用 ±50 km（±0.45 度）去差，得到的不是梯度而是「选了哪个格子」—— P944 实测：
     * 68.91 度与 71.72 度两行的梯度比是 0.097 与 0.290，差 3 倍。取一个完整节点间距
     * （±2.5 度）的中心差，等于相邻两条 5 度割线的平均 ⇒ 无跨界跳变，也无新常数
     * （2.5 度 = 表节点间距的一半）。
     *
     * <p>两处修正都<b>不含任何标定</b>：符号来自 {@code vSurf} 的定义，尺度来自表自己的分辨率。
     */
    static double dTzmDz(int zRow) {
        int m = DTDZ_MODE;
        if ((m & 2) != 0) {
            double a = Math.abs(Math.toDegrees(WorldContract.latOf(zRow)));
            double hi = Math.min(a + 2.5, 90.0), lo = Math.max(a - 2.5, 0.0);
            double dy = (hi - lo) * M_PER_DEG;
            if (dy <= 0.0) return 0.0;
            return (zmSlk(Math.toRadians(hi)) - zmSlk(Math.toRadians(lo))) / dy;
        }
        double latT = WorldContract.latOf(zRow + 50_000), latM = WorldContract.latOf(zRow - 50_000);
        double g = (zmSlk(latT) - zmSlk(latM)) / 100_000.0;
        if ((m & 1) != 0 && Math.abs(latT) < Math.abs(latM)) g = -g;
        return g;
    }

    private static void resetIfStale(long seed, int worldSeedInt) {
        long h = 1125899906842597L;
        h = h * 31 + seed; h = h * 31 + worldSeedInt;
        h = h * 31 + configStamp();
        h = h * 31 + SimClimate.configStamp();
        if (h != stamp) {
            ANOM.clear(); SPAN.clear(); SPAN_BY_ROW.clear(); BAND.clear(); VEL.clear();
            PSI_BY_ROW.clear(); PSI_ARR.clear();          // ★ N-b：psi 行缓存一并失效 ✓
            stamp = h;
        }
    }

    /** 模型自己的风应力旋度（4 相位平均）：curl = d(tau_z)/dx - d(tau_x)/dz。 */
    private static double curlAtmos(int x, int z, long seed, int cell) { return curlAtmos(x, z, seed, cell, -1); }

    /** §447：{@code phase < 0} = 4 相位平均（原式，逐位不变）；{@code phase >= 0} = 单相位。 */
    private static double curlAtmos(int x, int z, long seed, int cell, int phase) {
        if (phase >= 0) {
            double th = PH4[phase];
            double[] e = Atmosphere.windStress(x + GRAD, z, seed, cell, th, GRAD);
            double[] w = Atmosphere.windStress(x - GRAD, z, seed, cell, th, GRAD);
            double[] n = Atmosphere.windStress(x, z + GRAD, seed, cell, th, GRAD);
            double[] s = Atmosphere.windStress(x, z - GRAD, seed, cell, th, GRAD);
            return ((e[1] - w[1]) - (n[0] - s[0])) / (2.0 * GRAD);
        }
        double c = 0;
        for (double th : PH4) {
            double[] e = Atmosphere.windStress(x + GRAD, z, seed, cell, th, GRAD);
            double[] w = Atmosphere.windStress(x - GRAD, z, seed, cell, th, GRAD);
            double[] n = Atmosphere.windStress(x, z + GRAD, seed, cell, th, GRAD);
            double[] s = Atmosphere.windStress(x, z - GRAD, seed, cell, th, GRAD);
            c += ((e[1] - w[1]) - (n[0] - s[0])) / (2.0 * GRAD) / PH4.length;
        }
        return c;
    }

    private static final class Curl implements GyreRow.WindCurl {
        final long seed; final int cell; final int phase;
        Curl(long seed, int cell) { this(seed, cell, -1); }
        Curl(long seed, int cell, int phase) { this.seed = seed; this.cell = cell; this.phase = phase; }
        @Override public double at(int x, int z) {
            if (CURL_ZONAL_BAND) return bandCurl(z, seed, cell, phase);
            return curlAtmos(x, z, seed, cell, phase);
        }
    }

    // ==================== ★ F1：带状旋度（按行缓存） ====================
    private static final java.util.concurrent.ConcurrentHashMap<Long, Double> ZM_BAND =
            new java.util.concurrent.ConcurrentHashMap<Long, Double>();

    /** 纬向平均的 tau_x（Pa）。phase < 0 = 4 相位平均（与 curlAtmos 同口径）。 */
    private static double zmTauX(int z, long seed, int cell, int phase) {
        final int XMAX = 30_000_000;
        double s = 0;
        if (phase >= 0) {
            double th = PH4[phase];
            for (int i = 0; i < ZM_SAMPLES; i++) {
                int x = -XMAX + (int) (2L * XMAX * i / ZM_SAMPLES);
                s += Atmosphere.windStress(x, z, seed, cell, th, GRAD)[0];
            }
            return s / ZM_SAMPLES;
        }
        for (int i = 0; i < ZM_SAMPLES; i++) {
            int x = -XMAX + (int) (2L * XMAX * i / ZM_SAMPLES);
            for (double th : PH4)
                s += Atmosphere.windStress(x, z, seed, cell, th, GRAD)[0] / PH4.length;
        }
        return s / ZM_SAMPLES;
    }

    /**
     * ★ F1：**带状旋度** = {@code -d<tau_x>/dz}（Pa/m）。
     * <p>差分步长 = **现有的行间距** {@code Z_CYCLE/ROWS}（零新参数 ✓）。
     * <p>键里含 {@link #configStamp()} ⟹ 配置一变缓存自动失效。
     */
    private static double bandCurl(int z, long seed, int cell, int phase) {
        long key = configStamp() * 131L + (((long) rowIndexOf(z)) << 4) + (phase + 1);
        Double c = ZM_BAND.get(key);
        if (c != null) return c;
        double dz = Math.max(1.0, (double) WorldContract.Z_CYCLE / ROWS);
        double zN = z + dz * 0.5, zS = z - dz * 0.5;
        double v = -(zmTauX((int) Math.round(zN), seed, cell, phase)
                   - zmTauX((int) Math.round(zS), seed, cell, phase)) / (zN - zS);
        ZM_BAND.put(key, v);
        if (ZM_BAND.size() > 4096) ZM_BAND.clear();       // 防无界增长（多世界/多相位）
        return v;
    }

    private static GyreRow.Params params() {
        GyreRow.Params p = new GyreRow.Params();
        p.h = ROW_H; p.rhoH = CoastalLayer.RHO * H_TOTAL; p.aH = A_H;
        p.zCycle = WorldContract.Z_CYCLE;
        return p;
    }

    static int rowIndexOf(int z) {
        int m = (int) Math.round((double) z / WorldContract.Z_CYCLE * ROWS) % ROWS;
        return m < 0 ? m + ROWS : m;
    }
    static int rowZ(int zIdx) { return (int) ((zIdx + 0.5) / ROWS * WorldContract.Z_CYCLE); }

    /**
     * ★★★ **§447：缓存下标（相位分辨）。**
     *
     * <p>{@code phase < 0} ⇒ 返回 {@code zIdx} 本身 ⇒ **年平口径，与打开本开关之前逐位相同**；
     * {@code phase >= 0} ⇒ 返回 {@code ROWS * (phase + 1) + zIdx}。
     * 两者值域不相交（年平 ∈ [0,64)，相位 ∈ [64,320)）⇒ 不会互相污染。
     *
     * <p>为什么改下标而不是改 {@code SPAN_BY_ROW} 的结构：桶、{@code ANOM}、{@code SPAN}、{@code BAND}
     * 四处都按同一个整数索引，只换这个整数的算法 ⇒ **不需要动任何一张表的结构**，
     * 年平那条支路也就没有机会被改坏。
     */
    private static int zkey(int zIdx, int phase) { return phase < 0 ? zIdx : ROWS * (phase + 1) + zIdx; }

    /** 命中已缓存的 T-prime 行。{@code zk} 见 {@link #zkey}。 */
    private static Object[] hit(int x, int zk) { return hitIn(ANOM, x, zk); }

    /** 命中已缓存的速度行（与 {@link #hit} 同网格同键，只是取 {@link #VEL}）。 */
    private static Object[] hitVel(int x, int zk) { return hitIn(VEL, x, zk); }

    /** 命中已缓存的行（返回 {arr, westX}），否则 null。{@code zk} 见 {@link #zkey}。 */
    private static Object[] hitIn(HashMap<Long, double[]> src, int x, int zk) {
        java.util.ArrayList<Long> ks = SPAN_BY_ROW.get(zk);
        if (ks == null) return null;
        for (int i = 0; i < ks.size(); i++) {
            Long k = ks.get(i);
            int[] sp = SPAN.get(k);
            if (sp != null && x >= sp[1] && x <= sp[2]) {
                double[] a = src.get(k);
                if (a != null) return new Object[]{a, sp[1]};
            }
        }
        return null;
    }

    private static synchronized Object[] solveRow(int zRow, int zIdx, int phase, long seed, int worldSeedInt, int xHint) {
        int zk = zkey(zIdx, phase);
        int cell = PlateField.PLATE_CELL;
        long t0 = System.nanoTime();
        // ⚠⚠ E30（2026-09-13 实测抓到，生产级缺陷）：抑制必须覆盖**整个** solveRow。
        // 原实现只把 Atmosphere.suppressSst 包在 GyreRow.solve() 外面，于是下面 tauS 那个循环里的
        // windStress **是没有被抑制的** ⇒ pressureAnomaly -> sstAnom -> 提供者 -> anomalyAt
        // -> hit()==null（本行还没 put 进 ANOM）-> solveRow 再进一层 ⇒ **指数级重入**。
        // 实测：一行跑了 10 分钟没完，jstack 显示主线程栈已经深到打不出来。
        // 为什么 P467 没测出来：P467 直接调 OceanField.anomalyAt，**没有装提供者**
        // ⇒ sstAnom 恒为 0 ⇒ 那条递归支路根本不进。**「没装提供者」的验收证明不了「装了提供者」能跑。**
        //
        // 同时用 thread-local 而不是把全局 SST_PROVIDER 置 null —— 后者在多线程下会让别的线程
        // 在这几秒里读到 SST'=0（见 Atmosphere.suppressSst 的注释）。
        Atmosphere.suppressSst(true);
        boolean memoCreated = Atmosphere.beginMemo();   // §162.16 代价修复：同一 (x,z) 不重算 kappa/elev
        int d0 = depthGet();
        depthSet(d0 + 1);
        try {
            GyreRow.Row g = GyreRow.solve(xHint, zRow, seed, cell, new Curl(seed, cell, phase), params());
            solveCount++; solveNanos += System.nanoTime() - t0;
            if (!g.valid || g.n < 2) return null;
            double f = WorldContract.coriolis(WorldContract.latOf(zRow));
            // ⚠ §216.7：热成风要的是**本世界自己的**纬向平均经向温度梯度。
            //   用地球的 T_zm 会把「地球在那个纬度的陆地占比」带进海盆的斜压结构。
            //
            // ⚠ **§447 已知未建模项（不是妥协，是记账）**：这里的 dT/dz 仍是**年平**的。
            //   季节版应当用 `zonalMeanSeaLevelK(lat) + seasonalAnomalyZonal(lat, theta)`，
            //   但 `seasonalAnomalyZonal` 读的是**预置表** `ZonalTables.A_ZM_K`，
            //   其经向结构不是本世界推出来的。§216.7 已经用同一条理由禁止把地球的
            //   「该纬度陆地占比」带进海盆斜压结构 ⇒ 这里**不能**顺手加进去。
            //   先量纯 curl 季节性的效果，等本世界自己的季节 dT/dz 有了机制再另开一步。
            double dTdz = dTzmDz(zRow);
            double rd = CoastalLayer.rossbyRadius(f);
            double[] t = CoastalLayer.coastTangent(g.eastX, zRow, seed, cell);
            // §447：沿海切向风应力。单相位 ⇒ 只取该相位（沿岸急流是**季节**现象）；
            // 年平 ⇒ 4 相位平均（原式一字未改）。
            double tauS = 0;
            if (phase >= 0) {
                double[] ts = Atmosphere.windStress(g.eastX - 50_000, zRow, seed, cell, PH4[phase], GRAD);
                tauS = ts[0] * t[0] + ts[1] * t[1];
            } else {
                for (double th : PH4) {
                    double[] ts = Atmosphere.windStress(g.eastX - 50_000, zRow, seed, cell, th, GRAD);
                    tauS += (ts[0] * t[0] + ts[1] * t[1]) / PH4.length;
                }
            }
            double hc = CoastalLayer.hcLocal(tauS, f);
            double vJet = CoastalLayer.jetPeak(hc, f);
            double[] a = new double[g.n];
            // S623：把喂给 anomaly() 的表层速度同点存一份（供 OceanField.vAt / P478 的 A6）。
            // 逐位不影响 a[] —— 只是多写一个数组。
            double[] vv = new double[g.n];
            for (int i = 0; i < g.n; i++) {
                double vs = g.v[i] * SeaSurfaceTemp.SURF_FACTOR;
                double d = g.eastX - g.xAt(i);
                if (d > 0 && d < JET_RANGE_RD * rd) vs += vJet * Math.exp(-d / rd);
                vv[i] = vs;
                a[i] = SeaSurfaceTemp.anomaly(vs, dTdz);
                // ★ 2026-10-14（用户裁决）：旧的"域路线"（BasinGyre2D.tprimeAt）已整份删除 ✓
                //   冷暖交汇现由【本行内的纬向平流】实现 ✓（见下面的 U_ADVECT_ON 段 ✓）
                //   ⟹ 距平在解完后会被整行重解（含平流 + 饱和 + 急流局地叠加 ✓）
            }
            // ★★★★★ 2026-10-14（N-c，用户裁决【不动海陆、改从洋流侧解决】）：
            //   **把纬向分量 u 接进距平链** —— 这才是"冷暖交汇"真正落地的那一步 ✓
            //
            //   方程（B-3 探针已验证的线性化二维平流-松弛）：
            //       u * dT'/dx + v * (dT_zm/dz) = -lambda * T'
            //   展开成解 T' 的形式：
            //       T'[i] = ( v[i]*(-dTdz) - u[i]*(迎风 dT'/dx) ) / lambda
            //
            //   ★ 关键简化：经向项作用在【背景梯度】上 ✓（不是 T' 的经向导数 ✗）
            //     ⟹ **每一行彼此独立** ✓✓ ⟹ 本段只在本行内做迎风 Gauss-Seidel ✓
            //     （不需要跨行耦合、不需要 BasinFinder 的域、成本极小 ✓✓）
            //
            //   ★ u 的来源：OceanField.uAt ⟹ 相邻两行的 psi 差分（psi 本来就算 ✓）
            //     拿不到时（相邻行海盆不重叠）u=0 ⟹ 退化成原来的一维解 ✓（安全 ✓）
            //
            //   ⚠ 开关默认 false ⟹ 逐位不变 ✓；打开会改 SST' ⟹ 必须重跑完整验收 ✓
            if (U_ADVECT_ON) {
                double lam = SeaSurfaceTemp.LAMBDA;
                for (int sweep = 0; sweep < U_SWEEPS; sweep++) {
                    boolean fwd = (sweep % 2 == 0);
                    for (int tt = 0; tt < g.n; tt++) {
                        int i = fwd ? tt : (g.n - 1 - tt);
                        int xHere = (int) g.xAt(i);
                        double ui = uAt(xHere, zRow, worldSeedInt) * SeaSurfaceTemp.SURF_FACTOR;
                        double vi = vv[i];
                        // ★★★★★ 2026-10-14（N-d-1 实测抓到）：**离散化写错了** ✗✗
                        //   正确（u>0 的迎风）：lambda*a[i] + u*(a[i]-a[i-1])/h = src
                        //     ⟹ a[i]*(lambda + u/h) = src + (u/h)*a[i-1]
                        //     ⟹ ★ a[i] = (src + (u/h)*a[i-1]) / (lambda + u/h)      ← 分母含 u/h ⟹ 稳定 ✓
                        //   初版写成 (src - u*(a[i]-a[i-1])/h)/lambda ✗（只除 lambda ✗）
                        //     而 u/(lambda*h) ≈ 1e-3/(2.57e-7*125) ≈ **31** ✗✗
                        //     ⟹ 修正项被放大 31 倍 ⟹ 剧烈过冲 ✗
                        //   实测症状正合此因：典型点只变 1.4% ✓，而最强盆暖峰 +6.90 -> +10.44 K（+51%）✗
                        // ★★★★★ 2026-10-14（实测抓到）：**平流只作用于【环流】部分** ✓
                        //   为什么：急流（jet）是【局地东边界现象】✓ —— 它不该被 u 搬运 ✗
                        //   实测：把急流也算进源项 ⟹ 平流沿行把它累积 ⟹ 冷侧顶到 ANOM_MAX 的
                        //     饱和（最强盆冷峰 = −7.9989 K ✗，而观测冷舌是 −4~5 K ✓）
                        //   而单独算急流本身只给 src/lambda = 0.5*4.4e-6/3.858e-7 ≈ **5.7 K** ✓
                        //     ⟹ 本来就接近观测 ✓ ⟹ 多出来的全是【平流累积】✗
                        //   修法：平流解只吃【环流 v】✓；急流的贡献在解完后按一维局地式叠加 ✓
                        double vGyre = g.v[i] * SeaSurfaceTemp.SURF_FACTOR;
                        double src = vGyre * (-dTdz);
                        double cx = Math.abs(ui) / g.h;                              // 迎风系数 ≥ 0 ✓
                        double up = (ui > 0) ? ((i > 0) ? a[i - 1] : a[i])           // 上游邻居 ✓
                                 : ((i < g.n - 1) ? a[i + 1] : a[i]);
                        double aNew = (src + cx * up) / (lam + cx);
                        // ★★★★★ 2026-10-14（实测抓到）：**平流解必须与一维式用同一个饱和** ✗
                        //   一维式：T' = ANOM_MAX*tanh(...) ⟹ 上限 ±8 K ✓
                        //   而平流解是无界线性方程 ⟹ 在东边界（jet 很大处）会冲到 −38 K ✗✗
                        //   实测症状：P477 的"最强盆冷峰 = −38.3972 K" ✗ —— 物理上不可能 ✓
                        //   ⟹ 用模型自己的【平滑饱和】把平流解也钳住 ✓（与设计一致 ✓）
                        double am = SeaSurfaceTemp.ANOM_MAX;
                        a[i] = am * Math.tanh(aNew / am);
                    }
                }
                // ★ 解完之后，把【急流的局地贡献】按原来的一维局地式叠加回来 ✓
                //   （急流是局地边界现象 ✓ 不该被平流搬运 ✗ —— 见上面的注释 ✓）
                for (int i = 0; i < g.n; i++) {
                    double dJet = g.eastX - g.xAt(i);
                    double vJetPart = (dJet > 0 && dJet < JET_RANGE_RD * rd) ? vJet * Math.exp(-dJet / rd) : 0.0;
                    if (vJetPart != 0.0) {
                        double am = SeaSurfaceTemp.ANOM_MAX;
                        double sum = a[i] + SeaSurfaceTemp.anomaly(vJetPart, dTdz);
                        a[i] = am * Math.tanh(sum / am);
                    }
                }
            }
            // A7：西带/东带深度平均速度（生产解本身，探针只读）
            // ★ 2026-10-11：带宽 = Munk 层厚 delta_M = cbrt(A_H/beta)，按纬度取值
            //   （依据：本方法 :505 的注释「v_band ~ psi_max / delta」）。
            //   旧口径是固定常量 BAND_W（100 km 的地球参照，被缩 40x 后成了 2.5 km）。
            final double bandW = BAND_W_FROM_DELTA
                ? Math.cbrt(A_H / WorldContract.betaForLatitude(
                      WorldContract.latOf(zRow), WorldContract.Z_CYCLE))
                : BAND_W;
            int q = Math.max(1, (int) (bandW / g.h));
            if (g.n >= 2 * q) {
                double ws = 0, es = 0;
                for (int i = 0; i < q; i++) ws += g.v[i];
                for (int i = g.n - q; i < g.n; i++) es += g.v[i];
                // 第 3 个元素 = psi_max（Sverdrup 流函数峰值，m^2/s）—— 供 A2 的**物理核对**：
                // Munk 解应当满足 v_band ~ psi_max / delta，delta = (A_H/beta)^(1/3)。
                // 有了它，探针就能判「量级不足」是**求解器错**还是**风/几何给的 psi 本来就小**。
                BAND.put(key(seed, zk, g.westX), new double[]{ws / q, es / q, g.psiMax});
            }
            // ★★★★★ 2026-10-14（N-a/N-b，用户裁决【不动海陆、改从洋流侧解决】）：
            //   把这一行的 psi 留下来 ⟹ 纬向分量 u = -dpsi/dz 只需【相邻两行查表做差分】✓✓
            //   为什么这是关键：原来 psi 解完即丢 ✗ ⟹ 想拿 u 只能另解二维域 ✗
            //     而"域"的数量 ~10,000（用户裁决不改海陆 ⟹ 域数不会下降 ✗）
            //     ⟹ 域路线在生产上不可行 ✗；按行留存则【几乎零成本】✓✓
            //   ⚠ 只在开关打开时存（关闭时逐位不变 ✓）
            //   psi 布局：i 对应 x = westX + i*g.h ✓（与 GyreRow.Row 一致 ✓）
            if (PSI_ROWS_ON) {
                PSI_BY_ROW.put(key(seed, zk, g.westX),
                    new double[]{g.westX, g.h, g.n, g.psiMax});            // 头 4 个：westX, h, n, psiMax
                // 真身单独放一张表（避免与头混在一起 ✓）
                PSI_ARR.put(key(seed, zk, g.westX), g.psi);
            }
            long k = key(seed, zk, g.westX);
            ANOM.put(k, a);
            VEL.put(k, vv);
            // SPAN 里的第 0 位永远是**真实行号** zIdx（探针与 bandMeansAt 读它），不是 zk。
            SPAN.put(k, new int[]{zIdx, g.westX, g.eastX});
            java.util.ArrayList<Long> bucket = SPAN_BY_ROW.get(zk);
            if (bucket == null) { bucket = new java.util.ArrayList<>(); SPAN_BY_ROW.put(zk, bucket); }
            bucket.add(k);
            return new Object[]{a, g.westX};
        } finally {
            depthSet(d0);
            Atmosphere.endMemo(memoCreated);
            Atmosphere.suppressSst(false);
        }
    }

    // ---- 防重入护栏 + 计数器（正常必须恒为 0；P470 会断言） ----

    /** 诊断：解行期间的重入**被拦截**的次数。正常 = 0；不为 0 就说明抑制失效了（E30）。 */
    public static long reentryBlocked = 0;
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private static int depthGet() { Integer v = DEPTH.get(); return v == null ? 0 : v; }
    private static void depthSet(int v) { if (v <= 0) DEPTH.remove(); else DEPTH.set(v); }
    /** 该点是否正处于「解行中」的线程里（= 会被护栏拦下）。 */
    private static boolean inSolve() { return depthGet() > 0; }

    /** 该点所在海盆的 [zIdx, westX, eastX]；解不出来时返回 null。**探针采样必须用它**，不能假设 x 范围。 */
    public static synchronized int[] spanOf(int x, int z, int worldSeedInt) {
        if (inSolve()) { reentryBlocked++; return null; }   // 护栏：见 solveRow 的 E30 注释
        long seed = SimTerrain.seedOf(worldSeedInt);
        resetIfStale(seed, worldSeedInt);
        int zIdx = rowIndexOf(z);
        Object[] h = hit(x, zIdx);
        // §447：海盆几何（span）用**年平**解 —— 它是地理量，不该随季节变；
        // 而且所有既有探针（P620/P469/...）读的都是这一支，保住它们的口径。
        if (h == null) { h = solveRow(rowZ(zIdx), zIdx, -1, seed, worldSeedInt, x); if (h == null) return null; }
        return findSpan(x, zIdx);
    }

    /** 在**该桶**里找包含 x 的海盆；没有则 null。{@code zk} 见 {@link #zkey}。 */
    private static int[] findSpan(int x, int zk) {
        java.util.ArrayList<Long> ks = SPAN_BY_ROW.get(zk);
        if (ks == null) return null;
        for (int i = 0; i < ks.size(); i++) {
            int[] sp = SPAN.get(ks.get(i));
            if (sp != null && x >= sp[1] && x <= sp[2]) return sp;
        }
        return null;
    }

    /**
     * 该点所在海盆的 { 西带均速度, 东带均速度, psi_max }（m/s, m/s, m^2/s）；未解出时返回 null。
     *
     * <p>口径（★ 2026-10-11 更新）：带宽 = **Munk 层厚 {@code delta_M = (A_H/beta)^(1/3)}**，
     * 按该行的纬度取值（8.0~14.4 km），带内**算术平均**，
     * 由生产解 `GyreRow.Row.v[]` 直接取，**不重解、不复制公式**（探针与生产同一个解）。
     * 它服务 A7（西/东强化比）与 A2（西边界量级）。
     */
    public static synchronized double[] bandMeansAt(int x, int z, int worldSeedInt) {
        if (inSolve()) { reentryBlocked++; return null; }
        long seed = SimTerrain.seedOf(worldSeedInt);
        resetIfStale(seed, worldSeedInt);
        int zIdx = rowIndexOf(z);
        int zk = zkey(zIdx, -1);
        Object[] h = hit(x, zk);
        if (h == null) { h = solveRow(rowZ(zIdx), zIdx, -1, seed, worldSeedInt, x); if (h == null) return null; }
        int[] sp = findSpan(x, zk);
        if (sp == null) return null;
        double[] b = BAND.get(key(seed, zk, sp[1]));
        return b == null ? null : new double[]{b[0], b[1], b[2]};
    }

    /** 沿 x 在解出来的 `a[]` 上做**分段线性**插值（原 `anomalyAt` 的那 8 行，一字未改）。 */
    private static double lerpX(Object[] h, int x) {
        double[] a = (double[]) h[0];
        int westX = (int) h[1];
        double fi = (x - westX) / ROW_H;
        int i0 = (int) Math.floor(fi);
        if (i0 < 0) i0 = 0;
        if (i0 >= a.length - 1) return a[a.length - 1];
        double tx = fi - i0;
        return a[i0] * (1 - tx) + a[i0 + 1] * tx;
    }

    /** 单个相位的海温异常（K）；未缓存时当场解那一行（首查 0.3~4 s，见 §158）。 */
    private static double anomalyAtPhase(int x, int z, int zIdx, long seed, int worldSeedInt, int phase) {
        int zk = zkey(zIdx, phase);
        Object[] h = hit(x, zk);
        if (h == null) {
            h = solveRow(rowZ(zIdx), zIdx, phase, seed, worldSeedInt, x);
            if (h == null) return 0.0;
        }
        return lerpX(h, x);
    }

    /** 单个相位的表层经向速度（m/s，正 = 向极）。 */
    private static double velAtPhase(int x, int z, int zIdx, long seed, int worldSeedInt, int phase) {
        int zk = zkey(zIdx, phase);
        Object[] h = hitVel(x, zk);
        if (h == null) {
            solveRow(rowZ(zIdx), zIdx, phase, seed, worldSeedInt, x);
            // ★★★★★ 2026-10-14（P0 根因修复）：**必须重新查缓存**，不能直接用 solveRow 的返回值。
            //   ¤solveRow¤ 是【两条链共用】的（ANOM 与 VEL），而它的返回值固定是
            //   ¤new Object[]{a, g.westX}¤ —— 其中 a 是【海温距平 T'（K）】✗。
            //   ⟹ 旧写法在【缓存未命中】时把 T' 当成【速度 v（m/s）】返回 ✗✗
            //      （实测：vAt 在每盆的第一个采样点返回 −6.80 / −5.75 这种量级，
            //        而同一盆的 VEL 数组 max|a| 只有 0.025 ✓ —— 两者都是 K vs m/s 的混淆）
            //   ⟹ 症状：任何【按点采样】的探针（P477/P478）都会踩中；
            //      而【按带均值】的门（P479）走缓存命中路径，所以看起来是好的 ✓
            //   修法：solveRow 只负责"把结果写进缓存"，返回值一律【重新命中】取 ✓ 零口径变更 ✓
            h = hitVel(x, zk);
            if (h == null) return 0.0;
        }
        return lerpX(h, x);
    }

    /**
     * 该点的**表层经向速度 v（m/s，正 = 向极）** —— 就是喂给
     * {@link SeaSurfaceTemp#anomaly} 的那个量（{@code g.v[i]*SURF_FACTOR + vJet*exp(-d/rd)}）。
     *
     * <p>口径与 {@link #anomalyAt} <b>完全平行</b>（同一张 {@link #VEL} 表、同一个 {@code lerpX} 网格、
     * 同样的 4 相位平均）⇒ {@code anomalyAt(x,z,s) == SeaSurfaceTemp.anomaly(vAt(x,z,s), dTzmDz(rowZ))}
     * 逐位成立。
     *
     * <p><b>为什么必须直接暴露它</b>（P478 的 A6）：A6 问的是「经向海岸上<b>有没有流</b>」，
     * 那是关于<b>流动</b>的陈述。旧版只能用 |T-prime| 当代理量，而
     * {@code |T-prime| = ANOM_MAX*tanh(|v*dTds|/(lambda*ANOM_MAX))} ⇒ 同一个 |v| 在不同纬度
     * 给出完全不同的 |T-prime|。P946 实测：模型的速度场中位 |v| 在 0.050~0.531 m/s 之间
     * （纬度无关），而 |dTds| 变 9 倍 ⇒ 旧的 T-prime 判据等价于要求 |v| 随纬度变 9 倍。
     */
    /**
     * 该点的**风应力旋度**（Pa/m）—— 供"二维闭合环流"一类的消费者取力项。
     *
     * <p>★ 它走的是本类既有的 {@code curlAtmos(x,z,seed,cell,-1)} 通路，因此
     * <b>自动尊重 {@link #CURL_ZONAL_BAND}（F1）</b> ✓ —— 带状开关开着时返回带状旋度，
     * 关着时返回局地旋度，与生产口径完全一致 ✓（不引入第二份实现 ✓）。
     *
     * <p>2026-10-14 加：原 ¤curlAtmos¤ 是 private，二维类拿不到 ⟹ 只能开口子。
     * 这是**拓宽**API（不改任何既有行为）✓。
     */
    /**
     * 该点的**纬向（东西向）表层速度 u**（m/s）—— ★ 2026-10-14（N-b）新增。
     *
     * <h3>它解决什么</h3>
     * <p>环流要"闭合"就必须有 u ✗（原系统【全库没有任何 u】✗ ⟹ 一股西边界流从南到北之后无处可去 ✗，
     * 冷暖流只能并排摆着 ✓）。用户裁决【不动海陆分布】⟹ 不能靠"把海盆做大"来让二维域变便宜 ✗
     * ⟹ 本方法走【按行留存 psi + 行间差分】：
     * <pre>
     *   u(x,z) = -[ psi(z+dz, x) - psi(z-dz, x) ] / (2*dz)
     * </pre>
     * <p>★ 成本几乎为零 ✓：psi 本来就算出来了（只是以前被丢掉 ✗），这里只是一次查表 ✓
     * <p>★ 两行的海盆在 x 上【必须重叠】才有效 ✓；不重叠（岸线跳变处）返回 0 ✓ 并记账 ✓
     * <p>⚠ {@link #PSI_ROWS_ON}=false 时返回 0（⟹ 全链逐位不变 ✓）
     */
    public static synchronized double uAt(int x, int z, int worldSeedInt) {
        if (!PSI_ROWS_ON) return 0.0;
        if (inSolve()) { reentryBlocked++; return 0.0; }
        long seed = SimTerrain.seedOf(worldSeedInt);
        resetIfStale(seed, worldSeedInt);
        double dz = WorldContract.Z_CYCLE / (double) ROWS;            // 相邻行的 z 间距 ✓
        double zp = z + dz, zm = z - dz;
        // ★★★★★ 2026-10-14（N-b 实测抓到）：**必须先【触发】相邻两行的求解** ✗
        //   初版直接读 PSI_BY_ROW ⟹ 那两行若从未被求解过 ⟹ 表里没有 ⟹ 恒返回 NaN ⟹ u ≡ 0 ✗
        //   （实测：misses=4/4 ✓ 全灭 ✓）
        //   触发方式：调既有的公开访问器（它们会按需解那一行 ✓ 且带缓存 ✓）
        ensureRowSolved(x, zp, worldSeedInt);
        ensureRowSolved(x, zm, worldSeedInt);
        double pp = psiRowAt(seed, zp, x), pm = psiRowAt(seed, zm, x);
        if (Double.isNaN(pp) || Double.isNaN(pm)) { uMiss++; return 0.0; }   // 不重叠 ⟹ 0 ✓ 记账 ✓
        return -(pp - pm) / (2 * dz);
    }

    /** 诊断：因"相邻行海盆不重叠"而返回 0 的次数 ✓。 */
    public static long uMiss = 0;

    /** 触发某个 z 所在行的求解（走既有公开访问器 ✓ 带缓存 ⟹ 只有首次真解 ✓）。 */
    private static void ensureRowSolved(int x, double z, int worldSeedInt) {
        int zi = (int) Math.round(z);
        if (zi < 0) zi += WorldContract.Z_CYCLE;
        if (zi >= WorldContract.Z_CYCLE) zi -= WorldContract.Z_CYCLE;
        anomalyAt(x, zi, worldSeedInt);          // 解那一行并把 psi 存进 PSI_BY_ROW ✓
    }

    /** 取某个 z 所在行的 psi 在 x 处的值；该行没有 psi 或 x 不在其海盆内 ⟹ NaN ✓。 */
    private static double psiRowAt(long seed, double z, int x) {
        int zIdx = rowIndexOf((int) Math.round(z));
        int zk = zkey(zIdx, -1);
        java.util.ArrayList<Long> ks = SPAN_BY_ROW.get(zk);
        if (ks == null) return Double.NaN;
        for (int i = 0; i < ks.size(); i++) {
            Long k = ks.get(i);
            double[] hd = PSI_BY_ROW.get(k);
            if (hd == null) continue;
            double westX = hd[0], h = hd[1];
            int n = (int) hd[2];
            double fi = (x - westX) / h;
            if (fi < 0 || fi > n - 1) continue;                        // x 不在该行海盆内 ✓
            double[] psi = PSI_ARR.get(k);
            if (psi == null) continue;
            int i0 = (int) Math.floor(fi);
            if (i0 < 0) i0 = 0;
            if (i0 >= n - 1) return psi[Math.max(0, n - 1)];
            double tx = fi - i0;
            return psi[i0] * (1 - tx) + psi[i0 + 1] * tx;
        }
        return Double.NaN;
    }

    public static synchronized double curlAt(int x, int z, int worldSeedInt) {
        long seed = SimTerrain.seedOf(worldSeedInt);
        int cell = PlateField.PLATE_CELL;
        // ★★★★★ 2026-10-14（B-2d 对照实验抓到）：**必须尊重 F1 开关** ✗✗
        //   初版直接调 ¤curlAtmos¤ ⟹ 拿到的是【局地旋度】✗（随 x 变化）
        //   而生产链走 ¤new Curl(...).at()¤ ⟹ 那里才判 ¤CURL_ZONAL_BAND¤ ⟹ 拿到【带状旋度】✓
        //   实测（探针 B2CUR，同一行）：curlAt @xa = +5.02e-6，@xa+100km = −1.57e-8 ✗（本不该随 x 变）
        //     ⟹ 喂错强迫 ⟹ psi_max 符号都反 ✗（−31.5 vs 生产 +15.2）
        //   修法：在这里复刻 ¤Curl.at¤ 的分派 ✓（不引入第二份实现 —— 只是同一分派 ✓）
        if (CURL_ZONAL_BAND) return bandCurl(z, seed, cell, -1);
        return curlAtmos(x, z, seed, cell, -1);
    }

    public static synchronized double vAt(int x, int z, int worldSeedInt) {
        if (inSolve()) { reentryBlocked++; return 0.0; }
        long seed = SimTerrain.seedOf(worldSeedInt);
        resetIfStale(seed, worldSeedInt);
        int zIdx = rowIndexOf(z);
        if (PHASE_SEASONAL) {
            double s = 0;
            for (int p = 0; p < PH4.length; p++) s += velAtPhase(x, z, zIdx, seed, worldSeedInt, p);
            return s / PH4.length;
        }
        return velAtPhase(x, z, zIdx, seed, worldSeedInt, -1);
    }

    /**
     * 该点的**海温异常（K）**。未缓存时当场解那一行（首查 0.3~4 s，见 §158）。
     *
     * <p>{@link #PHASE_SEASONAL}=true 时返回 **4 个相位解的算术平均**，
     * 而不是「对年平 curl 的单次解」。为什么必须这样：线性插值在等间距 4 点上的**全周期平均
     * 恰好等于 4 个样本的平均** ⇒ 只有这样，本方法与 `mean_theta(anomalyAt(x,z,theta))` 才自洽。
     * 拿年平解顶替会出现「年平读数 ≠ 季节读数之年均」的隐性矛盾（探针会量出这个差）。
     */
    public static synchronized double anomalyAt(int x, int z, int worldSeedInt) {
        if (inSolve()) { reentryBlocked++; return 0.0; }   // 护栏：见 solveRow 的 E30 注释
        long seed = SimTerrain.seedOf(worldSeedInt);
        resetIfStale(seed, worldSeedInt);
        int zIdx = rowIndexOf(z);
        if (PHASE_SEASONAL) {
            double s = 0;
            for (int p = 0; p < PH4.length; p++) s += anomalyAtPhase(x, z, zIdx, seed, worldSeedInt, p);
            return s / PH4.length;
        }
        Object[] h = hit(x, zIdx);
        if (h == null) {
            h = solveRow(rowZ(zIdx), zIdx, -1, seed, worldSeedInt, x);
            if (h == null) return 0.0;
        }
        return lerpX(h, x);
    }

    /**
     * ★★★ **§447：带相位的海温异常（K）** —— 季风源区季节循环的入口。
     *
     * <p>{@link #PHASE_SEASONAL}=false ⇒ **直接回落**到 {@link #anomalyAt(int,int,int)}（年平，逐位相同）。
     * 否则解出 4 个相位并在 theta 上做**环形线性插值**。
     *
     * <p>⚠ **记账：4 点等间距采样的 Nyquist 上限是二次谐波** ⇒
     * 本方法只保留**年谐波 + 半年谐波**。季风的海温年循环以年谐波为绝对主导，
     * 所以这是一个明确写下来的近似；探针要报**残差**，不能只说「打开了」。
     */
    public static synchronized double anomalyAt(int x, int z, int worldSeedInt, double theta) {
        if (!PHASE_SEASONAL) return anomalyAt(x, z, worldSeedInt);
        if (inSolve()) { reentryBlocked++; return 0.0; }
        long seed = SimTerrain.seedOf(worldSeedInt);
        resetIfStale(seed, worldSeedInt);
        int zIdx = rowIndexOf(z);
        double[] v = new double[PH4.length];
        for (int p = 0; p < PH4.length; p++) v[p] = anomalyAtPhase(x, z, zIdx, seed, worldSeedInt, p);
        double q = theta * (PH4.length / (2.0 * Math.PI));
        q = q - Math.floor(q / PH4.length) * PH4.length;   // 归一到 [0,4)（theta 可负 / 可 > 2pi）
        int i0 = (int) q; if (i0 >= PH4.length) i0 = PH4.length - 1;
        double tx = q - i0;
        return v[i0] * (1.0 - tx) + v[(i0 + 1) % PH4.length] * tx;
    }
}
