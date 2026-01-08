package com.EyeOfHarmonyBuffer.command;

import net.minecraft.world.World;

public final class TalosClimateDiagnostics {

    private static MacroClimateSampler sampler = MacroClimateSampler.DUMMY;

    private TalosClimateDiagnostics() {}

    public static void installSampler(MacroClimateSampler newSampler) {
        sampler = (newSampler != null) ? newSampler : MacroClimateSampler.DUMMY;
    }

    public static TalosClimateSample sample(World world, int x, int z) {
        return sampler.sample(world, x, z);
    }

    public interface MacroClimateSampler {
        MacroClimateSampler DUMMY = (world, x, z) ->
            TalosClimateSample.error(x, z, "Talos climate sampler not installed.");

        TalosClimateSample sample(World world, int x, int z);
    }
}
