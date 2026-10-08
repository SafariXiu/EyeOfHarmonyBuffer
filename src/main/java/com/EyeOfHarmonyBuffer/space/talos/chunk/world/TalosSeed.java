package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import net.minecraft.world.World;

/**
 * 世界种子派生的【唯一权威】。
 *
 * <h3>★ 2026-10-08 统一（用户裁决：「让种子统一到一个接口统一调用 不能再出现这种问题了」）</h3>
 *
 * <p><b>为什么必须统一</b>：本会话实测发现，世界里同时存在【两套种子】：
 * <pre>
 *   worldSeedInt         = 179054954      ← 地形骨架 / 群系 / 山带 用
 *   SimTerrain.seedOf(..) = 1556270468    ← 地形高度 / 方块海陆判定 / 洋流 / 气候 用
 * </pre>
 * 两者在**同一点**给出完全不同的场 —— 实测 <b>48.1% 的海陆判定互相矛盾</b>
 * （群系说「山地」而高度是海底 63 格）。
 *
 * <p><b>调研发现的 5 个派生点</b>（现已全部收敛到本类）：
 * <ol>
 *   <li>{@code TalosSeed.of(world)} —— 官方入口（9 个调用点，已合规）</li>
 *   <li>{@code ClimatePreheat:34} 内联 {@code (int)(getSeed() & 0x7FFFFFFF)} —— <b>已改</b></li>
 *   <li>{@code WorldGenYuanShiDoubleConeCluster:96} 同上 —— <b>已改</b></li>
 *   <li>{@code CaveGenerator:163,236} 同上 —— <b>已改</b></li>
 *   <li>★ {@code SimTerrain.seedOf(int)} 二次哈希 —— <b>已改为恒等</b></li>
 * </ol>
 *
 * <h3>唯一规则</h3>
 * <pre>
 *   int worldSeedInt = (int) (world.getSeed() & 0x7FFFFFFFL);
 * </pre>
 * 掩码必须全世界一致（{@code & 0x7FFFFFFF} 保证 int 非负），否则同一维度会算出两个种子。
 *
 * <p><b>★ 不要再加第二个派生函数</b>：任何「再哈希一次」「加个 salt」「换个截断」
 * 都会让不同层描述两个世界。需要区分用途时，用<b>盐值参数</b>（{@link #salt}），
 * <b>不要</b>再造一个种子。
 */
public final class TalosSeed {

    private TalosSeed() {}

    /** ★ 唯一派生规则：{@code World.getSeed() → 非负 int}。 */
    public static int of(World world) {
        return ofLong(world.getSeed());
    }

    /** ★ 唯一派生规则（long 版）：{@code rawSeed → 非负 int}。 */
    public static int ofLong(long rawSeed) {
        return (int) (rawSeed & 0x7FFFFFFFL);
    }

    /**
     * {@code int → long} 的**类型提升**（恒等，不改值）。
     *
     * <p>存在意义：很多下层 API 收 {@code long seed}（历史遗留）。
     * 用本方法做提升，语义上明确「只是类型转换，没有派生」。
     * <p>⚠ 绝对不要在这里做哈希 —— 那正是本项目出过的 bug（{@code SimTerrain.seedOf}）。
     */
    public static long widen(int worldSeedInt) {
        return (long) worldSeedInt;
    }

    /**
     * 用途盐值：同一世界种子下为**不同用途**派生互相独立的随机流。
     *
     * <p>⚠ 与「再造一个种子」的区别：本方法只用于**噪声/随机流的 salt**，
     * <b>不</b>用于地形/群系/气候的「世界身份」。后者必须一律用 {@link #of}。
     *
     * @param worldSeedInt 由 {@link #of} 得到的世界种子（**不要**传别的）
     * @param salt         用途常量（各调用点自定义的字面量）
     * @return 该用途的 64 位流种子
     */
    public static long salt(int worldSeedInt, long salt) {
        long h = worldSeedInt * 0x9E3779B97F4A7C15L + salt;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return h;
    }
}