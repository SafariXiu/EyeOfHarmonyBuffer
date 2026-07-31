package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * 基于 tectonic_v1 的超级大陆查询适配器。
 *
 * 主要用于河流模板：
 *   - 给定 worldX/Z 和 worldSeedInt；
 *   - 推导出当前所在超级大陆的：
 *       * superId
 *       * 中心坐标 (cx, cz)
 *       * 基础半径 baseRadius
 *       * 从中心指向当前位置的大致角度 angleRad
 */

public final class SupercontinentAdapter {

    private SupercontinentAdapter() {}

    /**
     * (worldSeedInt, superId) -> {centerX, centerZ, baseRadius} 缓存。
     * 中心与半径只依赖 superId，同一超大陆内所有方块共用，不必每列重算。
     */
    private static final Long2ObjectOpenHashMap<double[]> CENTER_RADIUS_CACHE =
        new Long2ObjectOpenHashMap<double[]>();

    private static long centerRadiusKey(int worldSeedInt, int superId) {
        return (((long) worldSeedInt) << 32) ^ (superId & 0xffffffffL);
    }

    /**
     * 基于当前 worldX/Z，推导出所在超级大陆的 SupercontinentInfo。
     *
     * 若 superId == 0 或无法获取中心信息，则返回 null。
     */
    public static SupercontinentInfo getInfoAt(int worldX, int worldZ, int worldSeedInt) {
        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        return getInfoAt(superId, worldX, worldZ, worldSeedInt);
    }

    /**
     * 已知 superId 时的查询：跳过内部重复的 getSuperId 全量采样。
     * 中心 / 半径按 (seed, superId) 缓存，只有角度仍按点计算（代价极低）。
     */
    public static SupercontinentInfo getInfoAt(int superId,
                                               int worldX, int worldZ,
                                               int worldSeedInt) {
        if (superId == 0) {
            return null;
        }

        long key = centerRadiusKey(worldSeedInt, superId);
        double[] cr = CENTER_RADIUS_CACHE.get(key);
        if (cr == null) {
            int[] center = TalosLandMask.getSuperCenterXZById(superId, worldSeedInt);
            if (center == null) {
                return null;
            }

            double radius = TalosLandMask.getSuperBaseRadius(superId, worldSeedInt);
            if (radius <= 0.0) {
                radius = 1.0;
            }

            cr = new double[] { center[0], center[1], radius };
            if (CENTER_RADIUS_CACHE.size() > 2048) {
                CENTER_RADIUS_CACHE.clear();
            }
            CENTER_RADIUS_CACHE.put(key, cr);
        }

        double cx = cr[0];
        double cz = cr[1];
        double radius = cr[2];

        double dx = worldX - cx;
        double dz = worldZ - cz;

        if (dx == 0.0 && dz == 0.0) {
            dz = 1.0;
        }

        double angleRad = Math.atan2(dz, dx);

        return new SupercontinentInfo(superId, cx, cz, radius, angleRad);
    }
}
