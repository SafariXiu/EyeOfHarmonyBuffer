package com.EyeOfHarmonyBuffer.space.talos.chunk.hook;

import com.EyeOfHarmonyBuffer.space.talos.DefaultCoastlineAtlas;
import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.Talos2NoiseConfig;
import com.EyeOfHarmonyBuffer.space.talos.TalosMacroCellBuilder;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import net.minecraft.world.World;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Talos2Hooks {

    private Talos2Hooks() {}

    private static final ConcurrentMap<Integer, HookData> HOOKS = new ConcurrentHashMap<>();

    public static final class HookData {
        public final World world;
        public final long seed;
        public final DefaultCoastlineAtlas coastlineAtlas;
        public final MacroBiomeField macroField;
        public final MacroBiomeField.MacroBiomeConfig macroConfig;
        public final TalosMacroCellBuilder rawMacroCellBuilder;
        public final CachingMacroCellBuilder macroCellBuilder;
        public final SimplexNoiseOctave terrainNoise;

        private HookData(World world,
                         long seed,
                         DefaultCoastlineAtlas coastlineAtlas,
                         MacroBiomeField macroField,
                         MacroBiomeField.MacroBiomeConfig macroConfig,
                         TalosMacroCellBuilder rawMacroCellBuilder,
                         CachingMacroCellBuilder macroCellBuilder,
                         SimplexNoiseOctave terrainNoise) {
            this.world = world;
            this.seed = seed;
            this.coastlineAtlas = coastlineAtlas;
            this.macroField = macroField;
            this.macroConfig = macroConfig;
            this.rawMacroCellBuilder = rawMacroCellBuilder;
            this.macroCellBuilder = macroCellBuilder;
            this.terrainNoise = terrainNoise;
        }
    }

    public static void register(int dim,
                                World world,
                                long seed,
                                DefaultCoastlineAtlas coastlineAtlas,
                                MacroBiomeField macroField,
                                MacroBiomeField.MacroBiomeConfig macroConfig,
                                TalosMacroCellBuilder macroCellBuilder,
                                SimplexNoiseOctave terrainNoise) {

        TalosMacroCellBuilder rawBuilder = macroCellBuilder;
        CachingMacroCellBuilder cachedBuilder = new CachingMacroCellBuilder(rawBuilder, 1024);

        HookData data = new HookData(
            world,
            seed,
            coastlineAtlas,
            macroField,
            macroConfig,
            rawBuilder,
            cachedBuilder,
            terrainNoise
        );
        HOOKS.put(dim, data);
        log("register", dim, world, data);
    }

    public static void unregister(int dimensionId) {
        HOOKS.remove(dimensionId);
    }

    public static HookData resolve(World world) {
        HookData data = HOOKS.get(world.provider.dimensionId);
        log("resolve", world.provider.dimensionId, world, data);
        return data;
    }

    public static HookData resolveOrCreate(World world) {
        int dim = world.provider.dimensionId;
        HookData data = HOOKS.computeIfAbsent(dim, ignored -> createData(world));
        log("resolveOrCreate", dim, world, data);
        return data;
    }

    private static HookData createData(World world) {
        long seed = world.getSeed();
        MacroBiomeField.MacroBiomeConfig macroConfig = Talos2NoiseConfig.currentMacroConfig();

        MacroBiomeField field = new MacroBiomeField(seed, macroConfig);
        DefaultCoastlineAtlas atlas = new DefaultCoastlineAtlas(field, seed);
        SimplexNoiseOctave terrainNoise = new SimplexNoiseOctave(seed ^ 0x1234ABCDL, 4);
        TalosMacroCellBuilder rawBuilder = new TalosMacroCellBuilder(field, atlas);
        CachingMacroCellBuilder cachedBuilder = new CachingMacroCellBuilder(rawBuilder, 1024);

        return new HookData(world, seed, atlas, field, macroConfig, rawBuilder, cachedBuilder, terrainNoise);
    }

    private static void log(String tag, int dim, World world, HookData data) {
        System.out.println("[Talos2Hooks] " + tag +
            " dim=" + dim +
            " worldHash=" + System.identityHashCode(world) +
            (data == null
                ? " -> null"
                : " -> rawBuilderId=" + System.identityHashCode(data.rawMacroCellBuilder) +
                " cachedBuilderId=" + System.identityHashCode(data.macroCellBuilder) +
                " macroConfig=" + macroConfigDigest(data.macroConfig)));
    }

    private static String macroConfigDigest(MacroBiomeField.MacroBiomeConfig cfg) {
        if (cfg == null) return "null";
        return "{macroScale=" + cfg.macroScale +
            ", macroCellSize=" + cfg.macroCellSize +
            ", coarseWeight=" + cfg.coarseWeight +
            ", patchGridSize=" + cfg.patchGridSize +
            ", patchesPerCell=" + cfg.patchesPerCell +
            ", patchBlendRadius=" + cfg.patchBlendRadius +
            "}";
    }
}
