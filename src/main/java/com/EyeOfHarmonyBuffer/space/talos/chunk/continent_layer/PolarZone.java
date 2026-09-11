package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.PeriodicNoise;

/**
 * 极地结构的**唯一口径**：实心冰盖 / 浮冰带 / 虚拟墙 / 固定急流 / 极地冷带。
 *
 * <h3>三件套（2026-09 用户定案）</h3>
 * <ol>
 *   <li><b>实心冰盖</b>（强制成陆，{@link #CORE_BAND}）：视觉芯，同时是极地海盆的极侧岸线；</li>
 *   <li><b>浮冰带</b>（{@link #FLOE_BAND}~{@link #CORE_BAND}）：极地环流住的地方 ——
 *       <b>强制成海</b>（见下一节），海面铺浮冰，里面跑一条沿 X 无限的固定纬向急流；</li>
 *   <li><b>虚拟墙</b>（{@link #WALL_OUTER}~{@link #WALL_INNER}）：ψ=0 的硬墙，
 *       <b>不写进海陆掩码</b>、玩家撞不到，只把水挡住。</li>
 * </ol>
 * 五处落地：{@code NoiseContinentGrid}（冰盖+海盆）、{@code BarotropicGyre}（墙掩码）、
 * {@code RelaxedClimate}（急流）、{@code ThermalForcing}（冷带）、
 * {@code ChunkProviderTalos2}（浮冰方块）。全部调用本类，没有第二份阈值。
 *
 * <h3>为什么浮冰带必须"强制成海"（P236 实测教训）</h3>
 * 大陆是纯噪声给的，**不认纬度**。CORE_BAND 从 0.82 收到 0.96 之后，bandD 0.90~0.96
 * 在很多经度上本来就是陆地（实测 tileX=0 那一窗 bandD 0.88~0.92 的陆占比是 **100%**）——
 * 于是"浮冰带 / 极地环流 / 虚拟墙"三件套**根本没有水可谈**：墙加了但 43 个粗格里有 0 个
 * 是新挡住的海格，RMS 只动到小数点后第 6 位。所以浮冰带用 {@link #floeSeaWeight} 把残差压下去，
 * 保证那一圈**处处是海**；两端权重为 0 ⇒ 与自然海陆的接缝仍由残差决定（看起来是冰架边缘）。
 *
 * <h3>纬度学</h3>
 * {@code bandD = 到最近 1M 倍数的距离 / 500k}：z=0 处 0（赤道）、z=500k 处 1（**极点**）。
 * 每个 1M 周期只有**一个**极点，极点两侧各一条"翼"；用户说的"南北极"就是同一条极点线
 * 两侧的两翼。两翼各自被自己的虚拟墙封死 ⇒ 两个互不相通的极地海盆（定案的选择）。
 *
 * <h3>冰缘噪声：为什么是"整体平移"而不是"给阈值加噪声"</h3>
 * 早先的做法是 {@code be = bandD + A·noise(x,z)}。它有两个致命副作用（都被 P235 实测抓到）：
 * (a) 噪声在 z 上的梯度与 bandD 的梯度同量级 ⇒ 等值线**局部不再单调** ⇒ 冰盖会裂成几段、
 *     面积随噪声实现大幅摆动（实测 +27.6% / −13.6%）；
 * (b) 会让"看得见的冰缘"与"看不见的墙"互相穿插。
 * 现在改成**在 z 上刚性平移整个纬度图案**：{@code band(x,z) = rawBand(z − shift(x))}，
 * 其中 {@code shift(x)} 只依赖 x、零均值。于是
 *   · rawBand 对 z 严格单调 ⇒ 每条带在每一列都是**一段连续区间**，不可能有洞；
 *   · 平移不改变任何一条带的**宽度** ⇒ 面积按构造守恒（栅格相位差见 P235 [2] 的多相位平均）；
 *   · 墙用**不带平移**的 {@link #rawBand}，与冰缘之间留 {@link #WALL_INNER}→{@link #FLOE_BAND}
 *     的安全隙（0.87 vs 0.90，而最大平移 11km = 0.022）⇒ 墙里永远不会有浮冰。
 */
