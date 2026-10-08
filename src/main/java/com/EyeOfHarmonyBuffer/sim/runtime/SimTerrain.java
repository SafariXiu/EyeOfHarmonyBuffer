package com.EyeOfHarmonyBuffer.sim.runtime;

import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.sim.erosion.DetailNoise;
import com.EyeOfHarmonyBuffer.sim.erosion.ErosionFilter;

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
     * （3 个 false：HadleyCell/StationaryWave/Vegetation；3 个 true：OceanField/SimTerrain/SoilMoisture）。
     * **本份的含义是：SimTerrain（地形/运行时）。** 引用时务必写全类名（如 `SimTerrain.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     */
    public static boolean ENABLED = true;

    /**
     * **陆地垂直增益（blocks/m）**：8,069 m 的珠峰 -> 约 173 blocks。
     *
     * <p>★ <b>§7755 标定依据（用户实测反馈「整体高度过低、连 150 都过不了」）</b>：
     * 原值 <b>0.0153</b> 只用掉垂直预算的 37%（实测 p50 = Y93、p95 = Y120、max = Y179），
     * 而预算 = {@code SOFT_CAP_H - seaLevel = 188 格}。
     * 本类 javadoc 原本就推的是 {@code 188/8000 = 0.0235}（珠峰 8869 m → 190 格），
     * <b>0.0153 与它自相矛盾</b>。
     *
     * <p>实测扫描（P1462，用户种子，65 km 窗口）：
     * <pre>
     *   LAND_GAIN | p50 | p95 | max | 软封顶裁切
     *     0.0153  |  93 | 120 | 179 |  0.00%     &lt;== 旧值：过低
     *     0.0214  | 105 | 151 | 243 |  0.00%     &lt;== 现取：填满预算且零裁切
     *     0.0280  | 119 | 188 | 252 |  0.01%
     *     0.0350  | 133 | 220 | 252 |  0.15%     （开始裁切）
     * </pre>
     *
     * <p>⚠ <b>本值与以下量必须同步</b>（改一个必须重核其余）：
     * {@link #PEAK_REF_M}、{@link #FADE_VALLEY_BLK}、{@link #FADE_PEAK_BLK}，
     * 以及 {@code LevelField} 的 {@code level()}（海陆比）。
     * ⚠ 它不是 {@code final}（探针要扫），且**只在类初始化时**被上面三个量读取
     * ⟹ 运行时改它不会自动传播。
     *
     * <p>⚠ 第一版用了 `tanh(elev/3000)`，实测**把深海平原压成了一块板**（P294）：
     * tanh 在 −4,000~−5,377 m 之间只变化 0.9 blocks，取整后全落进同一格。
     * 现在改成**线性 + 软封顶**（softplus）：工作区间内保分辨率，只在接近世界高度上限时饱和。
     */
    /**
     * ★★★★★★★★ §7552：<b>层 1（虚拟高度，米）到 层 2（方块）的映射比例。</b>
     *
     * <p>原名 {@code LAND_GAIN}，§7552 拆成两个常量 —— 因为它原先背了<b>两个职责</b>：
     * <ol>
     *   <li>{@code SimTerrain:506} 的「米 -> 方块」映射（本常量）</li>
     *   <li>{@code SimTerrain:586} 的「侵蚀驱动器量纲换算」（{@link #ERO_M_TO_BLK}）</li>
     * </ol>
     * 后果：改「地形看起来多高」会连带改「侵蚀强度」，反之亦然。
     *
     * <p><b>取值依据（P1671，5 种子 x 4000 随机点 = 20000）</b>：
     * <pre>
     *   ELEV_TO_BLK | hNo p50 | p90  | max | 压平(>=251)%
     *      0.055    |   252   | 252  | 252 |  49.9%   &lt;- 一半陆地是平台
     *      0.030    |   179   | 252  | 252 |  23.8%
     *      0.025    |   160   | 239  | 251 |   0.0%   &lt;- 采用
     * </pre>
     * <p>⚠ 与 {@link #LAND_BASE_OFFSET} 一样是<b>标定量</b>（用户裁决的形态参数）。
     * <p>⚠ 改它【不影响】水系/大气/气候/洋流 —— 那些只读层 1 的 {@code elevationM}。
     */
    // ★★★★★★★ 2026-10-08：**原始地形模式**（用户要求：停掉海陆分布之外的所有高度修饰）
    //
    // 【用途】看清「海陆分布层本身」的地形长什么样。
    //   开启后：地形高度 = 纯 elev（海陆分布层的原始输出，米；1:1 当作方块高度）
    //   去掉：ELEV_TO_BLK 映射 / seaLevel / LAND_BASE_OFFSET / softCapTo /
    //         detailBlocks（220 格细节）/ SEABED_RELIEF / OCEAN_GAIN / maxDepth
    //   ⚠ 海陆判定仍在（elev >= 0 = 陆）⟹ 海岸线仍由海陆分布层决定
    //   【回滚】置 false ⟹ 逐位回到原行为
    // ★★★★★★★ 2026-10-08 修正：**RAW_ELEV_MODE = false**
    //   我最初理解错了：把 `ELEV_TO_BLK * elev + seaLevel`（米->格映射）也停掉了，
    //   结果地形高度变成 216 格（= elev 的原始米数）。
    //   ★ 用户要的是：**保留映射**，只停掉【给地形提供起伏的噪声层】。
    //   ⟹ 映射路径照旧；噪声改用下面的 NOISE_* 开关控制。
    public static boolean RAW_ELEV_MODE = false;

    // ★★★★★★★ 2026-10-08：**地形噪声层总开关**（用户要求：看纯海陆分布的地形）
    //
    // 【停掉的是】海陆分布层 elev 之上的【一切噪声起伏】：
    //   · DETAIL_ENABLED=false   ⟹ 停 `detailBlocks`（DetailNoise.fbmRot，220 格 / 5.8 格）
    //   · SEABED_ENABLED=false   ⟹ 停 `SEABED_RELIEF`（vnoise，880 格 / 3 格，仅海洋）
    //   · 侵蚀本来就已关（V2TerrainGen.EROSION_ENABLED=false）
    //   · `V2TerrainGen.baseAndPlain` / `TerrainBaseHeight`（旧系统的 PeriodicNoise
    //     频率分层）本来就是【死代码】—— `SimTerrain.ENABLED=true` 时 composeColumn
    //     提前返回，那条路一行都不走。
    //
    // 【保留的】`ELEV_TO_BLK * elev + seaLevel` 映射（这是海陆分布层 -> MC 方块的必要换算）
    //   ⟹ 地形 = 海陆分布的骨架，但**没有**噪声起伏
    //
    // 【回滚】两个都置 true ⟹ 逐位回到原行为
    public static boolean DETAIL_ENABLED = false;
    public static boolean SEABED_ENABLED = false;

    public static double ELEV_TO_BLK = 0.025;

    // ================= ★★★★★★★ B 方案：统一海岸剖面（无墙） =================
    //
    // 【为什么】原实现是【硬分支】：
    //     if (!PlateField.isLand(x,z,seed)) { 海：h = seaLevel − depth;  return; }
    //     陆：h = seaLevel + ELEV_TO_BLK·elev;
    //   ⟹ 两岸用不同的公式 ⟹ 在岸线上【永远不可能相遇】⟹ **墙**
    //
    // 【正解（用户方案）】用【连续的有符号到岸距离】做**统一剖面**：
    //   sigDist = (landScore−0.5)/|∇landScore|   ← 处处连续、岸线处 =0
    //   profile = coastProfileCF(sigDist)        ← 两侧同一公式 ⟹ 必然相遇
    //   h = seaLevel + ELEV_TO_BLK · CF_GAIN · profile
    //   ⟹ 岸线处 profile=0 ⟹ 两侧 h 都是 seaLevel ⟹ **无墙**
    public static boolean USE_CF_PROFILE = true;

    /** 统一剖面的幅度缩放（标定：让高度分布与旧 elev 一致）。 */
    public static double CF_GAIN = 3.0;

    /**
     * ★★★★★★★★ §7552：<b>侵蚀驱动器的「米 -> 方块」量纲换算</b>（原 {@code LAND_GAIN} 的第二个用法）。
     *
     * <p>用于 {@code SimTerrain:586}：把 4 km 中心差分的真实地形梯度（米）换算到
     * 「被侵蚀高度场」（方块）的量纲。
     *
     * <p><b>为什么独立（P1675，5 种子 x 400 点 = 2000 样本）</b>：
     * 该项在侵蚀驱动器里的占比只有 <b>3.5%</b>（主导项是噪声梯度）：
     * <pre>
     *   ERO_M_TO_BLK | 真实梯度占比 | 方向偏转 | 合成幅度
     *      0.055     |    3.5%      |  1.3 度  |  0.549
     *      0.025     |    1.6%      |  0.6 度  |  0.505（-8%）
     * </pre>
     * ⟹ 它【可以独立取值】，不必跟着 {@link #ELEV_TO_BLK} 走。
     * <p>初值 = 拆分前的 {@code LAND_GAIN}（0.055）⟹ 拆分本身是【零行为变化】的重构。
     */
    public static double ERO_M_TO_BLK = 0.055;

    /**
     * §7552：<b>兼容字段</b>。约 63 支既存探针读它 ⟹ 保留。
     *
     * <p><b>它不再被生产代码使用</b>：生产读 {@link #ELEV_TO_BLK}（映射）与
     * {@link #ERO_M_TO_BLK}（侵蚀换算）。用法点见 {@code SimTerrain:506} 与 {@code :586}。
     * <p>初值 = {@link #ERO_M_TO_BLK}（= 拆分前的 0.055）⟹ 旧探针取到的仍是它们历史期望的那个数。
     * <p>⚠ 探针若<b>写</b>它，不会影响生产（生产读的是那两个常量）。要改行为请写那两者。
     * @deprecated 用 {@link #ELEV_TO_BLK} 或 {@link #ERO_M_TO_BLK}。
     */
    @Deprecated
    public static double LAND_GAIN = 0.055;

    /**
     * §7552（S6）：PEAK_REF_M 已删除 —— 全仓 grep 确认它是【死代码】（只有定义与两处 javadoc 引用，
     * 零消费点）。它原本要用于 {@code ft = 2*(elev/PEAK_REF_M)-1}，但 §7742 改成了 FADE_*_BLK。
     */

    /**
     * ★★★★★★★★ <b>§7742：{@code fadeTarget} 的区间端点（block）。</b>
     *
     * <p><b>为什么必须改</b>（P1407/P1408 实测 + 博客逐字）：原实现用
     * {@code ft = 2*(elev/PEAK_REF_M) - 1}，等于把 {@code valleyAlt} 锚在
     * <b>0 m（海平面）</b>。但本仓陆地是从约 <b>1200 m</b> 才开始的 ⟹
     * 整个陆地挤在【负半边】：
     * <pre>
     *   实测 fadeTarget: min=-0.697  max=+0.219  mean=-0.353
     *   直方图: [+0.2, +1.0) = 0.0%   &lt;== 正值区间【完全没用上】
     * </pre>
     *
     * <p><b>博客第 217-218 行逐字</b>：
     * <pre>
     *   // Convert the altitude to a value between -1 and 1.
     *   float fadeTarget = inverse_lerp(valleyAlt, peakAlt, h) * 2.0 - 1.0;
     * </pre>
     * 且第 443 行自述：<b>「Pointy peaks are still dependent on the fade target having
     * a value close to 1.0」</b> —— 我们的 fadeTarget 永远到不了 1.0 ⟹
     * <b>尖峰机制从未激活，整张图只有「谷/沟」行为</b>。
     *
     * <p><b>标定</b>（P1408，200 km 窗口 160000 个陆地点，1%/99% 分位）：
     * {@code elev 991.5 m / 4843.6 m} ⟹ 经 {@code hNo = softCapTo(64 + 0.0153*elev, 252, 6)}
     * 换算为 block：<b>79.4 / 138.1</b>。取 1%/99% 而不是 0%/100% 是为了留 2% 的余量，
     * 避免极值点长期贴边（饱和会削弱峰/谷对比）。
     *
     * <p>⚠ 这两个值是<b>标定量</b>（与 {@code LAKE_MIN_DEPTH_M} 同性质），不是逐字常数。
     */
    public static double FADE_VALLEY_BLK = 0.36 * ((V2TerrainGen.SOFT_CAP_H - 64.0));
    /** 见 {@link #FADE_VALLEY_BLK}。 */
    public static double FADE_PEAK_BLK = 1.306 * ((V2TerrainGen.SOFT_CAP_H - 64.0));
    /** **海洋垂直增益（blocks/m）**：−5,377 m 的深海平原 -> 约 56 blocks。 */
    public static double OCEAN_GAIN = 0.0105;
    /** 深海平原的块级起伏（blocks）—— 与旧实现 SEABED_RELIEF 同量级。 */
    public static double SEABED_RELIEF = 3.0;
    /** 块级细节振幅（blocks）与波长（m）—— 让山有纹理，不是光滑高地。 */
    public static double DETAIL_AMP = 4.0;
    public static double DETAIL_W = 220.0;
    /** 沙滩带：高出海平面这么多方块以内算滩。 */
    public static double BEACH_BLOCKS = 3.0;

    /**
     * ★ <b>§7756：沙滩带总开关。默认 {@code false}（用户裁决：要做盆地，沙滩带会干扰）。</b>
     *
     * <p><b>机制</b>（{@code ChunkProviderTalos2:268-272}）：{@code c.beach=true} 时，
     * 该列顶部 <b>3 格</b>（1 表面 + 2 填充）被换成沙子，<b>优先于群系 profile</b>。
     * 触发条件是<b>纯高度</b>判据 {@code h - seaLevel <= BEACH_BLOCKS}，
     * <b>不看到海距离</b> ⟹ 内陆低洼地（例如将来的盆地底部）也会被铺沙。
     *
     * <p>{@code false} ⟹ {@code c.beach} 恒为 false ⟹ 全部走群系 profile（=「默认的」）。
     */
    public static boolean BEACH_ENABLED = false;

    /**
     * ★★★★★★★★ <b>§7757：陆地基础抬升（block）。</b>用户裁决：地形整体再高一些。
     *
     * <p><b>为什么要它</b>（P1465 实测，用户种子，3000 km / 576 点全球采样）：
     * <pre>
     *   offset | p25 | p50 | p75 | p90 | p95 | p99 | max | 撞封顶
     *      0     |     | 103 | 120 | 141 |     | 208 | 243 | 0.00%   （P1465 模拟基线）
     *     30     | 126 | 138 | 162 | 185 | 210 | 228 | 252 | 0.39%   <== 现取
     *     40     | 136 | 148 | 172 | 195 | 220 | 237 | 252 | 0.78%
     * </pre>
     * 用户目标「大部分山峰 ~180、偶尔高峰接近软顶、底部再高 20」⟹ **+30**，
     * 实测 p90=185（== 目标 180 量级）、p99=228、max=252（== 高峰触顶），撞封顶仅 0.39%。
     *
     * <p><b>口径</b>：它是**对陆地基础高度的均匀平移**（加在 {@code seaLevel + LAND_GAIN*elev} 之后、
     * softcap 之前）⟹ 起伏、地形形态、海陆比**都不变**，只是整体抬高。
     *
     * <p>⚠ 只作用于<b>陆地</b>分支（海洋分支是 {@code seaLevel - depth}，不受影响）。
     * <p>⚠ 与 {@link #LAND_GAIN} 一样，它是<b>标定量</b>（用户裁决的形态参数），不是逐字常数。
     */
    public static double LAND_BASE_OFFSET = 0.0;   // ★ §7794：配合 LAND_GAIN=0.040 保持 meanY 不变（155.7 vs 154.6）
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
    /** §7716：侵蚀的梯度 scratch（[0]=dx, [1]=dz）。 */
    private static final ThreadLocal<double[]> ERO_GRAD = ThreadLocal.withInitial(() -> new double[2]);

    /**
     * ★★★★★★★★ <b>§7743：侵蚀输入梯度的【量纲修正因子】。</b>
     *
     * <p><b>为什么必须要有它</b>（P1411 实测）：侵蚀滤镜的 {@code slopeLength} 参与
     * <b>两个绝对值运算</b>（{@code combiMask} 与 {@code roundingForInput}），
     * 所以它<b>必须与被侵蚀的高度场同量纲</b>。而
     * {@link com.EyeOfHarmonyBuffer.sim.erosion.DetailNoise#gradRidged} 算的是
     * <b>细节层【自己】高度</b>的导数 —— 那是<b>另一个高度场</b>。
     *
     * <pre>
     *   实测（P1411，cell 尺度）：噪声梯度 |g| 均值 = 0.0238
     *                             参考实现的 AssumedSlope = 0.70
     *   ==&gt; 我们的梯度比滤镜期望的小【30 倍】
     *   ==&gt; combiMask = easeOut(smoothStart(0.0297, 0.984)) = 0.00022 ≈ 0
     *   ==&gt; mix(fadeTarget, gullies, combiMask) 退化成【几乎全 fadeTarget】
     *   ==&gt; 沟壑项被压掉 ⟹ 只有平滑块状，没有细树枝网络
     * </pre>
     *
     * <p><b>推导（不是拟合）</b>：过滤器要的是 {@code d(blockHeight)/d(worldUnit)}。
     * <pre>
     *   细节层在 cell 尺度上的高度 ≈ 0.38 格（gradRidged 的各 octave 幅度之和）
     *   真实地形在 cell 尺度上的高度 ≈ 12 格
     *   ==&gt; 比值 ≈ 31.6
     * </pre>
     * 与实测 30 吻合 ⟹ 纯推导值是 <b>32</b>。但 <b>P1412 扫描后定稿取 8</b>：
     * <pre>
     *   GRAD_SCALE | relief | ridge密度 | straightness
     *        0     | 102.4  |    268.9  |   0.0263   （沟壑被完全压掉）
     *        8     |  99.2  |   1801.3  |   0.4480   &lt;== 定稿
     *       16     |  94.5  |   2222.5  |   0.5770
     *       32     |  91.1  |   2381.9  |   0.5904
     * </pre>
     * ridge 密度涨 <b>6.7 倍</b>（沟壑释放），但 straightness 也涨 17 倍（开始规则晶格）
     * ⟹ 取拐点前的 <b>8</b>。
     *
     * <p>⚠ 这是<b>标定量</b>；改 {@code ERO_EXTRA_AMP}（它改变了地形在 cell 尺度的高度）
     * 之后必须重标。
     */
    public static double ERO_GRAD_SCALE = 8.0;

    /**
     * 侵蚀门控的【下限】。**0 = 门控真正生效**（平原完全不侵蚀）。
     *
     * <h3>★ 2026-10-08 用户裁决：改成 0</h3>
     * <p>用户原话：「后续侵蚀滤镜我打算**单独用在海陆分布中输出为山地**的位置」。
     * <p>原值 0.5 的历史原因（§7763）：P1476 实测用户种子上约 2/3 的图块 gate=0
     * （那些地方完全没有侵蚀、没有沟壑），于是加了 FLOOR=0.5 让「平地也有 50% 侵蚀」。
     *
     * <p>⚠ 但那与「只在山地侵蚀」的意图**直接矛盾**：
     * <pre>
     *   本文件的门控注释写的是「平原 gate≈0（完全不侵蚀）」
     *   而 FLOOR=0.5 让平原恒有 50% ⟹ 用户在【海边平原】也看到了侵蚀（已确认）
     * </pre>
     *
     * <p><b>为什么现在可以改成 0</b>：gate = LandformField.Sample.mtnPlusPeak() = belt·m，
     * 而 belt 来自 OrographyField.beltMask01 ⟸ 山带层（MountainLayerV2）。
     * 修复 A 之后山带真的产生了（auth 可达 1.0、uplift 可达 436 格）
     * ⟹ ★ gate 现在真能区分「山地 / 平原」⟹ 可以让它真正门控。
     *
     * <p><b>后果</b>：平原 gate=0 ⟹ 完全不侵蚀；山地 gate→1 ⟹ 全侵蚀。
     * <p><b>回滚</b>：置回 0.5 ⟹ 逐位回到旧行为。
     */
    public static double ERO_GATE_FLOOR = 0.0;

    /**
     * §7792: 把侵蚀【驱动器梯度的幅度】归一到常数（0 = 关闭，逐位回滚点）。
     *
     * 依据（P1536 实测，用户站点附近 40000 点）：
     *   |eg| 的 min=0.01572  p10=0.18485  p50=0.63191  p90=0.77885  max=0.90211
     *   => max/min = 57.4 倍，p90/p10 = 4.2 倍
     * 而参考库（lpmitchell:387）喂的是【归一化 fBm】：
     *   gullies = vec3(phacelle.x, phacelle.y * phacelle.zw)
     *   —— 高度与坡度【来自同一个归一化场】=> 幅度近乎恒定。
     *
     * 为什么这是「墙」的解：
     *   滤镜输出的幅度【正比于输入梯度幅度】=> 幅度剧变的地方
     *   eroT 就有大落差 => 地形出现台阶（用户实测 5.99 格；§52 那处 27.21 格）。
     *   把幅度归一 => 侵蚀深度【处处一致】=> 落差消失，而【平均深度不变】。
     *
     * 生效方式：eg 乘 (1-a) + a*(|eg|的常数)/|eg|，a = ERO_DRIVER_NORM。
     * 若同时把 ERO_AMP 乘 (1-a)，则【平均侵蚀深度】守恒。
     */
    public static double ERO_DRIVER_NORM = 0.0;   // ★ §7788：恢复原值。P1518 实测这个参数对 lineIndex 几乎无影响（1.37~1.48），只是起伏杠杆（relief 111->150）=> 不该为线条牺牲它

    /**
     * §7744: 梯度【方向扰动】幅度（弧度）。0 = 关闭（逐位回退）。
     *
     * 为什么需要（P1412 + 用户游戏内实测）：修好 §7743 后 combiMask 打开，
     * 暴露出来的是 phacelle 条纹本身的规则性 —— 条纹方向 = gullySlope 的方向。
     * 我们的梯度方向场太均匀，条纹几乎平行，图上出现又长又直、间距规则的「伤口」
     * （straightness 0.026 -> 0.448，涨 17 倍）。
     *
     * 参考库没有这个问题，因为它们的梯度来自【真实 fBm】，方向逐点随机，有机分叉。
     * 本参数用一个 cell 尺度的平滑方向场去模拟那个随机性。
     *
     * 注意：这是本仓引入的机制，参考实现没有对应物（它们不需要）。
     */
    public static double ERO_DIR_JITTER = 0.0;

    /** 方向扰动场的波长（block）。取 cell 尺度。 */
    public static double ERO_DIR_WL = 0.105 * V2TerrainGen.ERO_UNIT;

    /**
     * §7745: 用【真实地形梯度方向】替换噪声方向的比例，0..1。0 = 关闭（逐位回退）。
     *
     * 依据：两个参考库的侵蚀梯度方向都来自【被侵蚀的那个高度场】。
     * 我们的噪声方向太均匀（P1411: 与真实坡度平均夹角 51 度，但方向场本身平滑），
     * 条纹因此几乎平行，图上出现又长又直的「伤口」。
     */
    public static double ERO_DIR_TERRAIN = 1.0;

    /** §7745: 真实地形方向的差分半宽（block）。P1416 实测方向变化率与此几乎无关
     *（4 格 52.08 度 / 105 格 48.19 度），所以取小的以保留局部性。 */
    public static double ERO_DIR_DH = 26.0;

    // ================= ★ §7739：细结构随坡度变化 =================
    /**
     * <b>细频段（{@code TalosField.hfExtra}，366 m）是否随坡度调制。</b>默认 {@code true}。
     *
     * <p><b>为什么需要</b>（P1393/P1396 实测）：细频段是<b>全局</b>加的，振幅是常数、
     * 不随位置变化 ⟹ 远景（12800 格）看是<b>均匀麻点纹理</b>，缺少「山脉—盆地」的组织。
     * 真实地形的小尺度粗糙度是<b>空间变化</b>的：陡坡粗糙、平原光滑。
     *
     * <p>{@code false} ⟹ 退回「全局常数振幅」（与 §7738 逐位相同，回滚点）。
     */
    public static boolean ERO_EXTRA_SLOPE_MOD = true;

    /**
     * <b>坡度采样的半宽（block）。</b>取侵蚀输入的 cell 尺度（{@code 0.105 x ERO_UNIT}），
     * 即「侵蚀滤镜自己看见的那个坡度」—— 自洽，不是新旋钮。
     */
    public static double EXTRA_SLOPE_HALF = 0.105 * V2TerrainGen.ERO_UNIT;

    /**
     * <b>坡度掩膜的上下阈值。</b>掩膜 = smoothstep(LO, HI, 坡度)，坡度单位 m/m。
     *
     * <p><b>§7739 标定依据</b>（P1399 实测，20 km 窗口 10000 点）：
     * cell 尺度（{@code EXTRA_SLOPE_HALF = 105} 格）上的坡度分布
     * <b>均值 0.97，96% 的点 &gt; 0.75</b> —— 也就是说初版的 0.35/0.75 让掩膜<b>几乎处处饱和</b>
     * （均值 0.9704）⟹ 等于没调制 ⚠。
     *
     * <p>改到 0.5/1.5：平原（坡度 &lt; 0.5）几乎不加细结构，陡坡（&gt; 1.5）全量加，
     * 中间平滑过渡。<b>注意</b>：它们只重分配细结构，不改变 {@link #ERO_EXTRA_AMP} 的上限。
     */
    public static double EXTRA_SLOPE_LO = 0.5;
    public static double EXTRA_SLOPE_HI = 1.5;

    /**
     * ★ <b>§7740：细结构掩膜的【下限】—— 任何地方都保留这个比例。</b>
     *
     * <p><b>为什么必须要有它</b>（用户实测反馈 + P1401）：{@link LandformField#mtnPlusPeak()}
     * 是「<b>是不是山</b>」的判据，实测<b>只在约 25% 的陆地地点非零</b>。
     * 若直接拿它当系数，剩下的 <b>75% 陆地会把细结构完全关掉 ⟹ 地形整体塌下去</b> ⚠
     * （这正是「把高度直接全压下去了」的原因）。
     *
     * <p>加下限后：掩膜 = {@code MASK_MIN + (1-MASK_MIN) * gate} ∈ [MASK_MIN, 1]，
     * <b>细结构处处存在</b>，只是在山区更粗、在平地更细。
     */
    public static double EXTRA_MASK_MIN = 0.45;

    /**
     * 该点的<b>细结构掩膜 ∈ [0,1]</b>：0 = 平原（不加细结构），1 = 山地（全量加）。
     *
     * <p><b>§7739 为什么不用「坡度」而用「山地门控」</b>（P1399 实测否证了坡度版）：
     * 在 cell 尺度（105 格）上量坡度，量到的<b>主要就是细频段自己的贡献</b> ⟹
     * 用它去调制细频段是<b>循环依赖</b>，实测掩膜均值 0.97（阈值 0.35/0.75）与 0.91（0.5/1.5）
     * —— <b>几乎处处饱和，等于没调制</b> ⚠。
     *
     * <p>改用<b>粗地形</b>的「这里是不是山」：{@link LandformField#mtnPlusPeak()}，
     * 也就是侵蚀门控（{@code §7723}）用的同一个量。它是<b>既有权威判据</b>（D43），
     * 连续、且与细频段无关 ⟹ 无循环依赖，也<b>零新增判据</b>。
     */
    public static double extraSlopeMask(int x, int z, int worldSeedInt) {
        if (!ERO_EXTRA_SLOPE_MOD) return 1.0;
        return LandformField.sample(x, z, worldSeedInt).mtnPlusPeak();
    }

    /**
     * 本列实际使用的细频段振幅（受 {@link #ERO_EXTRA_SLOPE_MOD} 与 {@link #EXTRA_MASK_MIN} 控制）。
     *
     * <p>⚠⚠ <b>必须传【原始世界种子 int】</b>（{@code worldSeedInt}）：{@link LandformField}
     * 按它做瓦片键。曾经这里传的是 {@code (int) seedOf(worldSeedInt)}（长种子的截断），
     * 那会去查<b>另一个世界</b>的门控 ⟹ 处处返回 0 ⟹ <b>细结构被整片关掉、地形塌下去</b>
     * （用户实测发现，P1404 定位）。
     */
    public static double extraAmpFor(int x, int z, int worldSeedInt) {
        double a = com.EyeOfHarmonyBuffer.sim.litho.PlateField.ERO_EXTRA_AMP;
        if (a <= 0.0) return 0.0;
        if (!ERO_EXTRA_SLOPE_MOD) return a;
        double g = LandformField.sample(x, z, worldSeedInt).mtnPlusPeak();
        if (g < 0.0) g = 0.0; if (g > 1.0) g = 1.0;
        return a * (EXTRA_MASK_MIN + (1.0 - EXTRA_MASK_MIN) * g);
    }
    /** **public 是为了让探针与生产同源**（P462 要量 D46 的沿岸-内陆过渡）。 */
    // ★★★★★★★ 2026-10-08 性能修复：**加记忆化**
    // 【为什么】`V2BiomeSelect.accumulateWeights` 对**每一格**都调本方法进 Snow 判定，
    //   而 `BiomeField.solve` = 81,000 格/次 ⟹ 大量重复计算。
    private static final ThreadLocal<java.util.HashMap<Long, Double>> WMT_MEMO =
        ThreadLocal.withInitial(java.util.HashMap::new);
    public static boolean WMT_MEMO_ON = true;
    public static void clearWmtMemo() { WMT_MEMO.get().clear(); }

    public static double warmestMonthTempK(int x, int z, int worldSeedInt) {
        if (!WMT_MEMO_ON) return warmestMonthTempK0(x, z, worldSeedInt);
        java.util.HashMap<Long, Double> m = WMT_MEMO.get();
        long key = (((long) x << 32) ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L + worldSeedInt;
        Double v = m.get(key);
        if (v != null) return v;
        double r = warmestMonthTempK0(x, z, worldSeedInt);
        if (m.size() > 262144) m.clear();
        m.put(key, r);
        return r; }

    private static double warmestMonthTempK0(int x, int z, int worldSeedInt) {
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
        // ★ §7739：细频段振幅随坡度变化（ERO_EXTRA_SLOPE_MOD=false 时逐位退回旧行为）
        // ★★★★★★★★ §7771（Round 20）：这里【必须】用恒定 ERO_EXTRA_AMP，不能用 extraAmpFor。
        //   根因（P1487/P1488 实测）：TalosField.level() 在 :918 用【零参数】fieldValue 采样
        //   （隐含 extraAmp = ERO_EXTRA_AMP），而本行原用 extraAmpFor()（实测 0.1125）。
        //   两条路径看到【不同的场分布】⟹ 65.5 分位点落在分布的不同处
        //   ⟹ 同一列的 elevation 符号相反 ⟹ composeColumn 的 land 与 TalosField.isLand 相反。
        //   实测：4000 列中 148 列不一致（3.7%），并打红 7 个验收门（P294/P442/P477/P479/P991）。
        //   P1488 实证：(-4444495,2222215) elev(zero)=-61.4 而 elev(explicit)=+469.4（符号相反）。
        //   修法：让生产与 level() 的标定口径一致 ⟹ elevation>=0 与 isLand 重新逐位等价。
        double extraAmp = com.EyeOfHarmonyBuffer.sim.litho.PlateField.ERO_EXTRA_AMP;
        double elev = com.EyeOfHarmonyBuffer.sim.litho.PlateField.elevation(x, z, seed, extraAmp);

        // 四个字段一起写：Column 是复用容器，**每个字段都必须被覆盖**，否则会漏出上一列的残值。
        c.plain = 0.0;
        c.mtnComp = 0.0;
        c.auth = 0.0;
        c.uplift = 0.0;
        c.detailStrength = 0.0;
        c.snow = false;
        c.beach = false;

        // ★★★★★ §7541：海陆分支改用 TalosField.isLand（= lowPass(fieldValue) > lvlFast），
        //   与 ChunkProviderTalos2:213 的【方块海陆判定】同一个函数。
        //   历史：这里原来用 elev < 0.0，而 isLand 自 §7541 起含 1 km 低通 ⟹ 两条口径在
        //   海岸线上有 2/4000 的差异（P294 GATE_LAND_CONSISTENT 实测）。
        //   elev 仍用于【连续量】（水深/高度），只有布尔分支换成单一来源。
        // ★★★★★★★ B 方案：统一剖面（在硬分支之前返回）
        if (USE_CF_PROFILE) {
            long cfWs = ((long) worldSeedInt) & 0xFFFFFFFFL;
            double sig = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandField
                .signedCoastDistCF(cfWs, x, z);
            double prof = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandField
                .coastProfileCF(sig);
            double hCF = seaLevel + ELEV_TO_BLK * CF_GAIN * prof;
            boolean landCF = sig >= 0.0;
            c.land = landCF;
            c.seaDepth = landCF ? 0.0 : Math.max(0.0, seaLevel - hCF);
            c.base = hCF;
            c.hNoDetail = hCF;
            double dCF = DETAIL_ENABLED ? detailBlocks(x, z, worldSeedInt) : 0.0;
            c.hDetail = hCF + dCF;
            c.hCapped = c.hDetail < 1.0 ? 1.0 : c.hDetail;
            c.h = clampY((int) Math.round(c.hCapped), maxY);
            return c; }

        if (!com.EyeOfHarmonyBuffer.sim.litho.PlateField.isLand(x, z, seed)) {
            c.land = false;
            if (RAW_ELEV_MODE) {
                // ★ 原始模式：海面高度 = elev（负值），无 OCEAN_GAIN / 无 SEABED_RELIEF / 无 maxDepth
                c.seaDepth = 0.0;
                c.base = 0.0;
                c.hNoDetail = elev;
                c.hDetail = elev;
                c.hCapped = elev < 1.0 ? 1.0 : elev;
                c.h = clampY((int) Math.round(c.hCapped), maxY);
                return c;
            }
            double maxDepth = seaLevel - 1.0;
            if (maxDepth < 1.0) maxDepth = 1.0;
            double dBase = softCapTo(OCEAN_GAIN * (-elev), maxDepth, 6.0);
            // 深海平原起伏：在岸边**连续地**消失（smoothstep），所以浅滩/沙滩不受影响
            // 深海平原起伏：在岸边**连续地**消失（smoothstep），所以浅滩/沙滩不受影响
            //   ★ 2026-10-08：原式是 `SEABED_RELIEF * vnoise(px,pz,seed) * smoothstep01(dBase/8.0)`；
            //     本次编辑一度把 smoothstep 误当成 vnoise 的第 4 个参数 —— 已改回乘法。
            double relief = (SEABED_ENABLED ? SEABED_RELIEF : 0.0)   // ★ 括号必需（三元优先级低于 *）
                * vnoise(x / (DETAIL_W * 4.0), z / (DETAIL_W * 4.0), worldSeedInt ^ 0x5A5A)
                * smoothstep01(dBase / 8.0);
            double depth = dBase + relief;
            c.seaDepth = depth;
            c.base = 0.0;
            c.hNoDetail = seaLevel - depth;
            c.hDetail = c.hNoDetail;
            c.hCapped = c.hNoDetail < 1.0 ? 1.0 : c.hNoDetail;
            c.h = clampY((int) Math.round(c.hCapped), maxY);
            return c;
        }

        c.land = true;
        c.seaDepth = 0.0;
        // ★ 原始模式：陆地高度 = elev（**海陆分布层的原始输出，米，1:1 当方块高度**）
        //   去掉：ELEV_TO_BLK（米->格映射）、seaLevel、LAND_BASE_OFFSET、softCapTo
        final double hNo = RAW_ELEV_MODE
            ? elev
            : softCapTo(seaLevel + LAND_BASE_OFFSET + ELEV_TO_BLK * elev,
                        V2TerrainGen.SOFT_CAP_H, V2TerrainGen.SOFT_CAP_K);
        c.base = hNo;
        c.hNoDetail = hNo;
        // 块级细节：只在陆地上加，振幅恒定（宏观起伏已由 dh 承担）
        // ★ 原始模式：关掉（220 格 / 5.8 格的 MC 级细节不属于海陆分布层）
        double d = DETAIL_ENABLED ? detailBlocks(x, z, worldSeedInt) : 0.0;   // ★ 噪声层开关
        double hDet = hNo + d;

        // ============ §7716：Runevision 侵蚀滤镜（MPL 2.0 移植）============
        // 【为什么在这里】：V2TerrainGen.composeColumn 在 :422 会因
        // SimTerrain.ENABLED=true 提前返回，所以那里注入是死代码。
        // 本方法才是 MC 实际走的地形合成路径。
        //
        // 【输入高度场】是【独立的各向同性细节层】（DetailNoise），不是 detailBlocks：
        //   · §7712：ETOPO 标定后地形功率谱 beta = 2H+2 = 2.82，375 km 以下必须有能量；
        //   · §7700：山地门控在平地会把细节归零 ⇒ 侵蚀没东西可咬 ⇒ 规则点阵花瓣；
        //   · §7714：value noise（TerrainNoise.fbm2D）有 45 度晶格偏置（aniso=1.327），
        //            会留下斜向条带 ⇒ 必须用 Simplex + 每 octave 旋转黄金角（aniso=1.108）。
        // 【归一化口径】：DetailNoise.fbmRot 输出约 [-1,1] ⟹ 无需再归一化。
        if (V2TerrainGen.EROSION_ENABLED) {
            long eroSeed = (long) worldSeedInt ^ V2TerrainGen.ERO_SEED_XOR;
            double[] eg = ERO_GRAD.get();
            // ★ §7722：用【ridge 化】的梯度（raw fBm 只造圆丘，不造山脊）
            DetailNoise.gradRidged(eroSeed, x, z, 1.0, V2TerrainGen.ERO_OCT,
                                   V2TerrainGen.ERO_WL0, V2TerrainGen.ERO_UNIT, eg);
            // ★ §7743：把「细节层自己的梯度」换算到「被侵蚀高度场」的量纲。
            //   不做这一步 combiMask 会塌到 0.0002，沟壑项被整体压掉（P1411 实测）。
            eg[0] *= ERO_GRAD_SCALE;
            eg[1] *= ERO_GRAD_SCALE;
            // ★ §7745：用【真实地形】的梯度方向替换噪声方向（参考库的做法）。
            //   依据：两个参考库的方向都来自真实高度场；我们的噪声方向太均匀，
            //   与沟壑应该跟随的地形无关 ⟹ 条纹平行 ⟹ 「伤口」。
            //   ERO_DIR_TERRAIN = 0 时不执行（逐位回退）。
            if (ERO_DIR_TERRAIN > 0.0) {
                final int DH = (int) Math.max(1, ERO_DIR_DH);
                double tx1 = PlateField.elevationWithCell(x + DH, z, seed, cell)
                           - PlateField.elevationWithCell(x - DH, z, seed, cell);
                double tz1 = PlateField.elevationWithCell(x, z + DH, seed, cell)
                           - PlateField.elevationWithCell(x, z - DH, seed, cell);
                double tm = Math.sqrt(tx1 * tx1 + tz1 * tz1);
                if (tm > 1e-9) {
                    double nm = Math.sqrt(eg[0] * eg[0] + eg[1] * eg[1]);
                    double w = ERO_DIR_TERRAIN;
                    double ux = (1 - w) * (eg[0] / Math.max(1e-12, nm)) + w * (tx1 / tm);
                    double uz = (1 - w) * (eg[1] / Math.max(1e-12, nm)) + w * (tz1 / tm);
                    double um = Math.max(1e-12, Math.sqrt(ux * ux + uz * uz));
                    double mag = nm <= 1e-12 ? 1.0 : nm;
                    eg[0] = ux / um * mag;
                    eg[1] = uz / um * mag;
                }
            }
            if (ERO_DIR_JITTER > 0.0) {
                double ja = ERO_DIR_JITTER * vnoise(x / ERO_DIR_WL, z / ERO_DIR_WL, worldSeedInt ^ 0x3C31);
                double cj = Math.cos(ja), sj = Math.sin(ja);
                double nx2 = eg[0] * cj - eg[1] * sj;
                double nz2 = eg[0] * sj + eg[1] * cj;
                eg[0] = nx2; eg[1] = nz2;
            }
            // ★★★ §7732（甲）：把【真实地形的梯度】加进侵蚀输入。
            //
            // 【依据】（runevision 博客逐字）：
            //   「the pivot point is never too far away. 【At least as long as the gradient of
            //     the height function doesn't change too drastically within a single cell】.」
            // 【问题】：纯均匀 fBm 的梯度【处处方向一致】⟹ cell 的 pivot 旋转失效
            //   ⟹ cell 边界在输出里【可见】。§7728 的二分实测（P1342 等高线图）：
            //   关掉侵蚀 ⟹ 多边形【完全消失】（hash 不同、图中无任何直线）。
            // 【解法】：叠加真实地形（米制海拔）的中心差分梯度。它的方向随位置【剧变】
            //   （大陆尺度骨架 + 海岸线），足以打散 cell。
            //
            // 【为什么用 elevationWithCell 而不是 composeColumn】：后者返回【每线程复用】
            //   的 Column，递归调用会破坏契约；而 elevationWithCell 是【纯函数】。
            // 【尺度】：取 4 km（远大于 cell 105 m、远小于 HF_WL_MIN 46.9 km 的一半），
            //   所以它读到的是【地形骨架的梯度】，不含细节噪声。
            if (V2TerrainGen.ERO_REAL_GRAD > 0.0) {
                final int GS = 4000;
                double ex1 = PlateField.elevationWithCell(x + GS, z, seed, cell);
                double ex0 = PlateField.elevationWithCell(x - GS, z, seed, cell);
                double ez1 = PlateField.elevationWithCell(x, z + GS, seed, cell);
                double ez0 = PlateField.elevationWithCell(x, z - GS, seed, cell);
                // 米 -> 格：乘 ERO_M_TO_BLK（§7552 起独立于 ELEV_TO_BLK）；再乘 ERO_UNIT/(2*GS) 换到 d(h)/d(worldUnit)
                double k = V2TerrainGen.ERO_REAL_GRAD * ERO_M_TO_BLK * V2TerrainGen.ERO_UNIT / (2.0 * GS);
                eg[0] += (ex1 - ex0) * k;
                eg[1] += (ez1 - ez0) * k;
            }
            // ★ §7792：把驱动器幅度归一到常数（参考库喂的是归一化 fBm，幅度恒定）。
            //   ERO_DRIVER_NORM = 0 时不执行（逐位回滚点）。
            if (ERO_DRIVER_NORM > 0.0) {
                double m0 = Math.sqrt(eg[0] * eg[0] + eg[1] * eg[1]);
                if (m0 > 1e-12) {
                    double a = ERO_DRIVER_NORM;
                    // 目标幅度取 0.5（|eg| 的中位量级），保持平均深度
                    double target = 0.5;
                    double sc = (1.0 - a) + a * (target / m0);
                    eg[0] *= sc; eg[1] *= sc;
                }
            }
            // ★ §7742：fadeTarget = inverse_lerp(valley, peak, h)*2-1  （博客第 217-218 行逐字）
            //   区间锚在本仓【陆地高度的实测分位】上，而不是海平面 ⟹ 峰/谷两端都用满。
            //   ⚠ 这里必须用与侵蚀同一个量纲的 hNo（block），不是 elev（米）。
            double ft = 2.0 * Math.min(1.0, Math.max(0.0,
                        (hNo - FADE_VALLEY_BLK) / (FADE_PEAK_BLK - FADE_VALLEY_BLK))) - 1.0;
            double dh = ErosionFilter.erosion(
                    x / V2TerrainGen.ERO_UNIT, z / V2TerrainGen.ERO_UNIT, eg[0], eg[1], ft,
                    V2TerrainGen.ERO_STRENGTH, 0.5, 1.5);
            // ★ §7723：【只在【山地】启用侵蚀】，而不是全世界。
            //   门控 = LandformField.Sample.mtnPlusPeak()
            //        = belt * smoothstep(0.22, 0.60, elevation01)   ∈ [0,1]
            //   （由 LandformField.computeWeights 的 out[3]+out[4] 恒等式得出：
            //     mtn + peak = belt·m·(1-pk) + belt·m·pk = belt·m）
            //   它【正是】「这里是不是真山」的既有权威判据（D43），
            //   而且是连续的 ⇒ 平原 gate≈0（完全不侵蚀）、山地 gate≈1（全侵蚀）。
            //   ⚠ 它【不是】新旋钮 —— 阈值 0.22/0.60 早已存在于 LandformField。
            LandformField.Sample lf = LandformField.sample(x, z, worldSeedInt);
            double gate = lf.mtnPlusPeak();
            // ★ §7727：整体【下移】（ERO_SINK），让侵蚀只往下挖、不往上堆。
            //   不下移时正偏移会把地形抬过 SOFT_CAP_H ⟹ 被压成平台 ⟹ 图上出现平滑长曲线。
            double gateEff = ERO_GATE_FLOOR + (1.0 - ERO_GATE_FLOOR) * gate;
        hDet += dh * V2TerrainGen.ERO_AMP * gateEff - V2TerrainGen.ERO_SINK * gateEff;
        }

        c.hDetail = hDet;
        // ★ 原始模式：硬夹到 [1, maxY-2]，不用 softCap（软上限会把高处压平）
        double hCap = RAW_ELEV_MODE ? Math.min(hDet, maxY - 2) : softCap(hDet);
        // ⚠ 2026-09-13 修正（审计 D19）：补回旧实现三处都有的「贴岸低地保险」
        //   V2TerrainGen:133 / :171-172 / :282 都是  h < seaLevel + 1 ? seaLevel + 1 : h。
        // 新链路漏了它，而块级细节噪声（|d| < 1.45*DETAIL_AMP = 5.8 格）**没有海岸淡化**、
        // 直接加在 seaLevel 上 ⇒ elev < 268 m 的陆地列都可能落到海平面以下；
        // 与「ChunkProviderTalos2.fillLandColumnV2 不铺水」叠加就是**海面下的干坑**。
        // ⇒ 这是 D16-a 的前置条件：谁走陆地分支由 D16-a 决定，保证不低于海面由这里决定。
        // ★ 原始模式：不加海面保底（要让 elev<64 的陆地按真实值显示）
        if (!RAW_ELEV_MODE) {
            final int hFloor = Math.min(seaLevel + 1, maxY);
            if (hCap < hFloor) hCap = hFloor;
        }
        c.hCapped = hCap;
        c.h = clampY((int) Math.round(hCap), maxY);
        c.beach = BEACH_ENABLED && (c.h - seaLevel) <= BEACH_BLOCKS;
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
    /**
     * ★★★★★★★ 2026-10-08 统一：**改为恒等**（不再二次哈希）。
     *
     * <p><b>原实现是历史上最严重的 bug 之一</b>：它对 {@code worldSeedInt} 再哈希一次，
     * 于是同一世界里存在两套种子 ——
     * <pre>
     *   worldSeedInt          = 179054954   ← 地形骨架 / 群系 / 山带
     *   seedOf(worldSeedInt)  = 1556270468  ← 地形高度 / 方块海陆判定 / 洋流 / 气候
     * </pre>
     * 实测：两者在**同一点**的**海陆判定 48.1% 互相矛盾**
     * （群系说「山地」，而高度算出来是海底）。
     *
     * <p>现在恒等 ⟹ 13 个调用点自动对齐到 {@link TalosSeed#of}。
     * <p>保留本方法（而不是删除）是为了**不改调用点**；它现在是纯类型提升。
     * <p>⚠ 绝对不要再往里加哈希。
     */
    public static long seedOf(int worldSeedInt) {
        return com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed.widen(worldSeedInt);
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

    /**
     * <b>块级细节（blocks）。</b>
     *
     * <p><b>§7728 改为 Simplex fBm（原来是双线性 value noise）</b>：
     * 原实现用 {@link #vnoise}（value noise，虽然插值本身是 smoothstep 的），
     * 而【value noise 的晶格结构无法靠插值消除】—— 它在 {@code DETAIL_W = 220} 格
     * 的尺度上于 {@code /talosmap} 与游戏里表现为【肉眼可见的多边形边界】
     * （用户放大图确认：直线段 + 折角，尺度 200-400 格）。
     *
     * <p>改用 {@link DetailNoise#fbmRot}（Simplex + 每 octave 旋转黄金角，
     * 与侵蚀的细节层【同一套噪声】）⟹ <b>无晶格结构</b>。
     * 波长对齐原实现的两个八度（{@code DETAIL_W} 与 {@code DETAIL_W*0.37}）；
     * 振幅同样对齐（原为 {@code a + 0.45b}，两 octave 之和 ≈ ±1.45）。
     */
    static double detailBlocks(int x, int z, int seed) {
        double v = DetailNoise.fbmRot((long) seed ^ 0x51EDL, x, z, 2, DETAIL_W,
                                      DetailNoise.GOLDEN_ANGLE);
        return DETAIL_AMP * 1.45 * v;
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
