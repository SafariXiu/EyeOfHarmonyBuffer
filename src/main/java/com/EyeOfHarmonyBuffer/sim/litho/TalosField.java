package com.EyeOfHarmonyBuffer.sim.litho;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.SimplexNoise2D;

/**
 * TalosField —— V8 陆地生成器的 Java 落地版（设计冻结 §305~§310）。
 *
 * <p><b>与 PlateField 的关系：完全独立。</b>本类不被任何现有代码调用，
 * 因此**不改变任何现有行为**（无需开关即可安全共存）。等性能与形态都达标后再整体切换。
 *
 * <p><b>硬约束（全部满足的结构）</b>：
 * <ul>
 *   <li>X 无限、Z 无限、**都无周期**（WorldContract：地形在 Z 上不重复）；</li>
 *   <li>**真 O(1)**：格点属性由 hash(i,j,seed) 派生，**无任何全局数组、无全图 min/max**；</li>
 *   <li>负坐标正确：晶格索引用 floor（E112）；</li>
 *   <li>每世界一次自标定（1024 点，与列数无关）得到海平面 LEVEL。</li>
 * </ul>
 *
 * <p><b>单位：block。</b>契约锁死 1 格 = 1 米，Z_CYCLE = 20,000,000 block = 20,000 km。
 */
public final class TalosField {

    private TalosField() {}

    // ================= §7753：黄金角旋转（与 DetailNoise 同一套） =================
    /** 黄金角（弧度）：{@code 2*pi*(1 - 1/phi)} = 137.507764... 度。出处见 {@code DetailNoise.GOLDEN_ANGLE}。 */
    public static final double GOLDEN_ANGLE = 2.399963229728653;
    static final double GOLDEN_COS = Math.cos(GOLDEN_ANGLE);
    static final double GOLDEN_SIN = Math.sin(GOLDEN_ANGLE);

    /**
     * ★★★★★★★★ <b>§7754：Simplex 实例缓存 —— 性能必需。</b>
     *
     * <p>{@code new SimplexNoise2D(seed)} 要填充 512 字节的置换表，实测<b>约 0.73 us</b>，
     * 而 {@code fbmBand}/{@code hfExtra} 是<b>每列每 octave</b> 调用 ⟹ 若每次新建，
     * 整列成本从 6.6 us 涨到 <b>15.4 us（+2.3 倍）</b>。所以必须复用。
     *
     * <p><b>键 = 种子</b>。这些函数的调用点种子是<b>固定的有限集合</b>
     * （{@code seed ^ 0x99}、{@code seed ^ 0xAD}、{@code seed ^ 0xB1}、{@code seed ^ 0xB2}、
     * {@code seed + 0xE77}，以及少量 {@code seed ^ 0xAA}），所以命中率接近 100%。
     *
     * <p>⚠ <b>有界</b>（{@link #SN_CACHE_MAX}）：满了就整体清空重填。
     * 不用 {@code ThreadLocal} —— 实例本身无状态（只读 perm 表），可安全跨线程共享。
     */
    private static final int SN_CACHE_MAX = 64;
    private static final long[] SN_SEED = new long[SN_CACHE_MAX];
    private static final SimplexNoise2D[] SN_INST = new SimplexNoise2D[SN_CACHE_MAX];
    private static int snN = 0;

    /** 取（或建）该种子的 Simplex 实例。调用方<b>不得</b>修改返回对象。 */
    static SimplexNoise2D simplexFor(long seed) {
        for (int i = 0; i < snN; i++) if (SN_SEED[i] == seed) return SN_INST[i];
        SimplexNoise2D sn = new SimplexNoise2D(seed);
        if (snN >= SN_CACHE_MAX) snN = 0;      // 满了整体清空（简单且绝不无限增长）
        SN_SEED[snN] = seed; SN_INST[snN] = sn; snN++;
        return sn;
    }

    // ================= 世界尺度 =================</br>
    /** 世界跨度（block）：对应原型里的 L = 20,000 km。 */
    public static final double L = 20_000_000.0;
    /** 格距：原型 NC = 9。 */
    public static final double DCELL = L / 9.0;
    public static final double SWS = 1200.0 * 1000.0;   // 顶点弯曲（km -> block）
    public static final double JIT = 0.40;
    /**
     * <b>细节层（hf）的振幅。§7735 由 ETOPO 实测标定（不再是「可调旋钮」）。</b>
     *
     * <p><b>标定方法</b>：在 ETOPO 2022 15 arc-sec 瓦片（463 m/px，8 块）上量
     * <b>结构函数</b> {@code S(d) = <(h(x+d)-h(x))^2>}（自仿射面的干净标定器；
     * {@code max-min} 会饱和，不能用），再用同一统计量量本仓地形，取跨尺度最小二乘振幅因子。
     *
     * <pre>
     *   d(格)  | 本仓 (m) | ETOPO (m) | 比
     *     463  |     38.9 |      48.3 | 0.805
     *     926  |     58.4 |      82.3 | 0.709
     *    1852  |     73.2 |     126.1 | 0.580
     *    4167  |     92.0 |     186.0 | 0.495
     *   ==> 振幅因子 1.51 ==> 0.320 x 1.51 = <b>0.4833</b>
     * </pre>
     *
     * <p><b>实测脚本</b>：{@code K:/moder/TT/etopo_spectrum/extract_H.py}（结构函数 + H）、
     * {@code extract_relief.py}（起伏 vs 窗口）。<b>探针</b>：P1377 / P1378（FIT-1，
     * logRMS 0.186）。完整报告：{@code K:/moder/Talos2/AN标定-ETOPO实测.md}。
     *
     * <p>⚠ <b>改它会改变海陆比</b>：{@code level()} 是 {@code fieldValue} 的分位，而本项进
     * {@code fieldValue} ⟹ 必须重新核对 land/ocean 占比。
     *
     * <p>旧值 0.320 无出处（接手说明 §4.4 已标记为「没有出处的可调旋钮」），偏小 1.51 倍。
     */
    public static double AN = 0.4833;
    /**
     * <b>细节层低频段 Hurst 指数。</b>
     *
     * <p><b>§7712 标定（ETOPO 实测，不是拍脑袋）：</b>
     * 对 {@code terrain-diffusion/data/global/etopo_10m.tif}（10 arc-min 全球，
     * 18.55 km/px，拟合频段 75.8–3562.2 km）做径向平均功率谱 + log-log 最小二乘：
     * <b>beta = 2.8193，R² = 0.9983</b>。二维分形面 {@code beta = 2H + 2}
     * ⟹ <b>H = 0.4097</b>。脚本：{@code K:/moder/TT/etopo_spectrum/extract_psd.py}。
     *
     * <p>旧值 0.55 是早期估计（无出处），会让 beta = 3.10，比地球陡 0.34。
     */
    public static double HH = 0.4097;
    /** 改 H 必须同时刷新预计算的倍频增益。 */
    public static void setHurst(double h) { HH = h; GHH = Math.pow(2, -h); }
    public static final double USx = 3.0;
    public static final double LAM = 0.9;
    public static final double RW  = 2.2 * DCELL;       // 紧支撑核半径（E113）
    static final double RW2 = RW * RW;                  // 核用 d^2 的多项式：省掉每列 25 次 sqrt

