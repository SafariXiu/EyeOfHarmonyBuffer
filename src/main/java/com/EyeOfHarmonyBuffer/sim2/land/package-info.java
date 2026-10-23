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
 */
package com.EyeOfHarmonyBuffer.sim2.land;
