package com.EyeOfHarmonyBuffer.space.talos;

import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.biome.BiomeGenBase;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    public WorldChunkManagerTalos2() {
        super();
    }

    @Override
    public BiomeGenBase getBiome() {
        return BiomeGenTalos2.talos2;
    }
}
