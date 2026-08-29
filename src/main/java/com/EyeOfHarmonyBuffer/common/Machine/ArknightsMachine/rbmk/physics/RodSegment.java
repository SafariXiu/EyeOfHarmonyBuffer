package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * 控制棒的一个材料段。
 * localBottom：该段底部相对"吸收段底部"的偏移（方块单位）。
 *   吸收段 localBottom=0；石墨置换段在吸收段下方（负数）；连接段在吸收段上方。
 */
public class RodSegment {

    public final RodSegmentMaterial material;
    public final double localBottom;
    public final double length;
    public final double worthPerBlock;

    public RodSegment(RodSegmentMaterial material, double localBottom, double length, double worthPerBlock) {
        this.material = material;
        this.localBottom = localBottom;
        this.length = length;
        this.worthPerBlock = worthPerBlock;
    }

    /** 段在世界中的底部（相对堆芯底部，吸收段底 = rod.position） */
    public double worldBottom(double rodPosition) {
        return rodPosition + localBottom;
    }

    public double worldTop(double rodPosition) {
        return worldBottom(rodPosition) + length;
    }
}