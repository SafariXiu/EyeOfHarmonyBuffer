package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import java.util.List;

/**
 * 一条河（主河或支流）的折线表示。
 */
public final class RiverPolyline {

    public final int id; // 全局唯一 ID（对本 worldSeedInt）
    public final int plateId; // 所属板块 ID，来自 TalosLandMask.getPlateId
    public final int level; // 0 = 主河，1 = 一级支流，2 = 二级支流...
    public final List<Node> nodes;

    public RiverPolyline(int id, int plateId, int level, List<Node> nodes) {
        this.id = id;
        this.plateId = plateId;
        this.level = level;
        this.nodes = nodes;
    }

    public static final class Node {
        public final double x;
        public final double z;

        public Node(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
