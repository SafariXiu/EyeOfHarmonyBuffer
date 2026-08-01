package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Talos 群系公共基类。
 *
 * 覆盖原版群系装饰入口 decorate(...) 为空实现：
 * Galacticraft 的 ChunkProviderSpaceLakes.populate 会调用
 * biome.decorate() 触发原版 theBiomeDecorator（草 / 树 / 花、
 * 水岩浆泉、沙砾黏土斑块、湖泊等），这里全部阻断，
 * 让地表完全由地形生成器与 BiomeDecoratorTalos2 显式控制。
 *
 * 装饰配置直接挂在群系类上（下方每个特征一个配置对象）：
 * 每个群系在自己的构造函数里配置，BiomeDecoratorTalos2 按区块中心群系读取并执行。
 * 所有配置里的 perChunk 都是「每区块尝试次数」，支持小数概率：
 * 1.0 = 每区块一次；0.5 = 平均每两个区块一次；0.25 = 平均每四个区块一次。
 */
public abstract class TalosBiomeBase extends GSBiomeGenBase {

    /**
     * 简单特征配置：只有数量（枯灌木 / 仙人掌 / 甘蔗 / 睡莲 / 灌木 / 2×2 巨石等）。
     */
    public static class SimpleConfig {
        public double perChunk = 0;

        public SimpleConfig() {}

        public SimpleConfig(double perChunk) {
            this.perChunk = perChunk;
        }
    }

    /** 高草 / 蕨配置。 */
    public static class GrassConfig {
        public double perChunk = 0;
        /** 1 = 高草，2 = 蕨。 */
        public int meta = 1;

        public GrassConfig() {}

        public GrassConfig(double perChunk, int meta) {
            this.perChunk = perChunk;
            this.meta = meta;
        }
    }

    /** 花配置。 */
    public static class FlowerConfig {
        public double perChunk = 0;
        public Block flower = Blocks.red_flower;

        public FlowerConfig() {}

        public FlowerConfig(double perChunk, Block flower) {
            this.perChunk = perChunk;
            this.flower = flower;
        }
    }

    /** 水洼配置。 */
    public static class PondConfig {
        public double perChunk = 0;
        /** 半径：5 ≈ 10×10 的不规则水体。 */
        public int radius = 5;
        /** 水底深度（中心地表向下挖几格）。 */
        public int depth = 2;
        /** 岸格铺沙概率 0~1。 */
        public double rimSandChance = 0.5;
        /** 允许落点的地表方块（默认草/泥土）。 */
        public Block[] groundBlocks = {Blocks.grass, Blocks.dirt};

        public PondConfig() {}

        public PondConfig(double perChunk, int radius, int depth, double rimSandChance) {
            this.perChunk = perChunk;
            this.radius = radius;
            this.depth = depth;
            this.rimSandChance = rimSandChance;
        }
    }

    /** 大型不规则石头配置。 */
    public static class RockConfig {
        public double perChunk = 0;
        public Block block = Blocks.stone;
        /** 占地边长（5 = 最大约 5×5）。 */
        public int footprint = 5;
        public int minHeight = 2;
        public int maxHeight = 5;
        /** 允许落点的地表方块（默认草/泥土）。 */
        public Block[] groundBlocks = {Blocks.grass, Blocks.dirt};

        public RockConfig() {}

        public RockConfig(double perChunk, Block block,
                          int footprint, int minHeight, int maxHeight) {
            this.perChunk = perChunk;
            this.block = block;
            this.footprint = footprint;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }
    }

    /** 地表斑块配置（泥土 / 石头 / 砂砾 / 沙共用，可配置多份）。 */
    public static class GroundPatchConfig {
        public double perChunk = 0;
        public Block block = Blocks.dirt;
        public int meta = 0;
        /** 斑块半径。 */
        public int radius = 2;
        /** 每格填充概率 0~1（越小斑块越稀疏）。 */
        public double fillChance = 0.5;

        public GroundPatchConfig() {}

        public GroundPatchConfig(double perChunk, Block block, int meta,
                                 int radius, double fillChance) {
            this.perChunk = perChunk;
            this.block = block;
            this.meta = meta;
            this.radius = radius;
            this.fillChance = fillChance;
        }
    }

    /** 树冠形状。 */
    public enum TreeShape {
        /** 圆冠（橡树风）：三层 + 顶部补尖。 */
        ROUND,
        /** 平冠（金合欢风）：两层宽 + 一层收窄。 */
        FLAT,
        /** 锥形冠（针叶树风）。 */
        CONE,
        /** 2×2 主干 + 大圆冠（丛林树风）。 */
        JUNGLE
    }

    /**
     * 树木风格：结构 / 方块 / 尺寸全部由群系自己定义，便于逐群系查看调参。
     * leafMeta 只写种类位（0~3），生成时自动加上"永不腐烂"位(4)。
     */
    public static class TreeStyle {
        /** 每区块树木数量（小数 = 概率）。 */
        public double perChunk = 0;
        public Block woodBlock = Blocks.log;
        public int woodMeta = 0;
        public Block leafBlock = Blocks.leaves;
        public int leafMeta = 0;
        /** 树干高度范围（不含树冠）。 */
        public int trunkMin = 4;
        public int trunkMax = 6;
        public TreeShape shape = TreeShape.ROUND;
        /** 树冠半径。 */
        public int canopyRadius = 2;
        /** 树叶密度 0~1：1 = 完整树冠，越小越稀疏。 */
        public double leafDensity = 1.0;
        /** 树干歪斜概率 0~1：1 = 必然在中途横向错开 1 格。 */
        public double leanChance = 0.0;
        /** 允许落点的地表方块（默认草/泥土）。 */
        public Block[] groundBlocks = {Blocks.grass, Blocks.dirt};
    }

    /** 树（结构 / 方块 / 数量）。 */
    public TreeStyle treeStyle = new TreeStyle();
    /** 高草 / 蕨。 */
    public GrassConfig grass = new GrassConfig();
    /** 花。 */
    public FlowerConfig flowers = new FlowerConfig();
    /** 枯灌木。 */
    public SimpleConfig deadBush = new SimpleConfig();
    /** 仙人掌。 */
    public SimpleConfig cactus = new SimpleConfig();
    /** 甘蔗。 */
    public SimpleConfig reeds = new SimpleConfig();
    /** 睡莲。 */
    public SimpleConfig waterlily = new SimpleConfig();
    /** 灌木。 */
    public SimpleConfig shrubs = new SimpleConfig();
    /** 水洼。 */
    public PondConfig pond = new PondConfig();
    /** 大型石头。 */
    public RockConfig rocks = new RockConfig();
    /** 2×2 巨石。 */
    public SimpleConfig boulders = new SimpleConfig();
    /** 地表斑块（可添加多份不同方块）。 */
    public final java.util.List<GroundPatchConfig> groundPatches =
        new java.util.ArrayList<GroundPatchConfig>();

    public TalosBiomeBase(int id) {
        super(id);
    }

    @Override
    public void decorate(World world, Random random, int chunkX, int chunkZ) {
        // 禁用原版装饰；统一走 BiomeDecoratorTalos2
    }
}
