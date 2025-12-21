package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private final SimplexNoiseOctave continentNoise;
    private final double continentScale = 0.0007D;
    private static final int COAST_RADIUS = 192;
    private final MacroBiomeField macroField;
    private final CoastWidthField coastWidthField;

    private static final double C_SPLIT = Talos2Continent.C_LAND;
    private static final double BAND = 0.08D;
    private static final int COAST_RADIUS_BLOCKS = 192;

    private final java.util.LinkedHashMap<Long, ChunkCoastField> coastCache =
        new java.util.LinkedHashMap<Long, ChunkCoastField>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<Long, ChunkCoastField> eldest) {
                return size() > 64;
            }
        };

    public WorldChunkManagerTalos2(World world) {
        super();
        long seed = world.getSeed();
        this.continentNoise = new SimplexNoiseOctave(
            seed ^ Talos2Continent.CONTINENT_SALT,
            Talos2Continent.CONTINENT_OCTAVES
        );

        this.macroField = new MacroBiomeField(seed);
        this.coastWidthField = new CoastWidthField(seed);
    }
    @Override
    public BiomeGenBase getBiome() {
        return TalosBiomes.TALOS_PLAINS;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long)chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private ChunkCoastField coastFieldFor(int gx, int gz) {
        int chunkX = gx >> 4;
        int chunkZ = gz >> 4;
        long key = chunkKey(chunkX, chunkZ);

        ChunkCoastField f = coastCache.get(key);
        if (f == null) {
            f = ChunkCoastField.build(this.continentNoise, chunkX, chunkZ, COAST_RADIUS);
            coastCache.put(key, f);
        }
        return f;
    }

    private BiomeGenBase pickBiomeFor(int x, int z) {
        double c = Talos2Continent.sampleC01(this.continentNoise, x, z);
        boolean isLand = c >= C_SPLIT;

        double dc = Math.abs(c - C_SPLIT);
        double t = clamp01(dc / BAND);
        int dist = (int) Math.round(t * COAST_RADIUS_BLOCKS);

        MacroBiome macro = this.macroField.pick(x, z);
        CoastProfile profile = CoastProfiles.forMacro(macro);

        int shelfW = this.coastWidthField.shelfWidthBlocks(x, z, profile);
        int beachW = this.coastWidthField.beachWidthBlocks(x, z, profile);

        if (!isLand) {
            return (dist <= shelfW) ? TalosBiomes.TALOS_SHELF : TalosBiomes.TALOS_OCEAN;
        } else {
            return (dist <= beachW) ? TalosBiomes.TALOS_BEACH : TalosBiomes.TALOS_PLAINS;
        }
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

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
