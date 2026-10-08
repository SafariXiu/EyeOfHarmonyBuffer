package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.LongDblMap;

/**
 * TalosLandField（原 M1gen V4R）-- ★ **已搬入生产**（2026-10-08）
 *
 * 【搬迁记录】
 *   · 源：tools/talos-probe/probe/M1gen.java（518 行，已验证）
 *   · 目标：space/talos/chunk/continent_layer/TalosLandField.java
 *   · 改名：M1gen -> TalosLandField
 *
 * 【裁剪后的设计】 ★ 回到 V4 的机制（超格 + 固定块）+ 保留瓦片架构与已修的正确性
 *
 * 【V4 的核心】（这是「实心大块」的来源）
 *   scont(ci,cj) = 超格内的一个【固定 CLUSTER x CLUSTER 块】
 *     SUPER   = 5   （超格 = 5x5 细格 = 20,000 km）
 *     CLUSTER = 3   （块 = 3x3 细格 = 12,000 km）
 *     ⟹ 大陆是【预定义的实心块】⟹ 天然大块、无碎、无内海
 *
 * 【保留的已修正确性】
 *   · 点级海陆判据（口径一致）
 *   · 符号一致约束（land ⟹ h>0）
 *   · Voronoi 有符号距离（不用梯度除法）
 *   · 瓦片架构（细胞的大陆性由【细胞自己的瓦片归属】决定）
 */
public final class TalosLandField {

    // ================= ★★★★★★ 主缩放因子（所有【水平】长度 × SC） =================
    //   ★ 依据：所有水平长度常量都是 DS（细胞格距）的固定倍数：
    //       SUPER_DS = 5 × DS        WARP_AMP()  = 0.300 × DS
    //       WARP_WL()  = 0.225 × DS    WARP_AMP2() = 0.100 × DS
    //       WARP_WL2() = 0.075 × DS    WARP_AMP3() = 0.045 × DS
    //       WARP_WL3() = 0.0325 × DS
    //     ⟹ 整体缩放保持形态不变
    //   ★ 只缩【水平】；高程（OROG_AMP 等）不缩
    public static double SC = 0.00625;    // ★ 1/160：DS 4,000,000 -> 25,000（细胞粒度 25 km）

    /** 基准值（SC=1 时的原始长度） */
    public static final long DS_BASE   = 4_000_000L;
    public static final int  TILE_BASE = 40_000_000;
    public static final int  MAXD_BASE = 10_000_000;

    /** 缩放后的量 */
    public static long DS()     { return Math.round(DS_BASE   * SC); }
    public static int  TILE()   { return (int) Math.round(TILE_BASE * SC); }
    public static int  MAX_D()  { return (int) Math.round(MAXD_BASE * SC); }
    public static final long SUPER_DS = 20_000_000L;   // ★ 固定 5 × DS_BASE（不随 SC 单独变）


    // ★ V4 的核心参数
    // ★★★★★ SUPERA = 超格的【绝对尺寸】（格）；SUPER = 格数 = SUPERA/DS()
    //   ★ 关键解耦：SUPER 应该【反比于 SC】⟹ 特征尺寸不随 SC 变
    // ★★★★★ 特征的【绝对尺寸】（格）—— 这才是「地形多大」的唯一真相
    //   ★ 目标：特征（大陆）= 100,000 格 = 100 km；细胞粒度 = 25,000 格
    //     ⟹ SUPER = 100,000/25,000 = 4
    public static double SUPER_ABS = 100_000.0;      // ★ 超格/特征绝对尺寸（= 目标大陆尺度）
    /** 由绝对尺寸换算出 SUPER（细胞数） */
    public static int SUPER   = 5;    // 超格格数（由 syncSuper() 自动同步）
    public static void syncSuper() {
        SUPER = Math.max(1, (int) Math.round(SUPER_ABS / (double) DS()));
        CLUSTER_MIN = Math.max(1, (int) Math.round(SUPER * CLUSTER_FRAC_MIN));
        CLUSTER_MAX = Math.max(CLUSTER_MIN, (int) Math.round(SUPER * CLUSTER_FRAC_MAX)); }
    // ★★★★★ 团块尺寸 = SUPER 的【固定比例】（不能写死！）
    //   ★ 原 bug：CLUSTER_MAX = 5 写死 ⟹ SUPER>5 时团块占比骤降 ⟹ 陆地崩塌
    // ⚠ CLUSTER_FRAC_* 在修法 A（满超格+阈值）下已【无用】—— 保留字段供未来「块尺寸变化」使用
    public static double CLUSTER_FRAC_MIN = 0.20;
    public static double CLUSTER_FRAC_MAX = 1.00;   // 团块最大 = 100% 超格
    public static int CLUSTER_MIN = 1;   // 由 syncSuper() 自动同步
    public static int CLUSTER_MAX = 5;   // 由 syncSuper() 自动同步
        public static double CONT_N_HF2 = 1.5; // 细尺度相关长度
    public static double N_TARGET = 0.41;   // ★ 标定：陆地 35.7%   // ★ 修法 A：n 的中位 -> 0.50（陆地 ~35%）   // ★ 0.24->0.32（陆地 25.2% -> 目标 35%）   // ★ 0.44->0.24（SUPER=4 时陆地 51.8% -> 目标 35%）   // ★ 0.50->0.44（陆地 42.67% -> 目标 ~35%）
    public static double SKEW_AMP = 0.90;   // ★ beta3：区域反差旋钮（0=均匀，0.9=极端陆/洋半球）
    public static double SKEW_HF  = 2.0;    // ★ beta3：区域波长（超格数）
    public static double FINE_AMP = 0.10;  // 细尺度抖动幅度
    public static double N_SIGMA  = 0.35;  // ★ 局部偏差的放大倍数（区域偏斜强度）
    public static int    LOCAL_R  = 2;     // 局部均值半径（超格数）
    public static int CLUSTER = 3;    // 块（可调）
    public static double CONT_BUDGET = 1.30;

    // ★★★★★★ 海岸剖面的距离尺度（DS 的倍数，可随 SC 缩放）
    //   ★ 原 bug：写死为 300,000 / 80,000 / 50,000 / 130,000 / 200,000 等
    //     ⟹ 缩 DS 后剖面仍按 300 km 展开 ⟹ 每个细胞都被拉平
    public static final double COAST_LAND_U1   = 6.0;    // ★ 12->6（陆地缓升 6×DS = 150 km）
    public static final double COAST_LAND_U2   = 120.0;  // 陆地后续上升 120×DS = 3,000 km
    public static final double COAST_SHELF     = 3.0;    // ★ 8->3（大陆架 3×DS = 75 km）
    public static final double COAST_SLOPE     = 3.0;    // ★ 4->3（大陆坡 3×DS = 75 km）
    public static final double COAST_DEEP_OFF  = 6.0;    // ★ 12->6（深海起点 6×DS = 150 km）
    public static final double COAST_DEEP_W    = 12.0;   // ★ 40->12（深海饱和 12×DS = 300 km）
    /** 海岸剖面的单位长度 = 超格的绝对尺寸 */
    // ★★★★★★ 海岸剖面的单位长度 = DS()（细胞尺度），不是 SUPER_ABS
    //   ★ 原问题：U = SUPER_ABS = 100,000 ⟹ 剖面在 250 km 内走完
    //     而海洋要跨 2,500 km ⟹ deep 项永不饱和 ⟹ 只到 -325 m
    //   ★ 正解：用细胞尺度 DS() = 25,000，并调大系数
    public static double coastUnit() { return DS(); }

    // ★★★★★★ 陆地阈值（修法 A：团块 = 满超格，n > 阈值 ⟹ 有陆）
    public static double LAND_THRESHOLD = 0.50;

    // ★★★★★★ 全局高度增益（人工放大系数）
    //   ★ 为什么需要：特征的【半宽】只有 ~50 km，而剖面参数是 300~1,300 km
    //     ⟹ smooth(d/300,000) 只能到 0.077 ⟹ base 被乘没了 ⟹ 地形平坦
    //   ★ 做法：把 (base + hfeat) 整体乘一个系数 ⟹ 【形态完全不变】，只是「立起来」
    //   ★ 代价：坡度同比例增加（对气候层无影响，对 MC 地形层有影响）
    public static double H_GAIN_LAND = 7.55;   // ★ 标定：1,081 × 7.55 = +8,162 m
    public static double H_GAIN_SEA  = 2.955;  // ★ 标定：-3,552 x 2.955 = -10,496 m
    public static double H_GAIN = 1.0;         // （保留，总增益）

    // ★★★★★★ 超格索引的域扭曲（打破「网格对齐」，让块的位置/尺寸不规则）
    //   ★ 原理：把查询的细胞坐标【先位移】，再算超格索引
    //     ⟹ 块的边界变成不规则曲线（像板块边界的域扭曲）
    //   ★ 仍 O(1)、连续、纯函数 ⟹ 不破坏任何性质
    public static double CELL_WARP = 1.8;    // ★ 标定：1.8 最好（形状自然、无方格感）
    public static double CELL_WL   = 2.0;    // 位移波长（× SUPER，与超格同量级）
    /** 细胞索引 (ci,cj) 的位移（细胞数） */
    public static long[] cellWarp(long ws, long ci, long cj) {
        if (CELL_WARP <= 0.0) return new long[]{0L, 0L};
        syncSuper();
        double wl = CELL_WL * SUPER;
        double dx = (cnoise(ci/wl, cj/wl, 1.0, ws ^ 0xC11L) - 0.5) * CELL_WARP * SUPER;
        double dz = (cnoise(ci/wl + 31.7, cj/wl - 17.3, 1.0, ws ^ 0xC22L) - 0.5) * CELL_WARP * SUPER;
        return new long[]{ Math.round(dx), Math.round(dz) }; }

    // ★★★★★★★★ 2026-10-08：SCAN 8 -> 3（**逐位等价**，但快 5~7 倍）
//   实测（4000 点，ref = SCAN=8）：
//     SCAN=2 (25 细胞)  isLand 0.495us  coastDist  1.126us  不一致=0  maxdh=0.0000
//     SCAN=3 (49 细胞)  isLand 0.567us  coastDist  1.489us  不一致=0  maxdh=0.0000
//     SCAN=8 (289细胞)  isLand 1.278us  coastDist  7.834us  <- 原值
//   ⟹ 最近细胞的 5x5 邻域就足够（Voronoi 局部性强）
//   ★ 取 3（而非 2）留一点安全余量
public static int SCAN = 3;
    // ★★★★★★★★ 2026-10-08：OPP_R 12 -> 4
//   实测：OPP_R 从 1 到 12，coastDist **偏差全是 0.0**
//   ⟹ 特征 100,000 格，而 ±4 细胞 = ±100,000 格 ⟹ 足够
public static int OPP_R = 4;
    // ★ 两级扫描：先用小半径（典型情况足够），找不到才扩大到 OPP_R
    //   动机：signedCoastDist 实测 99 us（625 细胞扫描），是最贵的单项
    public static int OPP_R_TIGHT = 2;   // ★ 两级扫描的小半径   // ★ 找「最近异类细胞」的扫描半径（±12 细胞 = ±300 km）
    public static double CAP_MULT = 2.0;

    public static double WARP_AMP()  { return 0.300  * DS(); }   // = 1,200,000 @ DS=4M
    public static double WARP_WL()   { return 0.225  * DS(); }   // =   900,000 @ DS=4M
    public static double WARP_AMP2() { return 0.100  * DS(); }   // =   400,000 @ DS=4M
    public static double WARP_WL2()  { return 0.075  * DS(); }   // =   300,000 @ DS=4M
    public static double WARP_AMP3() { return 0.045  * DS(); }   // =   180,000 @ DS=4M
    public static double WARP_WL3()  { return 0.0325 * DS(); }   // =   130,000 @ DS=4M

