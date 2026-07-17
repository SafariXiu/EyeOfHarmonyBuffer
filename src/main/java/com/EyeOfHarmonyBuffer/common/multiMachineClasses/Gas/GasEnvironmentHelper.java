package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 环境气体检测辅助类。
 * <p>
 * 提供静态方法用于：
 * <ul>
 *     <li>在世界中查询某个坐标当前的环境气体类型；</li>
 *     <li>对实现了 {@link IGasEnvironmentConsumer} 的机器执行一次“环境是否满足”的检查。</li>
 * </ul>
 *
 * 当前实现说明：
 * <ul>
 *     <li>{@link #getEnvironmentAt(net.minecraft.world.World, int, int, int)}：
 *         目前仅检查该坐标上的 TileEntity 是否实现了 {@link IGasEnvironmentProvider}，
 *         若是则直接返回其 {@code getProvidedEnvironmentType()}；否则返回 {@link GasEnvironmentType#NONE}。</li>
 *     <li>未来可以在这里扩展更复杂的逻辑，例如：
 *         <ul>
 *             <li>以某个 Provider 为中心扩展 XYZ ±N 格的范围；</li>
 *             <li>根据多个 Provider 合并环境类型并应用优先级规则；</li>
 *             <li>将 Provider 按区块索引以优化查询性能等。</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * 什么时候调用：
 * <ul>
 *     <li>在环境需求者机器的 Tick（例如 {@code onPostTick}）中调用
 *         {@link #checkConsumer(IGasEnvironmentConsumer, net.minecraft.world.World, int, int, int)}，
 *         以定期检查环境是否满足要求。</li>
 *     <li>也可以在调试或 Waila / The One Probe HUD 展示中调用 {@link #getEnvironmentAt} 查看当前环境类型。</li>
 * </ul>
 */

public final class GasEnvironmentHelper {

    private GasEnvironmentHelper() {}

    /**
     * 获取指定世界中某个坐标当前的环境气体类型。
     * <p>
     * 当前实现：
     * <ul>
     *     <li>若该坐标上的 TileEntity 实现了 {@link IGasEnvironmentProvider}，则返回其提供的环境类型；</li>
     *     <li>否则返回 {@link GasEnvironmentType#NONE}，表示此处没有任何环境提供者影响。</li>
     * </ul>
     *
     * @param world 世界对象
     * @param x     方块 X 坐标
     * @param y     方块 Y 坐标
     * @param z     方块 Z 坐标
     * @return 当前坐标的环境气体类型；若无提供者则为 {@link GasEnvironmentType#NONE}
     */
    public static GasEnvironmentType getEnvironmentAt(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGasEnvironmentProvider) {
            return ((IGasEnvironmentProvider) te).getProvidedEnvironmentType();
        }
        return GasEnvironmentType.NONE;
    }

    /**
     * 对一个环境需求者执行一次“环境是否满足”的检查。
     * <p>
     * 检查流程：
     * <ol>
     *     <li>读取 {@link IGasEnvironmentConsumer#getRequiredEnvironmentType()}；
     *         若返回 {@code null} 或 {@link GasEnvironmentType#NONE}，则认为不参与环境系统，直接返回。</li>
     *     <li>调用 {@link #getEnvironmentAt(World, int, int, int)} 取得当前位置的实际环境类型。</li>
     *     <li>若实际环境类型与所需不一致，则调用
     *         {@link IGasEnvironmentConsumer#onEnvironmentNotSatisfied(GasEnvironmentType, GasEnvironmentType)}。</li>
     * </ol>
     *
     * 典型调用位置：
     * <ul>
     *     <li>在实现了 {@link IGasEnvironmentConsumer} 的机器的 Tick 方法中，
     *         例如 {@code onPostTick}，并且只在服务端(world.isRemote == false)调用。</li>
     * </ul>
     *
     * @param consumer 实现了 {@link IGasEnvironmentConsumer} 的机器实例（通常为 {@code this}）
     * @param world    世界对象
     * @param x        机器所在的 X 坐标
     * @param y        机器所在的 Y 坐标
     * @param z        机器所在的 Z 坐标
     */
    public static void checkConsumer(IGasEnvironmentConsumer consumer,
                                     World world, int x, int y, int z) {
        GasEnvironmentType required = consumer.getRequiredEnvironmentType();
        if (required == null || required == GasEnvironmentType.NONE) return;

        GasEnvironmentType actual = getEnvironmentAt(world, x, y, z);
        if (actual != required) {
            consumer.onEnvironmentNotSatisfied(required, actual);
        }
    }
}
