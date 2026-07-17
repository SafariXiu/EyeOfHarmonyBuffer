package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

/**
 * 表示世界中的环境气体类型。
 * <p>
 * 这个枚举用于在坐标位置上描述一种“环境状态”，而不是传统意义上会流动/存储的流体或气体。
 * 典型用途：
 * <ul>
 *     <li>由 {@link IGasEnvironmentProvider} 的机器在一定范围内提供某种环境气体类型。</li>
 *     <li>由 {@link IGasEnvironmentConsumer} 的机器声明自己工作时所需要的环境气体类型。</li>
 * </ul>
 *
 * 设计约定：
 * <ul>
 *     <li>{@link #NONE}：表示该位置没有被任何环境提供者影响，或者该机器不参与环境系统。</li>
 *     <li>其它枚举值（如 {@link #STABILIZATION}, {@link #MOIST}, {@link #ACID_STAGE}, {@link #TERRASOIL}）
 *         表示具体的特殊环境，用于驱动特殊配方、成长、结构要求等逻辑。</li>
 * </ul>
 *
 * 使用场景：
 * <ul>
 *     <li>在环境提供者机器中，从 {@code getProvidedEnvironmentType()} 返回一个具体枚举值。</li>
 *     <li>在环境需求者机器中，从 {@code getRequiredEnvironmentType()} 返回自己需要的枚举值。</li>
 *     <li>在环境检测逻辑中，通过 {@link GasEnvironmentHelper#getEnvironmentAt} 取得当前位置的环境类型。</li>
 * </ul>
 */

public enum GasEnvironmentType {
    NONE,
    STABILIZATION,
    MOIST,
    ACID_STAGE,
    TERRASOIL
}
