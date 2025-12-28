package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2BiomeResolver.Talos2BiomeResolver;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private final MacroBiomeField macroField;
    private final CoastlineAtlas coastlineAtlas;
    private final Talos2BiomeResolver biomeResolver;

    public WorldChunkManagerTalos2(World world) {
        super();
        long seed = world.getSeed();
        Talos2Hooks.HookData hook = Talos2Hooks.resolve(world);

        MacroBiomeField.MacroBiomeConfig macroConfig =
            (hook != null && hook.macroConfig != null)
                ? hook.macroConfig
                : Talos2NoiseConfig.currentMacroConfig();

        if (hook != null && hook.macroField != null && hook.coastlineAtlas != null) {
            this.macroField = hook.macroField;
            this.coastlineAtlas = hook.coastlineAtlas;
        } else {
            this.macroField = new MacroBiomeField(seed, macroConfig);
            this.coastlineAtlas = new DefaultCoastlineAtlas(this.macroField, seed);
        }

        this.biomeResolver = new Talos2BiomeResolver(world, this.macroField);
    }

    @Override
    public BiomeGenBase getBiome() {
        return TalosBiomes.TALOS_PLAINS;
    }

    private BiomeGenBase pickBiomeFor(int x, int z) {
        MacroBiomeField.SampleDual sample = this.macroField.sampleDual(x, z);
        MacroBiome macroPrimary = (sample != null && sample.primary != null)
            ? sample.primary
            : MacroBiome.PLAINS_TEMPERATE;
        boolean isLand = this.coastlineAtlas.isLand(x, z);
        int dist = this.coastlineAtlas.distanceToCoast(x, z);
        int shelfW = this.coastlineAtlas.shelfWidth(x, z, macroPrimary);
        int beachW = this.coastlineAtlas.beachWidth(x, z, macroPrimary);

        if (!isLand) {
            return (dist <= shelfW) ? TalosBiomes.TALOS_SHELF : TalosBiomes.TALOS_OCEAN;
        }

        if (dist <= beachW) {
            return TalosBiomes.TALOS_BEACH;
        }

        BiomeGenBase biome = this.biomeResolver.resolve(x, z);
        if (biome == null) {
            biome = TalosBiomes.TALOS_PLAINS;
        }
        return biome;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return pickBiomeFor(x, z);
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] array,
                                                 int x, int z,
                                                 int width, int depth) {
        if (array == null || array.length < width * depth) {
            array = new BiomeGenBase[width * depth];
        }

        int i = 0;
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int gx = x + dx;
                int gz = z + dz;
                array[i++] = pickBiomeFor(gx, gz);
            }
        }
        return array;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] array,
                                                 int x, int z,
                                                 int width, int depth) {
        return getBiomesForGeneration(array, x, z, width, depth);
    }
}
