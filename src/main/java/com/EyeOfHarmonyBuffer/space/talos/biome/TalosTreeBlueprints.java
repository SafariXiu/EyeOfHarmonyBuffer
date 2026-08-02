package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * 各群系的树木蓝图集中定义处。
 *
 * 每个群系在构造器里挂自己那份蓝图（treeBlueprint），
 * 树长什么样只在这里调，不需要动生成器。
 */
public final class TalosTreeBlueprints {

    private TalosTreeBlueprints() {}

    private static TalosBiomeBase.TreeBlueprint.CanopyLayer layer(
            int yOffset, int radius, boolean skipCenter) {
        return new TalosBiomeBase.TreeBlueprint.CanopyLayer(
            yOffset, radius, skipCenter);
    }

    private static TalosBiomeBase.TreeBlueprint tree(
            Block wood, int woodMeta,
            Block leaf, int leafMeta,
            int trunkMin, int trunkMax,
            double leanChance, double leafDensity, double jitter,
            TalosBiomeBase.TreeBlueprint.CanopyLayer... layers) {
        TalosBiomeBase.TreeBlueprint b = new TalosBiomeBase.TreeBlueprint();
        b.woodBlock = wood;
        b.woodMeta = woodMeta;
        b.leafBlock = leaf;
        b.leafMeta = leafMeta;
        b.trunkMin = trunkMin;
        b.trunkMax = trunkMax;
        b.leanChance = leanChance;
        b.leafDensity = leafDensity;
        b.jitter = jitter;
        b.layers = layers;
        return b;
    }

    private static TalosBiomeBase.TreeBlueprint branches(
            TalosBiomeBase.TreeBlueprint b,
            double chance, int count, int length, int rise) {
        b.branches.chance = chance;
        b.branches.count = count;
        b.branches.length = length;
        b.branches.rise = rise;
        return b;
    }

    private static TalosBiomeBase.TreeBlueprint vines(
            TalosBiomeBase.TreeBlueprint b, double chance) {
        b.vines = true;
        b.vineChance = chance;
        return b;
    }

    // ===== 平原 / 草原 =====
    /** 平原橡树：中等主干 + 2 根短枝 + 饱满圆冠。 */
    public static final TalosBiomeBase.TreeBlueprint PLAINS_OAK = branches(
        tree(Blocks.log, 0, Blocks.leaves, 0, 4, 6, 0.35, 0.85, 0.3,
            layer(-1, 2, true), layer(0, 3, true), layer(1, 2, true)),
        0.6, 2, 2, 1);

    /** 温带草原孤树：高挑树干 + 稀疏小圆冠，常歪斜。 */
    public static final TalosBiomeBase.TreeBlueprint STEPPE_TREE = branches(
        tree(Blocks.log, 0, Blocks.leaves, 0, 5, 7, 0.55, 0.75, 0.4,
            layer(0, 2, true), layer(1, 2, true)),
        0.4, 1, 2, 1);

    /** 暖温带草原灌树：低矮歪斜 + 松散小冠。 */
    public static final TalosBiomeBase.TreeBlueprint WARM_STEPPE_TREE = branches(
        tree(Blocks.log, 0, Blocks.leaves, 0, 3, 5, 0.6, 0.6, 0.5,
            layer(0, 2, true), layer(1, 2, true)),
        0.3, 1, 2, 1);

    /** 稀树草原金合欢：歪干 + 扁平伞冠，树叶稀疏。 */
    public static final TalosBiomeBase.TreeBlueprint SAVANNA_ACACIA =
        tree(Blocks.log2, 0, Blocks.leaves2, 0, 4, 6, 0.9, 0.45, 0.5,
            layer(0, 3, true), layer(1, 2, true));

    // ===== 森林 =====
    /** 温带森林阔叶树：粗壮 + 2~3 根枝 + 大而繁茂的圆冠。 */
    public static final TalosBiomeBase.TreeBlueprint FOREST_OAK = branches(
        tree(Blocks.log, 0, Blocks.leaves, 0, 5, 8, 0.3, 0.95, 0.3,
            layer(-1, 2, true), layer(0, 3, true), layer(1, 3, true),
            layer(2, 2, true)),
        0.8, 2, 3, 1);

    /** 寒带针叶林塔松：高主干 + 多层收尖的层叠树冠。 */
    public static final TalosBiomeBase.TreeBlueprint SPRUCE_TOWER =
        tree(Blocks.log, 1, Blocks.leaves, 1, 7, 11, 0.1, 0.9, 0.2,
            layer(-1, 2, true), layer(-2, 1, true), layer(-3, 2, true),
            layer(-4, 1, true), layer(-5, 2, true));

    /** 高山矮针叶：矮小稀疏的圆锥。 */
    public static final TalosBiomeBase.TreeBlueprint MOUNTAIN_SPRUCE =
        tree(Blocks.log, 1, Blocks.leaves, 1, 3, 5, 0.25, 0.6, 0.3,
            layer(0, 1, true), layer(-1, 2, true), layer(-2, 1, true));

    /** 亚极地冻原矮松：贴地的小圆锥。 */
    public static final TalosBiomeBase.TreeBlueprint TUNDRA_PINE =
        tree(Blocks.log, 1, Blocks.leaves, 1, 2, 4, 0.3, 0.7, 0.3,
            layer(0, 1, true), layer(-1, 2, true), layer(-2, 1, true));

    // ===== 热带 / 盆地 =====
    /** 热带雨林巨树：2×2 高主干 + 大树枝 + 巨大树冠 + 藤蔓。 */
    public static final TalosBiomeBase.TreeBlueprint RAINFOREST_GIANT = vines(branches(
        tree(Blocks.log, 3, Blocks.leaves, 3, 12, 18, 0.0, 0.95, 0.2,
            layer(-1, 4, true), layer(0, 4, true), layer(1, 4, true),
            layer(2, 3, true)),
        0.8, 3, 3, 2),
        0.35);

    /** 湿热带盆地雨林树：中等主干 + 圆冠 + 少量藤蔓。 */
    public static final TalosBiomeBase.TreeBlueprint BASIN_TREE = vines(branches(
        tree(Blocks.log, 0, Blocks.leaves, 0, 5, 8, 0.3, 0.85, 0.3,
            layer(-1, 2, true), layer(0, 3, true), layer(1, 2, true)),
        0.5, 2, 2, 1),
        0.2);

    // ===== 高原 =====
    /** 高原橡树：挺拔 + 短枝 + 圆冠。 */
    public static final TalosBiomeBase.TreeBlueprint PLATEAU_OAK = branches(
        tree(Blocks.log, 0, Blocks.leaves, 0, 5, 7, 0.4, 0.8, 0.3,
            layer(-1, 2, true), layer(0, 3, true), layer(1, 2, true)),
        0.6, 2, 2, 1);

    /** 高山针叶允许落在石头 / 砂砾上。 */
    static {
        MOUNTAIN_SPRUCE.groundBlocks = new Block[] {
            Blocks.stone, Blocks.gravel, Blocks.grass, Blocks.dirt
        };
    }
}