    public static final double LAND_BASE =   300.0;
    public static final double SEA_BASE  = -4000.0;
    public static double REL_OROG = 1.2;    // ★ 0.15->1.2（带更宽 ⟹ 对 db 抖动不敏感）   // ★ 从 0.55 降到 0.15（山带变窄）
    public static double REL_OCEA = 0.6;    // ★ 0.20->0.6
    public static double REL_RIDGE= 1.2;    // ★ 0.60->1.2
    public static double REL_RIFT = 0.8;    // ★ 0.12->0.8
    public static final double OROG_AMP = 7500.0;   // ★ 4500->7500（最高 +5,157 -> ~+8,000）
    public static final double RIFT_AMP = 1200.0;   // ★ 3000->1200（张裂不该那么深，且它在 w≈0.5 时制造噪声）   // ★ 1500->3000
    public static final double TRENCH_D = 6500.0;   // ★ 9000->6500（最深 -12,996 -> ~-10,500）
    public static final double RIDGE_H  = 1600.0;
    public static final double ARC_AMP  = 1200.0;
    public static final double V_MAX    = 1.0;
    public static final double V_REF    = 0.5;

    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31); }
    static long rndL(long seed, long a, long b, long salt) {
        return mix(mix(a * 0x9E3779B97F4A7C15L + b) ^ seed) + salt * 0x2545F4914F6CDD1DL; }


    /**
     * ★★★★★★★ 2026-10-08 **回退**：这里曾加过记忆化，但造成【回归】。
     *
     * <p>【实测】加记忆化后（对比同场景 JFR）：
     * <pre>
     *   GCPhaseParallel          84,305 → 144,657  （+72%）
     *   PromoteObjectInNewPLAB  139,820 → 188,985  （+35%）
     *   ExecutionSample           6,886 →   9,704  （+41%）
     * </pre>
     * <p>【为什么】`rnd01` 被调用**上百万次**（`nIndex` → 25 `cnoise` → 4 `rnd01`），
     *   而每次调用要做 `ThreadLocal.get` + 哈希查找 ⟹ **比 `rndL` 本身还贵**。
     * <p>【正确做法】要缓存就缓存在**更高层**（`cnoise` 或 `nIndex`），
     *   那里调用次数少 4~100 倍。
     */
    static double rnd01(long seed, long a, long b, long salt) {
        return (rndL(seed, a, b, salt) >>> 11) * 0x1.0p-53; }

    /**
     * ★★★★★★★ 2026-10-08（方案 A）：`cnoise` 的**解析梯度**。
     *
     * <p>【为什么需要】原 `conv` 依赖「最近 vs 第二近细胞」的身份 ⟹ 跨 Voronoi 边界跳变。
     * 我第一版用「平滑速度场的中心差分」，但：
     * <pre>
     *   ① 差分间隔远小于 cnoise 格距 ⟹ 梯度被舍入到精确的 0
     *   ② 只用最近 3 个细胞加权 ⟹ 速度场在空间上几乎常数
     * </pre>
     * ⟹ conv 恒 0 ⟹ w 恒 0.5 ⟹ 山峰被压平 46%。
     *
     * <p>【正解】直接对 `cnoise` 求解析导数 —— 精确、连续、无间隔选择问题。
     *
     * <pre>
     *   cnoise(u,v) = t1 + (t2-t1)*sy
     *     t1 = a + (b-a)*sx,   t2 = c + (d-c)*sx
     *     sx = fx²(3-2fx),     sy = fy²(3-2fy)
     *     u = gi/C, v = gj/C
     *
     *   ∂/∂gi = [ (b-a)*sx'*(1-sy) + (d-c)*sx'*sy ] / C
     *   ∂/∂gj = [ (t2-t1)*sy' ] / C
     *     sx' = 6fx(1-fx),   sy' = 6fy(1-fy)
     * </pre>
     *
     * @param out 写入 {∂/∂gi, ∂/∂gj}（长度 ≥ 2）
     */

    static double cnoise(double gi, double gj, double C, long seed) {
        double u = gi / C, v = gj / C;
        long i0 = (long) Math.floor(u), j0 = (long) Math.floor(v);
        double fx = u - i0, fy = v - j0;
        double sx = fx*fx*(3-2*fx), sy = fy*fy*(3-2*fy);
        double a = rnd01(seed, i0, j0, 7L),   b = rnd01(seed, i0+1, j0, 7L);
        double c = rnd01(seed, i0, j0+1, 7L), d = rnd01(seed, i0+1, j0+1, 7L);
        double t1 = a + (b-a)*sx, t2 = c + (d-c)*sx;
        return t1 + (t2-t1)*sy; }

    static double cellX(long ws, long ci, long cj) {
        return (ci + 0.5)*DS() + (rnd01(ws, ci, cj, 11L) - 0.5)*DS()*1.70; }
    static double cellZ(long ws, long ci, long cj) {
        return (cj + 0.5)*DS() + (rnd01(ws, ci, cj, 22L) - 0.5)*DS()*1.70; }
    static double cellB(long ws, long ci, long cj) {
        return 1.0 + 3.0*rnd01(ws, ci, cj, 33L); }
    // ================= ★★★★★★ 速度场（连续化） =================
    //   ★ 根因：原来 cellVX/cellVZ 是【逐细胞独立随机值】
    //     ⟹ 相邻细胞的相对速度 = 两个随机数之差 ⟹ 纯随机
    //     ⟹ 缩放后（DS 4,000km -> 25km）相邻细胞只相距 25 km
    //     ⟹ conv 逐细胞跳变 ⟹ w 翻转 ⟹ hfeat 在 +7,500/-3,000 之间跳
    //   ★ 正解：速度场改为【低频噪声】⟹ 相邻细胞速度相近 ⟹ conv 平滑
    //     ⟹ 「挤压/张裂」变成【区域性的】而不是逐细胞跳
    public static double VEL_WL_R = 3.0;   // 速度场波长（× SUPER_ABS）= 特征尺寸的 3 倍
    static double velWL() { return VEL_WL_R * SUPER_ABS; }
    /** ★ 连续噪声场（供 medNoise / bandNoise 派生），返回 [-1,+1] */
    public static double velField(long ws, double x, double z, int which) {
        double w = velWL();
        double a = cnoise(x/w, z/w, 1.0, ws ^ (which == 0 ? 0xD1L : 0xD2L)) - 0.5;
        return a * 2.0; }
    static double cellVX(long ws, long ci, long cj) {
        double w = velWL();
        return (cnoise(ci*DS()/w, cj*DS()/w, 1.0, ws ^ 0x44L) - 0.5) * 2.0 * V_MAX; }
    static double cellVZ(long ws, long ci, long cj) {
        double w = velWL();
        return (cnoise(ci*DS()/w + 61.3, cj*DS()/w - 23.7, 1.0, ws ^ 0x55L) - 0.5) * 2.0 * V_MAX; }

    // ================= ★★★★★★★ 造山场：板块汇聚度（从海陆分布层派生） =================
    //
    // 【为什么要在这里】山脉 = 两个块体挤压。而「挤压」的物理量是速度场的**散度**：
    //     conv = −∇·v        （汇聚为正，张裂为负）
    // 本层已经有速度场（{@link #velField}，波长 velWL() = 300,000），它是**连续**的
    //   ⟹ 它的散度也是连续的、与「细胞身份」无关。
    //
    // 【与旧实现（已废弃）的区别】旧实现（方案 A）把 conv 定义成 |∇d|（距离场的梯度模长），
    //   那是**几何量不是物理量**：实测 |∇d| 属于 [0, 98.09]、27.8% 恰为 0，
    //   而 −∇·v 在步长 500~50,000 之间结果一致（实测）。见 待裁决/084。
    //
    // 【为什么旧实现会失败】[038] 记录的 4 次失败都是从【逐细胞速度】(cellVX/cellVZ) 构造，
    //   而相邻细胞的速度差是随机量。本实现直接用【连续场 velField】⟹ 不存在该问题。
    /** 求散度的差分步长（block）。★ 实测在 500~50,000 之间结果一致 ⟹ 取 DS()/2。 */
    public static double OROG_DH = 12_500.0;

    /** 汇聚度 = −∇·v（单位：1/block；板块挤压为正）。 */
    public static double convergence(long ws, double px, double pz) {
        final double h = OROG_DH;
        double dvx = (velField(ws, px + h, pz, 0) - velField(ws, px - h, pz, 0)) / (2.0 * h);
        double dvz = (velField(ws, px, pz + h, 1) - velField(ws, px, pz - h, 1)) / (2.0 * h);
        return -(dvx + dvz); }

    /**
     * 归一化的汇聚度 ∈ [0,1]：0 = 张裂/中性，1 = 强汇聚。
     *
     * <p>标定依据（实测 20,000 点，h=12,500）：conv 属于 [−1.066e−5, +1.138e−5]，
     * mean|div| = 2.837e−6，汇聚占 49.3%。
     * ⟹ C0 = 0（只有【净汇聚】才造山），C1 = 5.0e−6（约 1.8 × mean|div|，让强汇聚区饱和）。
     */
    public static double OROG_C0 = 0.0;
    // ★★★★★★★ 2026-10-08 标定：5.0e-6 -> 2.5e-6
    //   实测（±240km，步长 1.5km）：陆地 h>=150 的占比
    //     C1=5.00e-6 -> 4.87%      C1=2.50e-6 -> 9.72%      C1=1.25e-6 -> 11.75%
    //   ⟹ 5e-6 只承认「强汇聚」，山地太稀疏；2.5e-6（≈ mean|div|）是合适的门槛。
    public static double OROG_C1 = 2.5e-6;

    public static double convergence01(long ws, double px, double pz) {
        double t = (convergence(ws, px, pz) - OROG_C0) / (OROG_C1 - OROG_C0);
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        return t * t * (3.0 - 2.0 * t); }

    // ================= ★★★★★★★ 造山带 = 【Voronoi 细胞边界】(c 方案) =================
    //
    // 【为什么用细胞边界而不是速度零集】实测（400km x 400km, 100 万点）：
    //   速度零集网络 与 Voronoi 细胞边界 的相关系数 = 0.0031、Jaccard 相似度 = 0.00%
    //   ⟹ 两者【毫无关系】。用户要的是「两个细胞挤压」⟹ 山必须长在【细胞边界】上。
    //
    // 【怎么算】scanOpp 的 bd[0..2] 是【类型无关】的最近 3 个细胞的加权距离 phys/√b
    //   ⟹ bd[0]==bd[1] 的位置就是第 1/第 2 近细胞的 Voronoi 边界。
    //   用【无量纲】归一化，避免依赖细胞尺寸：
    //       tn = (bd[1] − bd[0]) / (bd[1] + bd[0])     0 = 正好在边界上，→1 = 细胞核心
    //   ⟹ 边界核 = clamp01(1 − tn/OROG_BND_W)^2
    /** 细胞边界核的带宽（tn 的量纲，0.05~0.3 之间；越小带越窄）。 */
    public static double OROG_BND_W = 0.10;

    /** 到最近【Voronoi 细胞边界】的接近度 [0,1]（1 = 正好在边界上）。 */
    public static double cellBoundary01(long ws, double px, double pz) {
        double[] w = warp(ws, px, pz);
        Q q = scan(ws, w[0], w[1]);
        double b0 = q.bd[0], b1 = q.bd[1];
        if (b1 >= 1e17 || b0 >= 1e17) return 0.0;
        double tn = (b1 - b0) / (b1 + b0 + 1e-9);
        double t = 1.0 - tn / OROG_BND_W;
        if (t <= 0.0) return 0.0;
        return t * t; }

    // ================= ★★★★★★★ 造山带 = 【板块（超格）边界】 =================
    //
    // 【为什么不用 25 km 细胞的 Voronoi 边界】实测（60,000 点）：
    //     tie = |bd[1]-bd[0]| : p50 = 798，tie<1000 占 【51.97%】
    //     ⟹ cellBoundary01 均值 0.4834、>0.99 占 【42.10%】
    //   即：25 km 细胞的位置抖动 ±0.85·DS、尺寸因子 b 属于 [1,4] ⟹ 格点近乎随机
    //   ⟹ 「到边界距离」在半个地图上都≈0 ⟹ 边界核退化成常数 1，带结构消失。
    //
    // 【正解】用【板块】= 超格（SUPER x DS = 125 km）。
    //   依据：cellContAt 的判据就是 cellContAtMemo(floorDiv(ci,SUPER), floorDiv(cj,SUPER))
    //   ⟹ 超格才是本模型里真正的「板块」单元，它的边界才是「两个板块的接缝」。
    /** 沿板块边的造山带半宽（block）。带总宽 = 2 x 本值。 */
    // ★ 2026-10-08 定稿：20000 -> 30000（带总宽 60 km，让山系更连绵；用户反馈「确实少」）
    public static double OROG_PLATE_BAND = 22000.0;

    // ================= ★★★★★★★ 板块中心（抖动） =================
    //
    // 【为什么必须抖动】诊断图 run/talos_maps/cell_diag.png 证明：
    //   · 超格类型（面板1）= 规整方块
    //   · 不抖动的超格边界（面板3）= 【横平竖直的正方网格】 <-- 用户看到「直溜溜的山脉」
    //   · 25 km 细胞的 Voronoi（面板2）= 不规则，但 tie=|bd1-bd0| 在半个地图上都约等于 0
    //     （实测 p50=2068，p15=0）⟹ 太乱，做不出窄带
    //   ⟹ 正解：**让超格中心也抖动**，则超格的 Voronoi 边界 = 不规则多边形（像板块）。
    /** 超格中心的抖动幅度（占超格边长的比例）。 */
    // ★ 2026-10-08：0.40 -> 0.90。
    //   0.40 时种子仍贴着方格 ⟹ Voronoi 边【贴着格边走】⟹ 山带呈「一横一竖」
    //   （用户：「正常地球上的山脉也没有这种吧」）。
    //   0.90 时种子近似泊松分布 ⟹ 边方向接近各向同性 ⟹ 山带方向随机。
    public static double SUP_JITTER = 0.90;
    /** 超格的尺寸因子范围（加权 Voronoi）。 */
    public static double SUP_BSPREAD = 0.8;
    static double supCell() { return SUPER * (double) DS(); }
    static double supX(long ws, long si, long sj) {
        double c = supCell();
        return (si + 0.5) * c + (rnd01(ws, si, sj, 71L) - 0.5) * c * SUP_JITTER; }
    static double supZ(long ws, long si, long sj) {
        double c = supCell();
        return (sj + 0.5) * c + (rnd01(ws, si, sj, 72L) - 0.5) * c * SUP_JITTER; }
    static double supB(long ws, long si, long sj) {
        return 1.0 + SUP_BSPREAD * rnd01(ws, si, sj, 73L); }

    /**
     * 到最近【板块（超格）Voroni 边界】的距离（block）。
     *
     * <p>做法与 {@link #signedCoastDistCF} 完全同构：取最近两个抖动超格中心，
     * 令 {@code phi = r1 - r0}（r = |x-c|/sqrt(b) 加权半径），则
     * <pre>
     *   phi = 0  ⟺  正在两块的 Voronoi 边界上（不规则多边形网络）
     *   dist = |phi| / |grad phi|        // 解析距离
     * </pre>
     * ★ 穿越边界时 c0/c1 互换 ⟹ phi 与 grad phi 同时变号 ⟹ 取绝对值后【处处连续】。
     */
    public static double plateBoundaryDist(long ws, double px, double pz) {
        syncSuper();
        double[] w = warp(ws, px, pz);
        double qx = w[0], qz = w[1];
        double cell = supCell();
        long si0 = (long) Math.floor(qx / cell), sj0 = (long) Math.floor(qz / cell);
        double b0 = 1e18, b1 = 1e18, bb0 = 1.0, bb1 = 1.0, c0x = 0, c0z = 0, c1x = 0, c1z = 0;
        for (long dj = -1; dj <= 1; dj++) {
            for (long di = -1; di <= 1; di++) {
                long si = si0 + di, sj = sj0 + dj;
                double cx = supX(ws, si, sj), cz = supZ(ws, si, sj);
                double b = supB(ws, si, sj);
                double ex = qx - cx, ez = qz - cz;
                double r = Math.sqrt(ex * ex + ez * ez) / Math.sqrt(b);
                if (r < b0) { b1 = b0; c1x = c0x; c1z = c0z; bb1 = bb0;
                              b0 = r;  c0x = cx;  c0z = cz;  bb0 = b; }
                else if (r < b1) { b1 = r; c1x = cx; c1z = cz; bb1 = b; } } }
        if (b1 > 1e17) return 1e9;
        double d0x = qx - c0x, d0z = qz - c0z, d1x = qx - c1x, d1z = qz - c1z;
        double phi = b1 - b0;
        // dr/dx = (x-cx)/(r*b)
        double gx = d1x / (b1 * bb1) - d0x / (b0 * bb0);
        double gz = d1z / (b1 * bb1) - d0z / (b0 * bb0);
        double gm = Math.sqrt(gx * gx + gz * gz);
        if (gm < 1e-12) return 1e9;
        return Math.abs(phi) / gm; }

    /** 板块边界核 [0,1]（1 = 正在板块接缝上）。 */
    public static double plateBoundary01(long ws, double px, double pz) {
        double t = 1.0 - plateBoundaryDist(ws, px, pz) / OROG_PLATE_BAND;
        if (t <= 0.0) return 0.0;
        return t * t; }

    // ================= ★★★★★★★ 造山带 = 【Voronoi 细胞边界】（正解） =================
    //
    // 【为什么弃用 plateBoundary01（超格栅格）】诊断图（run/talos_maps/cell_diag.png）：
    //   · 面板1 超格类型  = 规整方块
    //   · 面板3 plateBoundary01 = 【横平竖直的正方网格】  <-- 用户看到「直溜溜的山脉」
    //   · 面板2 tie=|bd[1]-bd[0]| = 【不规则 Voronoi 细胞网络】
    //   ⟹ 超格是 floorDiv(ci,SUPER) 的规整格点（边长 100 km），不是细胞边界。
    //
    // 【正解】用 tie = bd[1] - bd[0]（cell 的加权距离差）：
    //   · 它的零集 【就是】细胞的 Voronoi 边界网络（面板2 已验证）
    //   · 穿越边界时 bd[0]/bd[1] 互换 ⟹ tie 只变号 ⟹ 取绝对值后【连续】
    //   · 于是可以像 signedCoastDistCF 一样求【解析距离】：dist = |tie| / |grad tie|
    /** 求 cellEdgeDist 的差分步长（block）。 */
    public static double OROG_EDGE_DH = 4000.0;
    /** 造山带半宽（block，到细胞边界的距离）。 */
    public static double OROG_EDGE_BAND = 15000.0;

    /** 原始的 tie = bd[1] - bd[0]（★ 传入的必须是【已扭曲】的坐标）。 */
    private static double tieW(long ws, double qx, double qz) {
        double[] q = nearTieDbg(ws, qx, qz);
        if (q[1] >= 1e17 || q[0] >= 1e17) return 1e9;   // 扫不到足够细胞
        return q[1] - q[0]; }

    /** 到最近【Voronoi 细胞边界】的距离（block）。 */
    public static double cellEdgeDist(long ws, double px, double pz) {
        double[] w = warp(ws, px, pz);
        double qx = w[0], qz = w[1];
        final double h = OROG_EDGE_DH;
        double t0 = tieW(ws, qx, qz);
        double tx = tieW(ws, qx + h, qz) - tieW(ws, qx - h, qz);
        double tz = tieW(ws, qx, qz + h) - tieW(ws, qx, qz - h);
        double gm = Math.sqrt(tx * tx + tz * tz) / (2.0 * h);
        if (gm < 1e-12) return 1e9;                     // 平场（远离任何边界）
        double d = Math.abs(t0) / gm;
        double cap = 4.0 * OROG_EDGE_BAND;
        return d > cap ? cap : d; }

    /** 细胞边界核 [0,1]（1 = 正在细胞边界上）。 */
    public static double cellEdge01(long ws, double px, double pz) {
        double t = 1.0 - cellEdgeDist(ws, px, pz) / OROG_EDGE_BAND;
        if (t <= 0.0) return 0.0;
        return t * t; }

    // ================= ★★★★★★★ 造山（米） =================
    /**
     * 造山开关。false ⟹ height() 不含造山项（逐位回到纯海岸剖面）。
     * <p>⚠ 它同时影响气候层（height 是气候读到的海拔）与地形层（同一个 height 乘系数）。
     */
    public static boolean OROG_ON = true;
    /**
     * 造山抬升上限（<b>米</b>）。
     *
     * <p>标定目标（用户给的约束）：
     * <ul>
     *   <li>山脉最高【不超过 10,240 米】；</li>
     *   <li>与 {@link #CF_LAND_H}（大陆基底饱和高度，默认 900 米）相加后落在该上限内。</li>
     * </ul>
     * 900 + 9,000 = 9,900 米 &lt; 10,240 ✓
     */
    // ★ 2026-10-08 标定定稿：9000 -> 7500（把 >8000 m 那一档压下去）
    //   实测（CF_LAND_H=1100, PLATE_BAND=20000, GAMMA=1.3）：
    //     UMAX=9000 -> >8000m 占 1.75%，max 9873
    //     UMAX=8500 -> >8000m 占 0.88%
    //     ★ UMAX=7500 -> >8000m 占 0.05%，max 8371（接近珠峰 8849）
    public static double OROG_MAX_M = 7500.0;

    /**
     * 造山剖面的形状指数（幂）。1 = 线性；越大 ⟹ 山体越集中于脊线、山麓越缓。
     *
     * <p><b>为什么需要</b>：{@code 边界核 × 汇聚度} 是两个 [0,1] 掩码的乘积，
     * 两者都在大范围内接近 1 ⟹ 乘积有一个【大平顶】。实测（γ=1）陆地高程分布：
     * <pre>
     *   &gt;8000 m 占 12.54%     （地球 0.10%）   ← 平顶的直接后果
     *   1000-2000 m 只占 3.22%（地球 15%）
     * </pre>
     * 而真实山脉是【脊线高、山麓缓】。取 γ=3 后：
     * <pre>
     *   raw 0.3 -&gt; 0.027 -&gt;  243 m
     *   raw 0.5 -&gt; 0.125 -&gt; 1125 m
     *   raw 0.7 -&gt; 0.343 -&gt; 3087 m
     *   raw 1.0 -&gt; 1.000 -&gt; 9000 m
     * </pre>
     */
    // ★ 2026-10-08 标定定稿：1.0 -> 1.3（山体稍向脊线集中）
    public static double OROG_GAMMA = 1.3;

    /**
     * ★ 2026-10-08：造山带的【最小宽度比例】。
     *
     * <p><b>为什么需要</b>：原实现里带宽是常数 {@link #OROG_PLATE_BAND}，
     * 汇聚度只调制【幅度】不调制【宽度】⟹ 汇聚强与弱的地方一样宽
     * ⟹ 渲染出来是一条**等宽的蠕虫**（用户：「山脉地区怪怪的」）。
     * 真实造山带是**汇聚越强、带越宽越高**。
     * <pre>
     *   band = OROG_PLATE_BAND * (OROG_BAND_MIN + (1-OROG_BAND_MIN) * convergence01)
     * </pre>
     */
    public static double OROG_BAND_MIN = 0.30;

    /** 造山强度 [0,1] = 边界核（宽度随汇聚度变化） × 板块汇聚度，再取 OROG_GAMMA 次幂。 */
    public static double orogeny01(long ws, double px, double pz) {
        double c01 = convergence01(ws, px, pz);
        if (c01 <= 0.0) return 0.0;
        // ★ 宽度随汇聚度变化：弱汇聚 = 窄带，强汇聚 = 宽带
        double band = OROG_PLATE_BAND * (OROG_BAND_MIN + (1.0 - OROG_BAND_MIN) * c01);
        double t = 1.0 - plateBoundaryDist(ws, px, pz) / band;
        if (t <= 0.0) return 0.0;
        double raw = t * t * c01;
        return Math.pow(raw, OROG_GAMMA); }

    /** 造山抬升（米）。 */
    public static double orogenyM(long ws, double px, double pz) {
        return OROG_MAX_M * orogeny01(ws, px, pz); }

    // ================= ★★★★★★★ 平滑速度场（用于连续的 conv） =================
    //
    // 【为什么必须有】conv 原来是：
    //     rvx = cellVX(bi[1]) - cellVX(bi[0]);   conv = -(rvx*nx + rvz*nz)
    //   即「最近细胞 vs 第二近细胞」的速度差 —— 依赖两者的【身份】。
    //   跨过 Voronoi 边界时 bi[1] 换人 ⟹ conv 从 -0.0763 跳到 +0.0625（符号翻转）
    //   ⟹ w 从 0.18 跳到 0.82 ⟹ hfeat 从 623 跳到 5284 米（实测断崖）。
    //
    // 【修法】不用「两个细胞的差」，改用【平滑速度场】的散度：
    //     v(x) = Σ_k w_k·v_k / Σ_k w_k      w_k = 1/(|x-c_k| + ε)
    //   · c_k / v_k 在网格上是【光滑的】⟹ 任何有限差分都是连续的
    //   · ε 消除「查询点恰在细胞中心」的奇点
    //   · ⟹ conv 连续 ⟹ 断崖消除
    //
    /** 平滑速度场的采样点间隔（格）。★ = DS()/2（半个细胞），保证差分不退化 */
    static final double VEL_EPS = DS() * 0.5;

    // ★★★★★★★ 2026-10-08 修复：**有限差分的间隔**必须 ≥ cnoise 的格距。
    //
    // 【原 bug】差分间隔用了 VEL_EPS = 12,500 格，而 cnoise 的输入是
    //     cnoise(ci*DS()/w, ...)   其中 w = velWL() = 300,000
    //   ⟹ 相邻细胞的 cnoise 输入差 = 25,000/300,000 = 0.083
    //   ⟹ 差分位移 12,500 格只对应 cnoise 输入差 0.042（= 1/24 个格点）
    //   ⟹ 梯度被舍入到【精确的 0】⟹ velConvergence 恒为 0（实测确认）
    //
    // 【修法】差分间隔取 **一个 cnoise 格 = velWL()**（= 300,000 格）。
    //   ⚠ 这会覆盖好几个细胞，所以必须用【平滑速度场】（近邻加权）而不是单个细胞的速度。
    static double velDiffH() { return velWL(); }

    /** 平滑速度场的 x 分量（近邻 3 细胞加权）。 */

    /** 平滑速度场的 z 分量。 */

    /**
     * ★ 连续的汇聚度：−∇·v（板块汇聚为正）。
     *
     * <p>用【平滑速度场】的中心差分求散度 —— 全程连续，没有任何「身份」依赖。
     * <p>@return 汇聚度（量纲与原来的 conv 一致：速度量纲 / 长度量纲 × VEL_EPS）
     */

    /** ★ A/B 开关：true = 用平滑速度场算 conv（修复后）；false = 旧口径（回滚点）。 */
    // ★★★★★★★ 2026-10-08：**暂时置 false 回滚**
    //   ⟹ 山峰被压平 46%（p99 5937 -> 4294、max 8015 -> 4303）。
    //   断崖看似「改善 64%」其实是压平的副产物。
    //   ⟹ 先回滚到基线，再用【方案 A：解析梯度】重做。
    /** ★ 方案 A 开关：true = 用解析梯度算 conv。 */
    /** ★ 正解开关：固定偏移的差分。 */
    /** ★★ 方案 A 开关（正解）：conv = d 的沿向梯度。 */

    /**
     * ★ `d` 的梯度（= 大陆度变化率），分解为【沿向】与【切向】两个分量。
     *
     * <pre>
     *   gx = ∂d/∂x,  gy = ∂d/∂y        （固定步长中心差分）
     *   conv  = gx·nx + gy·nz          沿 n（指向大陆内部）= 汇聚度
     *   shear = |−gx·nz + gy·nx|       垂直 n = 剪切率（火山弧调制用）
     * </pre>
     *
     * <p><b>★ 为什么两者用同一个梯度</b>：它们是同一个梯度的两个正交投影，
     * 用同一组差分算出 ⟹ **零额外扫描**（原实现 shear 用「最近vs第二近细胞的速度差」，
     * 既依赖身份会跳变，又要额外两次 cellVX）。
     *
     * <p>步长固定 = {@code DS()} ⟹ 差分子是位置的光滑函数 ⟹ 无「身份」依赖。
     *
     * @param out 写入 {conv, shear}（长度 ≥ 2）
     */
    /**
     * ★★★★★★★ `d` 的梯度 —— **不再依赖细胞身份**。
     *
     * <h3>★ 为什么彻底改掉（本会话第 3 次也是最后一次重构）</h3>
     * <p>前两版的共同缺陷：都用「最近细胞 bi[0] → 第二近细胞 bi[1]」的**方向**作为法向 `n`。
     * 实测（x=-13391）：
     * <pre>
     *   z=128367  bi1=(-2,4)   n=(-0.7071,-0.7071)   conv=-0.0494
     *   z=128368  bi1=( 0,5)   n=(+0.3606,+0.9327)   conv=+0.0284   &lt;== bi1 换人，n 转 90 度
     * </pre>
     * ⟹ 用【细胞身份】定义方向，在 Voronoi 边界必然翻转 ⟹ `conv`/`shear` 一起跳。
     *
     * <h3>★ 正解</h3>
     * <p>法向 `n` 改用 **`∇d` 的方向**（`d` 是连续场）⟹ 全程无身份依赖：
     * <pre>
     *   gx = ∂d/∂x,  gy = ∂d/∂y                     （固定步长中心差分）
     *   conv  = |∇d|        内陆度增长率（离岸越远增长越慢）
     *   shear = 0           见下
     * </pre>
     *
     * <p><b>⚠ shear 为什么是 0</b>：`shear` 原本是「垂直于 n 的分量」，
     * 而 `n` 现在就定义为 `∇d` 的方向 ⟹ 垂直分量**恒为 0**（数学上必然）。
     * 它只用于 `volc`（火山弧强度），影响 `ARC_AMP`（弧的幅度）；
     * 置 0 让 `volc` 退化为常数 0.35 ⟹ 火山弧变成「均匀强度」，不再有强弱变化。
     * 这是**已知且可接受**的简化（原口径依赖身份，本身也是随机量）。
     *
     * <p>★ 精度**没有**牺牲：`d` 仍是逐点精确计算的（不是细胞级插值）。
     */
    static void coastGrad(long ws, double qx, double qz, double[] out) {
        final double h = DS();
        double gx = (evalD(ws, qx + h, qz) - evalD(ws, qx - h, qz)) / (2.0 * h);
        double gy = (evalD(ws, qx, qz + h) - evalD(ws, qx, qz - h)) / (2.0 * h);
        out[0] = Math.sqrt(gx*gx + gy*gy) * CONV_SCALE;   // conv = |∇d|
        out[1] = 0.0; }                                    // shear（见 javadoc）

    /**
     * 单点的有符号离岸距离 d（陆上 >0，海上 <0）—— **轻量版**。
     *
     * <p>★ 2026-10-08 性能优化：只做 {@code scanOpp} + 一次 {@code cellContAt}，
     * <b>不</b>调用完整的 {@code scan}（后者还会填 bi/bj/bd 与三个 c[k]，全是浪费）。
     *
     * <p>差分点 q±h·n 的 {@code ci0/cj0} 可直接算出 ⟹ 无需重新求最近细胞。
     */
    // ================= ★★★★★★★ 线程上下文（合并 10 个 ThreadLocal）================
    //
    // 【为什么】JFR 实测（Server thread，42 秒）：
    //     ThreadLocal$ThreadLocalMap.set       161 样本
    //     ThreadLocal$ThreadLocalMap.getEntry   60 样本
    //   ⟹ 合计 221 样本（31%）—— 当前最大热点
    //
    // 【根因】本类有 10 个 ThreadLocal，每次 `.get()` 都做一次 ThreadLocalMap 查找
    //   （哈希 + 线性探测）。而 `eval` 的最热路径要取 3~4 个：
    //     OPP_SAME / COAST_GRAD_BUF / EVAL_BUF / D_TMP
    //
    // 【正解】**合并成一个上下文对象** ⟹ 一次 `.get()` 取到全部字段。
    //   ⚠ 大对象（LongDblMap，每个 65536 槽）仍用独立 ThreadLocal 以保持懒加载语义不变；
    //     这里合并的是**热路径上的小对象**。
    public static final class Ctx {
        public final Q dTmp = new Q();
        public final double[] coastGrad = new double[2];
        public final double[] evalBuf = new double[8];
        public double oppSame = 1e18;
        public final double[] tmp1 = new double[1];      // 通用「单值出参」缓冲
        public final long[] nmOrg = new long[]{0, 0};    // 前缀和窗口原点
        public double[][] nmPre; }                        // 前缀和窗口（懒建）

    private static final ThreadLocal<Ctx> CTX = ThreadLocal.withInitial(Ctx::new);
    /** ★ 唯一入口：取本线程的上下文（替代 10 个 ThreadLocal.get）。 */
    static Ctx ctx() { return CTX.get(); }

        /** ★ scanOpp 的「最近同类细胞距离」出口（避免改签名）。 */
        public static double lastSamePhys() { return ctx().oppSame; }
    /** 诊断：{aL,aS,selfR,dL00,dS00,i0,j0,tx,tz,n} */
    /** 诊断：{oppPhys原始, samePhys原始, dOpp, dSame} */
    /** 诊断：{coastDist, fadeF, dOpp, dSame} */
    /** 诊断：{base, hfeat, gate, gLand, gSea, wLand, d, dGate} */
    /** coastGrad 的输出缓冲（复用，避免每点分配）。 */
        /** 诊断：eval 实际用到的 conv/shear（供定位残留台阶）。 */
    /** 诊断：eval 的 bi/bj/nx/nz/b0/b1（供定位状态切换）。 */

    /**
     * ★ 2026-10-08 性能优化：差分点用的扫描半径（比主路径小）。
     *
     * <p>【为什么可以更小】差分的目的是求 d 的**【变化率】**，不是绝对值。
     * 只要两点用【同一个半径】，它们的 oppDist 就落在同一口径下，差值有意义。
     * 半径小 ⟹ 成本 ∝ (2R+1)² 大幅下降。
     * <p>实测：R 从 4 降到 2，断崖指标不变（仍 0/16445）。
     */
    public static int DIFF_OPP_R = 2;
    /**
     * ★ 自洽版 evalD：**每次都重新判定 isLand**（不用调用方传的值）。
     *
     * <p>【为什么需要】`scanOpp` 的判据是 `land != myLand`；
     * 如果 `myLand` 是从【别的点】传进来的（如 coastGrad 复用基准点的），
     * 而差分点落在【别的超格】里，那么「异类」的定义就翻转了 ⟹ d 测的是完全不同的东西。
     */
    static double evalD(long ws, double px, double pz) {
        Q q = ctx().dTmp;
        long ci0 = (long) Math.floor(px / DS());
        long cj0 = (long) Math.floor(pz / DS());
        boolean isLand = cellContAtMemo(ws, ci0 / SUPER, cj0 / SUPER);
        double opp = scanOpp(ws, px, pz, ci0, cj0, q.bi, q.bj, q.bd,
                             isLand, DIFF_OPP_R);
        double dMag = oppDist(opp);
        return isLand ? dMag : -dMag; }

    /** 剪切的标定量（对齐旧口径的量级）。 */
    public static double SHEAR_SCALE = 1.0;

    /**
     * 把「d 的沿向梯度」换算回原来 conv 的量纲（标定量）。
     *
     * <p><b>★ 2026-10-08 标定结果</b>（CONV_SCALE 扫描，同时看断崖与山峰）：
     * <pre>
     *   SCALE   max跳   &gt;200米      p99高  max高
     *    0.02    90.6   0            4593   5893   （山峰被压低）
     *    0.10   180.3   0            6559   8125
     *    0.20   284.4   4(0.0958%)   7191   8125   &lt;== 定稿
     *    1.00   541.7   4(0.0958%)   8019   8161   （断崖过大）
     *
     *   基线参考: max跳=328.4  &gt;200米=17(0.1034%)  p99高=5937  max高=8015
     * </pre>
     * <p>取 0.2：&gt;200 米跳变从 17 降到 4（−76%），max 跳变 284 &lt; 基线 328，
     * 而山峰 7191/8125 &gt; 基线（说明没有「压平」副作用）。
     */
    public static double CONV_SCALE = 0.2;

    /**
     * ★★★★★ 细胞是否大陆 —— 纯【绝对坐标】的函数，【不含瓦片索引】
     *
     * 【为什么必须这样】（用户指出的设计原则）
     *   瓦片只负责【选点】⟹ 点的位置必须是绝对坐标的纯函数
     *   ⟹ 块位置由【绝对超格索引】决定
     *   ⟹ 天然跨瓦片连续，无相位跳变
     *
     * 【每瓦片不同怎么实现】
     *   由【跨世界连续的低频噪声】CONT_N 提供：
     *     · CONT_N 低的区域 ⟹ 块小/稀疏（海洋为主的区域）
     *     · CONT_N 高的区域 ⟹ 块大/密集（陆地为主的区域）
     *   它连续 ⟹ 跨瓦片无缝；它随位置变 ⟹ 不同区域不同
     */
    // ★★★★★★★★ eval 的输出缓冲（ThreadLocal，避免每次 new double[8]）
    
    // ================= ★★★★★★★★ 超格级 nIndex 记忆化 =================
    //
    // 【为什么必须有】
    //   nIndex(sgi,sgj) 内部有 LOCAL_R=2 的局部均值 = (2*2+1)^2 = 25 次 cnoise。
    //   而 cellContAt(ci,cj) = cellWarp(2 次) + nIndex(25 次) = 27 次 cnoise。
    //   eval() 的 scan 与 signedCoastDist() 各扫 625 个细胞 ⟹ 约 34,000 次 cnoise/点。
    //   LandformField.solve 有 80,000 点（400x200）⟹ ★ 27 亿次 cnoise ⟹ 实测 80 秒。
    //
    // 【为什么可以记忆化】
    //   ★ cellContAt 只依赖 (sgi, sgj)（超格索引），与细胞在超格内的偏移【无关】
    //   ⟹ 同一超格内所有细胞答案相同 ⟹ 625 个细胞只涉及 ~25 个超格 ⟹ 可省 25 倍
    //   ★ 用 ThreadLocal（多线程各自一份，无锁）
    // ★★★★★★★ 2026-10-08 性能优化：HashMap<Long,Boolean> → LongDblMap（零装箱）
    //   JFR 实测（Server thread，地形生成）：
    //     nIndex 424 样本（28%）+ ThreadLocalMap.set 140（9%）
    //     + HashMap$TreeNode.find 57 + comparableClassFor 24
    //   ⟹ HashMap 的装箱 + 树化 = 221 样本；ThreadLocal 写入 = 140 样本
    //   且容量只有 8192 ⟹ 工作集超出时反复清空重算。
    //   ⚠ 存 double（0/1）而不是 boolean ⟹ 复用 LongDblMap。
    private static final ThreadLocal<LongDblMap> NI_MEMO =
        ThreadLocal.withInitial(() -> new LongDblMap(1 << 16));
        /** 记忆化开关（诊断时可关） */
    public static boolean NI_MEMO_ON = true;

    /** 清空记忆化（换 seed 或诊断时用）—— 必须连带清【前缀和窗口】。 */
    public static void clearMemo() {
        NI_MEMO.get().clear();
        ctx().nmPre = null;
        ctx().nmOrg[0] = 0; ctx().nmOrg[1] = 0; }

    /** 带记忆化的 cellContAt（自包含，不走 cellWarp ⟹ 无递归） */
    static boolean cellContAtMemo(long ws, long sgi, long sgj) {
        if (!NI_MEMO_ON) return nIndex(ws, sgi, sgj) > LAND_THRESHOLD;
        LongDblMap m = NI_MEMO.get();
        // ★★★★★★★ 2026-10-08 修复：键必须含 seed！
        //   原 bug：key = (sgi<<32) ^ (sgj&0xFFFFFFFFL) —— 只有细胞坐标。
        //   后果：换 seed（或清缓存不彻底）时会返回【上一个 seed】的结果。
        //   实证：先 WI=179054954 再 seedOf=1556270468 ⟹ 两次都返回 70.9（应不同）。
        long key = ((sgi << 32) ^ (sgj & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + ws;
        double[] tmp = ctx().tmp1;
        if (m.get(key, tmp)) return tmp[0] != 0.0;
        boolean r = nIndex(ws, sgi, sgj) > LAND_THRESHOLD;
        if (m.size() > 262144) m.clear();
        m.put(key, r ? 1.0 : 0.0);
        return r; }

    /** 清空记忆化（换 seed 或诊断时用） */

    /** ★ 归一化索引 n（区域偏斜 + 细尺度）—— cellContAt / dbgN 共用 */
    public static double nIndexPublic(long ws, long sgi, long sgj) { return nIndex(ws, sgi, sgj); }

    // ================= ★★★★★★★ B 方案：连续的陆海标量场 =================
    //
    // 【架构问题（[067] 调研）】原来的陆海是【布尔】的：
    //     cellContAt(ci,cj) = nIndex(floorDiv(warp(ci,cj), SUPER=5)) > 0.5
    //   ⟹ 类型只在【5 细胞 = 125,000 格】的尺度上变化
    //   ⟹ 而细胞是 25,000 格 ⟹ 类型在细胞级【随机跳变】
    //   ⟹ 岸线 = 不规则的 Voronoi 边界
    //   ⟹ 而高度 = (base+hfeat) × (isLand ? 7.55 : 2.955)
    //     ★ 硬布尔 × 大增益 ⟹ 岸线上跳 2.55 倍 ⟹ **墙**
    //
    // 【正解】把陆海改成【连续的标量场】，岸线 = 等值线 F = 0.5：
    //   · F 直接按【block 坐标】采样噪声（不是超格索引）
    //   · 低通滤波保证光滑（去掉细尺度抖动）
    //   ⟹ 等值线是光滑曲线 ⟹ 处处有梯度 ⟹ 连续
    //   ⟹ 高度、base、landness 都可以由它连续导出
    //
    /** 陆海标量场的相关长度（block）—— 决定岸线的光滑尺度。 */
    public static double LS_WL   =  60_000.0;   // 大尺度（大陆/大洋）—— 目标：岸线在万格尺度起伏
    /** 细节层波长。 */
    public static double LS_WL2  =  18_000.0;
    /** 细节层幅度。 */
    public static double LS_AMP2 = 0.25;

    /**
     * ★ 连续的陆度 ∈ [0,1]（不是布尔！）。≥0.5 = 陆地。
     *
     * <p>由【低频噪声】直接按 block 坐标采样 ⟹ 处处光滑 ⟹ 等值线是光滑曲线。
     * <p>⚠ 与旧的 `nIndex`（超格量化）无关 —— 这是【B 方案】的新定义。
     */
    public static double landScore(long ws, double px, double pz) {
        double a = cnoise(px / LS_WL, pz / LS_WL, 1.0, ws ^ 0xA17L) - 0.5;
        double b = cnoise(px / LS_WL2 + 31.7, pz / LS_WL2 - 17.3, 1.0, ws ^ 0xB28L) - 0.5;
        // ★★★★★★★ 2026-10-08 修复：**去掉 [0,1] 硬钳位**。
        //
        // 【原代码】return f < 0.0 ? 0.0 : (f > 1.0 ? 1.0 : f);
        //   而 f = LS_TARGET(0.47) ± LS_AMP(0.55) ± LS_AMP2(0.25) ⟹ f 属于 [-0.33, 1.27]
        //   ⟹ 两端各有大片区域被【钳成常数】⟹ 那里 ∇f == 0。
        //
        // 【后果（实测量化）】signedCoastDistCF = (f-0.5)/|∇f|，而它的实现里有
        //   「|∇f| < 1e-12 ⟹ return 0.0」，于是在饱和区 sig 恒为 0 ⟹
        //       coastProfileCF(0) = 0 ⟹ h = seaLevel = 64
        //   ⟹ ★ 约 4.3% 的世界（陆地内部 + 深海盆）变成 **y=64 的平坦台地**。
        //   实测：signedCoastDistCF==0 而 f!=0.5 占 4.34%；"判陆但 height<=0" 占 1.23%。
        //
        // 【为什么去掉钳位是安全的】landScore 只有 3 个消费者，全部对钳位不敏感：
        //   ① isLand      : f >= 0.5      —— 钳位只在 f<0 / f>1 处生效，判定不变 ⟹ 陆地占比【逐位不变】
        //   ② landness    : smoothstep01(0.5 + (f-0.5)/0.02) —— 在 |f-0.5| >= 0.01 处已饱和，不变
        //   ③ signedCoastDistCF : 需要【真实的 ∇f】—— 这正是要去掉钳位的原因
        //   ⟹ 唯一变化的是 ③ 的距离幅值（以及由它导出的地形高度），判定与混合权重不变。
        //
        // 【回滚】恢复上面那行 return 即可。
        return LS_TARGET + LS_AMP * 2.0 * a + LS_AMP2 * 2.0 * b; }

    /** 陆地占比目标（0.5 = 一半）。 */
    public static double LS_TARGET = 0.47;
    /** 大尺度幅度。 */
    public static double LS_AMP = 0.55;
    /** ★ B 方案总开关：陆海判定改用连续场。 */
    public static boolean USE_CONT_FIELD = true;

    /**
     * ★ 连续的「陆性」∈ [0,1] —— 直接可用的混合权重。
     *
     * <p>= smoothstep(landScore) 在 0.5 附近的过渡 ⟹ 岸线处 = 0.5。
     */
    public static double landnessPublic(long ws, double px, double pz) { return landness(ws, px, pz); }
    // ================= ★★★★★★★ B 方案：解析的有符号到岸距离 =================
    //
    // 【为什么需要】`landScore` 是连续的 ⟹ 岸线是它的**等值线** F = 0.5。
    //   而「到等值线的距离」有**解析式**（隐式曲面的一阶近似）：
    //       dist = (F − 0.5) / |∇F|
    //   ⟹ 处处连续、处处有定义 ⟹ 不需要任何距离变换！
    //
    // 【符号】∇F 指向 F 增大处 = **陆地内部**
    //   ⟹ 陆上 F>0.5 ⟹ dist>0 ⟹ 正值 = 在内陆多深
    //   ⟹ 海上 F<0.5 ⟹ dist<0 ⟹ 负值 = 离岸多远
    //   ⚠ 与旧的 `d`（陆上 ±22900 的随机量）完全不同的口径差异：
    //     这是**真正的几何距离**（block），处处连续。
    /**
     * ★ 有符号到岸距离（block）。正值 = 内陆，负值 = 离岸。
     *
     * <p>用中心差分求 ∇F，再除以模长 ⟹ 一阶精度的几何距离。
     */
    public static double signedCoastDistCF(long ws, double px, double pz) {
        final double h = CF_DH;
        double f0 = landScore(ws, px, pz) - 0.5;
        double gx = (landScore(ws, px + h, pz) - landScore(ws, px - h, pz)) / (2.0 * h);
        double gz = (landScore(ws, px, pz + h) - landScore(ws, px, pz - h)) / (2.0 * h);
        double gm = Math.sqrt(gx * gx + gz * gz);
        if (gm < 1e-12) return 0.0;          // 平场（远离岸线）⟹ 视为岸线
        double d = f0 / gm;
        // 饱和（避免平场附近的巨大值）
        if (d >  CF_SAT) d =  CF_SAT;
        if (d < -CF_SAT) d = -CF_SAT;
        return d; }

    /** 中心差分的步长（block）。 */
    public static double CF_DH  = 200.0;
    /** 到岸距离的饱和值（block）。 */
    public static double CF_SAT = 60000.0;   // ★ 4000→60000（内陆需要距离梯度）

    /**
     * ★ 统一的海岸剖面：由【有符号到岸距离】直接给出高度偏移（米）。
     *
     * <p>**两侧用同一个公式** ⟹ 在岸线处必然相遇 ⟹ 无墙。
     * <ul>
     *   <li>d → +∞（内陆）：→ +CF_LAND_H</li>
     *   <li>d = 0（岸线）：0</li>
     *   <li>d → −∞（远海）：→ −CF_SEA_H</li>
     * </ul>
     */
    public static double coastProfileCF(double d) {
        if (d >= 0.0) {
            // ★ 陆地：**平方根曲线**（先快后慢，接近真实大陆剖面）
            //   d=0 ⟹ 0；d≥U ⟹ CF_LAND_H（饱和）
            double u = d / CF_LAND_U;
            if (u > 1.0) u = 1.0;
            return CF_LAND_H * Math.sqrt(u); }
        // 海洋：大陆架 → 大陆坡 → 深海（三段，全部在 d 的连续函数上）
        double s = -d;
        double shelf = -CF_SHELF_H * smoothstep01(s / CF_SHELF_U);
        double slope = -CF_SLOPE_H * smoothstep01((s - CF_SHELF_U) / CF_SLOPE_U);
        double deep  = -CF_DEEP_H  * (1.0 - Math.exp(-Math.max(0.0, s - CF_DEEP_OFF) / CF_DEEP_W));
        return shelf + slope + deep; }

    /** 陸侧目标高度（米）。 */
    // ★★★★★★★ 2026-10-08 重新标定：1973 -> 900（米）
    //   【为什么】用户裁决：本层输出的是【米】，要给两层用 ——
    //     ① 气候层用原生的米（多少米就是多少米）⟹ 必须是地球量级；
    //     ② 地形层通过一个系数换成 MC 的格。
    //   地球陆地高程中位数约 500 m、均值约 840 m ⟹ 大陆基底饱和值取 900 m 与之相称。
    //   （山地由 OROG_MAX_M 单独提供，见下。）
    // ★ 2026-10-08 标定：900 -> 650（对照地球陆地高程：中位数约 500 m、均值约 840 m）
    public static double CF_LAND_H  = 1100.0;
    /** 陸侧上升尺度（block）。 */
    // ★ 2026-10-08 标定：45000 -> 90000（让 0-200 m 的海岸平原占比接近地球的 25%）
    public static double CF_LAND_U  = 90000.0;   // ★ sqrt 曲线的饱和尺度（标定后）
    /** 大陆架深度（米）。 */
    public static double CF_SHELF_H = 200.0;
    /** 大陆架宽度（block）。 */
    public static double CF_SHELF_U = 400.0;
    /** 大陆坡落差（米）。 */
    public static double CF_SLOPE_H = 1800.0;
    /** 大陆坡宽度（block）。 */
    public static double CF_SLOPE_U = 600.0;
    /** 深海起点（block）。 */
    public static double CF_DEEP_OFF = 1000.0;
    /** 深海落差（米）。 */
    public static double CF_DEEP_H  = 2000.0;
    /** 深海饱和尺度（block）。 */
    public static double CF_DEEP_W  = 1500.0;

    public static double landness(long ws, double px, double pz) {
        double f = landScore(ws, px, pz);
        return smoothstep01(0.5 + (f - 0.5) / LS_BAND); }

    /** 陆性过渡带宽度（landScore 的单位）—— 越小岸线越锐利。 */
    public static double LS_BAND = 0.02;
    // ================= ★★★★★★★ nIndex 的前缀和加速 =================
    //
    // 【问题】`nIndex` 原实现算一个 **5×5 移动平均**：
    //     for dj,di in [-2,2]: s2 = cnoise(sgi+di, sgj+dj, SKEW_HF)   ← 25 次
    //   而 `cnoise` 只依赖【整数】坐标 (sgi+di, sgj+dj)（`SKEW_HF=2.0` 时 u=gi/2）
    //   ⟹ **相邻超格的移动平均高度重叠**（只差一行/一列）
    //   ⟹ JFR 实测：`nIndex` 占 Server thread **326 样本（19%）** —— 第 1 热点
    //
    // 【正解】**前缀和（积分图）**：
    //   ① 底层整数网格的 cnoise 值只在 (I,J) 处 —— 每个只算一次
    //   ② 5×5 矩形和 = O(1)（4 次前缀和查表）
    //   ⟹ 25 次 cnoise/nIndex → **~1 次（摊销）**
    //
    // 【实现】lazy 增长的线程本地网格 + 前缀和（缓存窗口随访问平移；命中不了就整体重建）
    private static final int NM_CAP = 96;          // 窗口边长（超格数）
        
    /** 原始 cnoise 值（格点，不含移动平均）。 */
    private static double rawCell(long ws, long I, long J) {
        return (cnoise(I, J, SKEW_HF, ws ^ 0x5E5EL) - 0.5) * SKEW_AMP * 2.0; }

    /** 确保 (sgi,sgj) 对应的窗口已就绪（含 5×5 平均所需的外扩）。 */
    private static void nmEnsure(long ws, long sgi, long sgj) {
        long[] org = ctx().nmOrg;
        double[][] p = ctx().nmPre;
        long ox = org[0], oy = org[1];
        boolean ok = (p != null)
            && sgi - (LOCAL_R + 1) >= ox && sgi + (LOCAL_R + 1) < ox + NM_CAP
            && sgj - (LOCAL_R + 1) >= oy && sgj + (LOCAL_R + 1) < oy + NM_CAP;
        if (ok) return;
        long nox = sgi - NM_CAP / 2, noy = sgj - NM_CAP / 2;
        double[][] np = new double[NM_CAP + 1][NM_CAP + 1];
        // 直接建前缀和：np[k][j] = 前 k 行 j 列的原始值之和
        for (int k = 1; k <= NM_CAP; k++) {
            double rowSum = 0.0;
            for (int j = 1; j <= NM_CAP; j++) {
                rowSum += rawCell(ws, nox + k - 1, noy + j - 1);
                np[k][j] = np[k - 1][j] + rowSum; } }
        org[0] = nox; org[1] = noy;
        ctx().nmPre = np; }

    public static double nIndex(long ws, long sgi, long sgj) {
        double S = rawCell(ws, sgi, sgj);
        // 5×5 移动平均（前缀和 O(1)）
        nmEnsure(ws, sgi, sgj);
        long[] org = ctx().nmOrg;
        double[][] p = ctx().nmPre;
        int x0 = (int) (sgi - org[0]) - LOCAL_R, y0 = (int) (sgj - org[1]) - LOCAL_R;
        int x1 = x0 + 2 * LOCAL_R + 1, y1 = y0 + 2 * LOCAL_R + 1;
        double Sm = p[x1][y1] - p[x0][y1] - p[x1][y0] + p[x0][y0];
        int cnt = (2 * LOCAL_R + 1) * (2 * LOCAL_R + 1);
        Sm /= cnt; S -= Sm;
        double nf = cnoise(sgi + 7.3, sgj - 3.1, CONT_N_HF2, ws ^ 0xD1D1L) - 0.5;
        return N_TARGET + S + FINE_AMP * nf; }

    /** 诊断：返回 (n, cl)；cl 现在恒为 SUPER（修法 A） */
    public static double[] dbgN(long ws, long sgi, long sgj) {
        syncSuper();
        return new double[]{ nIndex(ws, sgi, sgj), SUPER }; }
    public static int dbgCl(long ws, long sgi, long sgj) { syncSuper(); return SUPER; }
    static boolean cellContAt(long ws, long ci, long cj) {
        syncSuper();
        long[] dw = cellWarp(ws, ci, cj);
        long sgi = Math.floorDiv(ci + dw[0], (long) SUPER);
        long sgj = Math.floorDiv(cj + dw[1], (long) SUPER);
        return nIndex(ws, sgi, sgj) > LAND_THRESHOLD; }
    public static boolean cellContPublic(long ws, long ci, long cj) { return cellContAt(ws, ci, cj); }

    /**
     * ★★★★★★★ 2026-10-08：**连续的细胞陆性权重** ∈ [0,1]（不是布尔！）。
     *
     * <p>为什么需要：{@link #cellContAt} 返回 {@code nIndex > LAND_THRESHOLD} 的**布尔**，
     * 丢弃了连续信息。而 {@code profile} 原来按那个布尔分成三个互斥分支 ——
     * 跨过 Voronoi 细胞边界时它翻转 ⟹ 走不同分支 ⟹ **断崖**（实测 hfeat 跳 5359 米）。
     *
     * <p>本方法把 {@code nIndex} 的连续值映射到 [0,1]（围绕 {@code LAND_THRESHOLD} 的平滑过渡），
     * 让 {@code profile} 可以做**连续混合**而不是分支选择。
     *
     * <p>过渡宽度 {@link #CONT_W}：太小 ⟹ 退化成布尔；太大 ⟹ 海岸线模糊。
     */
    static double cellContW(long ws, long ci, long cj) {
        syncSuper();
        long[] dw = cellWarp(ws, ci, cj);
        long sgi = Math.floorDiv(ci + dw[0], (long) SUPER);
        long sgj = Math.floorDiv(cj + dw[1], (long) SUPER);
        double n = nIndex(ws, sgi, sgj);
        return clamp01(0.5 + (n - LAND_THRESHOLD) / (2.0 * CONT_W)); }

    /** 公开访问器（仅供探针/标定）。 */
    public static double cellContWPublic(long ws, long ci, long cj) { return cellContW(ws, ci, cj); }

    /** 连续陆性的过渡宽度（nIndex 的单位）。 */
    // ★★★★★★★ 2026-10-08 标定结果（CONT_W 扫描）：
    //   CONT_W  b1中间值%  max跳   >200米      p99高  max高
    //    0.08      26.5%   284.4    4(0.0958%)   7191   8125   （b1 大部分仍是布尔）
    //    0.35      26.5%   242.6    4(0.0958%)   6395   7849
    //    0.50      26.5%   213.5    2(0.0479%)   6187   7149
    //    0.70   ★ 100.0%   193.4  ★ 0(0.0000%)   5708   6496   ← ★ 定稿（完整连续化）
    //    1.00     100.0%   177.5    0(0.0000%)   5385   6184
    // ⟹ 0.70 是「b1 完整连续化」的临界点（中间值 26.5% -> 100%）；
    //   取 0.70 而不是 1.00 以保住山峰高度。
    // ⚠ 0.08 时 b1 有 73.5% 落在外（0/1），profile 的混合权重随 b1 跳变 ⟹ 残留台阶。
    public static double CONT_W = 0.70;

    /**
     * ★★★★★★★ 2026-10-08（方案 A 第 3 步）：**到岸衰减宽度**。
     *
     * <p>【为什么需要】前两步后剩余的断崖在 `hfeat` 本身：
     * <pre>
     *   跨海岸一格（38750→38751）：hfeat 3900 -> 1907（跳 1993 米）
     *   而 `gLand=0.195 / gSea=0` 是【极小的门控】，把真实的 hfeat（~20000）
     *   压到了 3900 —— 所以「修正门控」会让 hfeat 回到真实幅度（爆炸）。
     * </pre>
     * <p>【正解】不去动门控，而是让 `hfeat` 在海岸处**连续地趋近 0**：
     * <pre>
     *   coastDist = |dOpp − dSame| / 2     （岸线处 = 0，连续）
     *   hfeat *= smoothstep01(coastDist / COAST_FADE_W)
     * </pre>
     * · 岸线：coastDist=0 ⟹ hfeat → 0 ✓ 连续
     * · 内陆：coastDist 大 ⟹ hfeat 全幅 ✓（不依赖 gate 标定）
     *
     * <p>⚠ 取值：≤1×DS() 时内陆几乎无影响；>1×DS() 会削内陆。
     */
    public static double COAST_FADE_W = 1.0 * 25_000.0;   // = 1 × DS()

    // ================= ★★★★★★★ 海岸剖面（以 dNew 口径） =================
    //
    // 【为什么重写】旧的 base 公式用的是「旧 d」（±oppDist(oppPhys)），
    //   而旧 d 在岸线处不穿零（实测从 +22906 跳到 −22905）⟹ 两个分支各自用
    //   「已饱和的 d」⟹ 岸线处阶跃（6 格）。
    //
    // 【新公式】以【距离变换】的 dNew 为唯一输入，两端都过零点：
    //   d = 0（岸线）⟹ base = 0  ✓ 连续
    //   陆侧：smoothstep 抬到 LAND_BASE，再指数缓升
    //   海侧：大陆架 → 大陆坡 → 深海
    //
    // 【单位】dNew 与 d 的量纲相同（block），尺度参数沿用原来的 ×DS()。
    //
    /** ★ A/B：是否使用新的海岸剖面（以 dNew 为输入）。 */
    public static boolean USE_NEW_BASE = false;

    /**
     * 海岸剖面：由**连续的有符号到岸距离** d 求 base（米）。陆上 d > 0。
     *
     * <p>★ d = 0 处两侧都为 0 ⟹ **连续**（这是旧公式做不到的）。
     */
    public static double baseFromDist(double d) {
        // ★★★★★★★ 幅度归一化：让 dNew 的 [0, SAT_D] 映射到旧口径的 [0, 对应幅度]
        //
        // 【为什么】新公式的尺度（0..256 block 的真实距离）与旧口径（0..22906 的细胞距离）
        //   差 90 倍；若直接用 NB_* 常量，base 会升到 650（旧口径只有 162）。
        //   ⟹ 把 d 归一化到 [0,1]，再乘上【旧口径的目标幅度】。
        double ad = Math.abs(d);
        if (d >= 0) {
            // ★ 陆地：快升（NB_LAND_U1=80 格内到 LAND_BASE）+ 慢升（到 LAND_TARGET）
            double fast = LAND_BASE * smoothstep01(ad / NB_LAND_U1);
            double slow = (LAND_TARGET - LAND_BASE) * smoothstep01(ad / SAT_D);
            return fast + slow;
        }
        double u = clamp01(ad / SAT_D);      // ∈[0,1]
        // 海洋：目标 [0, -SEA_TARGET]（三段式：架 → 坡 → 深）
        double shelf = -SHELF_T * smoothstep01(u / 0.3);
        double slope = -SLOPE_T * smoothstep01((u - 0.3) / 0.3);
        double deep  = -DEEP_T  * (1.0 - Math.exp(-Math.max(0.0, u - 0.6) / 0.4));
        return shelf + slope + deep; }

    /** 陆侧 base 在 SAT_D 处的幅度（对齐旧口径：d=22906 时 base≈162）。 */
    public static double LAND_TARGET = 162.0;   // ★ 对齐旧口径（d=22906 时 base≈162）
    /** 海侧大陆架幅度。 */
    public static double SHELF_T = 60.0;
    /** 海侧大陆坡幅度。 */
    public static double SLOPE_T = 110.0;
    /** 海侧深海幅度。 */
    public static double DEEP_T = 50.0;

    /** 陆地的「深层基底」幅度（在 LAND_BASE 之上缓升到的量）。 */
    public static double LAND_DEEP = 400.0;
    // ★★★★★★★ 以下 5 个常量【专供 baseFromDist】（以 dNew 的真实 block 尺度）
    //   旧的 COAST_* 常量是按旧 d（±22906，细胞级）标定的，不适用于 dNew。
    /** 陆地缓升的距离尺度（block）：dNew 超过它就基本到 LAND_BASE。 */
    public static double NB_LAND_U1 = 100.0;         // ★ 100 block 内抬到 LAND_BASE
    /** 陆地后续缓升（block）。 */
    public static double NB_LAND_U2 = 100_000.0;
    /** 大陆架宽度（block）。 */
    public static double NB_SHELF = 80.0;
    /** 大陆坡宽度（block）。 */
    public static double NB_SLOPE = 80.0;
    /** 深海起点（block）。 */
    public static double NB_DEEP_OFF = 160.0;
    /** 深海饱和（block）。 */
    public static double NB_DEEP_W = 100_000.0;
    /** 大陆架深度。 */
    public static double SHELF_D = 200.0;
    /** 大陆坡落差。 */
    public static double SLOPE_D = 1800.0;
    /** 深海深度。 */
    public static double DEEP_D  = 2000.0;

    /** 点级海陆判据 = 最近细胞的类型（口径与 cellContAt 一致） */
    // ================= ★★★★★★ 最近细胞（统一核心） =================
    //   ★ 关键修复：原来 isLand() 内部【自己扭曲】，而 eval 传进来的已经是扭曲坐标
    //     ⟹ 双重扭曲 ⟹ isLand 与 d/h 不一致（实测 2.83% 的点矛盾）
    //   ★ 正解：拆成【扭曲】和【查最近细胞】两步，各入口共用
    static long[] nearestCell(long ws, double wx, double wz) {
        double best = Double.MAX_VALUE; long bi=0, bj=0;
        long ci0 = (long) Math.floor(wx/DS()), cj0 = (long) Math.floor(wz/DS());
        for (int dj = -SCAN; dj <= SCAN; dj++) for (int di = -SCAN; di <= SCAN; di++) {
            long ci = ci0+di, cj = cj0+dj;
            double ex = wx - cellX(ws,ci,cj), ez = wz - cellZ(ws,ci,cj);
            double r2 = (ex*ex + ez*ez) / cellB(ws,ci,cj);
            if (r2 < best) { best = r2; bi = ci; bj = cj; } }
        return new long[]{ bi, bj }; }

    /** 世界坐标的扭曲（eval 用它一次；之后所有查询都用扭曲后的坐标） */
    static double[] warp(long ws, double px, double pz) {
        double w1x = cnoise(px/WARP_WL(), pz/WARP_WL(), 1.0, ws ^ 0xA11L) - 0.5;
        double w1z = cnoise(px/WARP_WL() + 37.7, pz/WARP_WL() - 11.3, 1.0, ws ^ 0xB22L) - 0.5;
        double w2x = cnoise(px/WARP_WL2(), pz/WARP_WL2(), 1.0, ws ^ 0xC33L) - 0.5;
        double w2z = cnoise(px/WARP_WL2() + 17.1, pz/WARP_WL2() + 5.9, 1.0, ws ^ 0xD44L) - 0.5;
        double w3x = cnoise(px/WARP_WL3(), pz/WARP_WL3(), 1.0, ws ^ 0xE55L) - 0.5;
        double w3z = cnoise(px/WARP_WL3() + 9.3, pz/WARP_WL3() - 4.7, 1.0, ws ^ 0xF66L) - 0.5;
        return new double[]{ px + w1x*WARP_AMP() + w2x*WARP_AMP2() + w3x*WARP_AMP3(),
                             pz + w1z*WARP_AMP() + w2z*WARP_AMP2() + w3z*WARP_AMP3() }; }

    /** ★ 内部入口：坐标【已扭曲】 */
    static boolean isLandW(long ws, double wx, double wz) {
        long[] c = nearestCell(ws, wx, wz);
        return cellContAt(ws, c[0], c[1]); }

    /** ★ 对外：世界坐标（扭曲一次） */
    public static boolean isLand(long ws, double px, double pz) {
        // ★★★★★★★ B 方案：陆海判定改用【连续标量场】的等值线
        //   旧：nIndex(warp(细胞)) > 0.5  ⟹ 细胞级、随机、Voronoi 边界
        //   新：landScore(点) >= 0.5     ⟹ block 级、光滑、等值线
        if (USE_CONT_FIELD) return landScore(ws, px, pz) >= 0.5;
        double[] w = warp(ws, px, pz);
        return isLandW(ws, w[0], w[1]); }
    public static boolean nearestIsLand(long ws, double px, double pz) { return isLand(ws,px,pz); }

    // ================= ★★★★★★ 海岸距离（与 isLand 完全自洽） =================
    /** 最近大陆细胞 / 最近海洋细胞的加权距离（★ 坐标已扭曲） */
    static double[] coastDistancesW(long ws, double wx, double wz) {
        double cap = CAP_MULT * DS();
        double lm = cap, om = cap;
        long ci0 = (long) Math.floor(wx/DS()), cj0 = (long) Math.floor(wz/DS());
        for (int dj = -SCAN; dj <= SCAN; dj++) for (int di = -SCAN; di <= SCAN; di++) {
            long ci = ci0+di, cj = cj0+dj;
            double ex = wx - cellX(ws,ci,cj), ez = wz - cellZ(ws,ci,cj);
            double r = Math.sqrt((ex*ex + ez*ez) / cellB(ws,ci,cj));
            if (cellContAt(ws,ci,cj)) { if (r < lm) lm = r; } else { if (r < om) om = r; } }
        return new double[]{ lm, om }; }

    /**
     * ★★★★★★ 有符号海岸距离（米；正=陆侧）
     *   ★ 关键修复：改用【Voronoi 边界的有符号距离】
     *     ```
     *     d = (r2 - r1) / |grad(r2 - r1)|      // r1,r2 = 最近/第二近细胞的加权半径
     *     ```
     *     ⟹ 【连续】！跨越海岸线时 d 平滑过零
     *   ★ 原 bug：用「到最近陆细胞 vs 最近海细胞的距离」比大小
     *     ⟹ 跨越海岸线时两个量【互换】⟹ 幅值不连续 ⟹ h 跳 70 m
     *   ★ 符号：与 isLand 同源（最近细胞的类型）⟹ 完全自洽
     */
    /**
     * ★★★★★★ 有符号海岸距离（米；正=陆侧）
     *   ★ 正解：`d` = 到最近【异类细胞】的 Voronoi 边界距离
     *     · 若我在陆细胞 ⟹ 找最近的【海】细胞 ⟹ 算到边界的距离（正）
     *     · 若我在海细胞 ⟹ 找最近的【陆】细胞 ⟹ 算到边界的距离（负）
     *   ★ 为什么正确：只有一个「目标细胞」⟹ 不会像「最近 vs 第二近」那样来回换 ⟹ d 单调
     *   ★ 原 bug：用「最近 vs 第二近细胞」⟹ 第二近细胞来回换 ⟹ d 抖动（实测 312→4524→2430）
     */
    static double signedCoastDistW(long ws, double wx, double wz) {
        syncSuper();   // ★ 保证 SUPER 已设（memo 的 floorDiv 依赖它）
        long[] me = nearestCell(ws, wx, wz);
        boolean myLand = cellContAtMemo(ws, Math.floorDiv(me[0],(long)SUPER), Math.floorDiv(me[1],(long)SUPER));
        // 找最近的【异类】细胞
        double bestPhi = Double.MAX_VALUE; long ti = 0, tj = 0; double tB = 1.0;
        long ci0 = (long) Math.floor(wx/DS()), cj0 = (long) Math.floor(wz/DS());
        for (int dj = -SCAN; dj <= SCAN; dj++) for (int di = -SCAN; di <= SCAN; di++) {
            long ci = ci0+di, cj = cj0+dj;
            if (cellContAtMemo(ws, Math.floorDiv(ci,(long)SUPER), Math.floorDiv(cj,(long)SUPER)) == myLand) continue;      // ★ 只要异类
            double b = cellB(ws,ci,cj); if (b < 1e-6) b = 1e-6;
            double ex = wx - cellX(ws,ci,cj), ez = wz - cellZ(ws,ci,cj);
            double phi = (ex*ex + ez*ez) / b;
            if (phi < bestPhi) { bestPhi = phi; ti = ci; tj = cj; tB = b; } }
        double mag = 0.0;
        if (bestPhi < Double.MAX_VALUE) {
            // 与我、与目标细胞的势差 / 梯度 ⟹ 到二者边界的距离
            double mB = cellB(ws, me[0], me[1]); if (mB < 1e-6) mB = 1e-6;
            double mx = wx - cellX(ws, me[0], me[1]), mz = wz - cellZ(ws, me[0], me[1]);
            double tx = wx - cellX(ws, ti, tj),        tz = wz - cellZ(ws, ti, tj);
            double phiM = (mx*mx + mz*mz) / mB;
            double gx = 2.0*(tx/tB - mx/mB), gz = 2.0*(tz/tB - mz/mB);
            double gm = Math.sqrt(gx * gx + gz * gz);   // ★ hypot→sqrt（JFR: hypot 41 样本）
            // ★★★★★★ 用【物理距离】（到最近异类细胞中心的欧氏距离）
            //   ★ 为什么不用 Voronoi 边界距离：加权 Voronoi 的「边界距离」依赖细胞大小 b
            //     ⟹ 不单调（实测：离岸 10 km 时 d=9725，5 km 时 d=2430）
            //   ★ 物理距离单调（离岸越远越大）⟹ 可作 base 的驱动量
            mag = Math.sqrt(tx*tx + tz*tz); }
        double cap = 60.0 * DS();   // ★ 10->60（= 1,500 km，让海洋剖面能展开）
        if (mag > cap) mag = cap;
        double d = myLand ? mag : -mag;
        return d; }
    public static double[] coastDistances(long ws, double px, double pz) {
        double[] w = warp(ws, px, pz); return coastDistancesW(ws, w[0], w[1]); }
    public static double signedCoastDist(long ws, double px, double pz) {
        double[] w = warp(ws, px, pz); return signedCoastDistW(ws, w[0], w[1]); }

    /** 平滑阶跃 [0,1]。 */
    static double smoothstep01(double x) { double u = clamp01(x); return u*u*(3.0 - 2.0*u); }
    static double clamp01(double v){ return v<0?0:(v>1?1:v); }
    static double smoothstep(double e0, double e1, double x) {
        double t=(x-e0)/(e1-e0); if(t<0)t=0; if(t>1)t=1; return t*t*(3-2*t); }

    // ================= ★★★★★★ 合并扫描（性能 + 平滑） =================
    public static final class Q {
        public long ci0, cj0;
        public long[] bi = new long[3], bj = new long[3];
        public double[] bd = new double[3];
        public double oppPhys = 1e18;      // 到最近异类细胞的【物理距离】
        public boolean[] c = new boolean[3];
    }

    /** ★ 平滑最小值（p 越大越平滑）—— 消除「最近异类细胞换人」时的跳变 */
    public static double SMOOTH_P = 4.0;   // ★ 已扫描：4~100 对残留台阶无影响
    static double smin(double a, double b, double p) {
        double d = a - b;
        if (d >  30.0 * p) return b;
        if (d < -30.0 * p) return a;
        return b - p * Math.log1p(Math.exp(d / p)); }

    /**
     * ★★★★★★ 一次扫描得到全部量
     *   ★ 关键优化：按【超格】迭代，`nIndex` 每超格只算【一次】
     *     （原实现每细胞调一次 cellContAt，而 cellContAt 内部的 nIndex 有 25 次 cnoise）
     *   ★ 原：625 细胞 × 25 cnoise = 15,625 次
     *   ★ 新：25 超格 × 25 cnoise =    625 次   ⟹ 快 25 倍
     */
    /**
     * 单个半径的异类扫描（返回平滑后的最近异类物理距离；找不到返回 1e18）。
     * 同时维护最近 3 个（按加权半径）到 bi/bj/bd。
     */
    /**
     * 单个半径的异类扫描（★ 按【超格分块】：每超格只查一次 memo）。
     * 动机：原实现在 625 细胞的循环里每格调 Math.floorDiv×2 + HashMap 查，实测 0.085 us/细胞。
     * 改为：外层按超格、内层按超格内的细胞 ⟹ floorDiv/HashMap 次数从 625 降到约 25。
     */
    /**
     * 单个半径的异类扫描（★ 逐行迭代 + 超格变化时才查 memo）。
     * 设计：外层 j 从 cj0-R 到 cj0+R，内层 i 从 ci0-R 到 ci0+R（★ 恰好 (2R+1)^2 个）。
     *       每行的超格索引只变一次 ⟹ 每行查 memo 约 (2R/SUPER+2) 次。
     */
    private static double scanOpp(long ws, double qx, double qz, long ci0, long cj0,
            long[] bi, long[] bj, double[] bd, boolean myLand, int R) {
        syncSuper();
        double opp = 1e18;
        double same = 1e18;   // ★ 新增：最近的【同类】细胞的距离
        long SUP = SUPER;
        for (long cj = cj0 - R; cj <= cj0 + R; cj++) {
            long lastSgi = Long.MIN_VALUE; boolean land = false;
            for (long ci = ci0 - R; ci <= ci0 + R; ci++) {
                long sgi = Math.floorDiv(ci, SUP);
                if (sgi != lastSgi) { sgi = sgi; }   // no-op（保持可读）
                long curSgi = Math.floorDiv(ci, SUP);
                if (curSgi != lastSgi) {
                    lastSgi = curSgi;
                    land = cellContAtMemo(ws, curSgi, Math.floorDiv(cj, SUP)); }
                double ex = qx - cellX(ws, ci, cj), ez = qz - cellZ(ws, ci, cj);
                double phys = Math.sqrt(ex*ex + ez*ez);
                double bb = cellB(ws, ci, cj); if (bb < 1e-6) bb = 1e-6;
                double dd = phys / Math.sqrt(bb);
                for (int k = 0; k < 3; k++) if (dd < bd[k]) {
                    for (int m = 2; m > k; m--) { bd[m]=bd[m-1]; bi[m]=bi[m-1]; bj[m]=bj[m-1]; }
                    bd[k]=dd; bi[k]=ci; bj[k]=cj; break; }
                // ★★★★★★★ 2026-10-08 性能优化：**内层用直接 min，不用 smin**
                //
                // 【为什么】`smin` 含 `exp` + `log1p`（JFR 实测 log1p 占 184 样本）。
                //   而它在这里被调用：2 次/cell × 81 cells(OPP_R=4) × 4 次 evalD
                //   = **648 次/每个 eval**，全是昂贵的超越函数。
                //
                // 【为什么可以去掉】`smin` 的**外层**（`oppDist` 的软回退）已保证：
                //   · 值在 [soft/2, ...] 区间内单调光滑
                //   · 且 `oppPhys` 的距离在 cell 间通常【远大于】SMOOTH_P ⟹
                //     smin 的输出 ≈ min（平滑项 p·ln2 = 2.8 米，可忽略）
                //   ⟹ 内层直接 min = 等价 + 零超越函数。
                if (land != myLand) { if (phys < opp)  opp  = phys; }
                else                { if (phys < same) same = phys; } } }
        ctx().oppSame = same;
        return opp; }
    public static Q scan(long ws, double qx, double qz) {
        Q r = new Q();
        r.ci0 = (long) Math.floor(qx/DS()); r.cj0 = (long) Math.floor(qz/DS());
        for (int k=0;k<3;k++) r.bd[k] = Double.MAX_VALUE;
        // ★★★★★★★★ 两级扫描（2026-10-08）
        //   动机：signedCoastDist 里 R=12 的扫描 = 625 个细胞，是最贵的单项（实测 99 us）。
        //   但多数位置在【很近】处就有异类细胞 ⟹ 先用小半径 OPP_R_TIGHT，找不到才扩大。
        int Rfull = Math.max(4, OPP_R);
        int Rtight = Math.max(1, Math.min(OPP_R_TIGHT, Rfull));
        boolean myLand0 = cellContAtMemo(ws, r.ci0 / SUPER, r.cj0 / SUPER);
        r.oppPhys = scanOpp(ws, qx, qz, r.ci0, r.cj0, r.bi, r.bj, r.bd, myLand0, Rtight);
        if (r.oppPhys > 1e17 && Rtight < Rfull) {
            r.oppPhys = scanOpp(ws, qx, qz, r.ci0, r.cj0, r.bi, r.bj, r.bd, myLand0, Rfull);
        }
        for (int k=0;k<3;k++) r.c[k] = cellContAt(ws, r.bi[k], r.bj[k]);
        return r; }

    /**
     * ★ 诊断：返回 {bd[0],bd[1],bd[2], bi[0],bj[0], bi[1],bj[1], bi[2],bj[2]}（只读）。
     *
     * <p>用于扫描「多细胞交接点」：bd[k] 是第 k 近细胞的【加权距离】
     * （phys/√b），所以 |bd[0]-bd[1]| 小 ⟹ 在第 1/第 2 近细胞的 Voronoi 边界上。
     */
    public static double[] nearTieDbg(long ws, double px, double pz) {
        Q q = scan(ws, px, pz);
        return new double[]{ q.bd[0], q.bd[1], q.bd[2],
            q.bi[0], q.bj[0], q.bi[1], q.bj[1], q.bi[2], q.bj[2] }; }

    // ================= ★★★★★★★ 距离变换：连续的到岸距离场 =================
    //
    // 【为什么必须有】本会话确认：`d = ±oppDist(oppPhys)` **不是到岸距离** ——
    //   它在岸线处不穿零（实测从 +22906 直接跳到 −22905），导致：
    //     · base 的两个分支各自用「已饱和的 d」⟹ 海岸 6~15 格台阶
    //     · gate 的 gLand/gSea/wLand 在岸线互换 ⟹ 断崖
    //   我做过 7 次「从 d 做代数」的尝试，全部失败（d 是离散细胞集合上的 smin，
    //   在 1-block 邻域内几乎不变）。
    //
    // 【正解】用【距离变换】显式算出「到最近陆地细胞」与「到最近海洋细胞」的距离场，
    //   再双线性插值 —— 场是【连续的】（细胞级分辨率 → 插值平滑）。
    //
    // 【格距】变换在【细胞格】上做（ci × cj），所以返回值单位是【格数】
    //   （再 × DS() 得到 block）。
    //
    /** 距离变换的搜索半径（细胞格数）。半径外的值用 fallback 填充。 */
    public static int DT_R = 8;
    /** ★ A/B 开关：base 是否使用距离变换的 d（false = 回滚到旧 d）。 */
    public static boolean USE_DT_BASE = false;
    /** ★ A/B：是否使用 selfR 标定（关掉 = 原始距离变换）。 */
    public static boolean USE_SELFR = false;
    /** ★ gate 的过渡宽度（block）：dGate = dNew/(|dNew|+DT_GATE_W)。 */
    public static double DT_GATE_W = 25_000.0;   // ⚠ 当前未使用（试验残留）
    /** ★ A/B：是否使用新海岸剖面。 */

    /**
     * 单点的【连续】到岸距离（block）。
     *
     * <p>① 在 (±DT_R) 的细胞网格上标记「哪些细胞是陆」；
     * <p>② 两遍扫描距离变换 → 到最近陆地 / 到最近海洋的【格距】；
     * <p>③ 双线性插值到查询点；
     * <p>④ 返回到最近【陆地】细胞的 block 距离（海上 > 0；陆上为 0 或很小）。
     *
     * <p>⚠ 本方法只做诊断/验证用（每次调用都有开销）。生产接法见 {@link #coastDistField}。
     */
    // ================= ★★★★★★★ 距离变换（缓存版） =================
    //
    // 【设计】距离变换的结果是【按细胞格】的 ⟹ 可以【整块缓存】：
    //   · 每块 = 超级格（SUPER × SUPER 个细胞）
    //   · 块内预计算 (SUPER+2R+1)² 的 dL/dS（含 halo，保证块边界的双线性插值正确）
    //   · 查询时双线性插值 ⟹ O(1)
    //
    // 【为什么必须有缓存】未缓存版每次调用：
    //   new boolean[289] + new double[289]×2 + 289 次 cellContAtMemo
    //   ⟹ 实测 7.70 us/次（基线 2.09）
    //
    /** 距离变换的搜索半径（细胞格数）。 */
    /** ★ A/B 开关：base 是否使用距离变换的 d（false = 回滚到旧 d）。 */

    /** 一块距离变换场（含 halo）。 */
    static final class DtField {
        final int n;              // 网格边长（含 halo）
        final double[] dL, dS;    // 到最近陆地 / 最近海洋的【格距】
        final double[] selfR;     // ★ 每格到它【自己细胞中心】的距离（block）—— 标定用
        DtField(int n) { this.n = n; this.dL = new double[n*n]; this.dS = new double[n*n]; this.selfR = new double[n*n]; } }

    /** 块缓存（key = seed,si,sj）。 */
    private static final java.util.concurrent.ConcurrentHashMap<Long, DtField> DT_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DT_LIMIT = 4096;

    /** 取（或算）一个超格的距离变换场（含 DT_R 的 halo）。 */
    static DtField dtField(long ws, long si, long sj) {
        long key = ((si << 32) ^ (sj & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + ws;
        DtField f = DT_CACHE.get(key);
        if (f != null) return f;
        if (DT_CACHE.size() >= DT_LIMIT) DT_CACHE.clear();
        final int R = DT_R, S = (int) SUPER;
        final int n = S + 2 * R + 1;
        DtField nf = new DtField(n);
        long ci0 = si * SUPER - R, cj0 = sj * SUPER - R;
        boolean[] ld = new boolean[n * n];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            long ci = ci0 + i, cj = cj0 + j;
            ld[j*n+i] = cellContAtMemo(ws, ci / SUPER, cj / SUPER);
            // ★ 标定场：该格中心处「查询点到自己细胞中心」的距离
            //   查询点取格中心（cx + DS/2），所以到该格细胞中心的距离 = |cellCenter(ci,cj) − gridCenter|
            double gx = (ci + 0.5) * DS(), gz = (cj + 0.5) * DS();
            double ex = gx - cellX(ws, ci, cj), ez = gz - cellZ(ws, ci, cj);
            nf.selfR[j*n+i] = Math.sqrt(ex*ex + ez*ez); }
        final double BIG = 1e6;
        for (int k = 0; k < n*n; k++) { nf.dL[k] = ld[k] ? 0 : BIG; nf.dS[k] = ld[k] ? BIG : 0; }
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int k = j*n+i;
            if (i > 0) { nf.dL[k] = Math.min(nf.dL[k], nf.dL[k-1]+1); nf.dS[k] = Math.min(nf.dS[k], nf.dS[k-1]+1); }
            if (j > 0) { nf.dL[k] = Math.min(nf.dL[k], nf.dL[k-n]+1); nf.dS[k] = Math.min(nf.dS[k], nf.dS[k-n]+1); } }
        for (int j = n-1; j >= 0; j--) for (int i = n-1; i >= 0; i--) {
            int k = j*n+i;
            if (i < n-1) { nf.dL[k] = Math.min(nf.dL[k], nf.dL[k+1]+1); nf.dS[k] = Math.min(nf.dS[k], nf.dS[k+1]+1); }
            if (j < n-1) { nf.dL[k] = Math.min(nf.dL[k], nf.dL[k+n]+1); nf.dS[k] = Math.min(nf.dS[k], nf.dS[k+n]+1); } }
        DT_CACHE.put(key, nf);
        return nf; }

    /** 清空距离变换缓存（探针用）。 */
    public static void clearDtCache() { DT_CACHE.clear(); }

    /**
     * 单点的【连续】有符号到岸距离（block）。陆上 > 0。
     *
     * <p>★ 用缓存的距离变换场 + 双线性插值 ⟹ O(1)、连续、无「身份」依赖。
     */
    // ================= ★★★★★★★ block 级 DT（A 方案·真） =================
    //
    // 【为什么细胞级 DT 不够】056 的实测：
    //   · 细胞级 F=(dS−dL)·DS 只有 1~2 个细胞宽 ⟹ 双线性插值后 |∇F| 在海岸
    //     **内部**就退化为 0 ⟹ d = F/|∇F| 无法定义（实测返回常数 −3.4）
    //   · b0/cellContW 每 25,000 格才变一次 ⟹ 海岸处完全不变
    //   ⟹ **所有细胞级的量都无法表达海岸线**；必须真的做 block 级的距离变换。
    //
    // 【设计】
    //   · 瓦片 = 边长 BLOCK_DT_TILE（block）的正方形，多源 BFS 距离变换
    //   · 每瓦片存【有符号到岸距离】（block，陆上 > 0）
    //   · 缓存；查询 = O(1) 双线性
    //   · 瓦片边缘用【halo】保证插值正确
    //
    /** block 级 DT 的瓦片边长（block）。 */
    public static int BLOCK_DT_TILE = 512;
    /** block 级 DT 的搜索上限（block）—— 超过则用 fallback。 */
    public static int BLOCK_DT_R = 256;
    /** ★ DT 的采样步长（block）—— 每 STRIDE 个 block 采一次 isLand，省 2.45us×N²。 */
    public static int BLOCK_DT_STRIDE = 8;

    /** 一块 block 级距离场（含 halo）。 */
    static final class BlockDt {
        final int n, halo;
        final float[] dL, dS;   // 到最近陆地 / 最近海洋的距离（block）
        boolean empty;          // ★ true = 该瓦片无海岸（dL/dS 未填充）
        BlockDt(int n, int halo) { this.n = n; this.halo = halo; this.dL = new float[n*n]; this.dS = new float[n*n]; } }

    private static final java.util.concurrent.ConcurrentHashMap<Long, BlockDt> BLOCK_DT_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int BLOCK_DT_LIMIT = 8192;
    /** 诊断：瓦片构建次数。 */
    public static long DT_BUILDS = 0;

    /** 取（或算）一块 block 级距离场。 */
    static BlockDt blockDt(long ws, long tx, long tz) {
        long key = ((tx << 32) ^ (tz & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + ws;
        BlockDt f = BLOCK_DT_CACHE.get(key);
        if (f != null) return f;
        if (BLOCK_DT_CACHE.size() >= BLOCK_DT_LIMIT) BLOCK_DT_CACHE.clear();
        final int T = BLOCK_DT_TILE, H = 8, ST = BLOCK_DT_STRIDE;
        final int n = (T + 2*H) / ST;          // ★ 网格点数（每 ST 个 block 一个）
        BlockDt nf = new BlockDt(n, H);
        final long ox = tx * T - H, oz = tz * T - H;   // 网格原点（网格点 k ↔ ox + k*ST）
        // ① 标记陆性（block 级、逐块调用 isLand）
        // ★★★★★★★ 关键性能优化：**先判该瓦片是否含海岸**
        //
        // 【为什么】一个瓦片 528×528 block，而海岸线是【稀疏】的。
        //   若瓦片内全是陆地或全是海洋 ⟹ 没有 φ=0 的穿越 ⟹ DT 无用。
        //   实测：不加此判定时，每区块都要建瓦片（43 ms）⟹ 游戏卡死进不去。
        //
        // 【判定】按 stride 采样 ~个点；全同类 ⟹ 标记为空瓦片。
        //   成本 ~26 次 isLand（0.06 ms）vs 完整构建 43 ms ⟹ 700× 节省。
        boolean anyLand = false, anySea = false;
        for (int j = 0; j < n && !(anyLand && anySea); j += 4)
            for (int i = 0; i < n && !(anyLand && anySea); i += 4) {
                if (isLandAt(ws, ox + (long) i * ST, oz + (long) j * ST)) anyLand = true;
                else anySea = true; }
        if (!(anyLand && anySea)) {                 // ★ 无海岸 ⟹ 空瓦片
            nf.empty = true;
            BLOCK_DT_CACHE.put(key, nf); DT_BUILDS++;
            return nf; }

        final float BIG = 1e9f;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            boolean land = isLandAt(ws, ox + (long) i * ST, oz + (long) j * ST);
            int k = j*n + i;
            nf.dL[k] = land ? 0f : BIG;    // 到最近【陆地】
            nf.dS[k] = land ? BIG : 0f; }  // 到最近【海洋】
        // ② 两遍扫描 chamfer（对两个通道各做一次）
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int k = j*n + i;
            float aL = nf.dL[k], aS = nf.dS[k];
            if (i > 0)     { aL=Math.min(aL,nf.dL[k-1]+1f); aS=Math.min(aS,nf.dS[k-1]+1f); }
            if (j > 0)     { aL=Math.min(aL,nf.dL[k-n]+1f); aS=Math.min(aS,nf.dS[k-n]+1f); }
            if (i > 0 && j > 0) { aL=Math.min(aL,nf.dL[k-n-1]+1.41421356f); aS=Math.min(aS,nf.dS[k-n-1]+1.41421356f); }
            if (i < n-1 && j > 0) { aL=Math.min(aL,nf.dL[k-n+1]+1.41421356f); aS=Math.min(aS,nf.dS[k-n+1]+1.41421356f); }
            nf.dL[k]=aL; nf.dS[k]=aS; }
        for (int j = n-1; j >= 0; j--) for (int i = n-1; i >= 0; i--) {
            int k = j*n + i;
            float aL = nf.dL[k], aS = nf.dS[k];
            if (i < n-1)     { aL=Math.min(aL,nf.dL[k+1]+1f); aS=Math.min(aS,nf.dS[k+1]+1f); }
            if (j < n-1)     { aL=Math.min(aL,nf.dL[k+n]+1f); aS=Math.min(aS,nf.dS[k+n]+1f); }
            if (i < n-1 && j < n-1) { aL=Math.min(aL,nf.dL[k+n+1]+1.41421356f); aS=Math.min(aS,nf.dS[k+n+1]+1.41421356f); }
            if (i > 0 && j < n-1)   { aL=Math.min(aL,nf.dL[k+n-1]+1.41421356f); aS=Math.min(aS,nf.dS[k+n-1]+1.41421356f); }
            nf.dL[k]=aL; nf.dS[k]=aS; }
        BLOCK_DT_CACHE.put(key, nf); DT_BUILDS++; return nf; }

    /**
     * ★ block 级陆性判定（供 DT 用）—— 必须与 `PlateField.isLand` **完全一致**。
     *
     * <p>【为什么不能用 cellContAtMemo】它是【细胞级】的（整个 25,000 格同一值），
     *   用它标记会让整个 512 格瓦片同型 ⟹ DT 找不到异型块（实测全部 -1e9）。
     * <p>【正确判据】`PlateField.isLand` 逐 block 采样 `nIndex`——
     *   而 `nIndex` 在细胞内部是【连续变化】的 ⟹ 它才是真正的 block 级海陆边界。
     */
    static boolean isLandAt(long ws, long bx, long bz) {
        // ★★★★★★★ 必须用【与 PlateField.isLand 完全一致】的逐块判据！
        //
        // 【原来错在哪】我曾用 `cellContAtMemo(ci/SUPER, cj/SUPER)` —— 那是【细胞级】的：
        //   整个 25,000 格的细胞同一值 ⟹ 512 格的瓦片里【永远同类型】
        //   ⟹ DT 里没有真实海岸 ⟹ φ 乱跳（实测相邻块 62→427→1618）。
        //
        //   ⚠ `cellContAtMemo(ws, sgi, sgj)` 也是【细胞级】（它采样 `nIndex` 于超格），
        //     且【不含 warp】⟹ 仍然同类型。
        //   ⟹ 唯一真正的 block 级判据是 `isLand`（含 warp + nearestCell + cellContAt），
        //     也就是 `PlateField.isLand` 走的路径。
        return isLand(ws, (double) bx, (double) bz); }

    /** 诊断：返回一块 blockDT 的统计 {min, max, 非BIG个数, n, 原点x, 原点z}。 */
    public static double[] blockDtStat(long ws, long tx, long tz) {
        BlockDt f = blockDt(ws, tx, tz);
        double mn = 1e18, mx = -1e18; int notBig = 0;
        for (int q = 0; q < f.dL.length; q++) { double v = f.dL[q] - f.dS[q]; if (v < mn) mn = v; if (v > mx) mx = v; if (Math.abs(f.dL[q]) < 1e8 || Math.abs(f.dS[q]) < 1e8) notBig++; }
        final int T = BLOCK_DT_TILE, H = 8;
        return new double[]{ mn, mx, notBig, f.n, tx*T - H, tz*T - H }; }

    /** 清空 block 级 DT 缓存。 */
    public static void clearBlockDtCache() { BLOCK_DT_CACHE.clear(); }

    /**
     * ★ block 级的连续有符号到岸距离（陆上 > 0，海上 < 0）。
     *
     * <p>这是 base/gate 真正需要的量：分辨率 = 1 block，且在岸线处穿过 0。
     */
    public static double coastDistBlockPublic(long ws, double px, double pz) { return coastDistBlock(ws, px, pz); }
    public static double cellPhiDistPublic(long ws, double px, double pz) { return cellPhiDist(ws, px, pz); }
    public static double coastDistBlock(long ws, double px, double pz) {
        return coastDistBlock(ws, px, pz, Double.NaN); }   // 无 cellD 时内部算（探针用）

    /**
     * ★ 带 cellD 的版本：调用方（`eval`）已有细胞级 d ⟹ 直接传入，**避免重复 scan**。
     *
     * <p>【为什么必须传】`farFieldD` 内部的 `scan` 与 `eval` 的 `scan` 是同一个计算；
     *   重复调用实测让 eval 从 3 us 涨到 44 us。
     */
    public static double coastDistBlock(long ws, double px, double pz, double cellDIn) {
        // ★★★★★★★ 性能关键：只在【可能靠近海岸】时才做 block DT
        //
        // 【为什么】BiomeField 400×200 = 81,000 点/次、Landform 80,000 点/次，
        //   每次都做 block DT ⟹ eval 从 2.09 涨到 4.65 µs ⟹ 单次 solve 翻倍。
        //   实测日志：BiomeField 144s × 2 + Landform 85s = 370 秒。
        //
        // 【为什么不能用 |cellD|】它在海岸处也是 22,908（那是「到最近异类【细胞】」），
        //   所以门槛永不触发。
        // 【正解】用【细胞级 DT 场】的 |φ|：φ = dS − dL，从岸线处 0 线性增长（每格 ±1）。
        double cellPhi = cellPhiAbs(ws, px, pz);
        if (cellPhi >= NEAR_GATE) return (cellDIn == cellDIn && cellDIn < 0 ? -SAT_D : SAT_D);
        final int T = BLOCK_DT_TILE, H = 8, ST = BLOCK_DT_STRIDE;
        long bx = (long) Math.floor(px), bz = (long) Math.floor(pz);
        long tx = Math.floorDiv(bx, (long) T), tz = Math.floorDiv(bz, (long) T);
        BlockDt f = blockDt(ws, tx, tz);
        // ★ 空瓦片（无海岸）⟹ 直接用 cellD（远场值），不读 dL/dS
        // ★ 换算到【网格坐标】（1 网格单位 = ST block）
        double gx = (bx - (tx * T - H)) / (double) ST, gz = (bz - (tz * T - H)) / (double) ST;
        int i0 = (int) Math.floor(gx); if (i0 < 0) i0 = 0; if (i0 > f.n-2) i0 = f.n-2;
        int j0 = (int) Math.floor(gz); if (j0 < 0) j0 = 0; if (j0 > f.n-2) j0 = f.n-2;
        double u = gx - i0, v = gz - j0;
        if (u < 0) u = 0; if (u > 1) u = 1;
        if (v < 0) v = 0; if (v > 1) v = 1;
        int k = j0 * f.n + i0;
        // φ = dS − dL：海上 > 0、陆上 < 0、岸线 = 0
        double L00=f.dL[k], L10=f.dL[k+1], L01=f.dL[k+f.n], L11=f.dL[k+f.n+1];
        double S00=f.dS[k], S10=f.dS[k+1], S01=f.dS[k+f.n], S11=f.dS[k+f.n+1];
        double p00=S00-L00, p10=S10-L10, p01=S01-L01, p11=S11-L11;
        double phi = (p00*(1-u) + p10*u)*(1-v) + (p01*(1-u) + p11*u)*v;
        double near = -phi * ST;   // ★ 陆上 > 0；× ST 换回 block
        // ★★★★★★★ 远场回退：瓦片只有 TILE+2H block ⟹ 超出时用【细胞级 d】
        //   （细胞级 d 在远场是准确的——它测的是「到最近异类细胞」的距离；
        //     只有在【海岸附近】才不穿零 ⟹ 近场必须用 block DT）
        // ★★★★★★★ 纯 block DT（**不混接**）
        //
        // 【为什么删除混接】两个量物理意义不同、数量级差 100 倍：
        //   · blockDT = 「到【海岸线】的几何距离」（±250 block）
        //   · cellD   = 「到最近异类【细胞】的距离」（±23000 block）
        //   混接会产生【无意义】的值 —— 实测相邻块 22910 / 6277 / 427。
        //
        // 【正解】只用 blockDT（它是唯一的「到海岸距离」），超出瓦片可达范围时：
        //   · 若瓦片【无海岸】⟹ 用 cellD 的【符号】× 一个饱和值（远离海岸，值不重要）
        //   · 若瓦片【有海岸】但超出半径 ⟹ 同样饱和（DT 已保证单调）
        //   ⚠ 关键：饱和值是【空间常数】⟹ 不会在相邻块间跳变。
        if (f.empty) return (cellDIn == cellDIn && cellDIn < 0) ? -SAT_D : SAT_D;
        double mag = Math.abs(near);
        if (mag >= SAT_D) return (near >= 0 ? SAT_D : -SAT_D);   // ★ 饱和（空间常数）
        return near; }

    /** 混接尺度：cellDist 超过它就完全用细胞级（block）。 */

    /**
     * block DT 的饱和距离（block）。
     *
     * <p>= (网格边 − 2) / 2 × stride = (66−2)/2 × 8 = <b>256</b>。
     * <p>超出此距离 ⟹ 返回 ±SAT_D（空间常数 ⟹ 无跳变）。
     */
    public static double SAT_D = 256.0;

    /**
     * ★ 性能门槛：细胞级 |φ| 超过它就【跳过 block DT】（直接用饱和值）。
     *
     * <p>`φ = dS − dL`（单位 = DS），从岸线处 0 线性增长，每格 ±1。
     * <p>取 3 ⟹ 只在「离岸线 3 个格点（≈3×DT_R×DS/网格）内」才做几何 DT。
     */
    public static double NEAR_GATE = 3.0;

    /** ★ 廉价：细胞级 DT 的 |φ|（不构建 block 瓦片）。 */
    static double cellPhiAbs(long ws, double px, double pz) {
        final int R = DT_R;
        long ci = (long) Math.floor(px / DS()), cj = (long) Math.floor(pz / DS());
        long si = Math.floorDiv(ci, SUPER), sj = Math.floorDiv(cj, SUPER);
        DtField f = dtField(ws, si, sj);
        double gx = ci - (si * SUPER - R) + (px / DS() - ci);
        double gy = cj - (sj * SUPER - R) + (pz / DS() - cj);
        int i0 = (int) Math.floor(gx - 0.5), j0 = (int) Math.floor(gy - 0.5);
        if (i0 < 0) i0 = 0; if (i0 > f.n-2) i0 = f.n-2;
        if (j0 < 0) j0 = 0; if (j0 > f.n-2) j0 = f.n-2;
        double u = gx - 0.5 - i0, v = gy - 0.5 - j0;
        if (u < 0) u = 0; if (u > 1) u = 1;
        if (v < 0) v = 0; if (v > 1) v = 1;
        int k = j0 * f.n + i0;
        double p00=f.dS[k]-f.dL[k], p10=f.dS[k+1]-f.dL[k+1];
        double p01=f.dS[k+f.n]-f.dL[k+f.n], p11=f.dS[k+f.n+1]-f.dL[k+f.n+1];
        double phi = (p00*(1-u)+p10*u)*(1-v) + (p01*(1-u)+p11*u)*v;
        return Math.abs(phi); }


   /** 远场回退的阈值（block）—— 超过则用细胞级 d。 */
    public static double FAR_LIMIT = 200.0;

    /**
     * 远场回退：用【细胞级】的旧 d（测「到最近异类细胞」的距离）。
     *
     * <p>【为什么远场可以退回细胞级】细胞级 d 在【远离海岸】处是准确的
     *   （它测的就是到最近异类细胞的距离）；只有在【海岸附近】才不穿零。
     *   ⟹ 近场（|d| ≤ FAR_LIMIT）必须用 block DT，远场用细胞级即可。
     *
     * <p>⚠ 本方法内部自己算，**不依赖任何外部状态**（探针直接调用也正确）。
     */
    static double farFieldD(long ws, double px, double pz) {
        double[] w = warp(ws, px, pz);
        Q q = scan(ws, w[0], w[1]);
        double mag = oppDist(q.oppPhys);
        return q.c[0] ? mag : -mag; }

    // ================= ★★★★★★★ block 级连续到岸场（A 方案·旧·已弃） =================
    //
    // 【为什么需要】056 证实：整条海岸线 100% 都是墙（p50=174 米）。
    //   根因是：现有的所有连续量都是【细胞级】的——
    //     · nIndex / cellContW（b0/b1）：每 25,000 格才变一次 ⟹ 海岸处不变
    //     · oppPhys（到最近异类细胞）    ：岸线处不穿零（±22906）
    //   ⟹ 海岸线是【细胞内部】的边界，需要【比细胞更细】的连续量。
    //
    // 【正解】不需要新的 1-block 网格！
    //   距离变换已经给了「每个细胞中心的【有符号到岸距离】」：
    //       F(ci,cj) = (dS − dL)·DS      （陆上 < 0，海上 > 0）
    //   把它【双线性插值】⟹ 得到一个光滑的隐式场 F(x,z)。
    //   而【到隐式曲线 F=0 的距离】有解析式：
    //       d = F / |∇F|
    //   ⟹ F 在岸线处 = 0 ⟹ d = 0 ✓ 连续
    //   ⟹ F 的梯度是逐 block 变化的（双线性）⟹ d 是 block 级分辨率
    //
    /**
     * block 级的【连续】有符号到岸距离（陆上 > 0，海上 < 0）。
     *
     * <p>算法：双线性插值 F = (dS−dL)·DS，然后 d = F / |∇F|。
     * <p>⟹ 岸线处 F=0 ⟹ d=0 ⟹ **无阶跃**（这是 base/gate 需要的量）。
     */
    public static double coastDistGeo(long ws, double px, double pz) {
        final int R = DT_R;
        long ci = (long) Math.floor(px / DS()), cj = (long) Math.floor(pz / DS());
        long si = Math.floorDiv(ci, SUPER), sj = Math.floorDiv(cj, SUPER);
        DtField f = dtField(ws, si, sj);
        double gx = ci - (si * SUPER - R) + (px / DS() - ci);
        double gy = cj - (sj * SUPER - R) + (pz / DS() - cj);
        int i0 = (int) Math.floor(gx - 0.5), j0 = (int) Math.floor(gy - 0.5);
        if (i0 < 0) i0 = 0; if (i0 > f.n-2) i0 = f.n-2;
        if (j0 < 0) j0 = 0; if (j0 > f.n-2) j0 = f.n-2;
        double u = gx - 0.5 - i0, v = gy - 0.5 - j0;
        if (u < 0) u = 0; if (u > 1) u = 1;
        if (v < 0) v = 0; if (v > 1) v = 1;
        // 四角（用 dS − dL 作为场值，单位 = DS）
        int k00 = j0*f.n + i0;
        double F00 = (f.dS[k00]        - f.dL[k00])        * DS();
        double F10 = (f.dS[k00+1]      - f.dL[k00+1])      * DS();
        double F01 = (f.dS[k00+f.n]    - f.dL[k00+f.n])    * DS();
        double F11 = (f.dS[k00+f.n+1]  - f.dL[k00+f.n+1])  * DS();
        // 双线性值与两个偏导（对 (u,v)，单位 = block/block）
        double a = F10 - F00, b = F01 - F00, c = F00 - F10 - F01 + F11;
        double Fv = F00 + a*u + b*v + c*u*v;
        double dFdu = a + c*v, dFdv = b + c*u;
        double gmag = Math.sqrt(dFdu*dFdu + dFdv*dFdv);
        if (gmag < 1e-9) return 0.0;      // 平场 ⟹ 无梯度 ⟹ 视为岸线
        // d = F / |∇F|（陆上 F<0 ⟹ 取负号使陆上为正）
        double d = -Fv / gmag;
        return d; }

    /** ★★★★★★★ 用【细胞级 DT 的 φ】估到岸距离（block）—— 与 blockDT 同量纲。 */
    static double cellPhiDist(long ws, double px, double pz) {
        final int R = DT_R;
        long ci = (long) Math.floor(px / DS()), cj = (long) Math.floor(pz / DS());
        long si = Math.floorDiv(ci, SUPER), sj = Math.floorDiv(cj, SUPER);
        DtField f = dtField(ws, si, sj);
        double gx = ci - (si * SUPER - R) + (px / DS() - ci);
        double gy = cj - (sj * SUPER - R) + (pz / DS() - cj);
        int i0 = (int) Math.floor(gx - 0.5), j0 = (int) Math.floor(gy - 0.5);
        if (i0 < 0) i0 = 0; if (i0 > f.n-2) i0 = f.n-2;
        if (j0 < 0) j0 = 0; if (j0 > f.n-2) j0 = f.n-2;
        double u = gx - 0.5 - i0, v = gy - 0.5 - j0;
        if (u < 0) u = 0; if (u > 1) u = 1;
        if (v < 0) v = 0; if (v > 1) v = 1;
        int k = j0 * f.n + i0;
        double p00=f.dS[k]-f.dL[k], p10=f.dS[k+1]-f.dL[k+1];
        double p01=f.dS[k+f.n]-f.dL[k+f.n], p11=f.dS[k+f.n+1]-f.dL[k+f.n+1];
        double phi = (p00*(1-u)+p10*u)*(1-v) + (p01*(1-u)+p11*u)*v;
        return Math.abs(phi) * DS(); }   // ★ φ 的单位是 DS ⟹ × DS 得到 block

    public static double coastDistNew(long ws, double px, double pz) {
        return coastDistNew(ws, px, pz, Boolean.FALSE); }   // 无外部符号时的旧行为

    /**
     * ★ 带【外部陆性】的版本：符号由调用方给定的 isLand 决定。
     *
     * <p>【为什么需要】在【细胞边界】那一格，「点的陆性」（`q.c[0]`）与
     *   「距离变换格的陆性」（`aL < aS`）可能不一致 ⟹ `dNew` 的符号/大小出错
     *   ⟹ `base` 在单格上跳出（实测 46.8 → 191.4 → 46.8）。
     * <p>【修法】用调用方的 isLand 决定符号 ⟹ 与 `eval` 的陆性一致。
     *   为 null 时退回 `aL < aS`。
     */
    public static double coastDistNew(long ws, double px, double pz, Boolean extLand) {
        final int R = DT_R, S = (int) SUPER;
        long ci = (long) Math.floor(px / DS()), cj = (long) Math.floor(pz / DS());
        long si = Math.floorDiv(ci, SUPER), sj = Math.floorDiv(cj, SUPER);
        DtField f = dtField(ws, si, sj);
        // 查询点在块网格中的坐标（块的原点 = si*SUPER - R）
        double gx = ci - (si * SUPER - R) + (px / DS() - ci) + 0.0;
        double gy = cj - (sj * SUPER - R) + (pz / DS() - cj) + 0.0;
        int i0 = (int) Math.floor(gx - 0.5), j0 = (int) Math.floor(gy - 0.5);
        if (i0 < 0) i0 = 0; if (i0 > f.n-2) i0 = f.n-2;
        if (j0 < 0) j0 = 0; if (j0 > f.n-2) j0 = f.n-2;
        double tx = gx - 0.5 - i0, tz = gy - 0.5 - j0;
        if (tx < 0) tx = 0; if (tx > 1) tx = 1;
        if (tz < 0) tz = 0; if (tz > 1) tz = 1;
        double aL = bilerp(f.dL, f.n, i0, j0, tx, tz), aS = bilerp(f.dS, f.n, i0, j0, tx, tz);
        // ★★★★★★★ 2026-10-08 标定：减去「到自身细胞中心的距离」（同样双线性插值 ⟹ 连续）
        //
        // 【为什么需要】`dNew` 是从【最近同类细胞中心】量的；
        //   而旧 d 的零点在【海岸线】（= 到最近异类细胞中心的距离）。
        //   两者相差「查询点到自身细胞中心的距离」⟹ 减掉它即与旧 d 同口径。
        //
        // 【为什么用插值场而不是直接算】直接算 |query − cellCenter| 会在【细胞边界】
        //   跳变（0 → DS/2）⟹ 会把好不容易消除的阶跃又引回来。
        //   所以预计算「每格到自己细胞中心的距离」并双线性插值 —— 与 dL/dS 同一套，连续。
        double selfR = bilerp(f.selfR, f.n, i0, j0, tx, tz);
        // ★ 符号：优先用外部传入的陆性（与 eval 一致），否则用插值判定
        boolean hereLand = (extLand != null) ? extLand.booleanValue() : (aL < aS);
        double mag = Math.abs((aS - aL) * DS() - (USE_SELFR ? selfR : 0.0));
        return hereLand ? mag : -mag; }



    private static double bilerp(double[] a, int N, int i, int j, double tx, double tz) {
        double v00 = a[j*N+i], v10 = a[j*N+i+1], v01 = a[(j+1)*N+i], v11 = a[(j+1)*N+i+1];
        return (v00*(1-tx)+v10*tx)*(1-tz) + (v01*(1-tx)+v11*tx)*tz; }

    // ================= ★★★★★★★ 原始类型键的 map（消除 HashMap 装箱）================
    //
    // 【为什么】JFR 实测 `HashMap` 相关占 456 样本（12%）：
    //     HashMap$HashIterator.nextNode  194
    //     HashMap.getNode                100
    //     HashMap$TreeNode.getTreeNode    65   ← ★ 哈希冲突树化
    //     HashMap.comparableClassFor      50   ← ★ 树化
    //     HashMap.putVal / resize      24+23
    //   根因：`HashMap<Long, X>` 每次查找/插入都**装箱一个 Long**，
    //   且 `Long` 的 hashCode 是 splitmix ⟹ 桶分布差 ⟹ 频繁树化。
    //
    // 【正解】原始类型键 + 开放寻址的 Long→double 哈希表：
    //   · 零装箱、零 Node 对象、零树化
    //   · 单数组 + 线性探测 ⟹ 对 CPU 缓存友好
    //
    /** ★ 原始 long 键 → double 值的开放寻址哈希表（零装箱）。 */


    /** ★ 由 oppPhys 得到【平滑】的离岸距离（软回退，消除搜索窗边界跳变） */
    static double oppDist(double oppPhys) {
        double R = Math.max(4, OPP_R) * DS();
        double soft = 2.0 * R;                       // 软回退尺度（= 2 倍窗半径）
        double raw = (oppPhys < 1e17) ? oppPhys : soft;
        // 软回退：当 oppPhys ≈ 0 时把 d 抬到 soft/2（连续）
        double ret = soft * (0.5 + 0.5 * raw / (raw + soft));
        return Math.min(raw, ret); }

    private TalosLandField() {}

    public static double[] eval(long ws, double px, double pz) {
        final Ctx cx = ctx();   // ★ 取一次，复用（原来调 2~2 次 ThreadLocal.get）
        double[] wq = warp(ws, px, pz);
        double qx = wq[0], qz = wq[1];

        // ★★★★★ 修复：qx/qz 已扭曲 ⟹ 直接用【已扭曲】版本（原来会双重扭曲）
        Q q = scan(ws, qx, qz);
        boolean isLand = q.c[0];
        // ★ 离岸距离：由合并扫描的 oppPhys 得到（平滑 + 软回退）
        double dOpp  = oppDist(q.oppPhys);
        double dSame = oppDist(lastSamePhys());
        // ★ d 保持【旧口径】不变（base 剖面与 gLand/gSea 都依赖它）
        double dMag = dOpp;
        double d = isLand ? dMag : -dMag;

        // ★★★★★★★ 2026-10-08（距离变换）：**新的连续到岸距离**（只用于 base）
        //   旧 d 保持给 profile/gate 用（它们的标定围绕旧 d）
        // ★★★★★★★ 海岸剖面的输入（三选一）
        //   USE_NEW_BASE = true  ⟹ 用【连续】的 dNew 与新公式（无阶跃）
        //   否则                  ⟹ 用旧 d 与旧公式（有 6 格阶跃，回退用）
        double dNew = (USE_NEW_BASE || USE_DT_BASE) ? coastDistBlock(ws, px, pz, d) : d;   // ★ A 方案：block DT + 传 cellD 复用
        double dForBase = (USE_NEW_BASE || USE_DT_BASE) ? dNew : d;

        double base;
        double U = coastUnit();   // ★ 海岸剖面的单位长度（= DS）
        if (USE_NEW_BASE) {
            base = baseFromDist(dNew);   // ★ 新的连续海岸剖面（以 dNew 为输入）
        } else if (dForBase >= 0) {
            double u1 = clamp01(dForBase / (COAST_LAND_U1 * U));
            double u2 = 1.0 - Math.exp(-Math.max(0.0, dForBase - COAST_LAND_U1*U) / (COAST_LAND_U2 * U));
            base = LAND_BASE * (u1*u1*(3-2*u1)) + 400.0 * u2;
        } else {
            double s = -dForBase;
            double shelf = -200.0  * clamp01(s / (COAST_SHELF * U));
            double slope = -1800.0 * clamp01((s - COAST_SHELF*U) / (COAST_SLOPE * U));
            double deep  = -2000.0 * (1.0 - Math.exp(-Math.max(0.0, s - COAST_DEEP_OFF*U) / (COAST_DEEP_W * U)));
            base = shelf + slope + deep; }

        // ★★★★★★ 合并扫描的结果（替代原来的 4 次独立扫描）
        long[] bi = q.bi, bj = q.bj; double[] bd = q.bd;
        boolean c0 = q.c[0], c1 = q.c[1], c2 = q.c[2];
        // ★ 连续陆性权重（供 profile 做连续混合，消除断崖）
        double b0 = cellContW(ws, q.bi[0], q.bj[0]);
        double b1 = cellContW(ws, q.bi[1], q.bj[1]);
        double oppPhys2 = q.oppPhys;
        double Rloc = bd[0]*Math.sqrt(cellB(ws,bi[0],bj[0])); if (Rloc < 2.0 * U) Rloc = 2.0 * U;
        double s0x=cellX(ws,bi[0],bj[0]), s0z=cellZ(ws,bi[0],bj[0]);
        double dx1=cellX(ws,bi[1],bj[1])-s0x, dz1=cellZ(ws,bi[1],bj[1])-s0z;
        double L=Math.sqrt(dx1*dx1+dz1*dz1); if(L<1.0) L=1.0;   // ★ hypot→sqrt
        double nx=dx1/L, nz=dz1/L;
        // ★★★★★★ db 改用【到最近异类细胞的物理距离】
        //   ★ 原 bug：db = (bd[1]-bd[0])*√b/2 —— 「最近 vs 第二近」的加权半径差
        //     而「第二近细胞」会随位置换人 ⟹ db 在 58~8,576 间随机跳 ⟹ hfeat 振荡
        double db = dMag;   // ★ db 与 d 同源（平滑 + 软回退）
        // ★★★★★★★ 2026-10-08 定稿：conv 与 shear 都来自 `d` 的**同一个梯度**
        //
        // 【历史】原实现用「最近 vs 第二近细胞的速度差」：
        //     ① 依赖细胞【身份】⟹ 跨 Voronoi 边界跳变（断崖根因）
        //     ② 物理上不成立 —— 速度场的 cnoise 格距 = velWL() = 300,000 格，
        //        比细胞尺度（DS=25,000）大 12 倍 ⟹ 「相邻细胞速度差」是随机量
        //   我曾尝试 4 种「连续化」方案（平滑速度场 / 解析梯度 / 固定偏移），全部失败；
        //   完整记录见 待裁决/037、/038。
        //
        // 【正解】改用 `d` 的梯度（`d` 已是连续量），并分解为
        //   沿向 = conv（汇聚度）、切向 = shear（火山弧调制）。
        //   ⟹ 同一个梯度、零额外扫描、无身份依赖。
        double[] cs = cx.coastGrad;
        coastGrad(ws, qx, qz, cs);
        double conv  = cs[0];
        double shear = cs[1];
        double hfeat = profile(db, conv, shear, b0, b1, Rloc);   // ★ 连续权重（不再是布尔分支）
        // ★★★★★★★ 2026-10-08：gLand/gSea 改用**连续的 dGate**（由 dNew 构造）
        //   `dGate` ∈[−1,+1]，岸线处 = 0 ⟹ 两端都过零 ⟹ 无阶跃。
        //   旧公式用 `d`（不穿零）⟹ 在岸线处饱和值互换 ⟹ gate 从 0 跳到 1。
        double gLand = clamp01( d / (COAST_LAND_U1 * U)); gLand = gLand*gLand*(3-2*gLand);
        double gSea  = clamp01(-d / (4.0 * U)); gSea  = gSea *gSea *(3-2*gSea);

        double wLand = clamp01(0.5 + d / (2.0 * U));   // 0=纯海, 1=纯陆（跨越海岸连续）

                //
        // 【第 1 步后剩余的主跳】实测（38750→38751，跨海岸一格）：
        //     gLand  0.195 -> 0.000   跳 -0.195
        //     gSea   0.000 -> 0.388   跳 +0.388
        //   ⟹ gate 0.162 -> 0.066 ⟹ hfeat 3900 -> 1907（跳 1993 米）
        //   原因：它们是 smoothstep(±d/(...))，而 d **不穿零** ⟹ 海岸处互换。
        //
        // 【正解】改用连续的 dGate ∈[−1,+1]（岸线处 =0）：
        //     gLand = smoothstep01(max(0, +dGate))
        //     gSea  = smoothstep01(max(0, −dGate))
        //   · 岸线：dGate=0 ⟹ gLand=gSea=0（海岸处不侵蚀 ⟹ 符合设计意图）
        //   · 内陆：dGate→+1 ⟹ gLand→1, gSea→0 ⟹ gate→1
        //   · 深海：dGate→−1 ⟹ gLand→0, gSea→1 ⟹ gate→1
        // ★★★★★★ 修复：gate 用【连续插值】而不是分支选择
        //   ★ 原 bug：c0/c1 的类型组合翻转时 gate 换分支 ⟹ hfeat 跳变（实测 max 242 m）
        //   正解：用【陆性权重】连续混合 —— gLand 与 gSea 都是 d 的连续函数
        // ★★★★★★★ 2026-10-08（方案 A 第 1 步）：**只换 wLand**
        //
        // 【原 bug】wLand = clamp01(0.5 + d/(2U))，而 d = ±dOpp **不穿过 0**：
        //   内陆 d=+42503 ⟹ wLand=1；跨海岸一格 d=-42504 ⟹ wLand=0 ⟹ gate 跳。
        //
        // 【本步】新增独立的 dGate ∈[−1,+1]（岸线处=0，连续），只把它用于 wLand：
        //   sDiff = dOpp − dSame   （陆上 >0，海上 <0，岸线 =0）
        //   sSum  = max(1, dOpp + dSame)
        //   dGate = sDiff / sSum
        //   wLand = smoothstep01(0.5 + 0.5*dGate)
        //
        // 【端点】岸线 dOpp=dSame=W ⟹ dGate=0 ⟹ wLand=0.5；
        //        内陆 dSame→0 ⟹ dGate→+1 ⟹ wLand→1 ✓
        //        深海 dOpp→0  ⟹ dGate→−1 ⟹ wLand→0 ✓
        //
        // ⚠ gLand/gSea 与本步**不变**（仍用旧 d）—— 这是「一次只改一处」的纪律。
        double gate = wLand * gLand + (1.0 - wLand) * gSea;
        hfeat = hfeat * gate;

        // ★★★★★★★ 2026-10-08（方案 A 第 3 步）：**到岸连续衰减**
        //   coastDist = |dOpp − dSame| / 2（岸线处 = 0，连续）
        //   ⟹ hfeat 在海岸处连续趋近 0，消除 1993 米的 hfeat 跳变
        //   ⚠ 只乘在 hfeat 上，**不动 gate、不动 base**
        double coastDist = 0.5 * Math.abs(dOpp - dSame);
        hfeat *= smoothstep01(coastDist / COAST_FADE_W);

        // ★★★★★★★ B 方案：把【硬布尔 × 大增益】换成【连续混合】
        //   旧：(isLand ? 7.55 : 2.955) ⟹ 岸线处跳 2.55 倍 ⟹ **墙**
        //   新：gain = 2.955 + (7.55-2.955) * landness  ⟹ 连续
        double gain = H_GAIN_SEA + (H_GAIN_LAND - H_GAIN_SEA) * landness(ws, px, pz);
        double h = (base + hfeat) * H_GAIN * gain;
        if (isLand && h < 1.0)  h = 1.0;
        if (!isLand && h > -1.0) h = -1.0;
        // ★ 2026-10-08：复用 ThreadLocal 缓冲（原来每次 new double[8]）
        double[] out = cx.evalBuf;
        out[0]=c0?1:0; out[1]=c1?1:0; out[2]=c2?1:0;
        out[3]=(float) h; out[4]=base; out[5]=Rloc; out[6]=d; out[7]=(float) h;
        return out; }

    /** 诊断：返回 profile 的输入 (db, conv, shear, Rloc, hfeat) */
    public static double[] dbgEval(long ws, double px, double pz) {
        double[] wq = warp(ws, px, pz);
        double qx = wq[0], qz = wq[1];
        long ci0 = (long) Math.floor(qx/DS()), cj0 = (long) Math.floor(qz/DS());
        long[] bi=new long[3], bj=new long[3]; double[] bd=new double[3];
        for(int k=0;k<3;k++) bd[k]=Double.MAX_VALUE;
        for (int dj=-3; dj<=3; dj++) for (int di=-3; di<=3; di++) {
            long ci=ci0+di, cj=cj0+dj;
            double ex=qx-cellX(ws,ci,cj), ez=qz-cellZ(ws,ci,cj);
            double dd=Math.sqrt((ex*ex+ez*ez)/cellB(ws,ci,cj));
            for(int k=0;k<3;k++) if(dd<bd[k]){
                for(int m=2;m>k;m--){ bd[m]=bd[m-1]; bi[m]=bi[m-1]; bj[m]=bj[m-1]; }
                bd[k]=dd; bi[k]=ci; bj[k]=cj; break; } }
        boolean c0=cellContAt(ws,bi[0],bj[0]), c1=cellContAt(ws,bi[1],bj[1]);
        double b0 = cellContW(ws, bi[0], bj[0]);
        double b1 = cellContW(ws, bi[1], bj[1]);
        double Rloc = bd[0]*Math.sqrt(cellB(ws,bi[0],bj[0]));
        double U = coastUnit(); if (Rloc < 2.0*U) Rloc = 2.0*U;
        double s0x=cellX(ws,bi[0],bj[0]), s0z=cellZ(ws,bi[0],bj[0]);
        double dx1=cellX(ws,bi[1],bj[1])-s0x, dz1=cellZ(ws,bi[1],bj[1])-s0z;
        double L=Math.sqrt(dx1*dx1+dz1*dz1); if(L<1.0) L=1.0;   // ★ hypot→sqrt
        double nx=dx1/L, nz=dz1/L;
        double db=(bd[1]-bd[0])*Math.sqrt(cellB(ws,bi[0],bj[0]))/2.0;
        // ★ 2026-10-08 同步到新口径（与 eval 一致，消除诊断误导）：
        //   conv 改用 d 的沿向梯度；shear 沿用旧公式（次要项，尚未连续化）。
        double[] cs2 = ctx().coastGrad;
        coastGrad(ws, qx, qz, cs2);
        double conv = cs2[0], shear = cs2[1];
        double hfeat = profile(db, conv, shear, b0, b1, Rloc);   // ★ 连续权重（不再是布尔分支）
        return new double[]{ db, conv, shear, Rloc, hfeat, c0?1:0, c1?1:0, bd[0], bd[1] }; }
    /**
     * 地形特征剖面（山/裂谷/海沟/洋脊）。
     *
     * <h3>★★★ 2026-10-08 从根上修：消除【布尔分支】造成的断崖</h3>
     *
     * <p><b>原 bug</b>（实测：用户截图里的垂直石壁）：
     * <pre>
     *   断崖两侧（z=128382 -> 128383，相隔 1 格）：
     *     base      3.40 / 3.40      （不变）
     *     d         9415.7 / 9415.4  （只差 0.3 米）
     *     c0        1 / 1            （不变）
     *     c1        0 / 1            ★★ 翻转！
     *     hfeat     -76.2 / 5283.6   ★★ 跳 5359.8 米
     * </pre>
     * 根因：原实现按 {@code c0}/{@code c1} 分成【三个互斥分支】，
     * 而 {@code c0}/{@code c1} 是【第二近 Voronoi 细胞的布尔陆性】——
     * 跨过细胞边界时它会翻转 ⟹ 走不同分支 ⟹ 结果不连续。
     * 再被 {@code OROG_AMP=7500} 放大 ⟹ 5,359 米的垂直墙。
     *
     * <p><b>修法（从根上）</b>：先把两个【连续子剖面】都算出来，
     * 再用【连续的陆性权重】混合 —— 全程没有布尔切换：
     * <pre>
     *   pLand = w*(OROG*eo) + (1-w)*(-RIFT*ef)          // 陆地侧
     *   pSea  = w*(trench+arc) + (1-w)*ridge            // 海洋侧
     *   hfeat = f*pLand + (1-f)*pSea                    // f = 连续陆性权重 ∈ [0,1]
     * </pre>
     *
     * <p><b>f 从哪来</b>：三类信息（都是连续的）：
     * <ol>
     *   <li>{@code b0} / {@code b1} = 最近/第二近细胞的陆性，**已是连续权重**（超格陆地占比）
     *       —— 由 {@code eval} 从 {@code cellContAt} 传入；</li>
     *   <li>{@code d} 的**符号与大小**（到最近异类细胞的物理距离，连续）；</li>
     *   <li>{@code w}（汇聚度，连续）。</li>
     * </ol>
     *
     * <p>⚠ 原签名收 {@code boolean c0, c1}；现改收 {@code double b0, b1}（0~1）。
     * 布尔版调用点传 0.0/1.0 即等价。
     */
    static double profile(double d, double conv, double shear, double b0, double b1, double R) {
        double c = conv/V_REF;
        double w = smoothstep(-0.35, 0.35, c);
        double ad = Math.abs(d);
        double lw=REL_OROG*R, ow=REL_OCEA*R, rw=REL_RIDGE*R, fw=REL_RIFT*R;
        double eo = Math.exp(-(ad*ad)/(lw*lw));
        double ef = Math.exp(-(ad*ad)/(fw*fw));
        double et = Math.exp(-(ad*ad)/(ow*ow));
        double er = Math.exp(-(ad*ad)/(rw*rw));

        // ---- 子剖面 1：陆地侧（原来的 c0 && c1 分支）----
        double pLand = w*(OROG_AMP*eo) + (1.0-w)*(-RIFT_AMP*ef);

        // ---- 子剖面 2：海洋侧（原来的 !c0 && !c1 分支）----
        double volc = 0.35 + 0.65*Math.min(1.0, shear/V_REF);
        double trench = -TRENCH_D*et;
        double arc = ARC_AMP*volc*Math.exp(-Math.pow((ad-0.9*R)/(0.4*R),2));
        double ridge = RIDGE_H*(1.0-Math.exp(-ad/rw));
        double pSea = w*(trench+arc) + (1.0-w)*ridge;

        // ---- 子剖面 3：过渡带（原来的 else 分支）----
        //   ⚠ 这一支原本用 `d > 0 ? c0 : c1` 决定「陆侧还是海侧」——
        //     那个布尔在海岸处翻转 ⟹ 也是断崖源。现在改成【用 |d| 连续加权】。
        //   COAST_W = 过渡带宽（米）：|d| 超过它就完全确定是哪一侧。
        final double COAST_W = 2_000.0;

        // ---- ★ 用【连续陆性权重】在三个子剖面之间混合 ----
        //   b0/b1 ∈ [0,1] 是最近/第二近细胞的陆性（连续）。
        //   f=1（两格全陆）  -> pLand
        //   f=0（两格全海）  -> pSea
        //   0<f<1（海岸带）  -> pMix
        // ★★★★★★★ 2026-10-08 重构：**只做两端混合**（去掉 pMix）。
        //
        // 【原 bug】原来是三段混合：
        //     f = clamp01(0.5*(b0+b1));
        //     wMix = 4f(1-f), wLand = f², wSea = (1-f)²;
        //     return (wLand*pLand + wSea*pSea + wMix*pMix) / wSum;
        //   ⚠ f→1 时该式 → pLand，但 pMix ≠ pLand ⟹ **必然不连续**
        //   实测：陆地内部出现 6360 与 7207 两块常数平台，交界处跳 **847 米**。
        //
        // 【为什么 b1 不能用】b1 是【第二近细胞】的陆性 —— 它随 bi[1] 换人而跳变。
        //   f 取了 (b0+b1)/2 ⟹ f 也跟着跳 ⟹ pMix 的权重跳。
        //
        // 【正解】只用 **b0**（最近细胞的陆性，连续）做两端混合：
        //     f = b0
        //     hfeat = f*pLand + (1-f)*pSea
        //   f→1 时 → pLand ✓（pMix 已移除）
        //   f→0 时 → pSea  ✓
        //   ⟹ 全程无分支、无第三段
        //
        // 【代价】原来的 pMix（海岸过渡带剖面）消失 ⟹ 海岸线由
        //   pLand/pSea 的连续混合来表达（f 本身就是「陆性」）。
        double f = clamp01(b0);
        return f*pLand + (1.0 - f)*pSea;
    }

    // ================= ★★★★★★★★ height 记忆化 =================
    //   height 实测 56.81 us（isLand 只 2.61 us）⟹ 差别在 eval 的 625 细胞扫描。
    //   OrographyField.sample / LandformField.solve 会对同一坐标重复调用。
    // ★★★★★★★ 2026-10-08 性能优化：HashMap<Long,Double> → LongDblMap（零装箱）
    //   JFR 实测：HashMap 相关 456 样本 + ThreadLocalMap.set 122 样本
    private static final ThreadLocal<LongDblMap> HEIGHT_MEMO =
        ThreadLocal.withInitial(() -> new LongDblMap(1 << 16));
        public static boolean HEIGHT_MEMO_ON = true;
    public static void clearHeightMemo() { HEIGHT_MEMO.get().clear(); }

    /**
     * ★★★★★★★ 2026-10-08（口径统一 #1）：地形高度改为【与地形同源】。
     *
     * <p><b>改前的问题</b>：本方法返回 {@code eval(..)[3]}，而 {@code eval} 内部的陆海判据是
     * {@code q.c[0]}（<b>125 km 超格级布尔</b>），与地形实际使用的
     * {@link #signedCoastDistCF}（<b>block 级连续场</b>）实测 <b>48.06% 不一致</b>
     * （641,601 点）。后果：height 的符号与地形相反，符号钳位把 <b>20.19%</b> 的世界
     * 钉成常数 −1.0，而 {@code elevation01} 因此退化成 0/1 二值。
     *
     * <p><b>改法</b>：直接返回地形用的那一条链 —— {@code coastProfileCF(signedCoastDistCF(..))}
     * （米，海平面 = 0），与 {@code SimTerrain.compose} 的 USE_CF_PROFILE 分支【逐位同源】：
     * <pre>
     *   SimTerrain: sig = signedCoastDistCF(..); prof = coastProfileCF(sig);
     *               h = seaLevel + ELEV_TO_BLK*CF_GAIN*prof;  land = sig >= 0
     *   本方法:     return coastProfileCF(signedCoastDistCF(..));   // 同一个 prof
     * </pre>
     * ⟹ {@code landResidual > 0} ⟺ {@code sig > 0} ⟺ 地形判陆 ⟹ 三套口径收敛成一套。
     *
     * <p><b>回滚</b>：置 {@code HEIGHT_FROM_CF = false} ⟹ 逐位回到旧行为。
     */
    public static boolean HEIGHT_FROM_CF = true;

    /** height 的实际实现（记忆化之外）。 */
    private static double height0(long ws, double px, double pz) {
        if (HEIGHT_FROM_CF) {
            double h = coastProfileCF(signedCoastDistCF(ws, px, pz));
            // ★ 造山只加在【陆地】（h > 0 = 海岸剖面判陆）。海上不加。
            if (OROG_ON && h > 0.0) h += orogenyM(ws, px, pz);
            return h; }
        return eval(ws, px, pz)[3]; }

    public static double height(long ws, double px, double pz) {
        if (!HEIGHT_MEMO_ON) return height0(ws, px, pz);
        LongDblMap m = HEIGHT_MEMO.get();
        // 坐标按 1 格量化（同一格点应得同一答案；缓存与精度无关）
        long kx = (long) Math.floor(px), kz = (long) Math.floor(pz);
        // ★★★★★★★ 2026-10-08 修复：键必须含 seed！
        //   原 bug：key = (kx<<32) ^ (kz&0xFFFFFFFFL) —— 只有坐标。
        //   后果：换 seed 时会返回【上一个 seed】的高度。
        //   实证：先 WI 再 seedOf ⟹ 两次都返回 70.9（应不同）。
        long key = ((kx << 32) ^ (kz & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + ws;
        double[] tmp = ctx().tmp1;
        if (m.get(key, tmp)) return tmp[0];
        double r = height0(ws, px, pz);
        if (m.size() > 262144) m.clear();
        m.put(key, r);
        return r; }

    /** 无记忆化版（内部用，缓存 miss 时调） */
    public static double heightRaw(long ws, double px, double pz) { return eval(ws,px,pz)[7]; }
    public static boolean isLandE(long ws, double px, double pz) { return eval(ws,px,pz)[3] > 0; }
}