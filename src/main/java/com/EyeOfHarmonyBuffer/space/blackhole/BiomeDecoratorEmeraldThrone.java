package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Random;

import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenFlowers;
import net.minecraft.world.gen.feature.WorldGenTallGrass;
import net.minecraft.world.gen.feature.WorldGenTrees;

/** 翡翠王座装饰：少量树木、高草与野花（完全自绘，不依赖原版装饰器）。 */
public class BiomeDecoratorEmeraldThrone extends BiomeDecoratorSpace {

    private World currentWorld;
    private final WorldGenTrees treeGen = new WorldGenTrees(false);
    private final WorldGenTallGrass tallGrassGen = new WorldGenTallGrass(Blocks.tallgrass, 1);
    private final WorldGenFlowers flowerGen = new WorldGenFlowers(Blocks.red_flower);

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
        World world = this.currentWorld;
        Random rand = this.rand;

        for (int i = 0; i < 3; i++) {
            int x = this.chunkX + rand.nextInt(16) + 8;
            int z = this.chunkZ + rand.nextInt(16) + 8;
            int y = world.getTopSolidOrLiquidBlock(x, z);
            this.treeGen.generate(world, rand, x, y, z);
        }
        for (int i = 0; i < 12; i++) {
            int x = this.chunkX + rand.nextInt(16) + 8;
            int z = this.chunkZ + rand.nextInt(16) + 8;
            int y = world.getTopSolidOrLiquidBlock(x, z);
            this.tallGrassGen.generate(world, rand, x, y, z);
        }
        for (int i = 0; i < 4; i++) {
            int x = this.chunkX + rand.nextInt(16) + 8;
            int z = this.chunkZ + rand.nextInt(16) + 8;
            int y = world.getTopSolidOrLiquidBlock(x, z);
            this.flowerGen.generate(world, rand, x, y, z);
        }
    }
}
