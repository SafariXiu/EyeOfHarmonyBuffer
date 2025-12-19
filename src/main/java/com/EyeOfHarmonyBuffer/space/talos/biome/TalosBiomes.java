package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraftforge.common.BiomeDictionary;

public class TalosBiomes {

    public static BiomeGenTalos2Beach TALOS_BEACH;
    public static BiomeGenTalos2Ocean TALOS_OCEAN;
    public static BiomeGenTalos2Plains TALOS_PLAINS;
    public static BiomeGenTalos2Mountains TALOS_MOUNTAINS;
    public static BiomeGenTalos2Basin TALOS_BASIN;
    public static BiomeGenTalos2Plateau TALOS_PLATEAU;
    public static BiomeGenTalos2Desert TALOS_DESERT;

    public static void init(){
        TALOS_OCEAN  = new BiomeGenTalos2Ocean(180);
        TALOS_BEACH  = new BiomeGenTalos2Beach(181);
        TALOS_PLAINS = new BiomeGenTalos2Plains(182);
        TALOS_MOUNTAINS = new BiomeGenTalos2Mountains(183);
        TALOS_BASIN = new BiomeGenTalos2Basin(184);
        TALOS_PLATEAU = new BiomeGenTalos2Plateau(185);
        TALOS_DESERT = new BiomeGenTalos2Desert(186);

        BiomeDictionary.registerBiomeType(TALOS_OCEAN,  BiomeDictionary.Type.OCEAN);

        BiomeDictionary.registerBiomeType(TALOS_BEACH,  BiomeDictionary.Type.BEACH);

        BiomeDictionary.registerBiomeType(TALOS_PLAINS, BiomeDictionary.Type.PLAINS);

        BiomeDictionary.registerBiomeType(TALOS_MOUNTAINS, BiomeDictionary.Type.MOUNTAIN);

        BiomeDictionary.registerBiomeType(TALOS_BASIN, BiomeDictionary.Type.PLAINS);
        BiomeDictionary.registerBiomeType(TALOS_BASIN, BiomeDictionary.Type.SWAMP);

        BiomeDictionary.registerBiomeType(TALOS_PLATEAU, BiomeDictionary.Type.PLAINS);
        BiomeDictionary.registerBiomeType(TALOS_PLATEAU, BiomeDictionary.Type.HILLS);

        BiomeDictionary.registerBiomeType(TALOS_DESERT, BiomeDictionary.Type.DESERT);
        BiomeDictionary.registerBiomeType(TALOS_DESERT, BiomeDictionary.Type.DRY);
    }
}
