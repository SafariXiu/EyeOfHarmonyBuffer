package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject.WorldGenYuanShiVeinTalos;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomeBase;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBoundedFeature;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBoundedFeatures;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * Talos2 装饰器。
 *
 * 装饰配置直接挂在每个群系类上（TalosBiomeBase 里每个特征一个配置对象），
 * 这里按「区块中心群系」读取配置并执行：
 *   - 每个 16×16 区块只使用中心群系的一套配置（原版风格，不做逐点群系判定）；
 *   - 特征读取只在当前区块内，写入允许跨到已加载的相邻区块（±1 区块），
 *     与原版一致：populate 阶段 3×3 邻域已生成，跨区块写入不会触发连锁生成；
 *   - 树冠等大特征可平滑跨过区块边界，不再被裁掉。
 */
public class BiomeDecoratorTalos2 extends BiomeDecoratorSpace {

    private static final WorldGenYuanShiDoubleConeCluster CRYSTAL_CLUSTER_GEN =
        new WorldGenYuanShiDoubleConeCluster();

    private World currentWorld;

    private final WorldGenYuanShiVeinTalos veinGen = new WorldGenYuanShiVeinTalos();

    private final TalosBoundedFeatures.DeadBush deadBush = new TalosBoundedFeatures.DeadBush();
    private final TalosBoundedFeatures.Mushroom mushroom = new TalosBoundedFeatures.Mushroom();
    private final TalosBoundedFeatures.FallenLog fallenLog = new TalosBoundedFeatures.FallenLog();
    private final TalosBoundedFeatures.Cactus cactus = new TalosBoundedFeatures.Cactus();
    private final TalosBoundedFeatures.Reed reed = new TalosBoundedFeatures.Reed();
    private final TalosBoundedFeatures.Waterlily waterlily = new TalosBoundedFeatures.Waterlily();
    private final TalosBoundedFeatures.Shrub shrub = new TalosBoundedFeatures.Shrub();
    private final TalosBoundedFeatures.Boulder boulder = new TalosBoundedFeatures.Boulder();
    // 【已删除】riverRock / riverLog（河床乱石堆 / 河床枯木）与 acidLakeGen（酸雨湖）：
    // 它们的数据源是旧宏包 + RVR2 河网，旧轨退役后没有可用的"河"可装饰。

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

        // 晶簇切片必须在海洋/群系提前返回之前执行：
        // 跨到海洋或群系边缘的区块也要补自己的切片，否则晶簇会在边界被截断。
        CRYSTAL_CLUSTER_GEN.generate(world, rand, chunkX, chunkZ);

        final int centerX = worldX0 + 8;
        final int centerZ = worldZ0 + 8;

        // 群系**只**从世界群系管理器取（与地形/群系生成同源，不受已加载区块影响之外的差异）。
        // 旧宏气候分支已随旧轨一起删除 —— 装饰与地形不可能再读到两套群系。
        final BiomeGenBase biome = world.getBiomeGenForCoords(centerX, centerZ);
        if (biome == TalosBiomes.TALOS_OCEAN ||
            biome == TalosBiomes.TALOS_SHELF) {
            return;
        }

        if (!(biome instanceof TalosBiomeBase)) {
            return;
        }

        final Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);

        veinGen.generate(world, rand, chunkX, chunkZ);

        // 酸雨湖 / 河床乱石 / 河床枯木 / 资源植物簇都依赖**旧宏包 + RVR2 河网**，
        // 而旧轨已删除（这些系统不再运行）⇒ 这里只做群系地表装饰。
        // 这不是"暂时跳过"，而是"这些装饰的数据源已经不存在"；T3.x 换源后再接入新的水体/植被。
        decorateBiomeFeatures(world, rand, chunk, (TalosBiomeBase) biome);
    }

    /** 按群系配置逐项撒点（count = 每区块尝试次数，支持小数概率）。 */
    private void decorateBiomeFeatures(World world, Random rand,
                                       Chunk chunk, TalosBiomeBase biome) {
        scatter(world, rand, chunk, treeFor(biome), biome.treeStyle.perChunk);
        scatter(world, rand, chunk,
            new TalosBoundedFeatures.Grass(biome.grass.meta), biome.grass.perChunk);
        scatter(world, rand, chunk,
            new TalosBoundedFeatures.Grass(biome.ferns.meta), biome.ferns.perChunk);
        scatter(world, rand, chunk,
            new TalosBoundedFeatures.Flower(biome.flowers.flower), biome.flowers.perChunk);
        scatter(world, rand, chunk, this.deadBush, biome.deadBush.perChunk);
        scatter(world, rand, chunk, this.mushroom, biome.mushrooms.perChunk);
        scatter(world, rand, chunk, this.cactus, biome.cactus.perChunk);
        scatter(world, rand, chunk, this.reed, biome.reeds.perChunk);
        scatter(world, rand, chunk, this.waterlily, biome.waterlily.perChunk);
        scatter(world, rand, chunk, this.shrub, biome.shrubs.perChunk);
        scatter(world, rand, chunk,
            new TalosBoundedFeatures.Pond(biome.pond), biome.pond.perChunk);
        scatter(world, rand, chunk, this.fallenLog, biome.fallenLogs.perChunk);
        scatter(world, rand, chunk,
            new TalosBoundedFeatures.Rock(biome.rocks), biome.rocks.perChunk);
        scatter(world, rand, chunk, this.boulder, biome.boulders.perChunk);
        for (TalosBiomeBase.GroundPatchConfig patch : biome.groundPatches) {
            scatter(world, rand, chunk,
                new TalosBoundedFeatures.GroundPatch(
                    patch.block, patch.meta, patch.radius, patch.fillChance),
                patch.perChunk);
        }
    }

    /** 树木：群系挂了蓝图就用蓝图生成器，否则退回简单 TreeStyle。 */
    private TalosBoundedFeature treeFor(TalosBiomeBase biome) {
        if (biome.treeBlueprint != null) {
            return new TalosBoundedFeatures.BlueprintTree(biome.treeBlueprint);
        }
        return new TalosBoundedFeatures.Tree(biome.treeStyle);
    }

    private static void scatter(World world, Random rand, Chunk chunk,
                                TalosBoundedFeature feature, double count) {
        int n = (int) count;
        if (rand.nextDouble() < count - n) {
            n++;
        }
        for (int i = 0; i < n; i++) {
            feature.generate(world, rand, chunk,
                rand.nextInt(16), rand.nextInt(16));
        }
    }
}
