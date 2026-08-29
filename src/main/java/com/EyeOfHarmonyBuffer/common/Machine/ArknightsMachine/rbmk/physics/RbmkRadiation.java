package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * 辐射纯计算（无副作用，可单测）。
 * 等级 = max(全图基础等级, 所有源按距离的档位等级)。
 * 距离档位：≤100→7，≤500→6，≤800→5，≤1500→4，≤3000→3，更远→0。
 */
public final class RbmkRadiation {

    private RbmkRadiation() {}

    public static final int MAX_LEVEL = 7;
    /** 炸一个源后的全图基础等级 */
    public static final int BASE_LEVEL = 2;

    /** 距离档位表：{ 距离(格), 等级 }，按距离升序（近 → 高等级） */
    public static final int[][] LEVEL_RANGES = {
        { 100,  7 },
        { 500,  6 },
        { 800,  5 },
        { 1500, 4 },
        { 3000, 3 },
    };

    /**
     * 全图基础等级 = 2 + (源数 − 1)，封顶 7。
     * 炸 1 个 → 2；炸 2 个 → 3；……炸 6 个 → 全图 7。
     */
    public static int baseLevelFor(int sourceCount) {
        return Math.min(MAX_LEVEL, BASE_LEVEL + (sourceCount - 1));
    }

    /** 单源按距离的档位等级（不含全图基础）；更远返回 0 */
    public static int levelForSource(RbmkRadiationSource src, double px, double py, double pz) {
        double dx = px - src.x, dy = py - src.y, dz = pz - src.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (int[] r : LEVEL_RANGES) {
            if (dist <= r[0]) {
                return r[1];
            }
        }
        return 0;
    }

    /** 某位置的最终等级 = max(全图基础, 所有源按距离的档位) */
    public static int levelAt(Iterable<RbmkRadiationSource> sources, int sourceCount,
                              double px, double py, double pz) {
        int level = baseLevelFor(sourceCount);
        if (sources != null) {
            for (RbmkRadiationSource src : sources) {
                level = Math.max(level, levelForSource(src, px, py, pz));
            }
        }
        return level;
    }

    /** 防护匹配：装备保护等级 ≥ 区域等级才豁免；低级装备进高级区 = 无效 */
    public static boolean isProtected(int protectionLevel, int regionLevel) {
        return protectionLevel >= regionLevel;
    }

    /** 该区域是否不刷生物：等级 ≥ 5（5/6/7 级不刷，2-4 级正常刷） */
    public static boolean shouldBlockSpawning(int regionLevel) {
        return regionLevel >= 5;
    }
}
