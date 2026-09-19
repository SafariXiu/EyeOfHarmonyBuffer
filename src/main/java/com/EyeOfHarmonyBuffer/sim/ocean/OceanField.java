package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.util.HashMap;

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
     * 生产是否启用。
     *
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
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【6 份】，语义各不相同、默认值也不一致
     * （4 个 false：HadleyCell/SoilMoisture/StationaryWave/Vegetation；2 个 true：OceanField/SimTerrain）。
     * **本份的含义是：OceanField（海洋场）。** 引用时务必写全类名（如 `OceanField.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
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
    public static double ROW_H = 5_000.0;
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

    public static int GRAD = 500_000;
    /** 东边界急流作用范围（R_d 的倍数）。 */
    public static double JET_RANGE_RD = 4.0;
    /** curl 的季节相位。 */
    public static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};

    private static final HashMap<Long, double[]> ANOM = new HashMap<>();
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
    /** A7/A2 口径的「带」宽度（m）：与 §23.2 的真实参照（30 Sv / 100 km x 4000 m）同口径。 */
    public static double BAND_W = 100_000.0;
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

    /** 预热扫描步长（block）：枚举海盆用。海盆宽 ~10^4 km，500 km 不会漏。 */
    public static int WARM_SCAN = 500_000;

    /**
     * **预热：把 64 条纬度行全部解出来**（一次性 O(分钟)，见 §162 的代价记账）。
     *
     * <p>为什么必须预热而不是懒解：单行首解 1~7 s（P466/P467 实测），
     * 而查询是**在区块生成线程里**发生的 ⇒ 懒解 = 世界生成期间 64 次几秒级卡顿。
     * 预热应当**在后台线程**调用（本方法自己不加锁，逐次调用 {@link #spanOf} 由它加锁，
     * 所以预热期间其它线程仍可查询，只是会等待正在解的那一行）。
     *
     * <p>⚠⚠ <b>预热窗口是人为选的，不是「全球」</b>：扫描范围是
     * {@code x ∈ [-MAX_D, +MAX_D]} = [-10,000, +10,000] km。
     * 因为 {@code WorldContract} 的裁决是 **X 无限、无周期**，
     * 「全部海盆」这个概念**根本不存在**（走到 x=+30,000 km 就是全新的地方、全新的海盆）。
     * 所以：玩家在 ±10,000 km 带内时预热有效；出了这个带，行会被**懒解**
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
        h = h * 31 + com.EyeOfHarmonyBuffer.sim.ocean.GyreRow.CURL_STRIDE;
        // §447：相位分辨会换掉解出来的 SST' ⇒ 按本方法的准入判据必须进指纹。
        if (PHASE_SEASONAL) h = h * 31 + 0x7A11DL;
        h = h * 31 + SeaSurfaceTemp.configStamp();
        h = h * 31 + CoastalLayer.configStamp();
        return h;
    }

    private static void resetIfStale(long seed, int worldSeedInt) {
        long h = 1125899906842597L;
        h = h * 31 + seed; h = h * 31 + worldSeedInt;
        h = h * 31 + configStamp();
        h = h * 31 + SimClimate.configStamp();
        if (h != stamp) { ANOM.clear(); SPAN.clear(); SPAN_BY_ROW.clear(); BAND.clear(); stamp = h; }
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
        @Override public double at(int x, int z) { return curlAtmos(x, z, seed, cell, phase); }
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

    /** 命中已缓存的行（返回 {arr, westX}），否则 null。{@code zk} 见 {@link #zkey}。 */
    private static Object[] hit(int x, int zk) {
        java.util.ArrayList<Long> ks = SPAN_BY_ROW.get(zk);
        if (ks == null) return null;
        for (int i = 0; i < ks.size(); i++) {
            Long k = ks.get(i);
            int[] sp = SPAN.get(k);
            if (sp != null && x >= sp[1] && x <= sp[2]) {
                double[] a = ANOM.get(k);
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
            double dTdz = (Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(zRow + 50_000))
                         - Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(zRow - 50_000))) / 100_000.0;
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
            for (int i = 0; i < g.n; i++) {
                double vs = g.v[i] * SeaSurfaceTemp.SURF_FACTOR;
                double d = g.eastX - g.xAt(i);
                if (d > 0 && d < JET_RANGE_RD * rd) vs += vJet * Math.exp(-d / rd);
                a[i] = SeaSurfaceTemp.anomaly(vs, dTdz);
            }
            // A7：西带/东带深度平均速度（生产解本身，探针只读）
            int q = Math.max(1, (int) (BAND_W / g.h));
            if (g.n >= 2 * q) {
                double ws = 0, es = 0;
                for (int i = 0; i < q; i++) ws += g.v[i];
                for (int i = g.n - q; i < g.n; i++) es += g.v[i];
                // 第 3 个元素 = psi_max（Sverdrup 流函数峰值，m^2/s）—— 供 A2 的**物理核对**：
                // Munk 解应当满足 v_band ~ psi_max / delta，delta = (A_H/beta)^(1/3)。
                // 有了它，探针就能判「量级不足」是**求解器错**还是**风/几何给的 psi 本来就小**。
                BAND.put(key(seed, zk, g.westX), new double[]{ws / q, es / q, g.psiMax});
            }
            long k = key(seed, zk, g.westX);
            ANOM.put(k, a);
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
     * <p>口径：带宽 = {@link #BAND_W}（默认 100 km），带内**算术平均**，
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
