package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * 控制棒纯物理计算（无副作用，可单测）。
 * 核心：材料段 × 堆芯层的覆盖率 → 反应性；移动按速度逼近目标。
 */
public final class RodPhysics {

    private RodPhysics() {}

    /** 区间重叠长度 */
    public static double overlap(double a0, double a1, double b0, double b1) {
        return Math.max(0.0, Math.min(a1, b1) - Math.max(a0, b0));
    }

    /** 段在第 layer 层的覆盖率（0~1；层高 1 格） */
    public static double segmentLayerCoverage(RodState rod, RodSegment seg, int layer) {
        double s0 = seg.worldBottom(rod.positionBlocks);
        double s1 = seg.worldTop(rod.positionBlocks);
        return Math.min(1.0, overlap(s0, s1, layer, layer + 1.0));
    }

    /** 单棒对某层的反应性贡献（$） */
    public static double rodLayerReactivity(RodState rod, int layer, double layerImportance) {
        double sum = 0;
        for (RodSegment seg : rod.segments) {
            sum += segmentLayerCoverage(rod, seg, layer) * seg.worthPerBlock;
        }
        return sum * layerImportance;
    }

    /** 单棒总反应性（$，各层 importance=1） */
    public static double rodTotalReactivity(RodState rod) {
        double sum = 0;
        for (int layer = 0; layer < RbmkRodConstants.CORE_LAYERS; layer++) {
            sum += rodLayerReactivity(rod, layer, 1.0);
        }
        return sum;
    }

    /** 移动逻辑：按速度逼近目标（AZ-5 也走速度，不瞬移） */
    public static void moveToward(RodState rod, double dt) {
        if (rod.jammed) {
            rod.moving = false;
            return;
        }
        double diff = rod.targetPosition - rod.positionBlocks;
        double maxMove = rod.movementSpeed * dt;
        if (Math.abs(diff) <= maxMove) {
            rod.positionBlocks = rod.targetPosition;
            rod.moving = false;
        } else {
            rod.positionBlocks += Math.signum(diff) * maxMove;
            rod.moving = true;
        }
    }

    /** 构建全长控制棒材料分段（吸收 8 + 连接 1 + 石墨 4） */
    public static RodSegment[] buildFullRodSegments() {
        double a = RbmkRodConstants.ABSORBER_LENGTH;
        double g = RbmkRodConstants.GRAPHITE_DISPLACER_LENGTH;
        double c = RbmkRodConstants.CONNECTOR_LENGTH;
        return new RodSegment[] {
            // 石墨置换段在吸收段下方（localBottom 负）
            new RodSegment(RodSegmentMaterial.GRAPHITE_DISPLACER, -g, g, RbmkRodConstants.GRAPHITE_DISPLACER_WORTH),
            // 吸收段
            new RodSegment(RodSegmentMaterial.ABSORBER, 0, a, RbmkRodConstants.ABSORBER_WORTH),
            // 连接/水隙段在吸收段上方
            new RodSegment(RodSegmentMaterial.CONNECTOR, a, c, RbmkRodConstants.CONNECTOR_WORTH),
        };
    }

    /** 构建缩短吸收棒（UA）分段：短吸收段，无石墨段 */
    public static RodSegment[] buildUaRodSegments() {
        double a = RbmkRodConstants.UA_ABSORBER_LENGTH;
        return new RodSegment[] {
            new RodSegment(RodSegmentMaterial.ABSORBER, 0, a, RbmkRodConstants.ABSORBER_WORTH),
        };
    }
}