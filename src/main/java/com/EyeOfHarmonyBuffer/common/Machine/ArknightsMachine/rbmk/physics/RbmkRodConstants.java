package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * 控制棒物理常量（集中可调）。
 * 位置约定：positionBlocks = 吸收段底部距堆芯底部的高度（方块单位）。
 * 0 = 完全插入（吸收段覆盖 0~8 层）；FULL_OUT = 完全拔出（吸收段+石墨段都离开堆芯）。
 */
public final class RbmkRodConstants {

    private RbmkRodConstants() {}

    /** 堆芯层数 */
    public static final int CORE_LAYERS = 8;

    /** 吸收段长度（格，对齐 8 层） */
    public static final double ABSORBER_LENGTH = 8.0;

    /** 石墨置换段长度（格） */
    public static final double GRAPHITE_DISPLACER_LENGTH = 4.0;

    /** 水隙/连接段长度（格） */
    public static final double CONNECTOR_LENGTH = 1.0;

    /** 吸收段每格反应性价值（$ / 格，负）。全插 8 格 ≈ −2.2$ */
    public static final double ABSORBER_WORTH = -0.275;

    /** 石墨置换段每格反应性价值（$ / 格，正，排开底部水增强慢化） */
    public static final double GRAPHITE_DISPLACER_WORTH = 0.025;

    /** 连接段每格价值（中性） */
    public static final double CONNECTOR_WORTH = 0.0;

    /** 缩短吸收棒（UA）吸收段长度（格） */
    public static final double UA_ABSORBER_LENGTH = 4.0;

    /** 完全拔出时的 positionBlocks 值（吸收段 + 石墨段都离开堆芯顶部） */
    public static final double FULL_OUT = ABSORBER_LENGTH + GRAPHITE_DISPLACER_LENGTH + CONNECTOR_LENGTH;

    /** 控制棒驱动速度（格/秒） */
    public static final double DRIVE_SPEED = 2.0;
}