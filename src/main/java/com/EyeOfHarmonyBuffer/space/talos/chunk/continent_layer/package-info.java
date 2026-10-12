/**
 * ★★★★★ <b>大陆层 —— 海陆分布 / 地形 / 河道的【家】</b>
 *
 * <p>本包是全项目最容易认错的地方，因为有三个名字相近但职责不同的类。
 * <b>下面这张表是权威的</b>（2026-10-11 逐层核实过调用链）。
 *
 * <h2>一、谁是谁</h2>
 * <table border="1">
 *   <tr><th>类</th><th>职责</th><th>关键 API</th></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandField}</td>
 *       <td><b>海陆分布 + 岸线剖面</b>（「B 方案」连续场）</td>
 *       <td>{@code landScore}（陆度）、{@code isLand}、{@code signedCoastDistCF}、
 *           {@code coastProfileCF}、{@code height0}、{@code height}</td></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion}</td>
 *       <td>★ <b>LEM 演化 + 【河道系统】</b></td>
 *       <td>★ {@code chOrd}（河道格）、{@code riverWidthBlocks}（河宽）、
 *           {@code wetness01}（干河道）、{@code riverSurfaceBlocks}（水面）、{@code baseHeight}</td></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid}</td>
 *       <td><b>气候层的地形入口</b>（含 {@code ELEV_WITHOUT_LEM} 分岔）+ 配置指纹</td>
 *       <td>{@code height}、{@code isLand}、{@code configStamp}</td></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField}</td><td>造山（窄脊线带 × 板块汇聚度）</td>
 *       <td>{@code orogeny01}</td></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath}</td><td>板块数学（唯一名副其实的「板块」）</td><td>—</td></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone}</td><td>极区处理</td><td>—</td></tr>
 *   <tr><td>{@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassType}</td><td>气团类型枚举</td><td>—</td></tr>
 * </table>
 *
 * <h2>二、★★★ 高度有四个入口 —— 它们的关系是【有意的分岔】</h2>
 * <pre>
 *                TalosLandField（唯一的地形生成器）
 *                         |
 *         +---------------+----------------+
 *         |                                |
 *   [含 LEM]                          [不含 LEM]
 *   TalosLandField.height             TalosLandErosion.baseHeight
 *         |                                |
 *   +-----+------+                  NoiseContinentGrid.height
 *   |            |                  (ELEV_WITHOUT_LEM = true)
 *   渲染          水系河床                  |
 *   ({@link com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain})  ({@link com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion})   气候/降水/湖
 *   |            |                     ({@link com.EyeOfHarmonyBuffer.sim.atmos.PrecipField} / {@link com.EyeOfHarmonyBuffer.sim.hydro.WaterField})
 *   +-----+------+
 *         |
 *    ★ 实测差 0.08 m ⟹ 一致 ✓
 * </pre>
 *
 * <p><b>为什么分岔（{@code NoiseContinentGrid:48-80} 逐字）</b>：
 * 2026-10-08 的性能修复 —— 气候层的调用面会跨几十个 LEM 瓦片（冷瓦片 5~9 秒），
 * 实测「单行 500 次 {@code mmPerDay} 超过 6 分钟」。
 * 而<b>物理上气候层也不需要 LEM</b>：雨影由<b>大尺度</b>地形决定（{@code OROGEN_W = 90 km}），
 * LEM 的河谷是<b>小尺度</b>细节。
 *
 * <h2>三、⚠ 认错系统的历史（本文件存在的原因）</h2>
 * <p>2026-10-11 曾两次把<b>不同口径的量</b>当成<b>同一个量的两个实现</b>：
 * <ol>
 *   <li>把 {@code WaterField.isWater}（湖/湿地）当成「河道」去对比 ⟹ 误报「两套水系不一致」；</li>
 *   <li>把 {@code baseHeight}（不含 LEM）当成 {@code TalosLandField.height}（含 LEM）去对比
 *       ⟹ 误报「地形差 2425 m」。</li>
 * </ol>
 * <p><b>⟹ 规则：读到任何「高度 / 水面 / 河道」的 API，先确认它属于上图的哪一支。</b>
 */
package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;