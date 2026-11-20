package com.EyeOfHarmonyBuffer.sim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ★★★★★ **重构冻结标记（2026-10-21，用户裁决）。**
 *
 * <p>被标记的类/模块**不再接受功能改动、调参、性能优化**；只允许会导致崩溃/数据损坏的严重 bugfix
 * （修完须打 tag 记录）。重构时新代码应放在新包，只 import 白名单（ParcelLift / VerticalColumn /
 * Radiation / WorldContract / 守恒与性能基座）。
 *
 * <p>冻结范围见 {@code Talos2/工作目录/长期/冻结清单-2026-10-21.md}。
 * <pre>
 *   海陆/地形链      → 等待海陆分布 v2
 *   气候表/拟合层    → 等待自算纬向骨架
 *   2.5D 海洋        → 等待 3D 海洋
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Frozen {
    /** 冻结原因（必填）。 */
    String reason();
    /** 解冻条件（可选）。 */
    String until() default "";
}
