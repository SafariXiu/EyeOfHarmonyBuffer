package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

/**
 * 环境气体提供者接口。
 * <p>
 * 实现该接口的 TileEntity/机器会在自己所在区块为中心的一定范围内
 * “提供”一种 {@link GasEnvironmentType} 环境，用于被其它需要环境的机器检测。
 *
 * 具体 AoE 规则由 {@link GasEnvironmentHelper} 统一管理：
 * <ul>
 *     <li>当前实现为：以 Provider 所在区块为中心，周围 7×7 区块都视为受其影响；</li>
 *     <li>当多个 Provider 的 AoE 重叠时，按 {@link GasEnvironmentType#priority} 选择最终生效环境；</li>
 *     <li>Provider 只需正确返回当前自身提供的类型，不关心范围与冲突细节。</li>
 * </ul>
 *
 * 生命周期要求：
 * <ul>
 *     <li>在 Tile 放置/第一次 Tick 时调用
 *         {@link GasEnvironmentHelper#registerProvider(IGasEnvironmentProvider, net.minecraft.world.World, int, int, int)}；</li>
 *     <li>在 Tile 移除/无效化时调用
 *         {@link GasEnvironmentHelper#unregisterProvider(IGasEnvironmentProvider, net.minecraft.world.World, int, int, int)}；</li>
 *     <li>当 {@link #getProvidedEnvironmentType()} 的返回值发生变化时，
 *         调用 {@link GasEnvironmentHelper#onProviderEnvironmentChanged(IGasEnvironmentProvider, net.minecraft.world.World, int, int, int)}。</li>
 * </ul>
 */

public interface IGasEnvironmentProvider {

    /**
     * 返回当前机器在自身位置所提供的环境气体类型。
     *
     * @return 当前提供的环境类型；若返回 {@link GasEnvironmentType#NONE}，
     *         代表此机器当前不向外提供任何环境（等价于没有 Provider 的情况）。
     */
    GasEnvironmentType getProvidedEnvironmentType();
}
