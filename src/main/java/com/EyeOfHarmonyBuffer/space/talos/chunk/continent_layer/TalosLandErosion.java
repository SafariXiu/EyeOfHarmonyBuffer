package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TalosLandErosion —— **地貌演化层（LEM / stream power）**。
 *
 * <h3>它是什么</h3>
 * 在 {@link TalosLandField} 的「抬升场」上跑 <b>Braun &amp; Willett (2013) 的 O(n) 隐式
 * stream power</b>，演化到稳态，得到<b>自带树枝状河谷网络</b>的地形。
 *
 * <h3>为什么需要它（实测依据）</h3>
 * 旧的 {@code WaterField} 在 12 km 窗口里算 D8 汇流，实测：
 * <ul>
 *   <li>河长上限 = 窗口 = <b>12 km</b></li>
 *   <li>{@code isRiver} = <b>0%</b>、{@code isLake} = <b>0%</b>（门槛是 10 km 网格时代的，全部失效）</li>
 *   <li>流向脆弱：正负 0.1 格扰动改 10.5% 的流向</li>
 * </ul>
 * LEM 把地形演化到「完全排水 + 坡度由汇流面积决定」的不动点，
 * <b>河网成为地形的副产品</b>。
 *
 * <h3>数学</h3>
 * <pre>
 *   dz/dt = U - K * A^m * S^n                        （stream power，n = 1）
 *   z_i = (z_i + dtU_i + alpha * z_d) / (1 + alpha)  （Braun &amp; Willett 隐式）
 *     alpha = DK * A_i^m / L_i
 *     dtU_i = DTU * UREF * ((U_i - SEA) / UREF)^GAMMA
 * </pre>
 * ★ <b>遍历顺序必须是【高程升序】（出海口先）</b>，因为 z_i 依赖【下游的新值】。
 * ★ <b>zd = max(z_downstream, SEA)</b>：海平面是侵蚀基准面。
 *   不加这个 max 会把海岸带陆地一路拉到深海（实测 18.65% 的陆地被削穿）。
 * ★ 隐式格式<b>不产生坑</b>（新值是正权重加权平均，下坡关系被保持；
 *   实测洼地 3165 到 0 并保持）。
 *
 * <h3>窗口与瓦片</h3>
 * <ul>
 *   <li>求解窗口 {@link #SOLVE_CELLS} = 768 格（768 km）—— 远大于大陆特征尺度 125 km。</li>
 *   <li>有效输出 {@link #OUT_CELLS} = 512 格 —— 去掉两侧 buffer 后的中心区。</li>
 *   <li>buffer {@link #BUFFER_CELLS} = 128 格 —— 实测 buffer 64 km 时中心区 39.63% 的格差
 *       大于 1 格，<b>buffer 128 km 时差 = 0.0000 格（逐位相同）</b>，瓦片解<b>窗口无关</b>。</li>
 * </ul>
 * 因此每瓦片是 {@code (seed, tileX, tileZ)} 的<b>纯函数</b>，可无限外推。
 *
 * <h3>开关与回滚</h3>
 * {@link #ENABLED} 默认 <b>false</b>。关掉后 {@link TalosLandField#height0} 里
 * 那一行短路判断不执行，<b>逐位回到现状</b>。
 *
 * <h3>许可证</h3>
 * ★ 只借鉴<b>公式</b>（公式不受版权保护），未抄任何 GPL 代码。
 */
public final class TalosLandErosion {

    private TalosLandErosion() {}

    // ==================== 开关 ====================

    /** ★ 总开关。关掉后 {@link TalosLandField#height0} 里那一行短路判断不执行，逐位回到现状。 */
    public static volatile boolean ENABLED = true;

    // ==================== 网格与窗口 ====================

    /** 格距（格）。1 格 = 1 米。 */
    public static volatile double STEP_BLK = 1000.0;
    /** 求解窗口边长（格）。 */
    public static volatile int SOLVE_CELLS = 768;
    /** 有效输出边长（格）。 */
    public static volatile int OUT_CELLS = 512;
    /** 缓冲区（格）。★ 必须 &gt;= 大陆半径 125 km。 */
    public static volatile int BUFFER_CELLS = 128;

    /** 米 -&gt; 格（与 SimTerrain.ELEV_TO_BLK 同值）。 */
    public static final double ELEV_TO_BLK = 0.018;
    /** 海平面（格）。 */
    public static volatile double SEA_BLK = 64.0;

    // ==================== LEM 参数 ====================

    public static volatile int ITER = 200;
    public static volatile int FLOW_EVERY = 10;
    public static volatile double DK = 0.05;
    public static volatile double M = 0.5;
    public static volatile double DTU = 0.01351;
    public static volatile double GAMMA = 0.7;
    public static volatile double UREF = 100.0;

    // ==================== 抬升场中尺度扰动 ====================

    public static volatile double NA_AMP = 120.0;
    public static volatile double NA_WL = 3000.0;
    public static volatile int NA_OCT = 4;
    /** ★ 海岸淡化尺度（米）。没有它时 NA_AMP=120 会削穿 164 个陆地格。 */
    public static volatile double NA_TAPER = 150.0;

    // ==================== 河道层 ====================

    /**
     * ★ 河道阈值（临界流域面积，平方公里 = 格）。
     *
     * <p><b>为什么从 20 降到 3</b>：实测（[134]）阈值 20 时支流格只有 7612，
     * 而且支流在 1 km 网格上只有 1 到 2 格长 —— 看起来像「断头溪」，完全没有水系感。
     * 降到 3 后支流格 25704（<b>3.4 倍</b>），而总水面只从 0.077% 涨到约 0.11%
     * （因为支流只有 1 到 2 格宽）—— <b>几乎免费</b>。
     *
     * <table>
     *   <tr><th>ACC_MIN</th><th>河道格</th><th>占陆地</th><th>水面</th><th>支流格</th></tr>
     *   <tr><td>20</td><td>12036</td><td>10.8%</td><td>0.077%</td><td>7612</td></tr>
     *   <tr><td>5</td><td>24204</td><td>21.7%</td><td>0.100%</td><td>12168</td></tr>
     *   <tr><td><b>3</b></td><td>约 30000</td><td>约 27%</td><td>约 0.11%</td><td>约 20000</td></tr>
     *   <tr><td>2</td><td>44948</td><td>40.3%</td><td>0.121%</td><td>25704</td></tr>
     * </table>
     */
    public static volatile double ACC_MIN = 3.0;
    /** 河宽系数：width = WIDTH_W0 * sqrt(acc)。从 0.7 提到 1.0 让干流更宽。 */
    public static volatile double WIDTH_W0 = 1.0;
    public static volatile double WIDTH_MIN = 1.0;
    /** 最大河宽（格）。从 45 提到 60，让主干有更大的宽度跨度。 */
    public static volatile double WIDTH_MAX = 60.0;
    /** ★ 河道段内横向鼓出振幅（格）—— 让 MC 尺度的 1 km 直线段有蜿蜒感。
     *  必须是【段内鼓出】形状（端点处为 0），否则「到折线的距离场」在格边界断裂。 */
    public static volatile double MEANDER_AMP = 20.0;   // 已废弃（常数幅度会撕裂窄河），保留仅为兼容
    /**
     * ★ 段内横向蜿蜒幅度【相对河宽】的比例（0 到 0.5）。
     *
     * <p>实测（[134]）：用常数幅度（旧 {@link #MEANDER_AMP} = 20 格）时，
     * acc=3 的支流只有 1.7 格宽，一旦偏移为负就会让 <b>de 恒为正 ⟹ 河道消失</b>，
     * 而偏移为正时又胀到 40 格。因为符号逐格翻转、鼓出逐段起伏，
     * 河道每 1000 格就消失又出现一次 —— 正是用户报的现象。
     *
     * <p>改成比例后：河宽 1.7 格 ⟹ 偏移 ±0.4 格；河宽 60 格 ⟹ 偏移 ±15 格。
     * <b>任何河宽下都不会消失，也不会胀。</b>
     */
    public static volatile double MEANDER_FRAC = 0.25;

    // ==================== 河深 ====================

    /** 河深系数：riverDepth = clamp(DEPTH_MIN, DEPTH_MAX, DEPTH_D0 * acc^0.3)。 */
    public static volatile double DEPTH_D0 = 0.6;
    public static volatile double DEPTH_MIN = 1.0;
    public static volatile double DEPTH_MAX = 6.0;

    // ==================== 湖泊层 ====================

    public static volatile boolean LAKES_ENABLED = true;
    public static volatile int BOWL_SPACING = 24;
    public static volatile double BOWL_R = 8.0;
    public static volatile double BOWL_DEPTH = 16.0;
    /** ★ 环形抬升（相对碗深）。没有它时碗在斜坡上装不住水（湖面 0.44% 到 1.52%）。 */
    public static volatile double BOWL_RIM = 0.90;
    public static volatile double BOWL_MIN_RIFT = 0.40;
    public static volatile double LAKE_MIN_FILL = 0.3;

    // ==================== 缓存 ====================

    public static volatile int CACHE_TILES = 16;

    static final int[] DX = { -1, -1, -1, 0, 0, 1, 1, 1 };
    static final int[] DZ = { -1, 0, 1, -1, 1, -1, 0, 1 };
    static final double[] DD = { 1.41421356, 1, 1.41421356, 1, 1, 1.41421356, 1, 1.41421356 };
    static final double EPS = 1.0 / 256.0;

    // ==================== 内部状态 ====================

    private static final ConcurrentHashMap<Long, Tile> CACHE = new ConcurrentHashMap<Long, Tile>();
    private static volatile int installedSeed = Integer.MIN_VALUE;
    private static volatile long tilesSolved = 0L;
    private static volatile long solveNanos = 0L;

    public static synchronized void install(int worldSeedInt) {
        if (installedSeed == worldSeedInt) return;
        installedSeed = worldSeedInt;
        CACHE.clear();
        tilesSolved = 0L; solveNanos = 0L;
    }

    public static synchronized void uninstall() {
        installedSeed = Integer.MIN_VALUE;
        CACHE.clear();
    }

    // ==================== 后台预解 ====================

    /** 预解线程（daemon + 最低优先级，与 OceanWiring 的 SST 预热同款）。 */
    private static volatile Thread worker;
    private static volatile boolean warmStop = false;

    /**
     * 后台预解 (px,pz) 附近的 5x5 瓦片（由近及远，螺旋）。
     * ★ 未解出来时查询会【同步解一次】（1.4 到 1.7 s 卡顿），
     *   所以正式接入时应先 warmAround 再让玩家靠近。
     */
    public static synchronized void warmAround(double px, double pz) {
        if (!ENABLED) return;
        int ob = outBlocks();
        if (ob <= 0) return;
        Thread old = worker;
        if (old != null && old.isAlive()) {
            warmStop = true;
            try { old.join(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        warmStop = false;
        final long ws = installedSeed;
        final int ctx = (int) Math.floorDiv((long) Math.floor(px), (long) ob);
        final int ctz = (int) Math.floorDiv((long) Math.floor(pz), (long) ob);
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                for (int ring = 0; ring <= 2 && !warmStop; ring++) {
                    for (int dz = -ring; dz <= ring && !warmStop; dz++) {
                        for (int dx = -ring; dx <= ring && !warmStop; dx++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                            long k = tileKey(ws, ctx + dx, ctz + dz);
                            if (CACHE.containsKey(k)) continue;
                            Tile tt = solve(ws, ctx + dx, ctz + dz);
                            if (tt != null) { CACHE.put(k, tt); evictIfNeeded(); }
                        }
                    }
                }
            }
        }, "Talos-LEM-Warmup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        worker = t;
        t.start();
    }

    /** 预热线程是否还在跑（诊断用）。 */
    public static boolean isWarming() { return worker != null && worker.isAlive(); }

    /** 停掉预热线程。 */
    public static synchronized void stopWarm() {
        warmStop = true;
        Thread w = worker;
        if (w != null) w.interrupt();
        worker = null;
    }

    public static int installedSeed() { return installedSeed; }
    public static long tilesSolved() { return tilesSolved; }
    public static long solveNanos() { return solveNanos; }
    public static int cachedTiles() { return CACHE.size(); }

    // ==================== 对外查询 ====================

    /**
     * ★ LEM 影响在海岸附近的淡化尺度（米）。
     *
     * <p><b>为什么必须有</b>：LEM 会把海岸带的陆地侵蚀到海平面以下。若那时【硬切回】基础剖面，
     * 就会在海岸带出现「两个高度场硬切换」⟹ <b>海岸墙</b>（实测踩过）。
     * 这里改成：把 LEM 的效果表示成【相对基础剖面的变化量 d】，再乘一个随离岸高度渐弱的权重。
     * 于是靠海处 d 到 0 ⟹ 与基础剖面严丝合缝 ⟹ 无墙。
     */
    public static volatile double COAST_TAPER_M = 100.0;

    /**
     * (px,pz) 处经 LEM 改造后的高度（米）。
     *
     * <p>★ <b>陆海归属完全由 {@link #baseHeight}（= 海岸剖面）决定，LEM 不改变它。</b>
     * 这一点是硬约束：{@code isLand} 用的是 {@code landScore 大于等于 0.5}，而
     * {@code coastProfileCF(signedCoastDistCF) 大于 0} 与它等价（{@code sig = (landScore-0.5)/|grad landScore|}）。
     * 任何让 LEM 改变符号的写法都会让【两套口径】分叉（[084] 的教训）。
     */
    public static double height(long ws, double px, double pz) {
        final double baseM = baseHeight(ws, px, pz);
        if (baseM <= 0.0) return baseM;                    // ★ 海：完全不动（不改陆海）
        Tile t = tileAt(ws, px, pz);
        if (t == null) return baseM;
        final double v = sample(t.z, t, px, pz);           // LEM 高度（格）
        final double baseBlk = SEA_BLK + baseM * ELEV_TO_BLK;
        final double d = v - baseBlk;                      // ★ LEM 相对基础剖面的变化量（格）
        // ★ 海岸淡化：靠海处权重到 0 -> 与基础剖面连续 -> 无海岸墙
        final double taper = TalosLandField.smoothstep(0.0, COAST_TAPER_M, baseM);
        // ★★ 不许沉到海平面以下 —— 但必须用【连续】的方式限制，不能用 if 硬钳。
        //    硬钳（out 小于等于 SEA 就设成 SEA+0.5）会在海岸带制造 27 格的悬崖
        //    （实测：相邻格高差 max 从 5.63 米跳到 27.03 米）。
        //    这里改成限制 d 的【负向幅度】，再乘 taper：
        //      taper = 1 时最深正好到 SEA_BLK + 0.5；
        //      taper = 0 时完全不限制；
        //      对 taper 连续，对空间也连续（min 只产生拐点，不产生跳变）。
        final double dMin = -(baseBlk - SEA_BLK - 0.5);
        final double dUse = (d < dMin) ? dMin : d;
        final double out = baseBlk + dUse * taper;
        return (out - SEA_BLK) / ELEV_TO_BLK;
    }

    /** 河宽公式：clamp(WIDTH_MIN, WIDTH_MAX, WIDTH_W0 * sqrt(acc))。 */
    public static double widthOf(double acc) {
        if (!(acc > 0.0)) return 0.0;
        double w = WIDTH_W0 * Math.sqrt(acc);
        return w < WIDTH_MIN ? WIDTH_MIN : (w > WIDTH_MAX ? WIDTH_MAX : w);
    }

    /** ★ 河深（格）：clamp(DEPTH_MIN, DEPTH_MAX, DEPTH_D0 * acc^0.3)。 */
    public static double riverDepth(double acc) {
        if (!(acc > 0.0)) return 0.0;
        double d = DEPTH_D0 * Math.pow(acc, 0.3);
        return d < DEPTH_MIN ? DEPTH_MIN : (d > DEPTH_MAX ? DEPTH_MAX : d);
    }

    /**
     * ★ 到【河道中心线】的有符号距离（格）。小于等于 0 表示在河道内。
     *
     * <p>做法：取查询点所在格周围 5x5 邻域里所有满足「非海、acc 大于等于 ACC_MIN、非湖」的格，
     * 每格构成一条线段（本格中心 -&gt; 下游格中心），取点到线段距离减去半宽与段内鼓出后的最小值。
     *
     * <p>★ 段内鼓出 off = MEANDER_AMP * 4t(1-t) * 正负1：
     * 在段两端（格中心）恒为 0，所以跨格连续；只在段中间鼓出，让 1 km 的直线段有蜿蜒感。
     * 实测（[128]）：自由 2D 噪声偏移会把河道撕成碎片，段内鼓出不会。
     */
    public static double channelDistance(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return Double.MAX_VALUE;
        int W = t.W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        int gi = (int) Math.floor((px - ox) / STEP_BLK);
        int gj = (int) Math.floor((pz - oz) / STEP_BLK);
        double best = Double.MAX_VALUE;
        double bestAcc = 0.0;
        for (int dj = -2; dj <= 2; dj++) {
            for (int di = -2; di <= 2; di++) {
                int i = gi + di, j = gj + dj;
                if (i < 0 || i >= W || j < 0 || j >= W) continue;
                int k = j * W + i;
                if (t.sea[k]) continue;
                if (!(t.acc[k] >= ACC_MIN)) continue;
                if (LAKES_ENABLED && t.filled[k] - t.z[k] > LAKE_MIN_FILL) continue;   // ★ 湖优先
                int d = t.dir[k];
                if (d < 0) continue;
                int ni = i + DX[d], nj = j + DZ[d];
                if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                double ax = ox + (i + 0.5) * STEP_BLK, az = oz + (j + 0.5) * STEP_BLK;
                double bx = ox + (ni + 0.5) * STEP_BLK, bz = oz + (nj + 0.5) * STEP_BLK;
                double vx = bx - ax, vz = bz - az;
                double vl = vx * vx + vz * vz;
                double tt = vl > 0.0 ? ((px - ax) * vx + (pz - az) * vz) / vl : 0.0;
                tt = tt < 0.0 ? 0.0 : (tt > 1.0 ? 1.0 : tt);
                double qx = ax + vx * tt - px, qz = az + vz * tt - pz;
                double dist = Math.sqrt(qx * qx + qz * qz);
                // ---- 段内横向蜿蜒 ----
                // ★★★ 幅度必须【正比于河宽】，不能是常数！
                //   实测踩过的坑：MEANDER_AMP 是常数 20 格，而 acc=3 的支流只有 1.7 格宽
                //   ⟹ sign = +1 时河道胀到 40 格，sign = -1 时 de 恒为正 ⟹ 河道【完全消失】。
                //   而 bulge = 4t(1-t) 在每个格中心为 0、段中间为 1，sign 又逐格翻转，
                //   ⟹ 河道【每 1 个 LEM 格（1000 格）就消失又出现一次】——
                //     这正是用户报的「几百格一次，越来越窄然后突然放大」。
                final double wK = widthOf(t.acc[k]);
                // ★★★ 偏移必须是【位置的连续函数】，绝不能带【段内起伏】。
                //   实测踩过的坑：原来用 bulge = 4t(1-t)，它的周期是【1 个 LEM 格 = 1000 格】，
                //   于是河道中心线每 1000 格就左右摆一次 —— 从固定视角看就是
                //   「越来越窄然后突然放大」，周期约 1000 格（用户报的"几百格一次"）。
                //   改成纯位置的正弦（周期约 18 km）后：格边界处天然连续（它是位置的函数），
                //   而且没有 1 km 周期的呼吸。
                double off = (MEANDER_FRAC * wK) * Math.sin(px * 0.00035 + pz * 0.00027);
                double de = dist - wK * 0.5 - off;
                if (de < best) { best = de; bestAcc = t.acc[k]; }
            }
        }
        // ★ 把最近河道格【自己的】 acc 存进 ThreadLocal，供 riverWidthBlocks / channelAccAt 用。
        //   为什么不能用双线性插值的 acc：插值会把【非河道格】的低值混进来，
        //   于是河道线在格内游走时河宽会【忽宽忽窄】（实测：用户报的"一段宽一段窄"）。
        //   用河道格自己的 acc ⟹ 沿一条河段宽度恒定，且因为格 acc 沿流向单调 ⟹ 河宽单调。
        double[] ch = TL_CH.get();
        if (ch == null) { ch = new double[1]; TL_CH.set(ch); }
        ch[0] = bestAcc;
        return best;
    }

    private static final ThreadLocal<double[]> TL_CH = new ThreadLocal<double[]>();

    /** ★ 最近河道格【自己的】汇流面积（不是插值）。非河道返回 0。 */
    public static double channelAccAt(long ws, double px, double pz) {
        if (!(channelDistance(ws, px, pz) <= 0.0)) return 0.0;
        double[] ch = TL_CH.get();
        return ch != null ? ch[0] : 0.0;
    }

    /** 汇流面积（格 = 平方公里）。非陆返回 0。 */
    public static double accAt(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return 0.0;
        double a = sample(t.acc, t, px, pz);
        return a > 0.0 ? a : 0.0;
    }

    /** 是否在河道内（湖不算）。 */
    public static boolean isRiver(long ws, double px, double pz) {
        return channelDistance(ws, px, pz) <= 0.0;
    }

    /** ★ 水面高度（格，取整到 MC 的整数 y）。非河非湖返回 NaN。
     *  = round(filled)：Priority-Flood 保证 filled 沿流向单调不增，取整后仍单调，
     *    因此下游水面永不高于上游，水不会倒流且必然连到海。 */
    public static double riverSurfaceBlocks(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return Double.NaN;
        double f = sample(t.filled, t, px, pz);
        double zz = sample(t.z, t, px, pz);
        boolean lake = LAKES_ENABLED && (f - zz) > LAKE_MIN_FILL;
        if (!lake && !isRiver(ws, px, pz)) return Double.NaN;
        return Math.round(f);
    }

    /** 河道宽度（格）。非河道或湖面返回 0。 */
    public static double riverWidthBlocks(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return 0.0;
        double d = sample(t.filled, t, px, pz) - sample(t.z, t, px, pz);
        if (LAKES_ENABLED && d > LAKE_MIN_FILL) return 0.0;     // ★ 湖优先
        // ★ 用【最近河道格自己的 acc】，不用双线性插值 —— 否则河宽会忽宽忽窄。
        double a = channelAccAt(ws, px, pz);
        if (!(a >= ACC_MIN)) return 0.0;
        return widthOf(a);
    }

    /** 是否湖面。 */
    public static boolean isLake(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null || !LAKES_ENABLED) return false;
        return sample(t.filled, t, px, pz) - sample(t.z, t, px, pz) > LAKE_MIN_FILL;
    }

    /** 湖面高度（格）；非湖返回 NaN。 */
    public static double lakeSurfaceBlocks(long ws, double px, double pz) {
        if (!isLake(ws, px, pz)) return Double.NaN;
        Tile t = tileAt(ws, px, pz);
        return sample(t.filled, t, px, pz);
    }

    /** 湖深（格）；非湖返回 0。 */
    public static double lakeDepthBlocks(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return 0.0;
        double d = sample(t.filled, t, px, pz) - sample(t.z, t, px, pz);
        return d > 0.0 ? d : 0.0;
    }

    // ==================== 瓦片与采样 ====================

    static int outBlocks() { return (int) Math.round(OUT_CELLS * STEP_BLK); }

    /**
     * ★ 瓦片缓存键。【必须含 seed】。
     *
     * <p><b>为什么</b>：原实现只用 (tx, tz)，于是同一个 JVM 里换世界（换种子）时，
     * 旧种子的瓦片会被新世界命中 ⟹ <b>地形看起来"种子不稳定"</b>。
     * <p>虽然 {@link #install} 会清缓存，但只要有<b>任何一条路径</b>在 install 之前
     * 或之后用别的 ws 查询，就会出现跨种子的脏命中。把 seed 编进键里是【零成本】的根治。
     */
    private static long tileKey(long ws, int tx, int tz) {
        long h = ws * 0x9E3779B97F4A7C15L;
        h ^= (((long) tx) << 32) ^ (tz & 0xFFFFFFFFL);
        return h;
    }

    /**
     * ★ 取瓦片。**不依赖 installedSeed**。
     *
     * <h3>为什么去掉 installedSeed 门控（实测踩过的坑）</h3>
     * 原实现要求 {@code installedSeed == ws}，否则返回 null。实测诊断输出：
     * <pre>
     *   尝试 641601 点  命中 0  installedSeed=0  worldSeed=179054954  seedMismatch=641601
     * </pre>
     * ⟹ <b>有人用 0 调了 install()</b>，把真实种子覆盖掉了。
     * 推断时序：真实世界 onWorld(179054954) 之后，某个占位世界/别的维度又 onWorld(0)，
     * 于是 installedSeed 从 179054954 变成 0，之后所有水系查询全部返回 null。
     *
     * <p>★ 正确做法：<b>缓存的键里已经含 seed</b>（{@link #tileKey}），
     * 所以「不同种子互相污染」在结构上已经不可能。
     * 与其再加一道会误伤的门控，不如直接用查询传进来的 ws。
     * installedSeed 只保留作【诊断显示】。
     */
    private static Tile tileAt(long ws, double px, double pz) {
        if (!ENABLED) return null;
        int ob = outBlocks();
        if (ob <= 0 || SOLVE_CELLS < OUT_CELLS + 2 * BUFFER_CELLS) return null;
        // 只为诊断记录：谁最近被查询过（不参与正确性）
        if (installedSeed != (int) ws) installedSeed = (int) ws;
        int tx = (int) Math.floorDiv((long) Math.floor(px), (long) ob);
        int tz = (int) Math.floorDiv((long) Math.floor(pz), (long) ob);
        long k = tileKey(ws, tx, tz);
        Tile t = CACHE.get(k);
        if (t != null) return t;
        t = solve(ws, tx, tz);
        if (t == null) return null;
        CACHE.put(k, t);
        evictIfNeeded();
        return t;
    }

    private static void evictIfNeeded() {
        int cap = CACHE_TILES;
        if (cap <= 0) { if (CACHE.size() > 1) CACHE.clear(); return; }
        int toDrop = CACHE.size() - cap;
        if (toDrop <= 0) return;
        Iterator<Map.Entry<Long, Tile>> it = CACHE.entrySet().iterator();
        while (toDrop-- > 0 && it.hasNext()) { it.next(); it.remove(); }
    }

    private static double sample(double[] f, Tile t, double px, double pz) {
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        // ★ 半格对齐：f[j*W+i] 存的是【格心】(ox + (i+0.5)*STEP) 的值，
        //   所以先减 0.5 再取整，否则会与 channelDistance 的格心约定差半格
        //   （实测症状：格心处的 channelDistance 报 665 而不是 0）。
        double gx = (px - ox) / STEP_BLK - 0.5, gz = (pz - oz) / STEP_BLK - 0.5;
        int i0 = (int) Math.floor(gx), j0 = (int) Math.floor(gz);
        double fx = gx - i0, fz = gz - j0;
        int i1 = i0 + 1, j1 = j0 + 1;
        if (i0 < 0) { i0 = 0; i1 = 0; fx = 0.0; }
        if (j0 < 0) { j0 = 0; j1 = 0; fz = 0.0; }
        if (i1 >= t.W) { i0 = t.W - 1; i1 = i0; fx = 0.0; }
        if (j1 >= t.W) { j0 = t.W - 1; j1 = j0; fz = 0.0; }
        double a = f[j0 * t.W + i0], b = f[j0 * t.W + i1];
        double c = f[j1 * t.W + i0], d = f[j1 * t.W + i1];
        double u = a + (b - a) * fx, v = c + (d - c) * fx;
        return u + (v - u) * fz;
    }

    // ==================== 抬升场 ====================

    /** 抬升场（米）= 海岸剖面 + 造山 + 中尺度扰动（海岸淡化）。 */
    public static double baseHeight(long ws, double px, double pz) {
        double sig = TalosLandField.signedCoastDistCF(ws, px, pz);
        double b = TalosLandField.coastProfileCF(sig);
        if (b <= 0.0) return b;
        // ★ 必须与 TalosLandField.height0 【逐位同口径】，否则 LEM 开/关之间会出现台阶。
        //   造山的海岸淡化也要一样（[131]）。
        double ot = TalosLandField.OROG_COAST_TAPER_M > 0.0
                ? TalosLandField.smoothstep(0.0, TalosLandField.OROG_COAST_TAPER_M, b) : 1.0;
        double h = b + (ot > 0.0
                ? TalosLandField.OROG_MAX_M * TalosLandField.orogeny01(ws, px, pz) * ot : 0.0);
        if (NA_AMP != 0.0 && NA_WL > 0.0 && NA_OCT > 0) {
            double taper = TalosLandField.smoothstep(0.0, NA_TAPER, b);
            if (taper > 0.0) h += NA_AMP * taper * fbm(ws ^ 0x3CL, px, pz, NA_WL, NA_OCT);
        }
        return h;
    }

    /** fBm，值域 [-1,1]。 */
    static double fbm(long seed, double x, double y, double wl, int oct) {
        double v = 0.0, amp = 1.0, sum = 0.0, f = 1.0 / wl;
        for (int k = 0; k < oct; k++) {
            double n = TalosLandField.cnoise(x * f, y * f, 1.0, seed + k * 0x9E3779B9L);
            v += amp * (2.0 * n - 1.0);
            sum += amp; amp *= 0.5; f *= 2.0;
        }
        return sum > 0.0 ? v / sum : 0.0;
    }

    // ==================== 瓦片 ====================

    static final class Tile {
        final int tx, tz, W;
        final double[] z, acc, filled, U;
        final byte[] dir;
        final boolean[] sea, done;
        final int[] par;
        Tile(int tx, int tz, int w) {
            this.tx = tx; this.tz = tz; this.W = w;
            this.z = new double[w * w]; this.acc = new double[w * w];
            this.filled = new double[w * w]; this.U = new double[w * w];
            this.dir = new byte[w * w]; this.sea = new boolean[w * w];
            this.done = new boolean[w * w]; this.par = new int[w * w];
        }
    }

    /** 解一个瓦片。线程安全（只用局部缓冲）。 */
    static Tile solve(long ws, int tx, int tz) {
        long t0 = System.nanoTime();
        int W = SOLVE_CELLS;
        if (W <= 0 || OUT_CELLS <= 0 || BUFFER_CELLS < 0) return null;
        if (W < OUT_CELLS + 2 * BUFFER_CELLS) return null;
        Tile t = new Tile(tx, tz, W);
        int N = W * W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = tz * outBlk - BUFFER_CELLS * STEP_BLK;

        for (int j = 0; j < W; j++) {
            double wz = oz + j * STEP_BLK;
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                double m = baseHeight(ws, ox + i * STEP_BLK, wz);
                // ★ U 必须存【格】，与更新式同一量纲（米会把 uu 放大 55.6 倍 -> 地形被抬飞）
                t.U[k] = SEA_BLK + m * ELEV_TO_BLK;
                t.z[k] = SEA_BLK + m * ELEV_TO_BLK;
                t.sea[k] = (m <= 0.0);
            }
        }

        int fe = Math.max(1, FLOW_EVERY);
        long[] heap = new long[N];
        int[] ord = new int[N];
        int[] tmp = new int[N];
        int[] cnt = new int[256];
        double[] uBuf = new double[N];

        for (int it = 0; it < ITER; it++) {
            if (it % fe == 0) { fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt); }
            orderAsc(t.z, N, ord, tmp, cnt);
            for (int oi = 0; oi < N; oi++) {
                int k = ord[oi];
                if (t.sea[k]) continue;
                int d = t.dir[k]; if (d < 0) continue;
                int ni = k % W + DX[d], nj = k / W + DZ[d];
                if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                double al = DK * Math.pow(t.acc[k], M);
                double uu = (t.U[k] - SEA_BLK) / UREF;
                double up = uu > 0.0 ? DTU * UREF * Math.pow(uu, GAMMA) : 0.0;
                double nb = t.z[nj * W + ni];
                double zd = nb > SEA_BLK ? nb : SEA_BLK;
                t.z[k] = (t.z[k] + up + al * zd) / (1.0 + al);
            }
        }
        fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
        if (LAKES_ENABLED) {
            carveLakes(ws, t, ox, oz);
            fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
        }
        tilesSolved++;
        solveNanos += (System.nanoTime() - t0);
        return t;
    }

    /** 湖泊层：裂谷选位，挖碗 + 环形 rim。 */
    private static void carveLakes(long ws, Tile t, double ox, double oz) {
        int W = t.W;
        int sp = Math.max(2, BOWL_SPACING);
        double R = BOWL_R, D = BOWL_DEPTH, rim = BOWL_RIM;
        int Ri = (int) Math.ceil(R);
        if (Ri < 1 || D <= 0.0 || R <= 0.0) return;
        for (int cj = sp / 2; cj < W; cj += sp) {
            for (int ci = sp / 2; ci < W; ci += sp) {
                int ck = cj * W + ci;
                if (t.sea[ck]) continue;
                double pb = TalosLandField.plateBoundary01(ws, ox + ci * STEP_BLK, oz + cj * STEP_BLK);
                double cv = TalosLandField.convergence01(ws, ox + ci * STEP_BLK, oz + cj * STEP_BLK);
                if (pb * (1.0 - cv) < BOWL_MIN_RIFT) continue;
                for (int dj = -Ri; dj <= Ri; dj++) {
                    for (int di = -Ri; di <= Ri; di++) {
                        int i = ci + di, j = cj + dj;
                        if (i < 0 || i >= W || j < 0 || j >= W) continue;
                        int k = j * W + i;
                        if (t.sea[k]) continue;
                        double r2 = (di * di + dj * dj) / (R * R);
                        if (r2 >= 1.0) continue;
                        double w = (1.0 - r2) * (1.0 - r2);
                        double rimp = 4.0 * r2 * (1.0 - r2);
                        t.z[k] += D * (rim * rimp - w);
                    }
                }
            }
        }
    }

    // ==================== 填洼 / 流向 / 汇流 ====================

    private static void fill(Tile t, long[] heap) {
        int W = t.W, N = W * W;
        double[] f = t.filled;
        Arrays.fill(t.done, false);
        Arrays.fill(t.par, -1);
        int hs = 0;
        for (int k = 0; k < N; k++) {
            boolean edge = (k % W == 0) || (k % W == W - 1) || (k / W == 0) || (k / W == W - 1);
            if (t.z[k] <= SEA_BLK) {
                // ★ 海格的基准面是【海平面】，不是海底深度。
                //   若用海底深度（很低的负值），sample() 的双线性插值会把邻近【河道格】
                //   的水面污染到海平面以下（实测：297 个入海口里 144 个水面低于 64，最低 22）。
                //   这也与更新式的 zd = max(z_downstream, SEA_BLK) 一致。
                f[k] = SEA_BLK; t.done[k] = true;
                heap[hs++] = key(f[k], k);
            } else if (edge) {
                f[k] = t.z[k]; t.done[k] = true;
                heap[hs++] = key(f[k], k);
            }
        }
        for (int i = hs / 2 - 1; i >= 0; i--) siftDown(heap, i, hs);
        while (hs > 0) {
            long top = heap[0]; heap[0] = heap[--hs]; siftDown(heap, 0, hs);
            int k = (int) (top & 0xFFFFFFFFL), ki = k % W, kj = k / W;
            for (int d = 0; d < 8; d++) {
                int ni = ki + DX[d], nj = kj + DZ[d];
                if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                int nb = nj * W + ni;
                if (t.done[nb]) continue;
                t.done[nb] = true;
                double v = f[k] + EPS;
                f[nb] = t.z[nb] > v ? t.z[nb] : v;
                t.par[nb] = k;
                heap[hs++] = key(f[nb], nb);
                siftUp(heap, hs - 1);
            }
        }
    }

    private static void dirs(Tile t) {
        int W = t.W;
        for (int j = 0; j < W; j++) {
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                if (t.z[k] <= SEA_BLK) { t.dir[k] = -1; continue; }
                if (LAKES_ENABLED && t.filled[k] - t.z[k] > LAKE_MIN_FILL && t.par[k] >= 0) {
                    int pk = t.par[k], pi = pk % W, pj = pk / W;
                    int dx = Integer.signum(pi - i), dz = Integer.signum(pj - j);
                    int best = -1;
                    for (int q = 0; q < 8; q++) if (DX[q] == dx && DZ[q] == dz) { best = q; break; }
                    if (best >= 0) { t.dir[k] = (byte) best; continue; }
                }
                int best = -1; double bs = 0.0;
                for (int q = 0; q < 8; q++) {
                    int ni = i + DX[q], nj = j + DZ[q];
                    if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                    double sl = (t.filled[k] - t.filled[nj * W + ni]) / DD[q];
                    if (sl > bs) { bs = sl; best = q; }
                }
                t.dir[k] = (byte) best;
            }
        }
    }

    private static void accOf(Tile t, int[] ord, int[] tmp, int[] cnt) {
        int W = t.W, N = W * W;
        Arrays.fill(t.acc, 0.0);
        orderDesc(t.filled, N, ord, tmp, cnt);
        for (int q = 0; q < N; q++) {
            int k = ord[q];
            t.acc[k] += 1.0;
            int d = t.dir[k];
            if (d < 0) continue;
            int ni = k % W + DX[d], nj = k / W + DZ[d];
            if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
            t.acc[nj * W + ni] += t.acc[k];
        }
    }

    // ==================== 基数排序 / 堆 ====================

    private static int sk(double v) {
        int s = Float.floatToIntBits((float) v);
        return s ^ ((s >> 31) & 0x7FFFFFFF);
    }

    private static long key(double v, int i) {
        return (((long) sk(v)) << 32) | (i & 0xFFFFFFFFL);
    }

    private static void orderAsc(double[] key, int N, int[] idx, int[] tmp, int[] cnt) {
        for (int i = 0; i < N; i++) idx[i] = i;
        int[] src = idx, dst = tmp;
        for (int pass = 0; pass < 4; pass++) {
            Arrays.fill(cnt, 0);
            int sh = pass * 8;
            for (int i = 0; i < N; i++) cnt[(sk(key[src[i]]) >>> sh) & 0xFF]++;
            int sum = 0;
            for (int b = 0; b < 256; b++) { int c = cnt[b]; cnt[b] = sum; sum += c; }
            for (int i = 0; i < N; i++) { int k = src[i]; dst[cnt[(sk(key[k]) >>> sh) & 0xFF]++] = k; }
            int[] sw = src; src = dst; dst = sw;
        }
        if (src != idx) System.arraycopy(src, 0, idx, 0, N);
    }

    private static void orderDesc(double[] key, int N, int[] idx, int[] tmp, int[] cnt) {
        for (int i = 0; i < N; i++) idx[i] = i;
        int[] src = idx, dst = tmp;
        for (int pass = 0; pass < 4; pass++) {
            Arrays.fill(cnt, 0);
            int sh = pass * 8;
            for (int i = 0; i < N; i++) cnt[(~sk(key[src[i]]) >>> sh) & 0xFF]++;
            int sum = 0;
            for (int b = 0; b < 256; b++) { int c = cnt[b]; cnt[b] = sum; sum += c; }
            for (int i = 0; i < N; i++) { int k = src[i]; dst[cnt[(~sk(key[k]) >>> sh) & 0xFF]++] = k; }
            int[] sw = src; src = dst; dst = sw;
        }
        if (src != idx) System.arraycopy(src, 0, idx, 0, N);
    }

    private static void siftUp(long[] a, int i) {
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (a[p] <= a[i]) break;
            long t = a[p]; a[p] = a[i]; a[i] = t;
            i = p;
        }
    }

    private static void siftDown(long[] a, int i, int n) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < n && a[l] < a[m]) m = l;
            if (r < n && a[r] < a[m]) m = r;
            if (m == i) break;
            long t = a[m]; a[m] = a[i]; a[i] = t;
            i = m;
        }
    }
}
