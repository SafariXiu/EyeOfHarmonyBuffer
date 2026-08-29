package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

/**
 * 表示世界中的环境气体类型。
 * <p>
 * 这个枚举用于在坐标位置上描述一种“环境状态”，而不是传统意义上会流动/存储的流体或气体。
 * 典型用途：
 * <ul>
 *     <li>由 {@link IGasEnvironmentProvider} 的机器在一定范围内提供某种环境气体类型；</li>
 *     <li>由 {@link IGasEnvironmentConsumer} 的机器声明自己工作时所需要的环境气体类型。</li>
 * </ul>
 *
 * 设计约定：
 * <ul>
 *     <li>{@link #NONE}：表示该位置没有被任何环境提供者影响，或者该机器不参与环境系统。</li>
 *     <li>其它枚举值（如 {@link #STABLE}, {@link #HUMID}, {@link #ACRID}, {@link #XRANITE}）
 *         表示具体的特殊环境，用于驱动特殊配方、成长、结构要求等逻辑。</li>
 * </ul>
 *
 * 冲突与优先级：
 * <ul>
 *     <li>同一片区域可能被多个 Provider 影响；</li>
 *     <li>当多个环境类型叠加时，将按 {@link #priority} 值选择“优先级最高”的那一个作为最终环境；</li>
 *     <li>数值越大，优先级越高。</li>
 * </ul>
 */

public enum GasEnvironmentType {

    NONE(0),
    STABLE(10),
    HUMID(20),
    ACRID(30),
    XRANITE(40);

    /**
     * 环境优先级，数值越高，优先级越高。
     * <p>
     * 用于在同一区域被多个 Provider 影响时决策最终生效的环境类型。
     */
    public final int priority;

    GasEnvironmentType(int priority) {
        this.priority = priority;
    }
}
