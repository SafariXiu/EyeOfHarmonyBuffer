package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.TalosRiverProfile;

/**
 * 河流河谷雕刻（高度场版）。
 *
 * 在填充方块之前把河谷做进高度场：
 *   h = min(h, 河床目标Y)
 *
 * 河床剖面（V / U 型深度因子）与源头湖 / 暗河井逻辑全部连续，
 * 因此河谷两侧天然形成缓坡，不再需要事后垂直切方块 + 灌水的硬切操作。
 *
 * 阶段 A 仅处理陆地列（海洋一侧保持原样，入海口问题由阶段 B 的
 * 纵向深度剖面统一解决）。
 */
public final class TalosRiverChannelShaper {

    private TalosRiverChannelShaper() {}

    /**
     * 对某一列高度做河谷雕刻。
     *
     * @param worldX   世界 X
     * @param worldZ   世界 Z
     * @param heightD  当前（河岸塑形 + 海岸塑形之后的）地形高度
     * @param seaLevel 海平面高度
     * @param hydro    该列的水文采样（来自 TalosChunkContext）
     * @param macroId  该列的宏群系 ID（OCEANIC / null 时不雕刻）
     * @return 雕刻后的高度
     */
    public static double applyRiverChannelShaping(
        int worldX, int worldZ,
        int worldSeedInt,
        double heightD,
        int seaLevel,
        TalosRiverSystem.HydroSample hydro,
        MacroPackageId macroId
    ) {
        if (hydro == null) {
            return heightD;
        }

        double bedY = TalosRiverProfile.computeChannelBedY(
            worldX, worldZ, worldSeedInt,
            heightD, seaLevel, hydro, macroId
        );

        if (bedY >= heightD) {
            return heightD;
        }
        return bedY;
    }
}
