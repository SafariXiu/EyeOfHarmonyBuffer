/**
 * ★★★ <b>水文包 —— 注意：这里【没有】生产环境的河道。</b>
 *
 * <h2>本包唯一的类：{@link com.EyeOfHarmonyBuffer.sim.hydro.WaterField}</h2>
 * <p>它是 <b>Priority-Flood 填洼场</b>（湖 / 湿地），<b>不是河网</b>。
 * 它确实【算过】一套完整的河网（{@code flowAcc} / {@code riverWidthM} / {@code isRiver}），
 * 但那部分是 <b>死代码</b>（零消费者）—— 原因见
 * {@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion} 类头：「旧的 WaterField 在 12 km 窗口里算 D8 汇流，
 * 河长上限 = 窗口 = 12 km，{@code isRiver} = 0%」。
 *
 * <h2>★★★ 要改河道 / 河宽 / 干河道，请去这里</h2>
 * <pre>
 *   com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
 *     · chOrd[k] >= 0        —— 河道格判定
 *     · riverWidthBlocks(..) —— 河宽（W = WIDTH_W0 · sqrt(acc)）
 *     · wetness01(..)        —— 干河道
 *     · riverSurfaceBlocks(..) —— 水面高度（挖河用）
 * </pre>
 *
 * <h2>本包的唯一活的入口</h2>
 * <p>{@code WaterField.isWater(x,z,seed)} —— 「这一格是不是湖 / 湿地」。
 * 唯一消费者是 {@link com.EyeOfHarmonyBuffer.sim.atmos.PrecipField}:3293 的 {@code betaUsed = 1.0}（水面蒸发不受土壤限制）。
 *
 * <p>⚠ <b>历史上这里被误当成「水系」</b>，导致过两次错误量化（曾报「最大河宽 18 m」，
 * 而真实值是 60 格）。本文件就是为了防止那次错误重演。
 */
package com.EyeOfHarmonyBuffer.sim.hydro;