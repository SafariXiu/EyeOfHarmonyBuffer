package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Random;

import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.feature.WorldGenCactus;
import net.minecraft.world.gen.feature.WorldGenDeadBush;
import net.minecraft.world.gen.feature.WorldGenFlowers;
import net.minecraft.world.gen.feature.WorldGenTallGrass;
import net.minecraft.world.gen.feature.WorldGenTrees;

/**
 * 翡翠王座装饰：按区块中心群系分派——
 * 死亡之海：枯灌木 + 仙人掌；生之大陆：树木 + 高草 + 野花（完全自绘，不依赖原版装饰器）。
 */
public class BiomeDecoratorEmeraldThrone extends BiomeDecoratorSpace {

    private World currentWorld;
    private final WorldGenTrees treeGen = new WorldGenTrees(false);
    private final WorldGenTallGrass tallGrassGen = new WorldGenTallGrass(Blocks.tallgrass, 1);
    private final WorldGenFlowers flowerGen = new WorldGenFlowers(Blocks.red_flower);
    private final WorldGenDeadBush deadBushGen = new WorldGenDeadBush(Blocks.deadbush);
    private final WorldGenCactus cactusGen = new WorldGenCactus();

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
        // 全石头星球：暂无装饰（后续需要时在此按群系分派）
    }
}
