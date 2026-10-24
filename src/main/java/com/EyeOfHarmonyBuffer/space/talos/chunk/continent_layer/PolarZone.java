package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;

/**
 * 极地系统的**唯一口径**。2026-09 最终定案后，它只剩三件事：
 * <ol>
 *   <li><b>虚拟墙</b>（{@link #WALL_OUTER}~{@link #WALL_INNER}）：ψ=0 的求解器掩码，挡经向交换。
 *       <b>不写进海陆</b>、玩家撞不到、地形上看不出任何东西；</li>
 *   <li><b>极地冷带</b>（{@link #coldWeight}）：把极区的 SST 目标压到 −1 —— 只改温度；</li>
 *   <li><b>海冰</b>（{@link #isPolar}）：极区**真正的海面**上铺 {@code Blocks.ice}。</li>
 * </ol>
 * <b>对地形的影响 = 0，对洋流的影响 = 0。</b>
 *
 * <h3>这里曾经有过、现在全部删除的东西（每一条都是"用一条纬线跟自然抢地盘"）</h3>
 * <pre>
 *   · 强制成陆（造"实心冰盖"）      → 凭空造出一块横贯 500km 的陆地（P240 实测）
 *   · 强制成海（造"极地海盆"）      → 从大陆上凿掉七成，形成一条笔直的护城河
 *   · 极地水道（挖一条连通水脉）    → 窄了看不见、宽了就是护城河；两端都不讨好
 *   · 固定纬向急流                  → 规定流，不是算出来的
 *   · 沿岸环流                      → 同上
 *   · CORE_BAND（"冰盖芯"标记）     → 冰盖不强制之后，这条纬线没有任何物理依据
 * </pre>
 * 三条守卫盯着"不得复活"：P220 的 <b>U13</b>（地形链不得引用本类）、
 * P215 的 <b>T7</b>（本类不得暴露任何"规定洋流"的方法）、P240（极地地形必须是纯噪声）。
 *
 * <h3>纬度学</h3>
 * {@code bandD = 到最近 1M 倍数的距离 / 500k}：z=0 处 0（赤道）、z=500k 处 1（**极点**）。
 * 每个 1M 周期只有**一个**极点，极点两侧各一条"翼"；"南北极"就是同一条极点线两侧的两翼。
 *
 * <h3>冰缘平移</h3>
 * {@link #band} 在 z 上**刚性平移**整个纬度图案（{@link #EDGE_SHIFT}，只依赖 x、零均值）：
 * rawBand 对 z 严格单调 ⇒ 每条带在每一列都是连续区间；平移不改带宽 ⇒ 面积按构造守恒。
 * 它现在只影响**海冰边缘与出图标记**（地形不参与），所以"冰缘蜿蜒、地形不动"是自然结果。
 */
public final class PolarZone {

    private PolarZone() {}

    /**
     * **渲染约定（2026-09 教训，三个出图处都必须遵守）**：
     * 极地带只能做**半透明标记**叠加在**真实海陆**之上，**绝不允许**用几何判据直接决定颜色。
     *
     * 踩过的坑：{@code CommandTalosMap.polarColor} 与探针 P237/P238 都写过
     * {@code if (isFloeBand(be)) return 淡青;} —— 而这只是"纬度落在带里"，
     * 完全没问海陆。于是浮冰带里的真实陆地被整片刷成了海色，用户据此判断"极地还是一条干净的带"，
     * 而真实世界早就变了。**图层骗人比世界错了更坏**，因为它让人无法判断。
     */
    public static final int RENDER_RULE_VERSION = 4;

    // ---------------- 渲染叠加码：**"这一格属于哪个极地区域"的唯一判据** ----------------

    public static final int OV_NONE = 0;
    public static final int OV_COLD = 1;
    public static final int OV_FLOE_SEA = 2;
    public static final int OV_FLOE_LAND = 3;
    public static final int OV_WALL = 4;

    /**
     * 一格在出图里该被标成什么。**三个出图处（{@code /talosmap polar}、探针 P237、P238）都必须走这里。**
     * 带由本类判，海陆由调用方传入（唯一判据在 {@link NoiseContinentGrid#isLand}）。
     * 于是"海上的浮冰"和"陆上的雪盖"是两个不同的码，谁也盖不住谁。
     */
    public static int overlayCode(double be, double rawB, boolean land) {
        if (isWallCell(rawB)) {
            return OV_WALL;
        }
        if (isFloeBand(be)) {
            return land ? OV_FLOE_LAND : OV_FLOE_SEA;
        }
        return coldWeight(be) > 0.0 ? OV_COLD : OV_NONE;
    }

    // ================= 阈值（bandD，0=赤道 1=极点）=================

    /** 极区外缘（每翼 50km）：{@code be > FLOE_BAND} 即"极地"，海面铺海冰、冷带满幅。 */
    public static double FLOE_BAND = 0.90;
    /** 浮冰带内缘，仅用于出图分层（海冰本身铺满整个极区）。 */
    public static double ICE_INNER_BAND = 0.96;
    /**
     * 虚拟墙极侧边缘。墙是纬向带，**不得与任何地形/水体重叠使用**（本类已不再改地形，所以这条自动成立）。
     * 到 {@link #FLOE_BAND} 留 0.04 = 20km 安全隙，必须 > 最大冰缘平移 {@link #EDGE_SHIFT}=15km。
     */
    public static double WALL_INNER = 0.86;
    /** 虚拟墙赤道侧边缘。厚度 (0.86−0.82)·500km = 20km = 4 个窗口行 ≈ 2.1 个求解器格。 */
    public static double WALL_OUTER = 0.82;
    /** 极地冷带起点：这里权重 0，到 {@link #FLOE_BAND} 升到 1。墙的外缘与它对齐。 */
    public static double COLD_BAND = 0.82;
    /**
     * 冰缘在 z 上的刚性平移幅度上限（block，零均值）。±15km ≈ ±0.03 bandD。
     * 两个八度叠加后实际用掉约 40%，所以留够上限、靠安全隙兜底。
     */
    public static double EDGE_SHIFT = 15_000.0;

