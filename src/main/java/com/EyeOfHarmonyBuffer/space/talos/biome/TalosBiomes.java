package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraftforge.common.BiomeDictionary;

public class TalosBiomes {

    public static BiomeGenTalos2Beach TALOS_BEACH;
    public static BiomeGenTalos2Ocean TALOS_OCEAN;
    public static BiomeGenTalos2Plains TALOS_PLAINS;

    public static void init(){
        TALOS_OCEAN  = new BiomeGenTalos2Ocean(180);
        TALOS_BEACH  = new BiomeGenTalos2Beach(181);
        TALOS_PLAINS = new BiomeGenTalos2Plains(182);

        BiomeDictionary.registerBiomeType(TALOS_OCEAN,  BiomeDictionary.Type.OCEAN);
        BiomeDictionary.registerBiomeType(TALOS_BEACH,  BiomeDictionary.Type.BEACH);
        BiomeDictionary.registerBiomeType(TALOS_PLAINS, BiomeDictionary.Type.PLAINS);
    }
}
