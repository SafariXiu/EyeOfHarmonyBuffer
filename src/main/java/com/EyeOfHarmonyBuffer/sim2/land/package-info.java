/**
 * 新海陆分布 v3 —— 全新一套（M1 内核）。
 *
 * <h3>为什么另起一包</h3>
 * <p>旧的 {@code TalosLandField / NoiseContinentGrid / OrographyField / LandformField}
 * 是为 125 km 的小世界打磨出来的补丁集合（H_GAIN、90 km 海岸剖面、高度派生 kappa），
 * 在地球尺度下全部失效。2026-10-21 用户裁决：<b>完全推翻，另起一套；接线完成后删旧类</b>。
 *
 * <h3>本包的边界</h3>
 * <ul>
 *   <li>只 import JDK + {@code LandContract}（不 import 任何旧海陆类，也不 import MC 类）。</li>
 *   <li>纯函数：同种子 + 同坐标 ⟹ 逐位相同；可多线程并发。</li>
 *   <li>无离线预计算、无全局栅格；唯一的缓存是「每种子定标阈值」。</li>
 * </ul>
 *
 * <h3>核心公式</h3>
 * <pre>
 *   t_c = soft-min(最近 3 个板块的厚度)          // σ=70 km
 *   F   = t_c − T_REF + 造山/海沟/裂谷 + 细节
 *   isLand = F &gt; 0
 *   h      = K_ISO · F
 * </pre>
 *
 * <h3>当前进度（M1）</h3>
 * <p>已实现 I1 {@code isLand} 与 I2 {@code height}；I3 距离、I4 kappa、I5 山地门在 M2/M3。
 * 接线（{@code PlateField} 门面）在 M4，之前生产端不会调用本包。
 *
 * <h3>实测（原型，JDK 25）</h3>
 * <p>40,000 km 窗口：陆占 33.2%、≥3,000 km 陆块 9 个、连通世界洋、
 * {@code isLand} 0.23 µs/次。
 
 * <h3>旧 V2 地形层已于 2026-10-24 【整层删除】</h3>
 *
 * <p>以下类不再是本仓库的一部分，任何改回旧实现的想法都不成立——
 * 它们没有源码、没有 class、也没有任何代码引用（全量 clean 构建验证通过）：
 *
 * <table border=1>
 *   <caption>被删除的旧类与它的接替者</caption>
 *   <tr><th>已删除</th><th>现在的接替者</th></tr>
 *   <tr><td>{@code TalosLandField}（V8 海陆本体，144 KB）</td>
 *       <td>{@link LandV3}（高度/抬升/海岸距离/大陆度）</td></tr>
 *   <tr><td>{@code NoiseContinentGrid}（旧陆海与海岸距离）</td>
 *       <td>{@code PlateField}（门面）+ {@link LandV3}</td></tr>
 *   <tr><td>{@code LandField}（V2 海陆）</td>
 *       <td>{@link LandV3}</td></tr>
 *   <tr><td>{@code LandformField}（宏观地貌层）</td>
 *       <td>{@code PlateField.SEA_LEVEL_BLK} / {@code mountainStrength}</td></tr>
 *   <tr><td>{@code V2BiomeField}（群系 LUT）</td>
 *       <td>{@code sim2.biome.TalosBiomeDefault}（当前恒为塔罗斯平原；真实群系层将独立立项）</td></tr>
 *   <tr><td>{@code V2TerrainGen}（V2 地形合成）</td>
 *       <td>{@code TerrainComposer}（已作为门面保留，V2 合成分支删除）</td></tr>
 * </table>
 *
 * <p>代码注释里仍能看到这些类名（约 143 处）：那是**历史记录**，
 * 描述当初为什么这么改，不是可用的 API。看到时按历史注记理解，
 * 不要据此去找类。
*/
package com.EyeOfHarmonyBuffer.sim2.land;
