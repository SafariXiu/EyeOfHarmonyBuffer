package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.BiomeOverrideProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.api.TalosMountainSystem;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 山地群系覆盖实现：由组合根注册进气候层。
 * 依赖方向：mountain -> climate.api（气候层不反向依赖山地层）。
 */
public final class MountainBiomeOverrideProvider implements BiomeOverrideProvider {

    @Override
    public BiomeGenBase overrideBiome(int worldX, int worldZ, int worldSeedInt) {
        return TalosMountainSystem.getMountainBiomeOverride(
            worldX, worldZ, worldSeedInt
        );
    }
}