    // ================= hash 噪声（无界、非周期、负坐标安全） =================
    static long mix(long s, long a, long b) {
        long h = s * 0x9E3779B97F4A7C15L + a;
        h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L;
        h = h * 0x9E3779B97F4A7C15L + b;
        h ^= (h >>> 32); h *= 0x94D049BB133111EBL; h ^= (h >>> 29);
        return h;
    }
    static double rnd01(long h) {
        long v = h;
        v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL;
        v ^= (v >>> 33); v *= 0xC4CEB9FE1A85EC53L;
        v ^= (v >>> 33);
        return (v >>> 11) * 0x1.0p-53;
    }
    static double vnoise(double x, double z, long s) {
        double xf = Math.floor(x), zf = Math.floor(z);
        long xi = (long) xf, zi = (long) zf;
        double tx = x - xf, tz = z - zf;
        double u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(mix(s, xi, zi)) * 2 - 1, b = rnd01(mix(s, xi + 1, zi)) * 2 - 1;
        double c = rnd01(mix(s, xi, zi + 1)) * 2 - 1, d = rnd01(mix(s, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
    }
    /** 归一化 fbm：振幅按 H 衰减、总和归一（与原型逐位同构）。 */
    /** 倍频增益：Math.pow 在热路径上是纯浪费 —— 两个 H 都是编译期常量，直接预算。 */
    static final double G08 = Math.pow(2, -0.8);
    static double GHH = Math.pow(2, -HH);
    static double fbm(double x, double z, long s, int oct, double wl0, double gain) {
        double r = 0, a = 1, f = 1.0 / wl0, tot = 0;
        for (int o = 0; o < oct; o++) { r += a * vnoise(x * f, z * f, s + o * 7919L); tot += a; a *= gain; f *= 2; }
        return r / tot;
    }

    // ================= 格点属性（全部 hash 派生） =================
    static double cellX(long I, long J, long seed) {
        double nx = (I + 0.5) * DCELL, nz = (J + 0.5) * DCELL, dx = 0;
        for (int o = 0; o < 3; o++) { double aa = SWS * Math.pow(2, -0.8 * o), wl = 7_000_000.0 / (1 << o); dx += aa * vnoise(nx / wl, nz / wl, (seed ^ 0x11L) + o * 7919L); }
        return nx + dx + (rnd01(mix(seed ^ 0x44L, I, J)) - 0.5) * JIT * DCELL;
    }
    static double cellZ(long I, long J, long seed) {
        double nx = (I + 0.5) * DCELL, nz = (J + 0.5) * DCELL, dz = 0;
        for (int o = 0; o < 3; o++) { double aa = SWS * Math.pow(2, -0.8 * o), wl = 7_000_000.0 / (1 << o); dz += aa * vnoise(nx / wl + 0.37, nz / wl - 0.21, (seed ^ 0x22L) + o * 7919L); }
        return nz + dz + (rnd01(mix(seed ^ 0x55L, I, J)) - 0.5) * JIT * DCELL;
    }
    static double cellPHI(double cx, double cz, long seed) { return fbm(cx, cz, seed ^ 0x66L, 3, 9_000_000.0, G08); }
    static double cellPSI(long I, long J, long seed) { return fbm(cellX(I, J, seed), cellZ(I, J, seed), seed ^ 0x77L, 2, 7_000_000.0, G08); }

    static final int[][] NB = {{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}};

    /** 每种子记忆表的槽数（{@link #level} 与 {@link #calOf} 共用）。★ E126 起声明提前，供 CAL_* 使用。 */
    static final int NLVL = 8;

    // ================= ★ E126：归一化的【纯函数化】=================
    //
    //  ⚠ 修的缺陷（用户 2026-09-26 从图上看到硬切方块后定位）：
    //    buildWindow 原来用【本窗口 100 格】的统计算 sref/cref，再做归一化。
    //    ⇒ 窗口中心随查询格走 ⟹ 中心移一格，参与统计的那批格就换一批 ⟹ sref/cref 变
    //    ⇒ 【所有 wB 值都被重新缩放】⟹ bfield 在每条 DCELL 边界上跳变（P1185 实测 0.003466）。
    //    这与读取范围多大【无关】—— 只要统计随窗口变，接缝就不可避免。
    //
    //  ⚠ 而 §307 的 E116 曾经想把它换成【固定常数】并失败（8 个种子的陆地占比跨度 32.36 pt）。
    //    本页核实：那次失败的原因不是「常数」这个形式，而是【LEVEL/SREF/CREF 三者都被钉成 seed 2 的值】，
    //    而 §307:21708 实测【场的 sigma 本身因种子而异：sd(fbm) = 0.193 ~ 0.383，差 2 倍】。
    //    ⇒ 正解是 §307:21729 自己提出的方案 B：「每个世界自标定」—— 与 level() 完全同构。
    //
    //  ★ 所以本修法：sref/cref 改成【每种子一次、在固定的控制点格上标定】的常数 ⟹
    //    ① 与窗口位置无关 ⟹ 【完全连续】✓
    //    ② 随种子自标定   ⟹ 吸收 sigma 的 2 倍差异 ✓（与 level() 同一套机制）
    //
    /**
     * 控制点格 (I,J) 上的 wNRM（= buildWindow 第 3 步赋给 {@code wNRM[k]} 的那个量）。纯函数。
     *
     * <p>抽出来是为了让探针能【用纯函数复刻】原来「每窗口统计」的 sref/cref ——
     * 那是设计「位置的连续场」所必需的对照（E126 的每种子常数让地形粗糙度涨了 2.5 倍）。
     */
    public static double nrmAt(long I, long J, long seed) {
        double[] p = latCached(I, J, seed);                        // ★ E128
        return fbm(p[0], p[1], seed ^ 0x88L, 2, 12_000_000.0, G08);
    }

    /** 控制点格 (I,J) 上的 conv（与 buildWindow 第 3 步【同一公式】）。纯函数。 */
    public static double convAt(long I, long J, long seed) {
        double[] q0 = phiPsiCached(I, J, seed);                    // ★ E129
        double[] p0 = latCached(I, J, seed);
        double phi = q0[0];
        long bI = I, bJ = J; double best = phi;
        for (int[] d : NB) {
            long nI = I + d[0], nJ = J + d[1];
            double pn = phiPsiCached(nI, nJ, seed)[0];             // ★ E129
            if (pn > best) { best = pn; bI = nI; bJ = nJ; }
        }
        if (best <= phi) return 0.0;
        // ⚠ 原公式里 cellPSI 的【值】不参与 vrel（只用它的两个偏导）⇒ 这里只取偏导。
        double gxs = (phiPsiCached(I + 1, J, seed)[1] - phiPsiCached(I - 1, J, seed)[1]) / (2 * DCELL);
        double gzs = (phiPsiCached(I, J + 1, seed)[1] - phiPsiCached(I, J - 1, seed)[1]) / (2 * DCELL);
        double gxb = (phiPsiCached(bI + 1, bJ, seed)[1] - phiPsiCached(bI - 1, bJ, seed)[1]) / (2 * DCELL);
        double gzb = (phiPsiCached(bI, bJ + 1, seed)[1] - phiPsiCached(bI, bJ - 1, seed)[1]) / (2 * DCELL);
        double[] p1 = latCached(bI, bJ, seed);
        double dx = p1[0] - p0[0];
        double dz = p1[1] - p0[1];
        double nn = Math.sqrt(dx * dx + dz * dz) + 1e-6;
        double vrel = (gxs - gxb) * dx / nn + (gzs - gzb) * dz / nn;
        return vrel > 0 ? vrel : 0;
    }

    /** 每种子一次的归一化标尺（SREF/CREF）。与 level() 同构：固定采样 + 8 项记忆表。 */
    public static final class Cal { public final double sref, cref; Cal(double s, double c){ sref=s; cref=c; } }
    static final ThreadLocal<double[]> CAL_SEEDS = ThreadLocal.withInitial(() -> new double[NLVL]);
    static final ThreadLocal<double[]> CAL_SREF  = ThreadLocal.withInitial(() -> new double[NLVL]);
    static final ThreadLocal<double[]> CAL_CREF  = ThreadLocal.withInitial(() -> new double[NLVL]);
    static final ThreadLocal<int[]>    CAL_N     = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * 每种子一次的 SREF/CREF 标定。
     *
     * <p>采样：控制点格 {@code I,J ∈ [-CAL_R, CAL_R]} 步长 {@code CAL_STEP}（跨 ±CAL_R*DCELL）。
     * {@code CAL_R = 45} ⟹ ±100,000 km —— 与 {@link #level} 的采样范围【同一个量级】（零新尺度）。
     * 公式与原来【逐字同形】：{@code sref = sd(wNRM)}、{@code cref = 1.5*sqrt(mean(conv^2)) + 1e-12}。
     */
    static final int CAL_R = 45, CAL_STEP = 5;
    // ================= ★ E127：把归一化统计量做成【位置的连续场】=================
    //
    //  ⚠ 修的缺陷（用户裁决的「第三条路」，2026-09-26）：
    //    原始代码用【本窗口】的统计量 ⟹ 窗口中心一移，标尺就变 ⟹ bfield 在每条 DCELL 边界上跳。
    //    E126 把它换成【每种子常数】⟹ 接缝归零，但那【去掉了局部自适应】⟹
    //    地形粗糙度从 3.404 涨到 8.450 m/km ⟹ 西边界强化 |西/东| 中位 17.92 -> 2.41（P479 实测）。
    //
    //  ★ 本修法：统计量【仍然随位置变】（保留自适应），但【随位置连续变】（无接缝）。
    //    做法 = 在控制点格上定义局部统计 statAt(I,J)，再用【bfield 已有的同一个 C3 核】插值。
    //    ⇒ 零新尺度：7x7 块来自 RW/DCELL = 2.2 ⟹ ceil+1 = 3；核与 bfield 逐字同一套。
    //
    // ================= ★ E128：格点位置的每线程缓存（性能）=================
    //
    //  ⚠ 修的回归（P1189 实测）：E127 的逐格归一化让 elevation 的读取从 231 ms 涨到 5266 ms（23 倍）。
    //    根因：convAt 对【自己 + 8 个邻居】各调 cellPHI(cellX(...), cellZ(...))，
    //    而 cellX/cellZ 每次含 **3 个 Math.pow + 3 个 vnoise**（约 300 ns）。
    //    ⇒ 一次 convAt ≈ 9 x (6 pow + 6 vnoise) + 梯度 ≈ 4 us
    //    ⇒ 一次 statAt = 49 x (nrmAt + convAt) ≈ 200 us
    //    而 cellX/cellZ 是【(I,J,seed) 的纯函数】⟹ 完全可以缓存。
    //
    /** (I,J,seed) -> {x, z} 的每线程 LRU。值里带 (I,J) 校验（键可能碰撞）。 */
    static final int LAT_CAP = 65536;
    static final ThreadLocal<java.util.LinkedHashMap<Long, double[]>> LAT_CACHE =
        ThreadLocal.withInitial(() -> new java.util.LinkedHashMap<Long, double[]>(1024, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, double[]> e) { return size() > LAT_CAP; }
        });
    static double[] latCached(long I, long J, long seed) {
        java.util.LinkedHashMap<Long, double[]> m = LAT_CACHE.get();
        long k = mix(seed, I, J);
        double[] v = m.get(k);
        if (v != null && (long) v[2] == I && (long) v[3] == J) return v;
        double[] val = new double[]{ cellX(I, J, seed), cellZ(I, J, seed), I, J };
        m.put(k, val);
        return val;
    }

    // ================= ★ E129：格点 PHI/PSI 的每线程缓存（性能）=================
    //
    //  ⚠ E128 之后 convAt 仍有 1.7 us：它对【自己 + 8 个邻居】各调一次 cellPHI（3 八度）
    //    再加 8 次 cellPSI（2 八度）—— 那些【同样是 (I,J,seed) 的纯函数】，没理由每次重算。
    //    P1191 实测 statAt = 54 us = 49 x convAt ⟹ 而窗口重建要 169 个 statAt ⟹ 冷启动累积到秒级。
    //
    /** (I,J,seed) -> {phi, psi} 的每线程 LRU。值里带 (I,J) 校验。 */
    static final int PHI_CAP = 65536;
    static final ThreadLocal<java.util.LinkedHashMap<Long, double[]>> PHI_CACHE =
        ThreadLocal.withInitial(() -> new java.util.LinkedHashMap<Long, double[]>(1024, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, double[]> e) { return size() > PHI_CAP; }
        });
    static double[] phiPsiCached(long I, long J, long seed) {
        java.util.LinkedHashMap<Long, double[]> m = PHI_CACHE.get();
        long k = mix(seed, I, J);
        double[] v = m.get(k);
        if (v != null && (long) v[2] == I && (long) v[3] == J) return v;
        double[] p = latCached(I, J, seed);
        double[] val = new double[]{ cellPHI(p[0], p[1], seed), cellPSI(I, J, seed), I, J };
        m.put(k, val);
        return val;
    }

    /** 以控制点 (I,J) 为中心的 7x7 块的局部统计 {sref, cref}。纯函数（只依赖 (I,J,seed)）。 */
    public static double[] statAt(long I, long J, long seed) {
        final int LEN = 7 * 7;
        double m1 = 0, m2 = 0, c2 = 0;
        for (long a = I - 3; a <= I + 3; a++) for (long b = J - 3; b <= J + 3; b++) {
            double nn = nrmAt(a, b, seed);
            m1 += nn; m2 += nn * nn;
            double cv = convAt(a, b, seed); c2 += cv * cv;
        }
        double mu = m1 / LEN;
        double sref = Math.sqrt(Math.max(0.0, m2 / LEN - mu * mu));
        double cref = 1.5 * Math.sqrt(c2 / LEN) + 1e-12;
        return new double[]{ sref, cref };
    }

    /** statAt 的每线程 LRU（容量固定；statAt 每次约 880 次 fbm，不缓存会拖垮地形生成）。 */
    static final int STAT_CAP = 4096;
    static final ThreadLocal<java.util.LinkedHashMap<Long, double[]>> STAT_CACHE =
        ThreadLocal.withInitial(() -> new java.util.LinkedHashMap<Long, double[]>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, double[]> e) { return size() > STAT_CAP; }
        });
    static double[] statCached(long I, long J, long seed) {
        java.util.LinkedHashMap<Long, double[]> m = STAT_CACHE.get();
        // 键：混合 (I,J,seed)。用 Math.floorMod 折进 64 位；碰撞时按值重算（纯函数 ⟹ 结果相同）
        long k = mix(seed, I, J);
        // ⚠ 同键不同 (I,J) 的可能：key 只有 64 位而三元组更多 —— 所以把 (I,J) 也存进值里校验。
        double[] v = m.get(k);
        if (v != null && (long) v[2] == I && (long) v[3] == J) return v;
        double[] st = statAt(I, J, seed);
        double[] val = new double[]{ st[0], st[1], I, J };
        m.put(k, val);
        return val;
    }

    public static Cal calOf(long seed) {
        final double[] cs = CAL_SEEDS.get(), ss = CAL_SREF.get(), cc = CAL_CREF.get();
        final int[] n = CAL_N.get();
        for (int i = 0; i < n[0]; i++) if (cs[i] == seed) return new Cal(ss[i], cc[i]);
        int cnt = 0; double m1 = 0, m2 = 0, c2 = 0;
        for (int I = -CAL_R; I <= CAL_R; I += CAL_STEP) for (int J = -CAL_R; J <= CAL_R; J += CAL_STEP) {
            double nn = nrmAt(I, J, seed);
            m1 += nn; m2 += nn * nn;
            double cv = convAt(I, J, seed); c2 += cv * cv;
            cnt++;
        }
        double mu = m1 / cnt;
        double sref = Math.sqrt(Math.max(0.0, m2 / cnt - mu * mu));
        double cref = 1.5 * Math.sqrt(c2 / cnt) + 1e-12;
        if (n[0] < NLVL) { cs[n[0]] = seed; ss[n[0]] = sref; cc[n[0]] = cref; n[0]++; }
        else { System.arraycopy(cs,1,cs,0,NLVL-1); System.arraycopy(ss,1,ss,0,NLVL-1); System.arraycopy(cc,1,cc,0,NLVL-1);
               cs[NLVL-1]=seed; ss[NLVL-1]=sref; cc[NLVL-1]=cref; }
        return new Cal(sref, cref);
    }

    // ================= O1：格窗缓存 =================
    public static final int WIN = 10, WOFF = 5;
    /**
     * ★★ 线程安全（E123）：**全部可变状态搬进 {@link ThreadLocal}**。
     *
     * <p>为什么必须：本类原先用 {@code static} 数组做窗口缓存，而**区块生成是按多线程对待的** ——
     * 证据是 {@code space/talos/chunk/world} 里有 **7 处 ThreadLocal**（同一个 scratch 缓冲模式，
     * 例如 {@code V2TerrainGen:334 COLUMN}）。多线程共享一个窗口 ⇒ 线程 A 读到线程 B 建的窗 ⇒
     * **静默错值且不崩**，是最难查的一类缺陷。
     *
     * <p>修法与项目既有模式一致。代价：每线程一份（256 槽 x 6 数组 x 81 double 约 1 MB/线程）。
     */
    static final int NSLOT = 256;
    static final class Win {
        final long[] slotKey = new long[NSLOT];
        final long[] sCI = new long[NSLOT], sCJ = new long[NSLOT], sSeed = new long[NSLOT];
        final double[][] sCX = new double[NSLOT][], sCZ = new double[NSLOT][], sPHI = new double[NSLOT][],
                         sPSI = new double[NSLOT][], sNRM = new double[NSLOT][], sB = new double[NSLOT][];
        int cur = 0;
        long winCI = Long.MIN_VALUE, winCJ = Long.MIN_VALUE, winSeed = Long.MIN_VALUE;
        final long[] lvlSeeds = new long[NLVL];
        final double[] lvlVals = new double[NLVL];
        int lvlN = 0;
        Win() {
            for (int i = 0; i < NSLOT; i++) {
                slotKey[i] = Long.MIN_VALUE;
                sCX[i] = new double[WIN * WIN]; sCZ[i] = new double[WIN * WIN]; sPHI[i] = new double[WIN * WIN];
                sPSI[i] = new double[WIN * WIN]; sNRM[i] = new double[WIN * WIN]; sB[i] = new double[WIN * WIN];
            }
        }
    }
    static final ThreadLocal<Win> TL = ThreadLocal.withInitial(Win::new);

    // 分块记忆化：XY 全块算一次，PHI/PSI 各算一次 —— 杜绝重复求值（乱序 3.7x 的根因）
    static void buildWindow(Win w, long ci, long cj, long seed) {
        long key = mix(seed, ci, cj);
        int s = (int) ((key >>> 40) & (NSLOT - 1));
        w.cur = s;
        w.winCI = ci; w.winCJ = cj; w.winSeed = seed;
        w.slotKey[s] = key; w.sCI[s] = ci; w.sCJ[s] = cj; w.sSeed[s] = seed;
        final double[] wCX = w.sCX[s], wCZ = w.sCZ[s], wPHI = w.sPHI[s], wPSI = w.sPSI[s], wNRM = w.sNRM[s], wB = w.sB[s];
        // ★ 必须清零：只有内圈 7x7 会被写入，而下面的归一化遍历全部 81 格。
        // 不清零 = 外圈保留上一次构建的残值 ⇒ sref/cref 被污染 ⇒ **TalosField 不再是 (x,z,seed) 的纯函数**
        // （实测：换缓存策略后 LEVEL 从 0.6516 漂到 0.6620）。Python 原型用全新数组（外圈为 0）⇒ 这里必须对齐。
        java.util.Arrays.fill(wNRM, 0.0);
        java.util.Arrays.fill(wB, 0.0);
        // （E127 起，统计场的核插值用【每个格自己的位置 wCX[k]/wCZ[k]】，不用窗口中心。）
        // 1) XY 全 9x9（唯一会调用 cellX/cellZ 的地方）
        for (int p = 0; p < WIN; p++) for (int q = 0; q < WIN; q++) {
            int k = p * WIN + q; long I = ci - WOFF + p, J = cj - WOFF + q;
            wCX[k] = cellX(I, J, seed); wCZ[k] = cellZ(I, J, seed);
        }
        // 2) PHI 内 [1, WIN-1]、PSI 全 WINxWIN
        for (int p = 1; p < WIN - 1; p++) for (int q = 1; q < WIN - 1; q++) {
            int k = p * WIN + q; wPHI[k] = cellPHI(wCX[k], wCZ[k], seed);
        }
        for (int k = 0; k < WIN * WIN; k++) wPSI[k] = fbm(wCX[k], wCZ[k], seed ^ 0x77L, 2, 7_000_000.0, G08);
        // 3) 逐内格（★ E125 修：7x7，原来是 5x5）：父、汇聚、预算噪声 —— 全部走记忆化数组
        //
        //   ⚠ 为什么必须是 7x7 而不是 5x5（2026-09-26，用户看图抓到硬切方块后定位）：
        //   核半径 RW = 2.2*DCELL，而控制点带 jitter（JIT=0.40 ⇒ ±0.2 格）
        //   ⇒ 查询点能感知到的最远控制点距离 = 2.2 + 0.2 = 2.4 格 ⇒ ceil = 3
        //   ⇒ 所以读格必须覆盖 di,dj ∈ [-3,3]。
        //   原来只算 5x5（di,dj ∈ [-2,2]）⇒ 当查询点跨过 cj*DCELL 时，窗口中心随 cj 改变，
        //   而 J = cj±2 与查询点的距离只有 2.0*DCELL < RW ⇒ 权重非零（(1-(2/2.2)^2)^4 = 9.1e-4）
        //   ⇒ 那一项本该有贡献却不在窗口里 ⇒ 归一化后 bfield 在每条 DCELL 边界上跳变。
        //   P1185 实测：z = 5*DCELL 处 bf 跳 0.007254（而 hf 连续）⇒ 高程跳 543 m（该扫描均值 3.4 m/km）。
        //   ★ 窗口 WIN=10 / WOFF=5 ⇒ 本循环 p,q ∈ [2, WIN-2] = [2,8]（7x7），
        //     而 bfield 读 WOFF+di = 5+di ∈ [2,8]（di,dj ∈ [-3,3]）⇒ 【两者重合】✓
        //     为什么 p 不能取 1：下面 :gxb 用 (bi-1)，而 bi ≥ p-1 ⇒ 需要 p ≥ 2。
        for (int p = 2; p < WIN - 2; p++) for (int q = 2; q < WIN - 2; q++) {
            int k = p * WIN + q; double phi = wPHI[k]; int bi = p, bj = q; double best = phi;
            for (int[] d : NB) { int kk = (p + d[0]) * WIN + (q + d[1]); double pn = wPHI[kk];
                if (pn > best) { best = pn; bi = p + d[0]; bj = q + d[1]; } }
            double conv = 0;
            if (best > phi) {
                double gxs = (wPSI[(p+1)*WIN+q] - wPSI[(p-1)*WIN+q]) / (2 * DCELL);
                double gzs = (wPSI[p*WIN+q+1] - wPSI[p*WIN+q-1]) / (2 * DCELL);
                double gxb = (wPSI[(bi+1)*WIN+bj] - wPSI[(bi-1)*WIN+bj]) / (2 * DCELL);
                double gzb = (wPSI[bi*WIN+bj+1] - wPSI[bi*WIN+bj-1]) / (2 * DCELL);
                double dx = wCX[bi*WIN+bj] - wCX[k], dz = wCZ[bi*WIN+bj] - wCZ[k];
                double nn = Math.sqrt(dx * dx + dz * dz) + 1e-6;
                double vrel = (gxs - gxb) * dx / nn + (gzs - gzb) * dz / nn;
                conv = vrel > 0 ? vrel : 0;
            }
            wNRM[k] = fbm(wCX[k], wCZ[k], seed ^ 0x88L, 2, 12_000_000.0, G08);
            wB[k] = conv;
        }
        double[] bn = wNRM;
        // 解析归一化（E116）：SREF 取该窗的 sigma，CREF 用固定解析尺度（见 §308）
        //
        // ⚠★ 2026-09-26（C 步实验）：这里【回退】成「每窗口统计」—— 因为 E126 把它换成
        //   calOf(seed) 的每种子常数后，虽然接缝归零，但【地形的粗糙度涨了 2.5 倍】
        //   （P1184 实测 mean |dH| per 1 km：3.404 -> 8.450 m），
        //   而那通过「地形 -> 风应力/降水 -> 大洋环流」的链让 P442/P292/P479 三个环流判据翻掉。
        //   原因：本段把每个窗口的 wB 拉伸到 [0,1] 的同一分布 ⟹ 【压缩极端值】⟹ 地形被人为平滑。
        //   ⟹ 所以「连续」与「这个统计效果」是两件事，E126 只解决了前者。
        //   完整修法（未实施）是让 sref/cref 成为【位置的连续函数】而数值范围接近本段的典型值。
        //   calOf/convAt 保留在文件中（纯函数、无副作用），供那个完整修法使用。
        // ★★★ 2026-09-26（D2 实验）：每窗口统计，但【分母用真值格数】而不是 bn.length。
        //
        //   修的 bug（P1190 实测定位）：原来 mu/sref/cref 的【分母是 bn.length = WIN*WIN = 100】，
        //   而 E125 之后只有内圈 p,q ∈ [2, WIN-2] = 49 格有真值（其余 51 格是清零后的 0）。
        //   ⟹ 三个统计量被【系统性低估约 sqrt(100/49) = 1.43 倍】（实测比值 1.78）
        //   ⟹ sref 偏小 ⟹ bn/(3*sref) 偏大 ⟹ 归一化【过度拉伸】
        //   ⟹ 大量 wB 被 clamp 到 0/1 ⟹ 大片平坦区 ⟹ 【地形被人为平滑】（3.404 m/km）
        //   而 E126 用正确分母的固定采样给出 0.3098 ⟹ 地形恢复真实起伏（8.450 m/km）。
        //
        //   ⟹ 本实验 = 「只修分母，保留每窗口自适应」：
        //      若它也让 P442/P292/P479 翻 ⟹ 就证明【翻门的原因是「地形不再被过度拉伸」】，
        //      而不是 E126 的「常数化」。
        // ★ (E1) 2026-09-26（用户裁决）：恢复 E126 的「每种子常数」标尺。
        //   理由（§7545 的三列对照）：原始代码有【两个缺陷】——
        //     ① 读写 5x5 而 RW = 2.2*DCELL ⟹ 支撑边界掉项
        //     ② 分母 bn.length = 100 而只有 49 格有真值 ⟹ 统计量低估 1.43 倍 ⟹ 过度拉伸 ⟹ 地形被平滑
        //   而 P292/P442/P479 三个环流判据【是在那两个缺陷的产物上标定的】⟹ 修缺陷它们就翻。
        //   用户裁决：保留修正（统计正确 + 接缝归零），那三个门【重标定】。
        //   ⇒ 所以这里用 calOf(seed) 的正确分母标尺。
        // ★★★ E127：逐格归一化 —— 每个格用自己的【场值】sref(I,J)/cref(I,J)，
        //   而场值 = 用【bfield 的同一个 C3 核】对控制点格上的 statAt 做加权平均。
        //   ⟹ ① 标尺随位置变（保留局部自适应，粗糙度不被拉走）
        //      ② 标尺随位置连续（无 DCELL 接缝）
        //   ⚠ 代价：每格 49 次 statCached 查找（有 LRU 缓存；statAt 本身约 880 次 fbm，只算一次）
        for (int p = 2; p < WIN - 2; p++) for (int q = 2; q < WIN - 2; q++) {
            int k = p * WIN + q;
            final long I = ci - WOFF + p, J = cj - WOFF + q;
            // 场值：同一个核（RW、di/dj ∈ [-3,3]）对 statAt 的加权平均
            // ⚠ 评估点 = 【该格自己的位置】（不是窗口中心）—— 否则同一格在不同窗口里得到不同的标尺，接缝会回来。
            final double xk = wCX[k], zk = wCZ[k];
            double numS = 0, numC = 0, den = 0;
            for (int di = -3; di <= 3; di++) for (int dj = -3; dj <= 3; dj++) {
                double[] pp = latCached(I + di, J + dj, seed);      // ★ E128
                double dx = xk - pp[0];
                double dz = zk - pp[1];
                double tt = 1 - (dx * dx + dz * dz) / RW2; if (tt <= 0) continue;
                double kw = tt * tt * tt * tt;
                double[] st = statCached(I + di, J + dj, seed);
                numS += kw * st[0]; numC += kw * st[1]; den += kw;
            }
            double srefL = den > 0 ? numS / den : 1e-9;
            double crefL = den > 0 ? numC / den : 1e-12;
            if (srefL < 1e-12) srefL = 1e-12;
            if (crefL < 1e-30) crefL = 1e-30;
            double t = 0.65 * (bn[k] / (3.0 * srefL)) + 0.35 * Math.tanh(wB[k] / crefL);
            double v = 0.5 + 0.5 * t;
            wB[k] = v < 0 ? 0 : (v > 1 ? 1 : v);
        }
    }

    /**
     * ⚠ 陷阱（验收套件在 P296/P268/P297 上抓到）：
     * 原来写的是 {@code Math.abs(ci - winCI) <= 2}，而哨兵值是 {@code Long.MIN_VALUE} ——
     * {@code ci - Long.MIN_VALUE} 会 **long 溢出**，{@code Math.abs} 溢出后为负，{@code <= 2} 恒真
     * ⇒ **窗口从未建立** ⇒ {@code bfield} 用垃圾索引读到界外（ArrayIndexOutOfBounds）或读到脏值（下游挂死）。
     * 修法：用显式 {@code winValid} 标志（不用哨兵比较），并且差值用范围比较（不调 Math.abs）。
     */
    /**
     * 命中则复用槽，未命中才重建。
     *
     * <p>两级命中：① 直接映射槽的精确键命中；② **扫描所有槽找 |dci|<=2 且 |dcj|<=2 的同种子窗口**
     * —— 后者是 P536 时代的容差逻辑，粗步长采样时能省掉大量重建（P539 实测：去掉它 604 -> 712 ns）。
     */
    /**
     * ★★ E124：窗口中心**必须精确等于查询格**（容差半径 = 0）。
     *
     * <p>为什么原来的 ±2 容差是错的（P540 实测抓到，同一线程连续两次差 0.23）：
     * 窗口当时**只计算内圈 5x5 的 Bn**（即格 `[winCI-2, winCI+2]`），而容差命中允许查询格离中心 2 格 ⇒
     * 查询格 `ci = winCI+2` 时要读格 `[winCI, winCI+4]` —— **`winCI+3/+4` 在已计算区之外**，
     * （★ E125 之后内圈是 7x7 ⇒ 该论证的边界相应外移，但「容差 = 0」这条修法本身仍然必须保留）
     * 那里是归一化循环写进去的 0.5（外圈从 0 被归一化成 0.5）⇒ **静默错值**。
     *
     * <p>症状不是崩溃而是「结果依赖调用历史」：冷缓存时走精确路径（对），热缓存时走容差路径（错）。
     * 这也解释了为什么主线程与工作线程的 LEVEL 会不同（0.680054516 vs 0.681593467）。
     *
     * <p>代价：重建更频繁（P539 实测 604 -> 712 ns）。**正确性优先**；256 槽缓存 + 顺序访问已把代价压回去。
     */
    static int selectWindow(Win w, long ci, long cj, long seed) {
        long key = mix(seed, ci, cj);
        int s = (int) ((key >>> 40) & (NSLOT - 1));
        if (w.slotKey[s] == key) { w.cur = s; w.winCI = ci; w.winCJ = cj; w.winSeed = seed; return s; }
        buildWindow(w, ci, cj, seed);
        return w.cur;
    }

    /** 预算场：紧支撑 C3 核插值（E113 —— 进出支撑区权重为 0，无格缝）。 */
    public static double bfield(double x, double z, long seed) {
        long ci = (long) Math.floor(x / DCELL), cj = (long) Math.floor(z / DCELL);
        final Win w = TL.get();
        int s = selectWindow(w, ci, cj, seed);
        final double[] wCX = w.sCX[s], wCZ = w.sCZ[s], wB = w.sB[s];
        double num = 0, den = 0;
        // ★ E125：读 7x7（原来是 5x5）—— 理由见 buildWindow 第 3 步的注释（RW 2.2 + jitter 0.2）。
        for (int di = -3; di <= 3; di++) for (int dj = -3; dj <= 3; dj++) {
            // 窗口恒以 (ci,cj) 为中心 ⇒ p,q = WOFF+di / WOFF+dj = 5+di ∈ [2,8]，
            // 而 buildWindow 恰好把 [2, WIN-2] = [2,8] 写成真值 ✓（两者必须一致）
            // 窗口中心是 winCI（容差路径下 != ci）⇒ 索引 = WOFF + di + (ci - winCI)。符号写反会静默取错格。
            int k = (WOFF + di + (int) (ci - w.winCI)) * WIN + (WOFF + dj + (int) (cj - w.winCJ));
            if (k < 0 || k >= WIN * WIN) continue;   // 容差路径下的保险（不是死代码）
            double dx = x - wCX[k], dz = z - wCZ[k];
            double t = 1 - (dx * dx + dz * dz) / RW2; if (t <= 0) continue;
            double kw = t * t * t * t;      // 核权重（原名 w，与 ThreadLocal 工作区 w 撞名）
            num += kw * wB[k]; den += kw;
        }
        return den > 0 ? num / den : 0.5;
    }

    /** 细节层（宏观海岸线粗糙度）。 */
    /**
     * hf 的八度数。<b>§7733：7 → 13</b>。
     *
     * <p><b>为什么改</b>：{@code P1362} 实测基础高度在 <b>4096 格（4 km）内只变 1.14 格</b>
     * —— 也就是【地形在 46.9 km 以下完全没有能量】。后果是侵蚀滤镜的
     * {@code cell} 结构（每个 cell 中心必有一条白纹，见 runevision 博客）<b>没有任何
     * 大尺度地形去淹没它</b> ⟹ 在 {@code /talosmap} 与游戏里表现为多边形线
     * （{@code §7728} 的 {@code P1342} 二分实测：关掉侵蚀 ⟹ 线完全消失）。
     *
     * <p><b>为什么是 13</b>：{@code HF_WL_MIN = 3,000,000 / 2^12 = } <b>732 m</b>
     * —— 覆盖到 km 量级，正好落在侵蚀 cell（105 格）与侵蚀最细波长（97.7 格）
     * 的【上一级】，两者不再互相顶替，而是各管一段。
     */
    public static final int HF_OCT = 13;
    /** hf 的最粗波长（m）（原来写死在 hf 里）。 */
    public static final double HF_WL0 = 3_000_000.0;
    /**
     * ★ §7589：hf 的【最短】波长（m）= 最粗波长 / 2^(OCT-1) = 3,000,000 / 64 = 46,875。
     * 这是地形的最短特征尺度；任何【地形差分】的步长都必须与它同量级或更小，否则山脉会被混叠掉。
     */
    public static final double HF_WL_MIN = HF_WL0 / (double) (1L << (HF_OCT - 1));


    // ================= ★★★★★★★★ §7664：分段 fBm（hf 的频段分离） =================
    /**
     * <b>hf 的【分割倍频】</b>：oct &lt; SPLIT 用 {@link #HH}（低频段），
     * oct &ge; SPLIT 用 {@link #HH_HIGH}（高频段）。**
     *
     * <p><b>出处</b>（`04-行星科学与气候耦合.md:322-324` 逐字）：**
     * 「与第 3 条结论（H ~ 0.6-0.8 才能得到 D ~ 1.2-1.4）冲突吗？【不冲突，但必须分区处理】：
     *   海岸线维数由【海岸线所在尺度段的 H】决定；小尺度粗糙度由【高频 octave 的振幅】决定。
     *   推荐做法：**
     *     · 低频（大陆尺度，&gt;500 km）：H_low ~ 【0.7】（保证海岸线 D ~ 1.3）**
     *     · 高频（&lt;10 km，纹理）：用【更小 H】或【额外的高频层】，只要振幅足够小，
     *       就不会显著改变海岸线的分形维数」**
     *
     * <p><b>为什么是 3</b>：hf 的最粗波长 = {@link #HF_WL0} = 3,000 km，倍频后**
     * oct 0/1/2 = 3,000/1,500/750 km（&gt;500 km，属低频段），**
     * oct 3/4/5/6 = 375/187/94/47 km（&lt;500 km，属高频段）。**
     * 所以分割点【由出处的 500 km 直接决定】，不是自由参数 ✓**
     */
    public static final int OCT_SPLIT = 3;

    /**
     * <b>高频段的 Hurst 指数</b>（oct &ge; {@link #OCT_SPLIT}）。默认 <b>1.6</b>。**
     *
     * <p><b>出处</b>：`04:324` 逐字说高频用「【更小 H】或【额外的高频层】…只要振幅足够小」**
     * —— <b>而它【没有给具体值】</b> ⟹ 所以本常量是【可标定的形态参数】**
     * （与 `LAKE_MIN_DEPTH_M` / B4 的观测锚同性质）。**
     *
     * <p><b>为什么需要它</b>（P1316 实测）：单一 `HH` 无法两全 ——**
     * 出处推荐范围（0.6-0.8）只让 max acc 提升 1.17-1.39 倍（远远不够），**
     * 而要 3-6 倍必须 H &ge; 1.1，但那会把 ocean% 从 6.8% 推到 22.2%（海陆崩）⚠**
     *
     * <p><b>而分段后两者兼得</b>（P1317 实测）：**
     * <pre>
     *   H_high | ocean% | max acc | max/median
     *     0.70 |   6.8% |   1,023 |      341.0   （单段，对照）
     *     1.20 |   6.8% |   2,429 |      485.8
     *     1.60 |   6.8% | 【4,236】| 【605.1】
     *     2.00 |   6.8% |   5,247 |      524.7
     * </pre>
     * ⟹ <b>ocean% 恒为 6.8%（海陆完全不变）而 max acc 提升 5.1 倍</b> ✓✓✓**
     */
    /**
     * <b>细节层高频段 Hurst 指数。</b>
     *
     * <p><b>§7712 标定（ETOPO 2022 实测）：</b>
     * 8 块 15 arc-sec 瓦片（463 m/px，拟合频段 1–1667 km）逐块径向谱 + 平均：
     * <b>beta = 2.7100，R² = 0.9960</b>；与 10 arc-min 的重叠区间（75–1667 km）
     * <b>几乎完全重合 ⟹ 地球在 1–3562 km 是【单一幂律】</b>。
     * {@code beta = 2H + 2} ⟹ <b>H = 0.3550</b>。
     * 脚本：{@code K:/moder/TT/etopo_spectrum/extract_15s.py}。
     *
     * <p>旧值 1.6 是早期估计（无出处），会让 beta = 5.20，比地球陡 2.44 ——
     * 后果是 375 km 以下几乎没有能量，地形过光滑，侵蚀滤镜没有东西可咬。
     */
    public static double HH_HIGH = 0.3549;

    /**
     * ★★★★★★★★ <b>§7664：分段 fBm 的增益表 —— hf 专用</b>。**
     *
     * <p>与 {@link #fbm} 同构，只把「每 octave 乘同一个 gain」换成**
     * 「oct &lt; {@link #OCT_SPLIT} 乘 {@code 2^-HH}，否则乘 {@code 2^-HH_HIGH}」。**
     * 归一化（{@code r/tot}）保持不变 ⟹ 值域仍是 [-1,1] ⟹ 不改变 {@link #AN} 的标定 ✓**
     */
    static double fbmBand(double x, double z, long s, int oct, double wl0) {
        // ★ §7753：vnoise -> Simplex + 每 octave 黄金角旋转。
        //   依据（§7714 逐字 + P1452/P1453 实测）：value noise（双线性）有格状晶格，
        //   会在【整列/整行】上产生贯穿的直线（P1453 的 fix_vnoise.png 拍到了一条完美的
        //   竖直直线，位置正好在 vnoise 的格点边界 x = -2,000,000）。
        //   实测方向性：vnoise 3.05 / 5.12  ->  Simplex 1.45（= 各向同性标定值）。
        //   黄金角旋转让各 octave 的晶格朝向互相抵消（DetailNoise 已在用同一套）。
        SimplexNoise2D sn = simplexFor(s);
        double r = 0, a = 1, f = 1.0 / wl0, tot = 0;
        double ca = 1.0, sa = 0.0;
        final double cR = GOLDEN_COS, sR = GOLDEN_SIN;
        for (int o = 0; o < oct; o++) {
            double xx = x * ca - z * sa, zz = x * sa + z * ca;
            r += a * sn.noise2(xx * f, zz * f);
            tot += a;
            a *= (o < OCT_SPLIT) ? GHH : GH2;
            f *= 2;
            double nc = ca * cR - sa * sR, ns = ca * sR + sa * cR;
            ca = nc; sa = ns;
        }
        return r / tot;
    }
    /** {@link #HH_HIGH} 的预计算增益（{@code setHurstHigh} 会刷新它）。 */
    static double GH2 = Math.pow(2, -HH_HIGH);
    /** 改高频段 H 必须同时刷新 {@link #GH2}。 */
    public static void setHurstHigh(double h) { HH_HIGH = h; GH2 = Math.pow(2, -h); }
    /**
     * hf —— 细节层。**§7664 起走【分段 fBm】（{@link #fbmBand}）**：**
     * 低频段（oct &lt; {@link #OCT_SPLIT}，波长 &gt; 500 km）用 {@link #HH}（保住海岸线维数），**
     * 高频段用 {@link #HH_HIGH}（弱化纹理 ⟹ 河网能长出层级）。**
     */
    public static double hf(double x, double z, long seed) { return fbmBand(x, z, seed ^ 0x99L, HF_OCT, HF_WL0); }

    /**
     * ★★★★★★★★ <b>§7738：把频谱【向下延伸】—— 补上 {@link #HF_WL_MIN} 以下的 octave。</b>
     *
     * <p><b>为什么需要</b>（P1379~P1391 实测）：本仓地形的最细波长是 {@code HF_WL_MIN = 732 m}，
     * 而侵蚀的 cell 只有 {@code 0.105 x ERO_UNIT = 105 格}。也就是说<b>侵蚀作用的尺度比地形
     * 最细的结构还小 7 倍</b> ⟹ 侵蚀 cell 的接缝【没有东西可以掩盖】⟹ 图上出现规则多边形。
     *
     * <p><b>实测约束</b>：接缝幅度 = {@code 0.0395 x ERO_AMP}（格，严格线性），
     * 要它被地形掩盖，需要地形在 105 格上的 rms 粗糙度不少于接缝幅度。
     * 而现在该粗糙度只有 <b>0.158 格</b>（对应 {@code ERO_AMP <= 4}，即侵蚀等于没有）。
     *
     * <p><b>本方法</b>：继续用 {@link #HH_HIGH} 的增益阶梯，在 {@code HF_WL_MIN} 之下
     * 再加 {@code extra} 个 octave（波长 366 / 183 / 92 / 46 m ...）。
     * <b>这不是新旋钮</b>：它的增益沿用已由 ETOPO 标定的 {@code HH_HIGH}，
     * 只是把<b>已经存在的频段</b>延长，而不是引入新的可调量。
     *
     * <p><b>归一化口径</b>：返回 {@code sum(a_o * vnoise)}，<b>不做 tot 归一化</b> ⟹
     * 量级与 {@link #hf} 的分子一致，可直接乘 {@link #AN} 后相加。
     *
     * @param extra 额外 octave 数（0 = 与今天逐位相同）
     */
    public static double hfExtra(double x, double z, long seed, int extra) {
        if (extra <= 0) return 0.0;
        // ★ §7753：同上，vnoise -> Simplex + 黄金角旋转（理由见 fbmBand）。
        SimplexNoise2D sn = simplexFor(seed + 0xE77L);
        double r = 0, a = 1.0, wl = HF_WL_MIN;
        double ca = 1.0, sa = 0.0;
        final double cR = GOLDEN_COS, sR = GOLDEN_SIN;
        for (int o = 0; o < extra; o++) {
            wl *= 0.5;
            a *= GH2;
            double xx = x * ca - z * sa, zz = x * sa + z * ca;
            r += a * sn.noise2(xx / wl, zz / wl);
            double nc = ca * cR - sa * sR, ns = ca * sR + sa * cR;
            ca = nc; sa = ns;
        }
        return r;
    }
    // ================= ★★★★★★★★ §7643：各向异性（沿等高线拉伸） =================
    /**
     * <b>各向异性强度 K</b>：噪声坐标沿【等高线方向】被【拉伸 K 倍】（等价于垂直方向压缩）。
     *
     * <p><b>出处</b>（`01-板块构造模拟.md:393` 逐字）：
     * 「『沿脊线方向的各向异性噪声』比各向同性 FBM 更像山：可以用 H 的梯度方向来做
     * 「各向异性拉伸（例如把噪声坐标沿等高线方向压缩 4 倍）。这是一个 10 行代码的改动，
     * 「观感提升很大。」
     *
     * <p>⚠ <b>K = 4 是那句里的【示例值】，不是逐字常数</b> ⟹ 它是一个【可标定的形态参数】
     * （与 `LAKE_MIN_DEPTH_M` / B4 的观测锚同性质）。
     */
    public static double ANISO_K = 4.0;

    /**
     * 各向异性梯度的差分步长（m）。
     *
     * <p>§7644 修正：原来是 HF_WL0/16（187.5 km）—— 那个步长下 `lowf` 的梯度
     * 【分片光滑**]⟹ 拉伸在梯度变化处跳变 ⟹ 条纹伪影。改成 HF_WL0/4（750 km）
     * 让梯度场更光滑（P1290 的辐射状伪影的一半原因）。
     */
    public static double ANISO_STEP = HF_WL0 / 4.0;

    /**
     * ★★★★★★★★ §7644：各向异性位移的【波长】（m）。
     *
     * <p>为什么需要它：§7643 的位移 p' = p + (K-1)·(p·t̂)·t̂ 【以原点为中心】⟹
     * 离原点越远畸变越大 ⟹ P1290 图上出现【辐射状伪影**]。
     *
     * <p>修法：把位移改成【有界、周期】的形式
     * <pre>
     *   shift(s) = A · sin(2π s / λ)          s = p·t̂（沿等高线的弧长）
     *   A        = (K-1) · λ / (2π)           ⟸ 由「最大局部拉伸率 = K」反解
     * </pre>
     * 这样位移【不依赖原点】且【有界】（|shift| &le; A）⟹ 无辐射伪影。
     *
     * <p>λ 取 `HF_WL0 / 4`（= 750 km）：比 hf 的最粗波长小一个量级，
     * 使拉伸在【一个山系的尺度上】变化，而不是在整张图上。
     */
    public static double ANISO_LAMBDA = HF_WL0 / 4.0;

    /** 低频场（只用来求坡向）：oct=2, wl0 = hf 的最粗波长。用一个独立种子避免与 hf 相关。 */
    static double lowf(double x, double z, long seed) {
        return fbm(x, z, seed ^ 0xADL, 2, HF_WL0, G08);
    }

    /**
     * ★★★★★★★★ <b>§7644：各向异性的 hf（修正版）</b>。
     *
     * <p>做法（照 `01:393` 的两步）：
     * <ol>
     *   <li>用低频场 `lowf` 的【梯度】定出局部的【坡向】ĝ；
     *   <li>把 hf 的噪声坐标【沿【垂直于 ĝ 的方向（等高线方向）】做【周期性位移】。
     * </ol>
     *
     * <p>⚠⚠ <b>§7645：方向场从「梯度」改成【构造走向场】—— 因为梯度方案在拓扑上必然失败</b>。
     * P1290 实测：用 H 的梯度方向定 t̂ 时，t̂ 在梯度翻转处【瞬间跳 180°】
     * ⟹ 位移场不连续 ⟹ 图上出现【强烈的放射状/旋涡状条纹】。
     * 而那【不可避免】：球面上不存在无奇点的连续切向量场（毛球定理）。
     *
     * <p>本版改用两个独立大尺度标量场的比值定方向：
     * <pre>
     *   t̂ = ( grainA, grainB ) / |(grainA, grainB)|
     * </pre>
     * 奇点只在 {@code grainA = grainB = 0} 的【孤立点】（fbm 过零交集，测度为零）⟹ 可忽略。
     *
     * <p><b>代价</b>：多 2 次 `fbm(oct=2)`（各约 0.02 us）⟹ 约 0.04 us/call，
     * 而 `bfield` 是 4.77 us ⟹ 【不改变量级】。
     *
     * <p>⚠ 若 `ANISO_K &lt;= 1` 或方向退化 ⟹ 退回各向同性（逐位同旧）。
     */
    public static double hfAniso(double x, double z, long seed) {
        if (ANISO_K <= 1.0) return fbm(x, z, seed ^ 0x99L, HF_OCT, HF_WL0, GHH);
        // ★★★★★★★★ §7646：域弯曲式坐标扰动 —— 【不用方向场**]。
        //   §7645 用 t̂ = (grainA,grainB)/|·| 求 s = p·t̂ ⟹ 而 t̂ 【随位置旋转**]
        //   ⟹ s 在旋转处剧烈跳变 ⟹ sin(2πs/λ) 跳变 ⟹ 图上【仍有大尺度旋涡条纹**]（P1290 实测）⚠
        //   ⟹ 根因与 §7644 相同：**任何依赖「方向」的构造都会在方向的奇点/旋转处断裂**]。
        //
        //   ★ 正解（也是 Minecraft 域弯曲的做法，02:171 逐字「只应该形变 X」）：
        //     直接对【噪声坐标】做一个【纯标量驱动的位移**]——没有方向、没有 t̂、零奇点：
        //         x' = x + A·sin(2π·grainB(x,z)/λ)
        //         z' = z + A·sin(2π·grainA(x,z)/λ)
        //     因为 grainA/grainB 是【大尺度场**]，它们的等值线【本来就是长条纹**]
        //     ⟹ 位移沿那些条纹变化 ⟹ 噪声特征被【沿条纹拉长**]⟹ 隐式的各向异性 ✓
        double lambda = ANISO_LAMBDA;
        double amp = (ANISO_K - 1.0) * lambda / (2.0 * Math.PI);
        // ⚠ §7646 的第一版写成 amp*sin(2π·grain/λ)：而 grain ∈ [-1,1] ⟹ 相位只有 ~4e-6 弧度
        //   ⟹ 位移只有 ~1.5 米（P1294 实测 ratio = 0.0000，完全 no-op）⚠
        //   ⟹ 修：直接【用 grain 本身作位移**]——它已经是【大尺度平滑场**]（波长 750 km），
        //     所以位移的空间尺度天然就是 750 km，不需要再套一层 sin。
        double dx = amp * grainB(x, z, seed);
        double dz = amp * grainA(x, z, seed);
        return fbmBand(x + dx, z + dz, seed ^ 0x99L, HF_OCT, HF_WL0);   // ★ §7664：走分段 fBm
    }

    /**
     * §7646：域弯曲的驱动场 A（大尺度、平滑）。
     *
     * <p>⚠ 波长【必须与 ANISO_LAMBDA 同量级】：§7645 原来用 HF_WL0 * 2（= 6,000 km），
     * 而在 3,840 km 的图上那【几乎是常数】⟹ 位移退化成【整体平移】⟹ 统计不变
     * （P1269 实测：max len 与各向同性【逐位相同】）。改成 HF_WL0/4 = 750 km ⟹ 图上 5 个周期 ✓
     */
    static double grainA(double x, double z, long seed) {
        return fbm(x, z, seed ^ 0xB1L, 2, HF_WL0 / 4.0, G08);
    }
    /** §7646：域弯曲的驱动场 B。与 A 独立（不同种子）。 */
    static double grainB(double x, double z, long seed) {
        return fbm(x, z, seed ^ 0xB2L, 2, HF_WL0 / 4.0, G08);
    }
    /** 洋壳年龄场（与骨架解耦，E114）。 */
    public static double age(double x, double z, long seed) {
        double t = 0.5 + 0.5 * fbm(x, z, seed ^ 0xAAL, 3, 9_000_000.0, G08);
        return 70.0 * (t < 0 ? 0 : (t > 1 ? 1 : t));
    }
    /** 未减海平面的场值。 */
    /**
     * ★★★★★★★★ §7643：各向异性总开关（默认 <b>true</b>）。
     *
     * <p>{@code false} ⇒ 退回各向同性的 {@link #hf} ⟹ <b>与接线前逐位相同</b>（回滚点）。
     * <p>⚠ 它<b>改变地形</b> ⟹ 必须进 {@link #configStamp()} 那一族（让缓存失效）。
     */
    public static boolean ANISO = true;

    /** 未减海平面的场值。§7643 起 hf 走各向异性（{@link #ANISO} 可回滚）。 */
    /**
     * ★ <b>§7738：{@link #hfExtra} 的振幅（相对 {@link #AN} 的倍数）。0 = 与今天逐位相同。</b>
     *
     * <p><b>为什么要它</b>（P1379~P1394 实测）：侵蚀的 cell 是 {@code 0.105 x ERO_UNIT} 格，
     * 而本仓地形最细波长只有 {@code HF_WL_MIN = 732 m} ⟹ 侵蚀作用的尺度上地形几乎是平面
     * ⟹ 侵蚀 cell 的接缝没有东西可掩盖（图上就是那些多边形线）。
     *
     * <p><b>标定依据</b>：要把 {@code ERO_AMP} 提到能做出 60~100 格起伏，
     * 地形在 105 格尺度上的 rms 粗糙度必须同量级。P1393 实测：
     * {@code ERO_EXTRA_AMP} 从 0 到 0.60，该粗糙度从 <b>0.213 格</b> 涨到 <b>12.18 格</b>（57 倍），
     * 而 {@code seam/地形粗糙度} 从 <b>74 倍</b> 降到 <b>1.30 倍</b>。
     *
     * <p><b>不是新旋钮</b>：它只缩放 {@link #hfExtra}，而后者的增益阶梯沿用已由 ETOPO
     * 标定的 {@link #HH_HIGH}；它补的是<b>本来就该有、现在缺失</b>的频段（366 m 以下）。
     */
    public static double ERO_EXTRA_AMP = 0.25;

    /** 未减海平面的场值。§7643 起 hf 走各向异性（{@link #ANISO} 可回滚）。 */
    public static double fieldValue(double x, double z, long seed) {
        return fieldValue(x, z, seed, ERO_EXTRA_AMP);
    }

    /**
     * ★ <b>§7739：允许调用方【自己指定】细频段振幅</b>（用于「细结构随坡度变化」）。
     *
     * <p>{@code extraAmp = 0} ⟹ 与接线前逐位相同（回滚点）。
     * 传 {@link #ERO_EXTRA_AMP} 则与无参版本逐位相同。
     */
    public static double fieldValue(double x, double z, long seed, double extraAmp) {
        return bfield(x, z, seed) + AN * (ANISO ? hfAniso(x, z, seed) : hf(x, z, seed))
             + (extraAmp > 0.0 ? AN * extraAmp * hfExtra(x, z, seed, HF_EXTRA_OCT) : 0.0);
    }

    /** {@link #ERO_EXTRA_AMP} 使用的额外 octave 数（1 = 366 m）。 */
    public static int HF_EXTRA_OCT = 1;

    // ================= 每世界自标定（O(1) 于列数） =================
    // （E123：原 lvlSeed/lvl 的静态缓存已删 —— 那是不受保护的共享可变状态）
    // ★ E121：只缓存一个种子是致命的 —— 调用方只要交替传不同种子，
    // 每一次 isLandWithCell 都会重跑整次 1024 点标定（0.5 ms）=> P442 慢 1000 倍的根因（jstack 实证）。
    /**
     * 1024 点、跨 +-100,000 km（= 200,000,000 block）的固定采样上取 70% 分位。
     *
     * <p>E121：只缓存一个种子是致命的 —— 调用方交替传不同种子时，每一次 {@code isLandWithCell}
     * 都会重跑整次标定（0.5 ms）=> 探针慢 1000 倍（jstack 实证）。故按种子做 8 项记忆表。
     *
     * <p>E123：记忆表也在 {@link ThreadLocal} 里 ⇒ **不再需要 synchronized**（每线程各算一份，值相同）。
     */
    public static double level(long seed) { return level(TL.get(), seed); }
    /** §7665：清空 level 的每线程记忆表（改 LEVEL_HALF_SPAN 之后【必须】调用）。 */
    public static void __clearLevelMemo() { TL.get().lvlN = 0; }


    // ================= ★★★★★★★★ §7665：海平面标定的两个可标定量 =================
    /**
     * <b>海平面标定的【采样半宽】（米）</b>。原来是 <b>1.0e8</b>（= ±100,000 km，即 5 个世界跨度）。**
     *
     * <p><b>为什么必须改小</b>（P1319 实测，决定性）：`level` 的作用是给 `fieldValue` 定海平面，**
     * 而它采样出来的 q70 与【实际地图区域】的分布【差很多】：**
     * <pre>
     *   level(±1e8 采样的 q70) = 0.618658
     *   而地图区域（3,840 km）的分布：q10=0.6079  q50=0.6595  q70=0.6861  q93=0.7355
     *   ⟹ level 【远低于局部中位数】⟹ land = 83.6% 而 ocean = 16.4%（目标 6.8%）⚠
     * </pre>
     * ⟹ 所以要让它标在【玩家实际能到达的范围】上 ⟹ 本常量给出采样半宽。**
     *
     * <p>⚠ 这是<b>标定量</b>，不是逐字常数（与 `LAKE_MIN_DEPTH_M` 同性质）。**
     */
    public static double LEVEL_HALF_SPAN = 100_000_000.0;   // ★ 保持原值（1.0e8）；只标定 LEVEL_QUANTILE

    /**
     * <b>海平面标定的【分位】</b>。原来是 0.70（= 70% 陆地）。**
     *
     * <p>⚠ 它现在是【与 {@link #LEVEL_HALF_SPAN} 配套的标定量】：两者一起决定 ocean%。**
     */
    public static double LEVEL_QUANTILE = 0.655;
    static double level(Win w, long seed) {
        for (int i = 0; i < w.lvlN; i++) if (w.lvlSeeds[i] == seed) return w.lvlVals[i];
        final int G = 32, N = G * G;
        double[] f = new double[N];
        java.util.Random r = new java.util.Random(seed * 1000003L + 17L);
        for (int i = 0; i < N; i++) {
            // §7665：采样半宽可标定（原来写死 1.0e8 = 5 个世界跨度，与局部差很多）
            double ox = (r.nextDouble() * 2 - 1) * LEVEL_HALF_SPAN;
            double oz = (r.nextDouble() * 2 - 1) * LEVEL_HALF_SPAN;
            f[i] = fieldValue(ox, oz, seed);
        }
        java.util.Arrays.sort(f);
        double v = f[(int) Math.floor(LEVEL_QUANTILE * N)];
        if (w.lvlN < NLVL) { w.lvlSeeds[w.lvlN] = seed; w.lvlVals[w.lvlN] = v; w.lvlN++; }
        else { System.arraycopy(w.lvlSeeds, 1, w.lvlSeeds, 0, NLVL - 1);
               System.arraycopy(w.lvlVals, 1, w.lvlVals, 0, NLVL - 1);
               w.lvlSeeds[NLVL - 1] = seed; w.lvlVals[NLVL - 1] = v; }
        return v;
    }

    // ================= 高程：单调三次样条（Fritsch-Carlson，含平台段） =================
    static final double[] KX = {-1.60,-0.60,-0.100,-0.040,-0.016,-0.005,0.0,0.03,0.14,0.34,0.52,0.72,0.90,1.05,1.60};
    static final double[] KY = {-5600,-4400,-4180,-2150,-320,-110,0,160,430,760,1250,2300,3500,4600,5600};
    static final double[] MS = new double[KX.length];
    static {
        int n = KX.length; double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) d[i] = (KY[i + 1] - KY[i]) / (KX[i + 1] - KX[i]);
        MS[0] = d[0]; MS[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) MS[i] = (d[i - 1] + d[i]) / 2;
        for (int i = 0; i < n - 1; i++) {
            if (d[i] == 0) { MS[i] = 0; MS[i + 1] = 0; }
            else { double a = MS[i] / d[i], b = MS[i + 1] / d[i], s = a * a + b * b;
                   if (s > 9) { double t = 3 / Math.sqrt(s); MS[i] = t * a * d[i]; MS[i + 1] = t * b * d[i]; } }
        }
    }
    static double hyp(double u) {
        if (u <= KX[0]) return KY[0];
        if (u >= KX[KX.length - 1]) return KY[KY.length - 1];
        int lo = 0, hi = KX.length - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (u >= KX[mid]) lo = mid; else hi = mid; }
        int i = lo;
        double h = KX[i + 1] - KX[i], t = (u - KX[i]) / h;
        double t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * KY[i] + (t3 - 2 * t2 + t) * h * MS[i]
             + (-2 * t3 + 3 * t2) * KY[i + 1] + (t3 - t2) * h * MS[i + 1];
    }

    /** 无锁快路径：先查小记忆表（命中即返回），真未命中才进 synchronized 的 level()。 */
    static double lvlFast(long seed) {
        final Win w = TL.get();
        for (int i = 0; i < w.lvlN; i++) if (w.lvlSeeds[i] == seed) return w.lvlVals[i];
        return level(w, seed);
    }


    // ================= ★★★★★★★★ §7666：凹形纵剖面（大陆内部更高） =================
    /**
     * <b>凹形纵剖面的幅度 k（米）</b>。
     *
     * <p><b>出处</b>（03-侵蚀与水文.md:418 逐字）：
     * 「河道的凹形纵剖面（concave-up profile）可以用一条解析曲线近似：
     *   <b>h(s) = h0 - k·(s/L)^theta</b>（s 为沿河距离，<b>theta ≈ 0.4~0.6</b>）。
     *   运行时只要估出「到海岸的近似水力距离」」
     *
     * <p><b>而本仓的 s 代理</b>：v = fieldValue - level（无量纲）。
     * 它【单调反映内陆程度】（P1325 实测：海拔随距海岸距离单调升），
     * 而它【本来就在 elevation 里算】⟹ <b>本项【零新增开销】</b>✓
     *
     * <p><b>口径</b>：
     * <ul>
     *   <li>v 无量纲（fieldValue 的单位）
     *   <li>CONCAVE_VSCALE 无量纲，把 v 归一化到 [-1,1] 之外的内陆端
     *   <li>CONCAVE_K 米 ⟹ 本项输出的单位是<b>米</b>，与 elevation 一致 ✓
     * </ul>
     *
     * <p><b>为什么需要它</b>（用户洞察 + P1322 实测）：
     * <pre>
     *   水力距离（源头 -> 海）: mean=36 cells=360 km  中位=33 cells=330 km  最长=111 cells=1,110 km
     *   而地球：Amazon=6,400 km · Mississippi=3,700 km · 「主要河流」>1,000 km
     *   ⟹ 【我们的水平均只走 360 km 就出海】⟹ 【没有大型主要河流】⚠
     * </pre>
     * 而 P1325 实测：bfield 已给出凹形剖面，但幅度只 <b>237 m 跨 1,620 km</b>
     * （真实大陆约 1,000 m）⟹ 所以本项把它补足 ⟹ 内陆更高 ⟹ 水必须走更远 ✓
     */
    public static double CONCAVE_K = 760.0;

    /** 凹形剖面的指数 theta。出处 03:418 逐字：<b>theta ≈ 0.4~0.6</b>（取中值 0.5）。 */
    public static final double CONCAVE_THETA = 0.5;

    /** v 的归一化尺度（无量纲）。取 0.18（P1297/P1325 实测的内陆端 v 值）。 */
    public static double CONCAVE_VSCALE = 0.18;
    /**
     * 完整高程（米）。<b>§7666 起含凹形纵剖面项</b>（出处 03:418）——
     * 内陆比海岸高，使水【必须走更远才出海】⟹ 产生大型主要河流。
     *
     * <p><b>口径</b>：入参 (x,z) 单位【米】（世界坐标）；返回值单位【米】，相对 SEA_LEVEL。
     * <p><b>开销</b>：本项只用已经算好的 v（fieldValue 与 lvlFast 本来就要调）
     * ⟹ 【零新增地形查询】✓
     */
    public static double elevation(double x, double z, long seed) {
        return elevation(x, z, seed, ERO_EXTRA_AMP);
    }

    /** ★ §7739：同 {@link #fieldValue(double, double, long, double)}，供坡度调制的调用方使用。 */
    public static double elevation(double x, double z, long seed, double extraAmp) {
        double v = fieldValue(x, z, seed, extraAmp) - lvlFast(seed);
        double h = hyp(USx * v);
        // ★ §7666：凹形纵剖面（内陆更高）。s 的代理 = v（无量纲），theta 与 k 见上方 javadoc。
        if (v > 0.0) {
            h += CONCAVE_K * Math.pow(v / CONCAVE_VSCALE, CONCAVE_THETA);
        }
        double a = age(x, z, seed);
        double ridge = -(2200.0 + 320.0 * Math.sqrt(a));
        return h > ridge ? h : ridge;
    }
    public static boolean isLand(double x, double z, long seed) { return fieldValue(x, z, seed) > lvlFast(seed); }

    /** 本生成器的全部可调参数进指纹（D58：改了结果就必须让瓦片失效）。 */
    public static long configStamp() {
        long h = 0x7A105EEDL;
        h = h * 31 + Double.doubleToLongBits(DCELL);
        h = h * 31 + Double.doubleToLongBits(SWS);
        h = h * 31 + Double.doubleToLongBits(JIT);
        h = h * 31 + Double.doubleToLongBits(AN);
        h = h * 31 + Double.doubleToLongBits(ERO_EXTRA_AMP);
        h = h * 31 + HF_EXTRA_OCT;
        h = h * 31 + Double.doubleToLongBits(HH);
        h = h * 31 + Double.doubleToLongBits(USx);
        h = h * 31 + Double.doubleToLongBits(LAM);
        h = h * 31 + Double.doubleToLongBits(RW);
        // §7666：凹形剖面的参数也改变结果 ⟹ 必须进指纹
        h = h * 31 + Double.doubleToLongBits(CONCAVE_K);
        h = h * 31 + Double.doubleToLongBits(CONCAVE_THETA);
        h = h * 31 + Double.doubleToLongBits(CONCAVE_VSCALE);
        // §7664：分段 fBm 的两个参数
        h = h * 31 + OCT_SPLIT;
        h = h * 31 + Double.doubleToLongBits(HH_HIGH);
        // §7665：海平面标定的两个参数
        h = h * 31 + Double.doubleToLongBits(LEVEL_HALF_SPAN);
        h = h * 31 + Double.doubleToLongBits(LEVEL_QUANTILE);
        return h;
    }
}