public final class PolarZone {

    private PolarZone() {}

    // ================= 阈值（bandD，0=赤道 1=极点）=================
    // 全部取 0.01 的整数倍 = 每条带在 z 上的宽度都是 5km 的整数倍（见 P235 [2] 的相位分析）。

    /** 实心冰盖外缘（每翼 20km）：{@code be > CORE_BAND} 残差抬高 ⇒ 强制成陆。 */
    public static double CORE_BAND = 0.96;
    /** 浮冰带外缘 = **极区外缘**（用户定案 0.90；每翼 30km 留给浮冰带）。 */
    public static double FLOE_BAND = 0.90;
    /**
     * 虚拟墙极侧边缘。到 {@link #FLOE_BAND} 留 0.04 = 20km 安全隙，
     * 必须 > 最大冰缘平移 {@link #EDGE_SHIFT}=15km，否则浮冰会平移进墙里。
     */
    public static double WALL_INNER = 0.86;
    /** 虚拟墙赤道侧边缘。厚度 (0.86−0.82)·500km = 20km = 4 个窗口行 ≈ 2.1 个求解器格。 */
    public static double WALL_OUTER = 0.82;
    /** 极地冷带起点：这里权重 0，到 {@link #FLOE_BAND} 升到 1（与墙的外缘对齐）。 */
    public static double COLD_BAND = 0.82;
    /**
     * 冰缘在 z 上的刚性平移幅度上限（block，零均值）。±15km ≈ ±0.03 bandD。
     * 两个八度叠加后实际用掉约 40%（实测跨度 −4.8k~+3.4k @11km 设置），
     * 所以留够上限、靠安全隙兜底，而不是假设它一定用满。
     */
    public static double EDGE_SHIFT = 15_000.0;
    /** 强制成陆 / 成海的强度（自然残差量级 ±0.5，1.5 足够压过）。 */
    public static double ICE_FORCE = 1.5;
    /** 极地急流峰值（m/s）。真实 ACC 核心 0.3~0.5；生产洋流 RMS u≈0.30。 */
    public static double JET_PEAK = 0.35;
    /**
     * 急流符号。+1 = 沿 +X。依据：{@code profileP0} 在 b≈0.93 处是 westerly，
     * 风生环流应与盛行风同向；两翼同号是因为 profileP0 只是 b 的函数（无半球因子）。
     * 渲染后若发现急流与邻带反向，改这一个数即可（唯一的符号来源）。
     */
    public static double JET_SIGN = 1.0;

    // ================= 冰缘平移场 =================

    /** 冰缘噪声盐（与 warp / medNoise / 定常波各盐都不同）。 */
    private static final long EDGE_SALT = 0x7A10_5EEDL;
    /** 主波长：沿 X 拉长（与 X_STRETCH 同取向）⇒ 冰缘是大尺度海湾而不是锯齿。 */
    private static final double EDGE_WAV_X = 180_000.0;
    /** 次波长（八度），让冰缘有细节而不是一条正弦。 */
    private static final double EDGE_WAV_X2 = 62_000.0;
    /** 次八度振幅比。 */
    private static final double EDGE_OCT2 = 0.5;

    /** 裸纬度带（无平移）：{@code z=0→0}（赤道），{@code z=500k→1}（极点），对 z 严格单调。 */
    public static double rawBand(int z) {
        return ClimateLatitudes.getDistanceToCenter(z) / (double) ClimateLatitudes.MAX_D;
    }

    /**
     * 冰缘平移量（block，零均值，**只依赖 x**）。
     * 只依赖 x 是刻意的：它保证 {@link #band} 对 z 仍然严格单调（见类注释）。
     */
    public static double edgeShift(int x, int worldSeedInt) {
        double n1 = edgeNoise(EDGE_SALT + worldSeedInt, x, EDGE_WAV_X);
        double n2 = edgeNoise(EDGE_SALT + 31L + worldSeedInt, x, EDGE_WAV_X2);
        return EDGE_SHIFT * (n1 + EDGE_OCT2 * n2) / (1.0 + EDGE_OCT2);
    }

