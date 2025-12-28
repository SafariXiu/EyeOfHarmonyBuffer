package com.EyeOfHarmonyBuffer.space.talos;

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

        public HookData(World world,
                        long seed,
                        DefaultCoastlineAtlas coastlineAtlas,
                        MacroBiomeField macroField,
                        MacroBiomeField.MacroBiomeConfig macroConfig) {
            this.world = world;
            this.seed = seed;
            this.coastlineAtlas = coastlineAtlas;
            this.macroField = macroField;
            this.macroConfig = macroConfig;
        }
    }

    public static void register(int dim,
                                World world,
                                long seed,
                                DefaultCoastlineAtlas coastlineAtlas,
                                MacroBiomeField macroField,
                                MacroBiomeField.MacroBiomeConfig macroConfig) {
        HookData data = new HookData(world, seed, coastlineAtlas, macroField, macroConfig);
        HOOKS.put(dim, data);
    }

    public static void unregister(int dimensionId) {
        HOOKS.remove(dimensionId);
    }

    public static HookData resolve(World world) {
        return HOOKS.get(world.provider.dimensionId);
    }
}
