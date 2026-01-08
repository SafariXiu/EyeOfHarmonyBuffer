package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.command.TalosClimateDiagnostics;
import com.EyeOfHarmonyBuffer.command.TalosClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroBiomeSelector;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectionResult;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
import com.EyeOfHarmonyBuffer.space.talos.TalosBiomeDebugHooks;
import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.MacroSite;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Objects;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private static final BiomeGenBase DEFAULT_BIOME = TalosBiomes.TALOS_PLAINS;

    private final World world;
    private final Long2ObjectMap<ChunkBiomeCache> chunkCache = new Long2ObjectOpenHashMap<>();

    private volatile MacroBiomeSelector macroSelector;

    public WorldChunkManagerTalos2(World world) {
        super();
        this.world = Objects.requireNonNull(world, "world");

        Talos2Hooks.HookData hook = Talos2Hooks.resolveOrCreate(world);
        reload(hook.context());

        Talos2Hooks.registerWorldChunkManager(world.provider.dimensionId, this);
    }

    public void dispose() {
        Talos2Hooks.unregisterWorldChunkManager(world.provider.dimensionId, this);
        synchronized (chunkCache) {
            chunkCache.clear();
        }
    }

    @Override
    public BiomeGenBase getBiome() {
        return DEFAULT_BIOME;
    }

    public synchronized void reload(FieldContext context) {
        Objects.requireNonNull(context, "FieldContext");

        MacroBiomeSelector selector = context.getMacroSelector();
        if (selector == null) {
            throw new IllegalStateException("FieldContext returned null MacroBiomeSelector");
        }

        this.macroSelector = selector;

        TalosClimateDiagnostics.installSampler((world, sampleX, sampleZ) -> {
            MacroBiomeSelector active = this.macroSelector;
            if (active == null) {
                return TalosClimateSample.error(sampleX, sampleZ, "Macro selector not ready");
            }
            return active.buildDiagnosticSample(sampleX, sampleZ);
        });

        synchronized (chunkCache) {
            chunkCache.clear();
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
                int worldX = (x + dx) << 2;
                int worldZ = (z + dz) << 2;
                array[i++] = pickBiomeFor(worldX, worldZ);
            }
        }
        return array;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] array,
                                                 int x, int z,
                                                 int width, int depth) {
        if (array == null || array.length < width * depth) {
            array = new BiomeGenBase[width * depth];
        }

        int i = 0;
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int worldX = x + dx;
                int worldZ = z + dz;
                array[i++] = pickBiomeFor(worldX, worldZ);
            }
        }
        return array;
    }

    private BiomeGenBase pickBiomeFor(int x, int z) {
        BiomeGenBase debug = TalosBiomeDebugHooks.getGeneratedBiome(x, z);
        if (debug != null) {
            return debug;
        }

        MacroBiomeSelector selector = this.macroSelector;
        if (selector == null) {
            return DEFAULT_BIOME;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int localX = x & 15;
        int localZ = z & 15;
        int index = (localZ << 4) | localX;

        ChunkBiomeCache cache = resolveChunkCache(chunkX, chunkZ);

        BiomeGenBase biome = cache.biomes[index];
        if (biome != null) {
            return biome;
        }

        MacroSelectionResult macro = selector.select(x, z);
        biome = pickBiomeFromMacro(macro);
        cache.biomes[index] = biome;
        return biome;
    }

    private ChunkBiomeCache resolveChunkCache(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        synchronized (chunkCache) {
            ChunkBiomeCache cache = chunkCache.get(key);
            if (cache == null) {
                cache = new ChunkBiomeCache();
                chunkCache.put(key, cache);
            }
            return cache;
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private BiomeGenBase pickBiomeFromMacro(MacroSelectionResult macro) {
        MacroBiome.MacroBiomeVariant variant = macro.variant();
        if (variant != null && variant.biome != null) {
            return variant.biome;
        }

        BiomeGenBase primary = pickBaseBiomeForTag(macro.macroTag(), macro);
        MacroSite secondarySite = macro.secondarySite();

        if (secondarySite != null && macro.edgeFactor() < 0.45d) {
            BiomeGenBase secondary = pickBaseBiomeForTag(secondarySite.macroTag(), macro);
            if (secondary != null && secondary != primary) {
                return selectTransitionBiome(
                    primary,
                    secondary,
                    macro.edgeFactor(),
                    macro.macroSiteId(),
                    macro.microSiteId()
                );
            }
        }

        return primary != null ? primary : DEFAULT_BIOME;
    }

    private BiomeGenBase pickBaseBiomeForTag(MacroTag tag, MacroSelectionResult macro) {
        if (tag == null) {
            return DEFAULT_BIOME;
        }

        if (tag.isOceanic()) {
            return macro.coastDistance() <= macro.shelfWidth()
                ? TalosBiomes.TALOS_SHELF
                : TalosBiomes.TALOS_OCEAN;
        }

        if (tag.isCoastal() || macro.coastDistance() <= macro.coastWidth()) {
            return TalosBiomes.TALOS_BEACH;
        }

        if (tag.isFrozen()) {
            return TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
        }

        return switch (tag) {
            case DESERT -> TalosBiomes.TALOS_DESERT;
            case SAVANNA -> TalosBiomes.TALOS_SAVANNA;
            case STEPPE -> TalosBiomes.TALOS_WARM_STEPPE;
            case COOL_FOREST -> TalosBiomes.TALOS_COOL_FOREST;
            case TROPICAL -> TalosBiomes.TALOS_TROPICAL_RAIN;
            case TUNDRA -> TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
            case MOUNTAIN, ALPINE -> TalosBiomes.TALOS_MOUNTAINS;
            case BASIN -> TalosBiomes.TALOS_BASIN;
            default -> DEFAULT_BIOME;
        };
    }

    private BiomeGenBase selectTransitionBiome(BiomeGenBase primary,
                                               BiomeGenBase secondary,
                                               double edgeFactor,
                                               long macroSiteId,
                                               long microSiteId) {
        if (edgeFactor >= 0.55d) {
            return primary;
        }
        if (edgeFactor <= 0.20d) {
            return secondary;
        }

        long mixed = macroSiteId ^ (microSiteId * 0x9E3779B97F4A7C15L)
            ^ Double.doubleToLongBits(edgeFactor);
        return (mixed & 1L) == 0L ? primary : secondary;
    }

    private static final class ChunkBiomeCache {
        private final BiomeGenBase[] biomes = new BiomeGenBase[16 * 16];
    }
}
