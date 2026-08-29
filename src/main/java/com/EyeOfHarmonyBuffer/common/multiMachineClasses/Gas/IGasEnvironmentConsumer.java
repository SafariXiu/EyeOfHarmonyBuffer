package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

/**
 * 环境气体需求者接口。
 * <p>
 * 实现该接口的 TileEntity/机器声明自己“工作时需要处于哪种环境气体类型”。
 * 在逻辑 Tick 中，可以通过 {@link GasEnvironmentHelper#checkConsumer} 自动检查环境是否满足，
 * 并在不满足时触发 {@link #onEnvironmentNotSatisfied(GasEnvironmentType, GasEnvironmentType)}。
 *
 * 环境来源说明：
 * <ul>
 *     <li>实际环境类型由 {@link GasEnvironmentHelper} 综合多个 {@link IGasEnvironmentProvider}
 *         的 AoE 影响和优先级规则计算得出；</li>
 *     <li>环境需求者本身不需要关心 Provider 存在的位置和数量。</li>
 * </ul>
 */

public interface IGasEnvironmentConsumer {

    GasEnvironmentType getRequiredEnvironmentType();

    default void onEnvironmentNotSatisfied(GasEnvironmentType required, GasEnvironmentType actual) {
    }
}
