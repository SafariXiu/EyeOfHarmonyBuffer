package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * 群系覆盖钩子（依赖倒置）：
 * 气候层在选定原始群系后询问注册的 provider 是否要覆盖，
 * 由组合根（EyeOfHarmonyBuffer.init）注册山地层实现。
 * 这样气候层内部不依赖任何上层实现。
 */
public interface BiomeOverrideProvider {

    /** 返回覆盖群系；不需要覆盖时返回 null。 */
    BiomeGenBase overrideBiome(int worldX, int worldZ, int worldSeedInt);
}
