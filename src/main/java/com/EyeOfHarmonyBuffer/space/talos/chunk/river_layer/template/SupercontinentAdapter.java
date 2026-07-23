package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.Direction2D;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.SuperCenterInfo;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

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

        SuperCenterInfo center = TalosLandMask.getSuperCenter(superId, worldSeedInt);

        if (center == null) {
            center = TalosLandMask.getSuperCenterAt(worldX, worldZ, worldSeedInt);
        }

        if (center == null) {
            return null;
        }

        // SuperCenterInfo 的实际字段：
        //   public final int superId;
        //   public final int worldX;
        //   public final int worldZ;
        //   public final int baseRadius;
        double cx     = center.worldX;
        double cz     = center.worldZ;
        double radius = center.baseRadius;

        Direction2D dir = TalosLandMask.getPlateOutflowDirAt(worldX, worldZ, worldSeedInt);

        double dx = dir.dx;
        double dz = dir.dz;

        if (dx == 0.0 && dz == 0.0) {
            dz = 1.0;
        }

        double angleRad = Math.atan2(dz, dx);

        return new SupercontinentInfo(center.superId, cx, cz, radius, angleRad);
    }
}
