package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/** 控制棒材料分段类型。 */
public enum RodSegmentMaterial {
    /** 中子吸收段（B4C / 碳化硼），负反应性 */
    ABSORBER,
    /** 石墨置换段（排开通道底部水 → 增强慢化 → 正反应性，AZ-5 初期尖峰来源） */
    GRAPHITE_DISPLACER,
    /** 连接/水隙段，中性 */
    CONNECTOR,
    /** 空段（无材料） */
    EMPTY
}