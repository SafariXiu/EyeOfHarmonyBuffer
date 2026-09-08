package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

/**
 * 全球气候统一采样门面（V2 · docs/TerrainV2/design.md 二c：对外唯一 API）。
 *
 * 内部按权威因果链编排（单向依赖，禁止反向）：
 *   海陆(L1 NoiseContinentGrid) → 气团(L2 AirMassField) → 风/干湿(L0 GlobalCirculation)
 *   → 洋流(L4 OceanCurrentField，仅海上)。
 *
 * 对外只暴露 {@link #sample(int,int,int)}，返回完整 {@link ClimateSample}；
 * 层细节不外泄。确定性纯函数，O(1)（环流内部为固定次数的半固定系统求和）。
 *
 * 注意：本门面的海陆来源是 **V2 噪声场**（原型）；生产世界生成的海陆仍是旧
 * TectonicWorld（TalosLandMask），两者在 X1 阶段2 完成前并存（/talosmap land 对照）。
 */
public final class GlobalClimate {

    private GlobalClimate() {}

    /**
     * 单点完整气候采样。
     *
     * @param x,z           世界 block 坐标（任意范围，内部自行折叠环面）
     * @param worldSeedInt  世界种子（TalosLandMask.getWorldSeedInt 同口径）
     */
    public static ClimateSample sample(int x, int z, int worldSeedInt) {
        boolean isLand = NoiseContinentGrid.isLand(x, z, worldSeedInt);
        double coastDist = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);

        // L0 环流（band / 风 / 干湿 / 雨 / 主导系统 / gyre 占位）
        CirculationSample cs = GlobalCirculation.sample(x, z, worldSeedInt);

        // L2 气团
        AirMassField.AirMassSample am = AirMassField.sample(x, z, worldSeedInt);

        // L4 洋流（仅海上）
        double curX = 0.0, curZ = 0.0, seaT = Double.NaN, curV = 0.0;
        if (!isLand) {
            OceanCurrentField.CurrentSample cur = OceanCurrentField.sample(x, z, worldSeedInt);
            curX = cur.flowX;
            curZ = cur.flowZ;
            seaT = cur.temperature;
            curV = cur.speed;
        }

        return new ClimateSample(
            isLand, coastDist,
            cs.bandD, cs.windX, cs.windZ,
            cs.pressureDry, cs.rainfallBase, cs.pressureSystem, cs.gyreWarmth,
            am.type, am.temperature, am.humidity,
            curX, curZ, seaT, curV
        );
    }
}
