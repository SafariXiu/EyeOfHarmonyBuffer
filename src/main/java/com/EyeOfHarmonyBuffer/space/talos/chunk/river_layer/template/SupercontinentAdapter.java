package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

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
     * 基于当前 worldX/Z，推导出所在超级大陆的 SupercontinentInfo。
     *
     * 若 superId == 0 或无法获取中心信息，则返回 null。
     */
    public static SupercontinentInfo getInfoAt(int worldX, int worldZ, int worldSeedInt) {
        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return null;
        }

        int[] center = TalosLandMask.getSuperCenterXZById(superId, worldSeedInt);
        if (center == null) {
            return null;
        }

        double cx = center[0];
        double cz = center[1];

        double radius = TalosLandMask.getSuperBaseRadius(superId, worldSeedInt);
        if (radius <= 0.0) {
            radius = 1.0;
        }

        double dx = worldX - cx;
        double dz = worldZ - cz;

        if (dx == 0.0 && dz == 0.0) {
            dz = 1.0;
        }

        double angleRad = Math.atan2(dz, dx);

        return new SupercontinentInfo(superId, cx, cz, radius, angleRad);
    }
}
