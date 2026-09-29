package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import net.minecraft.world.World;

/**
 * 世界种子派生的**唯一入口**：{@code (int) (world.getSeed() & 0x7FFFFFFFL)}。
 *
 * <h3>为什么单独一个类</h3>
 * 这个掩码原先只写在一个地方（{@code TalosLandMask.getWorldSeedInt}）—— 值是对的，
 * 但**位置是错的**：TalosLandMask 属于已退役的旧海陆系统，于是每一个还需要种子的活代码
 * （新地形链的两处入口）都不得不 import 一个死系统的类。P220 的 U13 规则就是靠这一点
 * 把"活代码依赖旧系统"照出来的。
 * 把这一行搬到中立位置之后，活链与旧系统之间就没有任何引用。
 *
 * 掩码必须全世界一致（{@code & 0x7FFFFFFF} 保证 int 非负），否则同一维度会算出两个种子 ——
 * 这是"同一事实只许一处表达"的典型：见 P220 U11。
 */
public final class TalosSeed {

    private TalosSeed() {}

    /** World.getSeed() → 非负 int 种子。 */
    public static int of(World world) {
        return (int) (world.getSeed() & 0x7FFFFFFFL);
    }
}
