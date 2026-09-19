package com.EyeOfHarmonyBuffer.sim.runtime;

import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;

/**
 * **新模拟器 → 世界的运行时桥**（纵向切片第 1 步：地形）。
 *
 * <h3>为什么是这个形状</h3>
 * 旧实现里 {@code V2TerrainGen.composeColumn} 被称为「**块级合成高度的唯一入口**」
 * （生产、/talosmap、/talos_here 三处都走它）。所以接线只需要在**那一个点**做一次分派，
 * 旧路径在 {@link #ENABLED} 为 false 时**一行都不变**。
 *
 * <h3>世界契约的换代（必须记账）</h3>
 * <pre>
 *              旧（ClimateLatitudes）        新（WorldContract）
 *   Z 周期      LAT_CYCLE = 1,000,000          Z_CYCLE = 20,000,000
 *   极到赤道    MAX_D     =   500,000          MAX_D   = 10,000,000
 * </pre>
 * ⇒ 接线意味着**纬度方向放大 20 倍**：旧世界 500 km 从赤道走到极点，新世界是 10,000 km。
 * 这是"重写成地球尺度"的直接后果，不是接线引入的。
 *
 * <h3>垂直映射（smooth、单调、饱和，零阈值）</h3>
 * <pre>
 *   陆地：h = softCapTo( seaLevel + LAND_GAIN * elev_m , SOFT_CAP_H, SOFT_CAP_K )
 *   海洋：depth = softCapTo( OCEAN_GAIN * (-elev_m) , seaLevel-1, 6 ) + 深海起伏*smoothstep
 * </pre>
 * 两者都处处 C∞、单调、以渐近值为界，因此**不会出现硬切**。
 *
 * <p>⚠ <b>2026-09-13 修正（审计 D9/D14）</b>：本段原来写的是 **tanh 版本**的公式
 * （{@code dh = (SOFT_CAP_H-seaLevel)*tanh(elev/LAND_SCALE)}、{@code OCEAN_MAX_DEPTH}、
 * {@code LAND_SCALE} / {@code OCEAN_SCALE}）—— 那三个常量**在本文件里根本不存在**，
 * 是接线前的旧设计。现行实现是**线性增益 + 软封顶**（见下方字段注释：第一版用 tanh
 * 把深海平原压成了一块板，P294 实测后改掉）。**类头的形状描述与字段注释当时是互相矛盾的两版。**
 *
 * <h3>本步**已经**做的事（原「诚实清单」已过期，2026-09-13 更正）</h3>
 * <ol>
 *   <li><b>群系已经换成新气候</b>（§97）：{@code ClimateCoords.sample} 在 {@code SimClimate.ENABLED}
 *       时分派到 {@link SimClimate}，旧的 250 m LUT + 瓦片缓存机制已搬到新气候上；</li>
 *   <li><b>雪线已经接上新温度</b>（§98）：{@code SNOW_FROM_TEMP=true}，判据是
 *       <b>最暖月</b>地表温度 &lt; {@link #SNOW_T}（永久雪线的定义），不再是按高度；</li>
 *   <li><b>方块层的海陆已与群系/高度同源</b>（审计 D16-a）：{@code ChunkProviderTalos2} 改用
 *       {@code PlateField.isLandWithCell}；海床也已改用本类的海洋分支高度（审计 D16-b）。</li>
 * </ol>
 * <p><b>⚠ 2026-09-18 更正</b>：本段原写「仍未做的：洋流（A2/A3）与降水里的 SST 距平
 * **还没有生产来源** —— {@code ocean/} 整个包零生产调用者（见设计冻结 §102.8）」。
 * **那句话现在不成立了**，已核实的生产调用者有：
 * <ul>
 *   <li>{@code WorldChunkManagerTalos2} 构造器 → {@code OceanWiring.onWorld(worldSeedInt)}（世界级接线）</li>
 *   <li>{@code SimClimate.configStamp()} → {@code OceanField.configStamp()}</li>
 *   <li>{@code GlobalClimate.sample}（/talosmap 出图）→ {@code OceanField.bandMeansAt} / {@code anomalyAt}</li>
 * </ul>
 * ⇒ 洋流**有**生产来源了；§102.8 是当时的事实，已被后续的接线步骤取代。
 *
 * <p>纯函数：给定 (seed, x, z) 结果逐位可复现；无静态可变字段（{@link #ENABLED} 等
 * 是**开关注入点**，与旧实现的 {@code ClimateCoords.ENABLE_ORO} 同类）。
 */
