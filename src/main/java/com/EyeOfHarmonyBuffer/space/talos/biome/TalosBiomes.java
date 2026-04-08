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
    public static BiomeGenTalos2Shelf TALOS_SHELF;

    public static BiomeGenTalos2TropicalRain TALOS_TROPICAL_RAIN;
    public static BiomeGenTalos2Savanna TALOS_SAVANNA;
    public static BiomeGenTalos2WarmSteppe TALOS_WARM_STEPPE;
    public static BiomeGenTalos2TemperateForest TALOS_TEMPERATE_FOREST;
    public static BiomeGenTalos2TemperateSteppe TALOS_TEMPERATE_STEPPE;
    public static BiomeGenTalos2CoolForest TALOS_COOL_FOREST;
    public static BiomeGenTalos2SubpolarTundra TALOS_SUBPOLAR_TUNDRA;
    public static BiomeGenTalos2Alpine TALOS_ALPINE;
    public static BiomeGenTalos2PolarDesert TALOS_POLAR_DESERT;

    public static void init(){
        TALOS_OCEAN = (BiomeGenTalos2Ocean) new BiomeGenTalos2Ocean(180)
            .setColor(0x0C285A);
        TALOS_SHELF = (BiomeGenTalos2Shelf) new BiomeGenTalos2Shelf(187)
            .setColor(0x1F6CB3);
        TALOS_BEACH = (BiomeGenTalos2Beach) new BiomeGenTalos2Beach(181)
            .setColor(0xE7D38B);
        TALOS_PLAINS = (BiomeGenTalos2Plains) new BiomeGenTalos2Plains(182)
            .setColor(0x55B44D);
        TALOS_TEMPERATE_FOREST = (BiomeGenTalos2TemperateForest) new BiomeGenTalos2TemperateForest(191)
            .setColor(0x2F7B4C);
        TALOS_COOL_FOREST = (BiomeGenTalos2CoolForest) new BiomeGenTalos2CoolForest(193)
            .setColor(0x3E5C4D);
        TALOS_DESERT = (BiomeGenTalos2Desert) new BiomeGenTalos2Desert(186)
            .setColor(0xD7B26A);
        TALOS_WARM_STEPPE = (BiomeGenTalos2WarmSteppe) new BiomeGenTalos2WarmSteppe(190)
            .setColor(0xBBA75A);
        TALOS_SAVANNA = (BiomeGenTalos2Savanna) new BiomeGenTalos2Savanna(189)
            .setColor(0xC6C454);
        TALOS_TROPICAL_RAIN = (BiomeGenTalos2TropicalRain) new BiomeGenTalos2TropicalRain(188)
            .setColor(0x1F8B45);
        TALOS_BASIN = (BiomeGenTalos2Basin) new BiomeGenTalos2Basin(184)
            .setColor(0x2A6FCC);
        TALOS_PLATEAU = (BiomeGenTalos2Plateau) new BiomeGenTalos2Plateau(185)
            .setColor(0x7DB56E);
        TALOS_MOUNTAINS = (BiomeGenTalos2Mountains) new BiomeGenTalos2Mountains(183)
            .setColor(0x9DA4A8);
        TALOS_ALPINE = (BiomeGenTalos2Alpine) new BiomeGenTalos2Alpine(195)
            .setColor(0xBAC6D8);
        TALOS_SUBPOLAR_TUNDRA = (BiomeGenTalos2SubpolarTundra) new BiomeGenTalos2SubpolarTundra(194)
            .setColor(0x95B6C7);
        TALOS_POLAR_DESERT = (BiomeGenTalos2PolarDesert) new BiomeGenTalos2PolarDesert(196)
            .setColor(0xE1E6EB);

        TALOS_TEMPERATE_STEPPE = (BiomeGenTalos2TemperateSteppe)
            new BiomeGenTalos2TemperateSteppe(192)
                .setColor(0x8FB05A);

        BiomeDictionary.registerBiomeType(TALOS_OCEAN,  BiomeDictionary.Type.OCEAN);
        BiomeDictionary.registerBiomeType(TALOS_SHELF,  BiomeDictionary.Type.OCEAN);

        BiomeDictionary.registerBiomeType(TALOS_BEACH,  BiomeDictionary.Type.BEACH);

        BiomeDictionary.registerBiomeType(TALOS_PLAINS, BiomeDictionary.Type.PLAINS);

        BiomeDictionary.registerBiomeType(TALOS_MOUNTAINS, BiomeDictionary.Type.MOUNTAIN);

        BiomeDictionary.registerBiomeType(TALOS_BASIN, BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.SWAMP);

        BiomeDictionary.registerBiomeType(TALOS_PLATEAU, BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.HILLS);

        BiomeDictionary.registerBiomeType(TALOS_DESERT, BiomeDictionary.Type.DESERT, BiomeDictionary.Type.DRY);

        BiomeDictionary.registerBiomeType(TALOS_TROPICAL_RAIN,
            BiomeDictionary.Type.JUNGLE, BiomeDictionary.Type.DENSE, BiomeDictionary.Type.HOT, BiomeDictionary.Type.WET);
        BiomeDictionary.registerBiomeType(TALOS_SAVANNA,
            BiomeDictionary.Type.SAVANNA, BiomeDictionary.Type.HOT, BiomeDictionary.Type.SPARSE, BiomeDictionary.Type.DRY);
        BiomeDictionary.registerBiomeType(TALOS_WARM_STEPPE,
            BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.SAVANNA, BiomeDictionary.Type.DRY);
        BiomeDictionary.registerBiomeType(TALOS_TEMPERATE_FOREST,
            BiomeDictionary.Type.FOREST, BiomeDictionary.Type.DENSE);
        BiomeDictionary.registerBiomeType(TALOS_TEMPERATE_STEPPE,
            BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.SPARSE);
        BiomeDictionary.registerBiomeType(TALOS_COOL_FOREST,
            BiomeDictionary.Type.CONIFEROUS, BiomeDictionary.Type.FOREST, BiomeDictionary.Type.COLD);
        BiomeDictionary.registerBiomeType(TALOS_SUBPOLAR_TUNDRA,
            BiomeDictionary.Type.COLD, BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.PLAINS);
        BiomeDictionary.registerBiomeType(TALOS_ALPINE,
            BiomeDictionary.Type.MOUNTAIN, BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.COLD, BiomeDictionary.Type.HILLS);
        BiomeDictionary.registerBiomeType(TALOS_POLAR_DESERT,
            BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.WASTELAND, BiomeDictionary.Type.COLD, BiomeDictionary.Type.DRY);
    }
}
