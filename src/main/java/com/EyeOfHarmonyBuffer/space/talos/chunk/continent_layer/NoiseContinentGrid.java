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
    /**
     * ★★★★★★★ 2026-10-08 性能修复 #3（同 §165/§166 的家族）：**不再走 LEM**。
     *
     * <h3>改前（最严重的一处）</h3>
     * <pre>
     *   PlateField.elevationWithCell  ->  本方法  ->  TalosLandField.height
     *                                 ->  height0  ->  ★ TalosLandErosion.height（LEM 瓦片求解）
     *   ⟹ 而本方法的调用面是【整个气候/降水层】：
     *      Atmosphere.kappaElev（elev 那半）、PrecipField.upwindElev / wTerrain / mmPerDay 的 elev、
     *      SimClimate 的高程与梯度（5 处）、WaterField.solveTile 的高程场。
     *   ⟹ 实测后果：验收探针 P296 的 B 段（Zonal.profile 沿 40,000,000 格采样）
     *      跨越约 78 个 LEM 瓦片（512 km each, 冷瓦片 5~9 秒）
     *      ⟹ 单行 500 次 mmPerDay 超过 6 分钟仍未完成
     *      （而 Zonal.java 的 javadoc 记的历史值是 B 段共 1160 秒）。
     * </pre>
     *
     * <h3>修法（有物理依据）</h3>
     * <p>改用 {@link TalosLandErosion#baseHeight} —— 「海岸剖面 + 造山 + 中尺度扰动」，
     * 即 {@code height0} <b>不含 LEM 的那一部分</b>，且源码逐字声明与 {@code height0} 逐位同口径。
     *
     * <p>★ 为什么这在物理上【更对】：
     * <ul>
     *   <li><b>降水</b>：雨影由 <b>大尺度</b>地形决定（{@code OROGEN_W = 90 km}），
     *       LEM 的河谷是<b>小尺度</b>细节 ⟹ 不需要。</li>
     *   <li><b>气候</b>：{@code SimClimate} 的高程用于温度/气压/降水的大尺度场 ⟹ 不需要。</li>
     * </ul>
     *
     * <p>★★ <b>而 LEM 的【唯一正当消费者】是地形生成</b>（{@code SimTerrain:674}），
     * 它【直接】调 {@code TalosLandField.height}，<b>不经过本方法</b> ⟹ 不受本开关影响 ✓
     *
     * <p>★ 回滚：{@code ELEV_WITHOUT_LEM = false} ⟹ 逐位回到旧行为。
     */
    public static boolean ELEV_WITHOUT_LEM = true;

    public static double height(int x, int z, int worldSeedInt) {
        final long w = ws(worldSeedInt);
        return ELEV_WITHOUT_LEM
             ? TalosLandErosion.baseHeight(w, x, z)
             : TalosLandField.height(w, x, z); }

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
    /**
     * ★★★★★★★ 2026-10-08 性能修复 #2（同 §165 的家族）：**不再走 LEM**。
     *
     * <h3>改前</h3>
     * <pre>
     *   本方法 -> TalosLandField.height -> height0
     *          -> if (TalosLandErosion.EROSION_ON && h>0) h = TalosLandErosion.height(..)
     *   ⟹ 每次调用都可能触发一次 LEM 瓦片求解（512 km 窗口，冷瓦片 5~9 秒）。
     * </pre>
     *
     * <h3>消费者与它们真正需要的东西</h3>
     * <table border=1>
     * <tr><th>消费者</th><th>用途</th><th>需要 LEM 的河谷细节吗</th></tr>
     * <tr><td>{@code OrographyField.elevation01}（:190/:300）</td>
     *     <td>地形 kind 分档（LOWLAND/PLATEAU/…）</td>
     *     <td><b>否</b> —— 那是气候/群系层的大尺度分类</td></tr>
     * <tr><td>{@code ClimateCoords:114}</td><td>同上（转发）</td><td><b>否</b></td></tr>
     * <tr><td>{@code V2TerrainGen:607}</td>
     *     <td>源码逐字注释「{@code r<0 = 海上}」⟹ <b>只用符号</b></td>
     *     <td><b>否</b> —— LEM 只重塑陆地细节，不改海陆分布</td></tr>
     * </table>
     *
     * <p>★★ ⟹ 全部消费者都走 {@code TalosLandErosion.baseHeight}（不含 LEM，
     * 且源码逐字声明与 {@code height0} 逐位同口径）即可。
     *
     * <p>★ 回滚：{@code LAND_RESIDUAL_WITHOUT_LEM = false} ⟹ 逐位回到旧行为。
     */
    public static boolean LAND_RESIDUAL_WITHOUT_LEM = true;

    public static double landResidual(int x, int z, int worldSeedInt) {
        final long w = ws(worldSeedInt);
        return LAND_RESIDUAL_WITHOUT_LEM
             ? TalosLandErosion.baseHeight(w, x, z)
             : TalosLandField.height(w, x, z); }

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
    // ★★★★★ 2026-10-12（待裁决/338）：2000 -> 200，【与 CF_LAND_H 同步】。
    //   为什么必须同步：kappa = clamp(h0 / KAPPA_H)，而 landScore = 2*kappa-1；
    //     设计意图是「内陆饱和(h0=CF_LAND_H) ⟹ landScore = 1.0」⟹ 要求 KAPPA_H = CF_LAND_H。
    //   ⚠ 实测这条关系【已经断了】：CF_LAND_H 从 1973 改到 1100 时 KAPPA_H 没跟着改
    //     ⟹ 比值只有 0.55 ⟹ 内陆 kappa 只到 0.775（而非 1.0）⟹ 大陆度被系统性低估。
    //   本次 CF_LAND_H=1100->200，故 KAPPA_H 同步 2000->200 ⟹ 比值恢复 1.0 ✓
    //   ★ 注意：kappa 的【符号】不受 KAPPA_H 影响（h0>=0 ⟺ h>=0）⟹ 不改陆海分布 ✓
    //   ⚠ 回滚：改回 2000.0（但那时 CF_LAND_H 也要改回 1100.0）。
    public static double KAPPA_H = 200.0;

    /**
     * ★★★★★★★ 2026-10-08 性能修复：**不再走 LEM**。
     *
     * <h3>改前的灾难（实测）</h3>
     * <pre>
     *   调用链：landScore -> kappa -> TalosLandField.height
     *           -> height0 -> if (TalosLandErosion.EROSION_ON && h>0) h = TalosLandErosion.height(..)
     *   ⟹ 每一次 kappa 查询都可能触发一次【LEM 瓦片求解】（512 km 窗口，冷瓦片 5~9 秒）。
     *   ⟹ 验收探针 P296（每纬线 500 个 x 采样 x 40,000 km = 跨约 78 个 LEM 瓦片）
     *      实测 ~27 分钟才输出一行（原本应在秒级）。
     *   ⟹ 而 kappaAt 的 javadoc 记的 56.6 us（P469 实测）是【LEM 接入之前】的数字。
     * </pre>
     *
     * <h3>修法（有物理依据，不只是提速）</h3>
     * <p>改用 {@link TalosLandErosion#baseHeight} —— 它是
     * <b>「海岸剖面 + 造山 + 中尺度扰动」</b>，即 {@code TalosLandField.height0}
     * <b>不含 LEM 的那一部分</b>，且源码逐字声明「必须与 TalosLandField.height0 逐位同口径，
     * 否则 LEM 开/关之间会出现台阶」。
     *
     * <p>★ 为什么这在物理上【更对】而不是妥协：{@code kappa}（大陆度）的消费者是
     * {@code Atmosphere.gammaOf}/{@code cdOf} 的连续插值、海陆年均对比 —— 那是
     * <b>气候层的大尺度量</b>。而 LEM 只重塑<b>陆地上的河谷细节</b>，
     * <b>不改变海陆分布</b> ⟹ 用 baseHeight 得到的 kappa 与用 height 得到的
     * 在气候层语义上【同义】，却省掉了整条 LEM 链。
     *
     * <p>★ 回滚：{@code KAPPA_WITHOUT_LEM = false} ⟹ 逐位回到旧行为。
     */
    public static boolean KAPPA_WITHOUT_LEM = true;

    public static double kappa(int x, int z, int worldSeedInt) {
        final long w = ws(worldSeedInt);
        final double h0 = KAPPA_WITHOUT_LEM
                        ? TalosLandErosion.baseHeight(w, x, z)
                        : TalosLandField.height(w, x, z);
        double h = h0 / KAPPA_H;
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
        // ★★★★★★★ 2026-10-11 补全（用户裁决 A：「补全 —— 逐个判定那 78 个」）
        //
        // 【背景】本方法的 javadoc 承诺「把当前海陆层的【全部行为相关】参数编码成一个 long」，
        //   而实现只折入了 8 个，而 `TalosLandField` 有 88 个非 final 常量 ⟹ 80 个缺口 ✗
        //   ⟹ 本条按 D58 的准入判据「改了结果 ⇒ 必须进指纹」逐个判定后补全。
        //
        // 【判定：不加的（已核实其 javadoc 自证不改结果）】
        //   · `SCAN`        —— 「SCAN 8 -> 3（**逐位等价**，但快 5~7 倍）」
        //   · `OPP_R`       —— 「实测：OPP_R 从 1 到 12，coastDist **偏差全是 0.0**」
        //   · `OPP_R_TIGHT` —— 两级扫描的小半径（`OPP_R` 的加速位，同上逐位等价）
        //   · `DIFF_OPP_R`  —— 「实测：R 从 4 降到 2，断崖指标**不变**（仍 0/16445）」
        //   · `NI_MEMO_ON` / `HEIGHT_MEMO_ON` —— 「记忆化开关（诊断时可关）」，只影响速度
        //   · `BLOCK_DT_*`  —— block 级 DT 的**分块粒度**；DT 值由 `DT_R` 决定（见下）
        //
        // 【判定：加的】—— 全部是【参与求解/剖面/造山/距离变换计算】的参数。
        //   分六组（按物理含义），每组一行注释：
        //
        //   ① 单元与聚集（决定细胞/Voronoi 的几何）
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SC);
        h = h * 31 + TalosLandField.SUPER;
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CLUSTER_FRAC_MIN);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CLUSTER_FRAC_MAX);
        h = h * 31 + TalosLandField.CLUSTER_MIN;
        h = h * 31 + TalosLandField.CLUSTER_MAX;
        h = h * 31 + TalosLandField.CLUSTER;
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SUP_JITTER);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SUP_BSPREAD);
        //   ② 场形（偏斜/细节/预算/波数）
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SKEW_AMP);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SKEW_HF);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.FINE_AMP);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.N_SIGMA);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CONT_N_HF2);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LOCAL_R);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CONT_BUDGET);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CELL_WL);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.H_GAIN);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CONT_W);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SMOOTH_P);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.VEL_WL_R);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SHEAR_SCALE);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CONV_SCALE);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CAP_MULT);
        //   ③ 造山（OROG 族）
        h = h * 31 + (TalosLandField.OROG_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_DH);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_C0);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_C1);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_BND_W);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_PLATE_BAND);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_EDGE_DH);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_EDGE_BAND);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_MAX_M);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_GAMMA);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.OROG_BAND_MIN);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.REL_OROG);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.REL_OCEA);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.REL_RIDGE);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.REL_RIFT);
        //   ④ 大尺度场（LS 族）+ 基底切换
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LS_WL);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LS_WL2);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LS_TARGET);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LS_AMP);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LS_AMP2);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LS_BAND);
        h = h * 31 + (TalosLandField.USE_CONT_FIELD ? 1 : 0);
        h = h * 31 + (TalosLandField.USE_NEW_BASE ? 1 : 0);
        h = h * 31 + (TalosLandField.HEIGHT_FROM_CF ? 1 : 0);
        //   ⑤ 海岸剖面（CF 族）—— ★ CF_SAT / CF_LAND_U / CF_LAND_H 见下（§195 补漏）
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_DH);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_SHELF_H);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_SHELF_U);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_SLOPE_H);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_SLOPE_U);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_DEEP_OFF);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_DEEP_H);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_DEEP_W);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.COAST_FADE_W);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LAND_TARGET);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.LAND_DEEP);
        //   ⑥ 剖面族（NB/T/D 三组）+ 距离变换 + 门控
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NB_LAND_U1);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NB_LAND_U2);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NB_SHELF);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NB_SLOPE);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NB_DEEP_OFF);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NB_DEEP_W);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SHELF_T);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SLOPE_T);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.DEEP_T);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SHELF_D);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SLOPE_D);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.DEEP_D);
        h = h * 31 + TalosLandField.DT_R;
        h = h * 31 + (TalosLandField.USE_DT_BASE ? 1 : 0);
        h = h * 31 + (TalosLandField.USE_SELFR ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.DT_GATE_W);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.SAT_D);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.NEAR_GATE);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.FAR_LIMIT);
        //
        //   ⑦ ★ §195 的 D58 补漏（CF_SAT 改动未折入指纹）
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_SAT);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_LAND_U);
        h = h * 31 + Double.doubleToLongBits(TalosLandField.CF_LAND_H);
        return h; }

    // ================= 9. 统计缓存清理（兼容旧签名） =================
    public static void clearStats() { /* 新实现无按种子标定缓存 */ }

    // ================= 内部：int seed -> long worldSeed =================
    private static long ws(int worldSeedInt) {
        return ((long) worldSeedInt) & 0xFFFFFFFFL; }

    private NoiseContinentGrid() {}
}
