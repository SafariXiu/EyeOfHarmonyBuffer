package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * 噪声大陆高度场 —— ★★★ **委托壳**（2026-10-08 重写）
 *
 * 【本次重写（接入 TalosLandField）】
 *   · 实现已【全部搬到】{@link TalosLandField}（原 M1gen，已在探针环境充分验证）
 *   · 本类保留**全部原有 public 签名** ⟹ **所有调用方零改动**
 *   · 每个方法都是一行委托
 *
 * 【为什么换实现】旧实现是纯 fbm 噪声 + 自适应阈值，自述问题：
 *   「实测 3~7 块大陆，部分种子出现一块 80%+ 的泛大陆」
 *   「沿东西方向的连续海段中位数只有 690 km」
 *   新实现（TalosLandField）用【超格 + 实心块 + 三级域扭曲】
 *   ⟹ 形态确定性更好、海岸线自然、且有完整的高度剖面（-10,497 ~ +8,161 m）
 *
 * 【量纲与符号约定】（★ 2026-10-08 口径统一 #1 后逐条重核）
 *   · {@link #height}           —— 米，海平面 = 0；★ 已与地形同源 = coastProfileCF(signedCoastDistCF)
 *   · {@link #coastDistBlocks}  —— block，**陆上 < 0**；★ 已是**真几何距离**（岸线精确过零）
 *   · {@link #landResidual}     —— 陆上 > 0（= 海拔米数），海上 <= 0；★ 符号与地形判陆**逐点一致**
 *   · {@link #residualScale}    —— ★ = CF_LAND_H（陆地剖面饱和值），使 elevation01 落在 [0,1]
 *   · {@link #seaResidualScale} —— ★ = 海洋剖面饱和值（架+坡+深海）
 *   · {@link #medNoise}/{@link #bandNoise} —— [0,1]
 *
 * ⚠ 注意 {@link #landScore} 与 {@code TalosLandField.landScore} **不是同一个量**：
 *   前者 = 2*kappa−1（由**高度**派生的大陆度），只供 {@code Atmosphere.kappaAt}；
 *   后者是**判陆**用的 block 级连续场（{@code TalosLandField.isLand} 的输入）。
 *   ★ 判陆的**唯一权威**是 {@code TalosLandField.isLand} / {@link #isLand}。
 */
public final class NoiseContinentGrid {

    // ================= 旧字段（保留，供外部读；新实现不用） =================
    /** @deprecated 新实现不用（保留以免破坏读取方） */
    @Deprecated public static double CONTINENT_SCALE = 2.5;
    /** @deprecated 新实现不用 */
    @Deprecated public static double X_STRETCH = 5.0;
    /** 目标陆地占比（新实现对应 TalosLandField.N_TARGET 的映射结果） */
    public static double TARGET_LAND = 0.33;

    // ================= 1. 是否陆地 =================
    /** ★ 公开的 seed 转换（供 OrographyField 等直接调 isLand）。 */
    public static long wsOf(int worldSeedInt) { return ws(worldSeedInt); }

    public static boolean isLand(int x, int z, int worldSeedInt) {
        return TalosLandField.isLand(ws(worldSeedInt), x, z); }

    // ================= 2. 高度场（米） =================
    public static double height(int x, int z, int worldSeedInt) {
        return TalosLandField.height(ws(worldSeedInt), x, z); }

    // ================= 3. 有符号海岸距离（block，陆上 <0） =================
    // ★★★★★★★ 2026-10-08 性能修复：**加记忆化**
    //
    // 【为什么】`V2BiomeSelect.accumulateWeights` 对**每一格**都调本方法，
    //   而 `BiomeField.solve` 是 400×200 = 81,000 格/次 ⟹ 81,000 次全量扫描。
    //   实测日志：BiomeField 单次 solve = **144 秒**（占全部地形生成时间的 77%）。
    //   而 `LandformField.sample` 与 `warmestMonthTempK` 也依赖它。
    //
    // 【缓存】ThreadLocal + 坐标键（含 seed）。403×203 的工作集 ⟹ 上限 65536 足够。
    private static final ThreadLocal<java.util.HashMap<Long, Double>> CD_MEMO =
        ThreadLocal.withInitial(java.util.HashMap::new);
    public static boolean CD_MEMO_ON = true;
    public static void clearCdMemo() { CD_MEMO.get().clear(); }

    /**
     * ★★★★★★★ 2026-10-08（口径统一 #1）：改用【真正的几何到岸距离】。
     *
     * <p><b>改前</b>：{@code -signedCoastDist(..)} —— 那是「到最近【异类细胞中心】的物理距离」，
     * 细胞尺度 25 km ⟹ 它在**岸线两侧都约等于 22,900**，只有**符号**有意义
     * （待裁决/065 已证：「岸线是布尔场的边界，不是连续场的等值线」）。
     * 拿它当距离用的消费者（山带 COAST_MIN 闸门 / shoreFade / V2BiomeSelect / ThermalForcing）
     * 全部落在无意义的量程上。
     *
     * <p><b>改后</b>：{@code -signedCoastDistCF(..)} —— 由连续 field 的解析梯度给出的
     * <b>真几何距离</b>（block），岸线处精确过零、处处连续、内陆单调。
     * 与 {@code SimTerrain.compose} 判陆用的 {@code sig >= 0} <b>同源同号</b>。
     *
     * <p><b>性能</b>：旧实现内部是 625 细胞的扫描（实测约 400 us/点）；
     * 新实现是 5 次 landScore（cnoise）⟹ 快约两个数量级（且仍有本方法的记忆化）。
     *
     * <p><b>回滚</b>：{@code COASTDIST_FROM_CF = false} ⟹ 逐位回到旧行为。
     */
    public static boolean COASTDIST_FROM_CF = true;

    static double coastDistBlocks0(int x, int z, int worldSeedInt) {
        if (COASTDIST_FROM_CF) return -TalosLandField.signedCoastDistCF(ws(worldSeedInt), x, z);
        return -TalosLandField.signedCoastDist(ws(worldSeedInt), x, z); }

    public static double coastDistBlocks(int x, int z, int worldSeedInt) {
        if (!CD_MEMO_ON) return coastDistBlocks0(x, z, worldSeedInt);
        java.util.HashMap<Long, Double> m = CD_MEMO.get();
        long key = (((long) x << 32) ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + worldSeedInt;
        Double v = m.get(key);
        if (v != null) return v;
        double r = coastDistBlocks0(x, z, worldSeedInt);
        if (m.size() > 262144) m.clear();
        m.put(key, r);
        return r; }

    // ================= 4. 残差（陆上 >0 = 海拔米数） =================
    public static double landResidual(int x, int z, int worldSeedInt) {
        return TalosLandField.height(ws(worldSeedInt), x, z); }

    public static boolean isLandResidual(double residual) { return residual > 0.0; }

    // ================= 5. 残差标尺（= 1.0，因为已是米） =================
    /**
     * ★★★★★★★ 2026-10-08（口径统一 #1）：回归文档写明的契约 —— **「每种子 q93 标尺」**。
     *
     * <p><b>改前</b>：返回常量 {@code 1.0}（注释写「因为 landResidual 已是米」）。
     * 但 {@code OrographyField.elevation01} 的用法是
     * {@code smoothstep(0.10, 1.25, residual / residualScale)} —— 那是给
     * <b>[0,1] 归一量</b>设计的边界。残差是**米**（可达 +7,249）⟹ 只要 residual &gt; 1.25
     * 就饱和。实测：陆地 elevation01 <b>61.31% 恰为 0、38.61% 恰为 1</b>，中间只有 0.08%
     * ⟹ elevation01 退化成 0/1 二值，kind 分档随之失真（LOWLAND 52% / PLATEAU 34%）。
     *
     * <p><b>改后</b>：陆地剖面的解析饱和值 {@code CF_LAND_H}。因为
     * {@code residual = coastProfileCF(sig) = CF_LAND_H * sqrt(sig/CF_LAND_U)}
     * ⟹ {@code residual / CF_LAND_H = sqrt(sig/CF_LAND_U)} 天然落在 <b>[0,1]</b>，
     * 无需任何采样标定即可让 {@code elevation01} 恢复连续。
     * <p>⚠ 它必须与 {@code TalosLandField.CF_LAND_H} 保持一致（同一常量）。
     */
    public static double residualScale(int worldSeedInt) { return TalosLandField.CF_LAND_H; }

    /** 海上 |残差| 的标尺：整条海洋剖面（大陆架+坡+深海）的饱和值。 */
    public static double seaResidualScale(int worldSeedInt) {
        return TalosLandField.CF_SHELF_H + TalosLandField.CF_SLOPE_H + TalosLandField.CF_DEEP_H; }

    // ================= 6. 噪声（[0,1]） =================
    public static double medNoise(int x, int z, int worldSeedInt) {
        double a = TalosLandField.velField(ws(worldSeedInt), x, z, 0);
        return 0.5 + 0.5 * a; }

    /**
     * ★ 频带噪声 [0,1]（供风带摆动等）
     *   ★ 签名与旧实现一致（含 `oct` 八度数）
     *   `x/z` 乘 `baseFreq` 后作为噪声坐标；`oct` 层叠加
     */
    public static double bandNoise(int x, int z, int worldSeedInt, long salt, double baseFreq, int oct) {
        long ws = ws(worldSeedInt) ^ salt;
        double v = 0.0, amp = 1.0, wsum = 0.0, f = baseFreq;
        int n = Math.max(1, Math.min(6, oct));
        for (int k = 0; k < n; k++) {
            v += amp * TalosLandField.velField(ws + k, x * f, z * f, 1);
            wsum += amp; amp *= 0.5; f *= 2.0; }
        return 0.5 + 0.5 * (v / wsum); }

    // ================= 7. 大陆度（供 Atmosphere.kappaAt） =================
    /**
     * ★★★ 大陆度 kappa ∈ [0,1]：0 = 纯海洋，1 = 纯内陆（**连续**）
     *   `Atmosphere.kappaAt = 0.5×(landScore+1)`
     *   用途：{@code gammaOf}/{@code cdOf} 的连续插值、{@code landSeaAnnualAnomaly}
     *   ★ 由【高度】派生：海平面 ⟹ 0.5，深海 ⟹ 0，内陆高地 ⟹ 1
     */
    // ★★★★★★★ 2026-10-08（口径统一 #1）：8000 -> 2000。
    //   高度已改为 CF 剖面（陆 +1973 / 海 −4000 米），KAPPA_H 必须跟着改，
    //   否则 kappa 只用到 [0.25, 0.62] 的窄带（实测改前 mean=0.4606）。
    //   KAPPA_H = 2000 ≈ CF_LAND_H ⟹ 海平面 0.5、内陆饱和 1.0、深海饱和 0.0。
    public static double KAPPA_H = 2000.0;

    public static double kappa(int x, int z, int worldSeedInt) {
        double h = TalosLandField.height(ws(worldSeedInt), x, z) / KAPPA_H;
        if (h >  1.0) h =  1.0;
        if (h < -1.0) h = -1.0;
        return 0.5 + 0.5 * h; }

    public static double landScore(int x, int z, int worldSeedInt) {
        return 2.0 * kappa(x, z, worldSeedInt) - 1.0; }

    // ================= 8. 山地门（[0,1]） =================
    public static double mountainGate(int x, int z, int worldSeedInt) {
        double dev = Math.abs(2.0 * medNoise(x, z, worldSeedInt) - 1.0);
        double relief = 1.0 - Math.min(1.0, dev);
        double t = (relief - 0.30) / (0.65 - 0.30);
        if (t < 0) t = 0; if (t > 1) t = 1;
        return t * t * (3 - 2 * t); }

    // ================= 8b. 配置戳（缓存失效用） =================
    /**
     * ★★★ 配置戳：把当前海陆层的全部【行为相关】参数编码成一个 long。
     *   任何参数变化 ⟹ 戳变化 ⟹ 下游瓦片缓存失效。
     */
    public static long configStamp() {
        long h = 0x9E3779B97F4A7C15L;
        h = h * 31 + TalosLandField.DS();
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SUPER_ABS);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.N_TARGET);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LAND_THRESHOLD);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CELL_WARP);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.H_GAIN_LAND);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.H_GAIN_SEA);
        h = h * 31 + Double.doubleToLongBits(KAPPA_H);
        return h; }

    // ================= 9. 统计缓存清理（兼容旧签名） =================
    public static void clearStats() { /* 新实现无按种子标定缓存 */ }

    // ================= 内部：int seed -> long worldSeed =================
    private static long ws(int worldSeedInt) {
        return ((long) worldSeedInt) & 0xFFFFFFFFL; }

    private NoiseContinentGrid() {}
}
