package com.EyeOfHarmonyBuffer.space.blackhole;

import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.biome.BiomeGenBase;

/** 翡翠王座：单群系维度（WorldChunkManagerSpace 只需实现 getBiome）。 */
public class WorldChunkManagerEmeraldThrone extends WorldChunkManagerSpace {

    @Override
    public BiomeGenBase getBiome() {
        return BiomeGenEmeraldThrone.INSTANCE;
    }
}
