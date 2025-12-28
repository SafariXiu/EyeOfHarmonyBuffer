package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2ClimateSampler;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public final class Talos2ClimateService {

    private static final Map<Integer, Talos2ClimateSampler> CACHE = new HashMap<>();

    private Talos2ClimateService() {}

    public static Talos2ClimateSampler get(World world) {
        int dim = world.provider.dimensionId;
        Talos2ClimateSampler sampler = CACHE.get(dim);
        if (sampler != null) {
            return sampler;
        }

        Talos2Hooks.HookData hook = Talos2Hooks.resolve(world);
        MacroBiomeField macroField;

        if (hook != null && hook.macroField != null) {
            macroField = hook.macroField;
        } else {
            long seed = world.getSeed();
            MacroBiomeField.MacroBiomeConfig macroCfg = Talos2NoiseConfig.currentMacroConfig();
            macroField = new MacroBiomeField(seed, macroCfg);
        }

        sampler = new Talos2ClimateSampler(world, macroField);
        CACHE.put(dim, sampler);
        return sampler;
    }

    public static void invalidate(World world) {
        CACHE.remove(world.provider.dimensionId);
    }

    public static void clearAll() {
        CACHE.clear();
    }
}