public final class SimTerrain {

    private SimTerrain() {}

    /** 总开关。false ⇒ {@code composeColumn} 一行都不走这里，与接线前逐位相同。 */
    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【6 份】，语义各不相同、默认值也不一致
     * （4 个 false：HadleyCell/SoilMoisture/StationaryWave/Vegetation；2 个 true：OceanField/SimTerrain）。
     * **本份的含义是：SimTerrain（地形/运行时）。** 引用时务必写全类名（如 `SimTerrain.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     */
    public static boolean ENABLED = true;

    /**
     * **陆地垂直增益（blocks/m）**：8,069 m 的珠峰 -> 约 190 blocks。
     *
     * <p>⚠ 第一版用了 `tanh(elev/3000)`，实测**把深海平原压成了一块板**（P294）：
     * tanh 在 −4,000~−5,377 m 之间只变化 0.9 blocks，取整后全落进同一格。
     * 现在改成**线性 + 软封顶**（softplus）：工作区间内保分辨率，只在接近世界高度上限时饱和。
     */
    public static double LAND_GAIN = 0.0235;
    /** **海洋垂直增益（blocks/m）**：−5,377 m 的深海平原 -> 约 56 blocks。 */
    public static double OCEAN_GAIN = 0.0105;
    /** 深海平原的块级起伏（blocks）—— 与旧实现 SEABED_RELIEF 同量级。 */
    public static double SEABED_RELIEF = 3.0;
    /** 块级细节振幅（blocks）与波长（m）—— 让山有纹理，不是光滑高地。 */
    public static double DETAIL_AMP = 4.0;
    public static double DETAIL_W = 220.0;
    /** 沙滩带：高出海平面这么多方块以内算滩。 */
    public static double BEACH_BLOCKS = 3.0;
    /** 雪线是否改用**地表温度**判定（§98）。false = 退回旧实现的纯几何规则（本类不设 snow）。 */
    public static boolean SNOW_FROM_TEMP = true;
    /**
     * 降雪的温度阈值（K）—— 判的是**最暖月**，不是年均。
     *
     * <p>⚠ 这是**物理判据**，不是随手取的数：**永久雪线的定义是「最暖月均温 &lt; 0 度」**。
     * 用年均会把它压到约 60 度（那里年均已低于冰点）—— 实测确实如此，所以改成最暖月。
     */
    public static double SNOW_T = 273.15;
    /**
     * **海水冰点**（K）：盐度 35 的标准值是 **−1.8 °C = 271.35 K**。
     *
     * <p>审计 D72：海冰原来由 `PolarZone` 的**几何纬度带**决定（而且那套纬度的周期是 1M，
     * 世界契约是 20M）⇒ 冰被铺在**赤道 25.5 °C 的海面**上。现在改用「**最冷月海温 < 冰点**」，
     * 与真实物理一致：冰只出现在真的会结冰的海里，暖流海域自动不结冰。
     */
    public static double SEA_ICE_T = 271.35;

    /**
     * 该点的**最暖月**地表温度（K）。
     *
     * <pre>
     *   T_warm = T_annual + A(phi, kappa)      （季节项 A*cos(theta - psi) 的极大值）
     * </pre>
     * `T_annual` 取自 {@link SimClimate#surfaceTempK}（含本列自己的海拔直减）；
     * `A` 用 {@link ZonalTables#aLand}/{@link ZonalTables#aSea} 的表，**不需要任何额外气候求值**。
     * `kappa` 从 {@link SimClimate#coords} 拿（同一个缓存、命中级代价）。
     */
    /** 每线程一份的 scratch：**不能**用可变静态（跨线程共享），见 V2TerrainGen 里那条同族注记。 */
    private static final ThreadLocal<double[]> KAPPA3 = ThreadLocal.withInitial(() -> new double[3]);
    /** **public 是为了让探针与生产同源**（P462 要量 D46 的沿岸-内陆过渡）。 */
    public static double warmestMonthTempK(int x, int z, int worldSeedInt) {
        // ⚠ D46 修复（2026-09-13）：这里原来把 SimClimate.coords 的 out3[2] 当 kappa 用，
        // 但 out3[2] 是 **continent**（= clamp01(-coastD / 40 km)，一个 40 km 就饱和的**到岸距离**坡），
        // **不是大陆度 κ**。两者在**岸线上**分别是 0 与 **0.5**（κ 在岸线 = 0.5）。
        // 后果：季节性振幅 A(phi, kappa) 在每条海岸线上 40 km 内跨 12.5 K（50 度处）
        //   ⇒ 雪线在岸线上跳 ~1900 m（12.5 K / 6.5 K/km），而且**沿海/内陆的对比正好被这个台阶喂饱**，
        //     所以 P297 的「同纬度内陆 vs 沿海应当不同」判据一直是**假通过**。
        // **最终修法（用户裁决）**：不用 continent（40 km 就饱和），也不用 κ（COAST_BLEND = 800 km），
        // 而是给海陆季节振幅一个**专用的海洋影响穿透尺度** SimClimate.MARITIME_SCALE = 200 km。
        // 依据是我自己从 ERA5 0.25° 取的年较差-离岸距离断面（30~35N 拟合 L ≈ 200 km）——
        // 数据 URL、拟合式与局限都写在 SimClimate.MARITIME_SCALE 的 javadoc 里。
        return SimClimate.surfaceTempK(x, z, worldSeedInt) + seasonalAmpK(x, z, worldSeedInt);
    }

    /**
     * 该点的**季节振幅** `A(phi, kappa)`（K）。**最暖月 = 年平 + A，最冷月 = 年平 − A。**
     *
     * <p>抽出来是因为审计 D72 需要「最冷月」：海冰必须在**最冷月**低于冰点的海里形成，
     * 而不是按纬度画一条几何带。两者必须用**同一个** A（同一事实不许两处算）。
     */
    public static double seasonalAmpK(int x, int z, int worldSeedInt) {
        double kappa = SimClimate.maritimeInland(x, z, worldSeedInt);
        double latDeg = Math.toDegrees(WorldContract.latOf(z));
        double aSea = ZonalTables.aSea(latDeg);
        double amp = aSea + (ZonalTables.aLand(latDeg) - aSea) * kappa;
        return amp < 0.0 ? 0.0 : amp;
    }

    /**
     * 该点的**最冷月**地表温度（K）= 年平 − A(phi, kappa)。**海冰判据用它**（审计 D72）。
     *
     * <p>⚠ 海面用它与 {@link #SEA_ICE_T} 比较；陆地列不要用它（陆地的最冷月还要考虑雪/冰反照率，
     * 本模型没有那套反馈，所以只把它用于「这片海会不会结冰」这一个问题）。
     */
    public static double coldestMonthTempK(int x, int z, int worldSeedInt) {
        return SimClimate.surfaceTempK(x, z, worldSeedInt) - seasonalAmpK(x, z, worldSeedInt);
    }

    /**
     * 把新模拟器的地形写进旧实现的列容器。**只写字段，不分配、不返回新对象**
     * （{@code Column} 是每线程复用的容器，见 V2TerrainGen 的纪律）。
     *
     * <p>⚠⚠ <b>调用前必读（审计 D38，2026-09-13）</b>：陆地分支会调
     * {@link #warmestMonthTempK} → {@link SimClimate}，而 SimClimate 是<b>按瓦片（100x50 km）缓存</b>的
     * ⇒ <b>每进入一个新瓦片，第一列要等一次气候求解（2~8 s）</b>。
     * 世界里的调用模式是「一个区块 256 列、局部」⇒ 一个瓦片一次，可以接受；
     * 但**在大范围上调用本方法（探针的普查循环）代价是无界的**：
     * 实测 P294（334 万列）与 P295（1200 万列）会穿过数千个瓦片，
     * 从 ~4 s / ~10 s 变成<b>数小时</b>（两个地形验收探针曾经因此完全卡死）。
     *
     * <p><b>不读 {@code c.snow} 的调用方必须先把 {@link #SNOW_FROM_TEMP} 置 false</b>：
     * 它只写 {@code c.snow}，对 {@code c.land}/{@code c.h}/{@code c.hCapped} 等**完全无影响**，
     * 因此是<b>口径中性</b>的（P294/P295 就是这么修的）。
     */
    public static V2TerrainGen.Column compose(V2TerrainGen.Column c, int x, int z,
                                              int worldSeedInt, int seaLevel, int maxY) {
        long seed = seedOf(worldSeedInt);
        int cell = PlateField.PLATE_CELL;
        double elev = PlateField.elevationWithCell(x, z, seed, cell);

        // 四个字段一起写：Column 是复用容器，**每个字段都必须被覆盖**，否则会漏出上一列的残值。
        c.plain = 0.0;
        c.mtnComp = 0.0;
        c.auth = 0.0;
        c.uplift = 0.0;
        c.detailStrength = 0.0;
        c.snow = false;
        c.beach = false;

        if (elev < 0.0) {
            c.land = false;
            double maxDepth = seaLevel - 1.0;
            if (maxDepth < 1.0) maxDepth = 1.0;
            double dBase = softCapTo(OCEAN_GAIN * (-elev), maxDepth, 6.0);
            // 深海平原起伏：在岸边**连续地**消失（smoothstep），所以浅滩/沙滩不受影响
            double relief = SEABED_RELIEF * (vnoise(x / (DETAIL_W * 4.0), z / (DETAIL_W * 4.0), worldSeedInt ^ 0x7A11) * 2.0 - 1.0);
            double depth = dBase + relief * smoothstep01(dBase / 8.0);
            c.seaDepth = depth;
            c.base = 0.0;
            c.hNoDetail = seaLevel - depth;
            c.hDetail = c.hNoDetail;
            // 同一族的一致性（审计 D19）：SEABED_RELIEF 可以把 depth 推过 maxDepth 一点，
            // 于是 hCapped 可能 < 1 而 h 已被 clampY 夹到 1 —— 让两者对齐。
            c.hCapped = c.hNoDetail < 1.0 ? 1.0 : c.hNoDetail;
            c.h = clampY((int) Math.round(c.hCapped), maxY);
            return c;
        }

        c.land = true;
        c.seaDepth = 0.0;
        double hNo = softCapTo(seaLevel + LAND_GAIN * elev, V2TerrainGen.SOFT_CAP_H, V2TerrainGen.SOFT_CAP_K);
        c.base = hNo;
        c.hNoDetail = hNo;
        // 块级细节：只在陆地上加，振幅恒定（宏观起伏已由 dh 承担）
        double d = detailBlocks(x, z, worldSeedInt);
        double hDet = hNo + d;
        c.hDetail = hDet;
        double hCap = softCap(hDet);
        // ⚠ 2026-09-13 修正（审计 D19）：补回旧实现三处都有的「贴岸低地保险」
        //   V2TerrainGen:133 / :171-172 / :282 都是  h < seaLevel + 1 ? seaLevel + 1 : h。
        // 新链路漏了它，而块级细节噪声（|d| < 1.45*DETAIL_AMP = 5.8 格）**没有海岸淡化**、
        // 直接加在 seaLevel 上 ⇒ elev < 268 m 的陆地列都可能落到海平面以下；
        // 与「ChunkProviderTalos2.fillLandColumnV2 不铺水」叠加就是**海面下的干坑**。
        // ⇒ 这是 D16-a 的前置条件：谁走陆地分支由 D16-a 决定，保证不低于海面由这里决定。
        final int hFloor = Math.min(seaLevel + 1, maxY);
        if (hCap < hFloor) hCap = hFloor;
        c.hCapped = hCap;
        c.h = clampY((int) Math.round(hCap), maxY);
        c.beach = (c.h - seaLevel) <= BEACH_BLOCKS;
        // ---- 雪线：从「按高度」改成「按地表温度」（§98）--------------------------------
        // 旧实现是纯几何的：c.snow = c.hCapped >= snowLineY(z)，只看纬度与高度 ⇒
        // 同纬度同高度必然同结果，表达不出「沿海 vs 内陆」「暖流海岸」这些差异。
        // 现在直接问气候：用 SimClimate 的**年平**地表温度（含本列自己的海拔直减）。
        // 为什么用年平：群系层也是年平的（静态、要进存档）—— 两者同口径才不会打架
        // （否则会出现「热带雨林上下雪」这类自相矛盾）。
        if (SNOW_FROM_TEMP) {
            c.snow = warmestMonthTempK(x, z, worldSeedInt) < SNOW_T;
        }
        return c;
    }

    /** 世界种子（int）→ PlateField 的 long 种子。纯函数、逐位可复现。 */
    public static long seedOf(int worldSeedInt) {
        long h = worldSeedInt * 0x9E3779B97F4A7C15L + 0x5EED_0001L;
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 27);
        return h;
    }

    static int clampY(int y, int maxY) { return y < 1 ? 1 : (y > maxY ? maxY : y); }

    /**
     * 软封顶 —— **与旧实现逐位同式**（V2TerrainGen.composeColumn 里那段 softplus）。
     *
     * <pre>
     *   hCapped = SOFT_CAP_H - SOFT_CAP_K * log1p(exp((SOFT_CAP_H - h) / SOFT_CAP_K))
     * </pre>
     * 它是 softplus 的补：处处连续（在 h = SOFT_CAP_H - 6*SOFT_CAP_K 处两侧同值），
     * 旧的 `if` 只是**远离封顶时跳过昂贵 exp 的性能守卫**，不是物理阈值。
     */
    static double softCap(double h) { return softCapTo(h, V2TerrainGen.SOFT_CAP_H, V2TerrainGen.SOFT_CAP_K); }

    /** 通用软封顶：单调、处处 C∞、以 `cap` 为渐近值。 */
    static double softCapTo(double h, double cap, double k) {
        if (h > cap - 6.0 * k) return cap - k * Math.log1p(Math.exp((cap - h) / k));
        return h;
    }

    /** 连续的门控：t<=0 给 0、t>=1 给 1，中间光滑。用于让某个项"在岸边连续地消失"。 */
    static double smoothstep01(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    /** 块级细节（blocks）：两个八度的值噪声，振幅 ±DETAIL_AMP。 */
    static double detailBlocks(int x, int z, int seed) {
        double a = vnoise(x / DETAIL_W, z / DETAIL_W, seed ^ 0x51ED) * 2.0 - 1.0;
        double b = vnoise(x / (DETAIL_W * 0.37), z / (DETAIL_W * 0.37), seed ^ 0x2C9F) * 2.0 - 1.0;
        return DETAIL_AMP * (a + 0.45 * b);
    }

    static double vnoise(double px, double pz, int seed) {
        double fx = Math.floor(px), fz = Math.floor(pz);
        int xi = (int) fx, zi = (int) fz;
        double tx = px - fx, tz = pz - fz;
        tx = tx * tx * (3.0 - 2.0 * tx);
        tz = tz * tz * (3.0 - 2.0 * tz);
        double a = hash01(xi, zi, seed), b = hash01(xi + 1, zi, seed);
        double e = hash01(xi, zi + 1, seed), f = hash01(xi + 1, zi + 1, seed);
        return (a * (1.0 - tx) + b * tx) * (1.0 - tz) + (e * (1.0 - tx) + f * tx) * tz;
    }

    static double hash01(int x, int y, int seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + y * 0x94D049BB133111EBL;
        h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 32);
        return (h >>> 11) * 0x1.0p-53;
    }
}
