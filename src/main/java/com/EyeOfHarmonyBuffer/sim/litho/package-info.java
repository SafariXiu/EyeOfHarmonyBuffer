/**
 * ★★★ <b>岩石圈包 —— {@code PlateField} 是【门面】，不是板块模型。</b>
 *
 * <h2>本包唯一的类：{@link com.EyeOfHarmonyBuffer.sim.litho.PlateField}</h2>
 * <p>类名里有「板块」，但 §567 之后它 <b>不再生成任何地形</b>，只剩三件事：
 * <ol>
 *   <li><b>委托</b>：{@code elevation} / {@code elevationWithCell} / {@code isLand} /
 *       {@code isLandWithCell} 全部转给 {@code NoiseContinentGrid}（→ {@code TalosLandField}）；</li>
 *   <li><b>度量钩子</b>：{@code LandMask} / {@code ElevSource}（生产恒 {@code null}）；</li>
 *   <li><b>海岸度量的采样图案</b>。</li>
 * </ol>
 *
 * <h2>★★★ 真正的地形本体在哪</h2>
 * <pre>
 *   {@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandField}       —— 海陆分布 + 岸线剖面（landScore / coastProfileCF）
 *   {@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion}     —— LEM 演化 + 河道系统
 *   {@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField}       —— 造山
 *   {@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid}   —— 气候层的地形入口（含 ELEV_WITHOUT_LEM 分岔）
 *   {@link com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain}           —— 区块列合成（地形生成的组装点）
 * </pre>
 *
 * <p>⚠ {@code PlateField.PLATE_CELL = 2,400,000} 是 <b>僵尸常量</b> ——
 * 新地形路不用它，只作为既有调用点的形参保留（其 javadoc 已说明）。
 */
package com.EyeOfHarmonyBuffer.sim.litho;