    /** z=0 且 wavZ=1 ⇒ value2XZ 退化成纯 x 的 1D 值噪声（v=fz=0，见其实现）。 */
    private static double edgeNoise(long seed, int x, double wav) {
        return PeriodicNoise.value2XZ(seed, x, 0.0, wav, 1.0) * 2.0 - 1.0;
    }

    /**
     * 带冰缘平移的纬度带：{@code band(x,z) = rawBand(z − shift(x))}。
     * <b>极区几何的唯一自变量</b>：冰盖、浮冰、冷带都用它（墙用 {@link #rawBand}）。
     */
    public static double band(int x, int z, int worldSeedInt) {
        return rawBand(z - (int) Math.round(edgeShift(x, worldSeedInt)));
    }

    // ================= 判据（只接受已算好的 band，不重采样噪声）=================

    /** 实心冰盖（强制成陆）。 */
    public static boolean isCore(double be) {
        return be > CORE_BAND;
    }

    /** 浮冰带（极地海盆；海面铺浮冰）。 */
    public static boolean isFloeOcean(double be) {
        return be > FLOE_BAND && be <= CORE_BAND;
    }

    /** 极区（冰盖 + 浮冰带）：出图与验收统计用。 */
    public static boolean isPolar(double be) {
        return be > FLOE_BAND;
    }

    /**
     * 虚拟墙格。**用裸纬度**（不带冰缘平移）：平移只依赖 x，但冰缘判据是比较运算，
     * 一旦带上平移，墙与浮冰带在个别列上就会互相穿插；裸纬度保证每一列的墙都是
     * 严格连续的一段，且与浮冰带之间永远留着安全隙。
     */
    public static boolean isWallCell(double rawB) {
        return rawB > WALL_OUTER && rawB <= WALL_INNER;
    }

    /**
     * 浮冰带"强制成海"的权重 [0,1]：梯形（两端 0、中段 1，两侧 smoothstep 过渡）。
     * 两端为 0 是必须的 —— 否则会在冰盖那一侧和赤道侧各留下一道高度场的硬台阶。
     */
    public static double floeSeaWeight(double be) {
        double t = (be - FLOE_BAND) / (CORE_BAND - FLOE_BAND);
        if (t <= 0.0 || t >= 1.0) {
            return 0.0;
        }
        final double ramp = 0.22;                 // 两侧各 22% 做过渡，中段 56% 满幅
        double w = Math.min(Math.min(t / ramp, (1.0 - t) / ramp), 1.0);
        return w * w * (3.0 - 2.0 * w);
    }

    /**
     * 固定纬向急流（m/s）。**严格 {@code u=u(b)}、{@code v≡0}**：对 X 求导恒为 0
     * ⇒ 散度不变，注入 {@code fu} 不破坏平流。
     *
     * 剖面 = 浮冰带内一个 {@code sin²(πt)} 包：两端精确为 0（不在带外制造台阶），
     * 带中央 = {@link #JET_PEAK}。之所以不用 smoothstep：smoothstep 是**单调**的，
     * 那会得到"靠极点最强"的斜坡而不是一条急流（P235 [3a] 实测峰值只有 0.324 且位置在带边）。
     * 5km 的行距下，浮冰带 6 行正好落在 t = 0,1/6,…,1 ⇒ 采样峰值**精确**等于 JET_PEAK。
     */
    public static double jetU(double rawB) {
        double t = (rawB - FLOE_BAND) / (CORE_BAND - FLOE_BAND);
        if (t <= 0.0 || t >= 1.0) {
            return 0.0;
        }
        return JET_SIGN * JET_PEAK * (0.5 - 0.5 * Math.cos(2.0 * Math.PI * t));
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
