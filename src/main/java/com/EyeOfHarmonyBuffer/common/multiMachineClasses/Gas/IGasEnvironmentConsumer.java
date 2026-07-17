package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

/**
 * 环境气体需求者接口。
 * <p>
 * 实现该接口的 TileEntity/机器声明自己“工作时需要处于哪种环境气体类型”。
 * 在逻辑 Tick 中，可以通过 {@link GasEnvironmentHelper#checkConsumer} 自动检查环境是否满足，
 * 并在不满足时触发 {@link #onEnvironmentNotSatisfied(GasEnvironmentType, GasEnvironmentType)}。
 *
 * 适合在哪些类上实现：
 * <ul>
 *     <li>对周围环境有要求的化工设备、反应器、生长舱等。</li>
 *     <li>例如：只能在 STABILIZATION 环境下进行的高危反应器、需要 MOIST 环境的种植机等。</li>
 * </ul>
 *
 * 典型使用时机：
 * <ul>
 *     <li>在机器的 {@code onPostTick} / 自定义 Tick 方法中调用
 *         {@link GasEnvironmentHelper#checkConsumer(IGasEnvironmentConsumer, net.minecraft.world.World, int, int, int)}。</li>
 *     <li>若环境不满足，Helper 会调用 {@link #onEnvironmentNotSatisfied(GasEnvironmentType, GasEnvironmentType)}，
 *         由机器自己决定停机、报警、损坏或爆炸等行为。</li>
 * </ul>
 */

public interface IGasEnvironmentConsumer {

    /**
     * 返回该机器在正常工作时所“需要”的环境气体类型。
     *
     * @return 所需环境类型；
     *         若返回 {@link GasEnvironmentType#NONE} 或 {@code null}，
     *         表示该机器不依赖环境系统，不会触发任何环境检查。
     */
    GasEnvironmentType getRequiredEnvironmentType();

    /**
     * 当当前位置的实际环境类型不满足 {@link #getRequiredEnvironmentType()} 返回的需求时调用。
     * <p>
     * 默认实现为空，具体机器可以根据需要：
     * <ul>
     *     <li>强制停机、重置进度；</li>
     *     <li>对玩家/方块造成伤害或爆炸；</li>
     *     <li>输出告警信息、生成粒子效果等。</li>
     * </ul>
     *
     * @param required 该机器声明需要的环境类型（{@link #getRequiredEnvironmentType()} 的返回值）。
     * @param actual   当前坐标实际检测到的环境类型
     *                 （来自 {@link GasEnvironmentHelper#getEnvironmentAt(net.minecraft.world.World, int, int, int)}），
     *                 可能为 {@link GasEnvironmentType#NONE}。
     */
    default void onEnvironmentNotSatisfied(GasEnvironmentType required, GasEnvironmentType actual) {
    }
}
