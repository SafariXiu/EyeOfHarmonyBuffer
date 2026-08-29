package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * 超级大陆布点规则（确定性纯函数）。
 *
 * 布点格网 = PLACEMENT_CELL_SIZE（40000 格）：
 *   - 主大陆固定占 (奇, 奇) 格，永远存在；
 *   - 次级大陆占其余三种格，按确定性概率存在；
 *   - 所有候选在放置前做 5×5 邻居分离检查（中心距 ≥ 双方最大半径和 + 安全间距），
 *     不满足则视为海洋。
 *
 * 全部逻辑只依赖 (worldSeedInt, cellX, cellZ)，任何点都能独立判定，
 * 无状态、无顺序依赖，因此不会破坏「多个超级大陆互不连通」的特性。
 */
public final class SupercontinentPlacement {

    private SupercontinentPlacement() {}

    /** 单个格点的放置信息。 */
    public static final class Placement {
        public final boolean isMain;
        public final boolean exists;
        public final double centerX;
        public final double centerZ;
        /** 基准半径随机范围（决定平均大小）。 */
        public final double baseRadiusMin;
        public final double baseRadiusMax;
        /** 海岸线半径钳制范围（决定最大 / 最小实体范围）。 */
        public final double clampMinRadius;
        public final double clampMaxRadius;

        private Placement(boolean isMain, boolean exists,
                          double centerX, double centerZ,
                          double baseRadiusMin, double baseRadiusMax,
                          double clampMinRadius, double clampMaxRadius) {
            this.isMain = isMain;
            this.exists = exists;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.baseRadiusMin = baseRadiusMin;
            this.baseRadiusMax = baseRadiusMax;
            this.clampMinRadius = clampMinRadius;
            this.clampMaxRadius = clampMaxRadius;
        }
    }

    private static final Placement ABSENT_SUB =
        new Placement(false, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    /** 主大陆格 = (奇, 奇) 布点格。 */
    public static boolean isMainCell(int cellX, int cellZ) {
        return (cellX & 1) == 1 && (cellZ & 1) == 1;
    }

    /**
     * 原始放置：主格必存在，次格按确定性概率存在。
     * 不做分离检查（供邻居判定与构造使用，成本极低）。
     */
    public static Placement rawPlacement(int cellX, int cellZ, int worldSeedInt) {
        long seed = worldSeedInt & 0xFFFFFFFFL;
        boolean isMain = isMainCell(cellX, cellZ);
        double cellSize = TectonicConfig.PLACEMENT_CELL_SIZE;
        double baseCenterX = cellX * cellSize + cellSize / 2.0;
        double baseCenterZ = cellZ * cellSize + cellSize / 2.0;

        if (isMain) {
            long seedCx = TectonicMath.hashInts((int) seed, 0x10001, cellX, cellZ);
            long seedCz = TectonicMath.hashInts((int) seed, 0x10002, cellX, cellZ);
            double jx = TectonicMath.randRange(
                seedCx, -TectonicConfig.CENTER_JITTER_MAX, TectonicConfig.CENTER_JITTER_MAX);
            double jz = TectonicMath.randRange(
                seedCz, -TectonicConfig.CENTER_JITTER_MAX, TectonicConfig.CENTER_JITTER_MAX);
            return new Placement(
                true, true,
                baseCenterX + jx, baseCenterZ + jz,
                TectonicConfig.BASE_RADIUS_MIN,
                TectonicConfig.BASE_RADIUS_MAX,
                TectonicConfig.MIN_RADIUS,
                TectonicConfig.MAX_RADIUS
            );
        }

        long seedP = TectonicMath.hashInts((int) seed, 0x30001, cellX, cellZ);
        double p = TectonicMath.randUnitDouble(seedP);
        if (p > TectonicConfig.SUB_PRESENCE_PROB) {
            return ABSENT_SUB;
        }

        long seedCx = TectonicMath.hashInts((int) seed, 0x30002, cellX, cellZ);
        long seedCz = TectonicMath.hashInts((int) seed, 0x30003, cellX, cellZ);
        double jx = TectonicMath.randRange(
            seedCx, -TectonicConfig.SUB_JITTER_MAX, TectonicConfig.SUB_JITTER_MAX);
        double jz = TectonicMath.randRange(
            seedCz, -TectonicConfig.SUB_JITTER_MAX, TectonicConfig.SUB_JITTER_MAX);
        return new Placement(
            false, true,
            baseCenterX + jx, baseCenterZ + jz,
            TectonicConfig.SUB_BASE_RADIUS_MIN,
            TectonicConfig.SUB_BASE_RADIUS_MAX,
            TectonicConfig.SUB_MIN_RADIUS,
            TectonicConfig.SUB_MAX_RADIUS
        );
    }

    /**
     * 有效放置：原始存在 + 与周围 5×5 邻居的分离检查。
     *
     * 规则（不对称让位，保证主大陆永远存在）：
     *   - 主大陆不参与检查：主-主间距数学上安全（≥ 2×格距 - 2×抖动 > 2×最大半径 + 间距），
     *     且冲突时永远由次级让位；
     *   - 次级大陆与任何邻居（主或次级）中心距 < 双方最大半径和 + SAFETY_GAP 时：
     *       邻居是主大陆 → 本格让位；
     *       邻居是次级大陆 → 按格点坐标让位（较小 (cellX, cellZ) 者保留）。
     * 邻居按 rawPlacement 判定（纯函数、无递归、无状态、确定性）。
     */
    public static Placement effectivePlacement(int cellX, int cellZ, int worldSeedInt) {
        Placement p = rawPlacement(cellX, cellZ, worldSeedInt);
        if (!p.exists || p.isMain) {
            return p;
        }

        final int R = 2;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                Placement np = rawPlacement(cellX + dx, cellZ + dz, worldSeedInt);
                if (!np.exists) {
                    continue;
                }

                double ddx = p.centerX - np.centerX;
                double ddz = p.centerZ - np.centerZ;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);

                if (dist < p.clampMaxRadius + np.clampMaxRadius + TectonicConfig.SAFETY_GAP) {
                    // 冲突：主大陆永远优先
                    if (np.isMain) {
                        return ABSENT_SUB;
                    }
                    // 两个次级冲突：较小格点坐标者保留
                    int nx = cellX + dx;
                    int nz = cellZ + dz;
                    if (nx < cellX || (nx == cellX && nz < cellZ)) {
                        return ABSENT_SUB;
                    }
                }
            }
        }

        return p;
    }
}
