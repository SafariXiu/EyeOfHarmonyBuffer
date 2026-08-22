package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

import java.util.Arrays;

/**
 * 单根控制棒状态。纯数据 + 少量访问器。
 * positionBlocks：吸收段底部距堆芯底部（0 = 完全插入，FULL_OUT = 完全拔出）。
 */
public class RodState {

    public final long channelId;
    public final int rodType;   // 0 = 全长控制棒，1 = 缩短吸收棒(UA)
    public final RodSegment[] segments;

    public double positionBlocks;
    public double targetPosition;
    public double movementSpeed;

    public boolean scram;
    public boolean jammed;
    public boolean moving;

    public RodState(long channelId, int rodType, RodSegment[] segments, double initialPosition, double speed) {
        this.channelId = channelId;
        this.rodType = rodType;
        this.segments = segments;
        this.positionBlocks = initialPosition;
        this.targetPosition = initialPosition;
        this.movementSpeed = speed;
    }

    /** 显示用插入度：0 = 完全插入，1 = 完全拔出 */
    public double getInsertion() {
        return positionBlocks / RbmkRodConstants.FULL_OUT;
    }

    public void setInsertion(double insertion) {
        this.targetPosition = insertion * RbmkRodConstants.FULL_OUT;
        this.scram = false;
    }

    public void scramInsert() {
        this.targetPosition = 0;
        this.scram = true;
    }

    @Override
    public String toString() {
        return "RodState#" + channelId + " pos=" + positionBlocks + " target=" + targetPosition
            + " scram=" + scram + " jammed=" + jammed + " seg=" + Arrays.toString(segments);
    }
}