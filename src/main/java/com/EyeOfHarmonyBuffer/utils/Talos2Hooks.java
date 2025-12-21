package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.Talos2Continent;
import net.minecraft.world.World;

public final class Talos2Hooks {

    private Talos2Hooks() {}

    public static final class HookData {
        public final World world;
        public final SimplexNoiseOctave continentNoise;
        public final long seed;

        public HookData(World world, SimplexNoiseOctave continentNoise, long seed) {
            this.world = world;
            this.continentNoise = continentNoise;
            this.seed = seed;
        }
    }

    public static HookData resolve(World world) {
        long seed = world.getSeed();
        SimplexNoiseOctave continentNoise = new SimplexNoiseOctave(
            seed ^ Talos2Continent.CONTINENT_SALT,
            Talos2Continent.CONTINENT_OCTAVES
        );
        return new HookData(world, continentNoise, seed);
    }
}