    // ================= 冰缘平移场 =================

    /** 冰缘噪声盐（与 warp / medNoise / 定常波各盐都不同）。 */
    private static final long EDGE_SALT = 0x7A10_5EEDL;
    /** 主波长：沿 X 拉长（与 X_STRETCH 同取向）⇒ 冰缘是大尺度海湾而不是锯齿。 */
    private static final double EDGE_WAV_X = 180_000.0;
    /** 次波长（八度），让冰缘有细节而不是一条正弦。 */
    private static final double EDGE_WAV_X2 = 62_000.0;
    /** 次八度振幅比。 */
    private static final double EDGE_OCT2 = 0.5;

    /**
     * 裸纬度带（无平移）：{@code z=0→0}（赤道），**极点→1**，对 z 严格单调。
     *
     * <p>★★ 2026-09-18 顶死一套（第 6 段）—— <b>本函数原先名不副实</b>：
     * 实现是 {@code ClimateLatitudes.getDistanceToCenter(z) / ClimateLatitudes.MAX_D}，
     * 那是**旧栈 1M 契约的「几何带索引」**，在一个 40M 契约周期里会出现 **20 条等距带** ——
     * 正是审计 D72 实测到的那 20 条假冰带（纬度 +4.05, +13.05, +22.05, …，
     * 连最冷月海温 **+25.55 C** 的海面都被刷成冰，见 `ChunkProviderTalos2` 的记账）。
     *
     * <p>D72 只改掉了 `ChunkProviderTalos2` 的**物理**判据（改成按海表温度判海冰），
     * 却没人更新**渲染** ⇒ `/talosmap` 的 polar 层至今仍在画那 20 条假冰带。
     * <b>图层骗人比世界错了更坏。</b>
     *
     * <p>现在统一到世界契约 {@code WorldContract.bandD}（D1 之后：0 = 两条赤道、1 = 两个极点），
     * 于是本函数的**实现终于与它自己的文档一致**；下面的阈值也自动获得合理的纬度含义：
     * {@code WALL_OUTER = 0.82} ⇒ 73.8 度，{@code FLOE_BAND = 0.90} ⇒ 81 度，
     * {@code ICE_INNER_BAND = 0.96} ⇒ 86.4 度。
     */
    public static double rawBand(int z) {
        return com.EyeOfHarmonyBuffer.sim.world.WorldContract.bandD(z);
    }

    /** 冰缘平移量（block，零均值，**只依赖 x**）：只依赖 x 才能保证 {@link #band} 对 z 仍严格单调。 */
    public static double edgeShift(int x, int worldSeedInt) {
        double n1 = edgeNoise(EDGE_SALT + worldSeedInt, x, EDGE_WAV_X);
        double n2 = edgeNoise(EDGE_SALT + 31L + worldSeedInt, x, EDGE_WAV_X2);
        return EDGE_SHIFT * (n1 + EDGE_OCT2 * n2) / (1.0 + EDGE_OCT2);
    }

    /** z=0 且 wavZ=1 ⇒ value2XZ 退化成纯 x 的 1D 值噪声。 */
    private static double edgeNoise(long seed, int x, double wav) {
        return PeriodicNoise.value2XZ(seed, x, 0.0, wav, 1.0) * 2.0 - 1.0;
    }

    /**
     * 带冰缘平移的纬度带：{@code band(x,z) = rawBand(z − shift(x))}。
     * 海冰、冷带、渲染标记用它（墙用 {@link #rawBand}）。
     */
    public static double band(int x, int z, int worldSeedInt) {
        return rawBand(z - (int) Math.round(edgeShift(x, worldSeedInt)));
    }

    // ================= 判据 =================

    /** 浮冰带的**出图分层**用内圈（{@code FLOE_BAND < be <= ICE_INNER_BAND}）。 */
    public static boolean isFloeBand(double be) {
        return be > FLOE_BAND && be <= ICE_INNER_BAND;
    }

    /** 极区：{@code be > FLOE_BAND}。海冰只铺在真正的海上。 */
    public static boolean isPolar(double be) {
        return be > FLOE_BAND;
    }

    /**
     * 虚拟墙格。**用裸纬度**（不带冰缘平移）：平移只依赖 x，但冰缘判据是比较运算，
     * 一旦带上平移，墙与海冰标记在个别列上就会互相穿插；裸纬度保证每一列的墙都是
     * 严格连续的一段，且与极区之间永远留着安全隙。
     */
    public static boolean isWallCell(double rawB) {
        return rawB > WALL_OUTER && rawB <= WALL_INNER;
    }

    /**
     * 极地冷带权重 [0,1]：{@link #COLD_BAND} 处 0，{@link #FLOE_BAND} 及以内 1。
     * 用带平移的 {@code be} 以便冷舌边缘与冰缘同步蜿蜒（smoothstep 两端导数为 0，
     * 不会在冷带边缘给气压场制造折角）。
     */
    public static double coldWeight(double be) {
        if (be <= COLD_BAND) {
            return 0.0;
        }
        if (be >= FLOE_BAND) {
            return 1.0;
        }
        double t = (be - COLD_BAND) / (FLOE_BAND - COLD_BAND);
        return t * t * (3.0 - 2.0 * t);
    }
}
