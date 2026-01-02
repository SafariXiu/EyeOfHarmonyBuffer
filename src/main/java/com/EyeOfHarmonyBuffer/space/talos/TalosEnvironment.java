package com.EyeOfHarmonyBuffer.space.talos;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class TalosEnvironment {

    private TalosEnvironment() {}

    public static float getTemperature(World world, int x, int z) {
        BiomeGenBase biome = world.getBiomeGenForCoords(
            MathHelper.floor_double(x), MathHelper.floor_double(z));
        return biome.temperature;
    }

    public static float getHumidity(World world, int x, int z) {
        BiomeGenBase biome = world.getBiomeGenForCoords(
            MathHelper.floor_double(x), MathHelper.floor_double(z));
        return biome.rainfall;
    }

    public static float getRoughness(World world, int x, int z) {
        BiomeGenBase biome = world.getBiomeGenForCoords(
            MathHelper.floor_double(x), MathHelper.floor_double(z));
        return biome.rootHeight;
    }
}
