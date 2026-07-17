package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

/**
 * 环境气体提供者接口。
 * <p>
 * 实现该接口的 TileEntity/机器会在自己所在位置（以及之后你扩展的范围逻辑内）
 * “提供”一种 {@link GasEnvironmentType} 环境，用于被其它需要环境的机器检测。
 *
 * 适合在哪些类上实现：
 * <ul>
 *     <li>各种“发生器”、“环境控制器”、“场发生器”等主动改变周围环境的机器。</li>
 *     <li>例如：惰性/稳定化环境控制机、潮湿环境发生器、酸性阶段处理室、土壤化环境站等。</li>
 * </ul>
 *
 * 典型使用时机：
 * <ul>
 *     <li>机器工作、消耗能量/资源时，返回对应的环境类型；</li>
 *     <li>机器停止工作、缺资源时，可以返回 {@link GasEnvironmentType#NONE}，表示此时不再提供环境。</li>
 * </ul>
 *
 * 与其它组件的关系：
 * <ul>
 *     <li>{@link GasEnvironmentHelper} 会在 {@code getEnvironmentAt} 中检查某位置是否有实现本接口的 Tile。</li>
 *     <li>{@link IGasEnvironmentConsumer} 的机器会通过 Helper 查询当前位置的环境，并与自身需求比较。</li>
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
