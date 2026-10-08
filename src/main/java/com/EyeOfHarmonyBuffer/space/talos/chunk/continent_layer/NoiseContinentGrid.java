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
 * 【量纲与符号约定】（与旧实现一致，已逐条核对）
 *   · {@link #height}           —— 米，海平面 = 0
 *   · {@link #coastDistBlocks}  —— block，**陆上 < 0**
 *   · {@link #landResidual}     —— 陆上 > 0（= 海拔米数），海上 <= 0
 *   · {@link #residualScale}    —— = 1.0（因为 landResidual 已是米）
 *   · {@link #medNoise}/{@link #bandNoise} —— [0,1]
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

    public static double coastDistBlocks(int x, int z, int worldSeedInt) {
        if (!CD_MEMO_ON) return -TalosLandField.signedCoastDist(ws(worldSeedInt), x, z);
        java.util.HashMap<Long, Double> m = CD_MEMO.get();
        long key = (((long) x << 32) ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + worldSeedInt;
        Double v = m.get(key);
        if (v != null) return v;
        double r = -TalosLandField.signedCoastDist(ws(worldSeedInt), x, z);
        if (m.size() > 262144) m.clear();
        m.put(key, r);
        return r; }

    // ================= 4. 残差（陆上 >0 = 海拔米数） =================
    public static double landResidual(int x, int z, int worldSeedInt) {
        return TalosLandField.height(ws(worldSeedInt), x, z); }

    public static boolean isLandResidual(double residual) { return residual > 0.0; }

    // ================= 5. 残差标尺（= 1.0，因为已是米） =================
    public static double residualScale(int worldSeedInt) { return 1.0; }
    public static double seaResidualScale(int worldSeedInt) { return 1.0; }

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
    public static double KAPPA_H = 8000.0;

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
