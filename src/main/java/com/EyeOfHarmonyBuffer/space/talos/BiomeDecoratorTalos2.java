package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject.WorldGenYuanShiDepositTalos;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Random;

public class BiomeDecoratorTalos2 extends BiomeDecoratorSpace {

    private World currentWorld;

    private final WorldGenYuanShiDepositTalos yuanShiGen = new WorldGenYuanShiDepositTalos();

    @Override
    protected void setCurrentWorld(World world) {
        this.currentWorld = world;
    }

    @Override
    protected World getCurrentWorld() {
        return this.currentWorld;
    }

    @Override
    protected void decorate() {
        final World world = this.currentWorld;
        final Random rand = this.rand;

        if (world == null || rand == null) {
            return;
        }

        final int worldX0 = this.chunkX;
        final int worldZ0 = this.chunkZ;

        final int chunkX = worldX0 / 16;
        final int chunkZ = worldZ0 / 16;

        final int centerX = worldX0 + 8;
        final int centerZ = worldZ0 + 8;

        final int worldSeedInt = TalosMacroClimate.getWorldSeedInt(world);
        final BiomeGenBase biome = TalosMacroClimate.getBiome(centerX, centerZ, worldSeedInt);

        if (biome == TalosBiomes.TALOS_OCEAN ||
            biome == TalosBiomes.TALOS_SHELF) {
            return;
        }

        yuanShiGen.generate(world, rand, chunkX, chunkZ);
    }
}
