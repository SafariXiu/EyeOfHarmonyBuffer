/**
 * 已废弃的海陆层（待删除）。
 *
 * <p><b>状态（2026-10-23）</b>：本包用于收纳被 V3（{@code sim2.land.LandV3}）取代的旧海陆代码。
 * 目前是<b>空包 + 说明</b>，具体文件会在「引用清零」之后逐个迁入，避免一次性大规模改动的风险。
 *
 * <h3>迁入条件（每个文件必须同时满足）</h3>
 * <ol>
 *   <li>生产路径（{@code ChunkProviderTalos2}/{@code SimTerrain}/{@code PlateField}/
 *       {@code TalosLandErosion}/{@code SimClimate}）不再引用它；</li>
 *   <li>它不依赖旧包里的包私有成员（例如 {@code LandNoise.h01}），否则迁入即编译失败；</li>
 *   <li>构建通过，且 V3 路径的验收数字不变（接缝 ≤50 m/km、陆占 ~32%、峰值 5–7 km）。</li>
 * </ol>
 *
 * <h3>待迁入清单（按引用数从少到多）</h3>
 * <table>
 *   <caption>旧场引用统计（2026-10-23 扫描）</caption>
 *   <tr><th>类</th><th>外部引用</th><th>备注</th></tr>
 *   <tr><td>{@code LandField}（V2 本体）</td><td>0</td>
 *       <td><b>已迁入本包</b>（2026-10-23）</td></tr>
 *   <tr><td>{@code V2BiomeField}</td><td>23</td><td>生物群系，随 V2 一起废弃</td></tr>
 *   <tr><td>{@code NoiseContinentGrid}</td><td>27</td><td>旧陆海/海岸距离</td></tr>
 *   <tr><td>{@code V2BiomeSelect}</td><td>27</td><td>—</td></tr>
 *   <tr><td>{@code LandformField}</td><td>38</td><td>—</td></tr>
 *   <tr><td>{@code V2TerrainGen}</td><td>65</td><td>地形合成入口</td></tr>
 *   <tr><td>{@code TalosLandField}</td><td>155</td><td>旧 V8 海陆，最大的一块</td></tr>
 * </table>
 *
 * <h3>已完成</h3>
 * <ul>
 *   <li>{@code PlateField}：删除了全部 {@code LANDV2_ENABLED} 分支（4 处）与开关本身；</li>
 *   <li>{@code PlateField.LANDV3_ENABLED = true}、{@code LandV3.ENABLED = true}（V3 成为生产路径）；</li>
 *   <li>{@code OrographyField}：{@code isLand}/{@code coastDist} 改为经由
 *       {@code PlateField}/{@code LandV3}（消除"地形 V3、海岸旧场"的混合源）；</li>
 *   <li>{@code LandNoise.h01} 与 {@code LandNoise.mix} 改为 public（解除迁移的编译障碍）；</li>
 *   <li>{@code LandField} 迁入本包，全仓库外部引用 = 0；</li>
 *   <li>{@code NoiseContinentGrid} 迁入本包，外部引用 = 0
 *       （{@code PlateField} 改为 V3-only；{@code OrographyField} 的 land/orogeny/coast
 *       改由 {@code LandV3.height/uplift/coastDist} 提供）；</li>
 *   <li>{@code V2BiomeField} 迁入本包，外部引用 = 0
 *       （群系改为 {@code sim2.biome.TalosBiomeDefault} 的塔罗斯平原占位）；</li>
 *   <li>{@code TalosLandField} 迁入本包，外部引用 = 0
 *       （{@code TalosLandErosion}/{@code SimTerrain} 改用本地 shim + V3）。</li>
 * </ul>
 *
 * <p><b>剩余待迁</b>：{@code LandformField}（23 处）、{@code V2TerrainGen}（51 处）。
 *
 * <p><b>注意</b>：本包内的类不应被任何非废弃代码引用；一旦引用清零即可整包删除。
 */
package com.EyeOfHarmonyBuffer.sim2.land.deprecated;
