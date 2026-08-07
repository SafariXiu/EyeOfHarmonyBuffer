package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.fluids.EOHBFluidBlockRegistry;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.botany.BlockIntermediateResources;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.botany.ResourceClusterDef;
import com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject.WorldGenPrecipitationAcidLake;
import com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject.WorldGenYuanShiVeinTalos;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomeBase;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBoundedFeature;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBoundedFeatures;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
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

    /** 河床装饰：水下乱石堆每区块尝试次数 / 水下枯木概率。 */
    private static final double RIVERBED_ROCK_PER_CHUNK = 2.0;
    private static final double RIVERBED_LOG_PER_CHUNK = 1.0;

    private World currentWorld;

    private final WorldGenYuanShiVeinTalos veinGen = new WorldGenYuanShiVeinTalos();
    private WorldGenPrecipitationAcidLake acidLakeGen;

    private final TalosBoundedFeatures.DeadBush deadBush = new TalosBoundedFeatures.DeadBush();
    private final TalosBoundedFeatures.Mushroom mushroom = new TalosBoundedFeatures.Mushroom();
    private final TalosBoundedFeatures.FallenLog fallenLog = new TalosBoundedFeatures.FallenLog();
    private final TalosBoundedFeatures.Cactus cactus = new TalosBoundedFeatures.Cactus();
    private final TalosBoundedFeatures.Reed reed = new TalosBoundedFeatures.Reed();
    private final TalosBoundedFeatures.Waterlily waterlily = new TalosBoundedFeatures.Waterlily();
    private final TalosBoundedFeatures.Shrub shrub = new TalosBoundedFeatures.Shrub();
    private final TalosBoundedFeatures.Boulder boulder = new TalosBoundedFeatures.Boulder();
    private final TalosBoundedFeatures.RiverRockPile riverRock =
        new TalosBoundedFeatures.RiverRockPile();
    private final TalosBoundedFeatures.RiverDeadLog riverLog =
        new TalosBoundedFeatures.RiverDeadLog();

    @Override
    protected void setCurrentWorld(World world) {
        this.currentWorld = world;
        if (acidLakeGen == null && EOHBFluidBlockRegistry.precipitationAcidBlock != null) {
            acidLakeGen = new WorldGenPrecipitationAcidLake(
                EOHBFluidBlockRegistry.precipitationAcidBlock
            );
        }
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

        if (!(biome instanceof TalosBiomeBase)) {
            return;
        }

        final MacroPackageId macro = TalosMacroClimate.getMacroPackageId(
            centerX, centerZ, worldSeedInt);

        final Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);

        veinGen.generate(world, rand, chunkX, chunkZ);

        if (acidLakeGen != null && rand.nextInt(2000) == 0) {
            int lakeX = worldX0 + rand.nextInt(16);
            int lakeZ = worldZ0 + rand.nextInt(16);
            // 完全避开河道影响：整片湖的足迹上河流 mask 必须全为 0
            if (isFullyOutsideRiver(lakeX, lakeZ, worldSeedInt)) {
                acidLakeGen.generateAt(world, rand, lakeX, lakeZ);
            }
        }

        // 河床装饰：水下乱石堆 + 水下枯木（特征内部只放河道内）
        scatter(world, rand, chunk, this.riverRock, RIVERBED_ROCK_PER_CHUNK);
        scatter(world, rand, chunk, this.riverLog, RIVERBED_LOG_PER_CHUNK);

        // 资源植物簇：先于群系地表装饰放置，避免被后续装饰挤占
        scatterResourcePlants(world, rand, chunk, macro);

        decorateBiomeFeatures(
            world, rand, chunk, (TalosBiomeBase) biome
        );
    }

    /** 资源植物按宏包撒点：每约 500 区块触发一簇，每簇只撒一种植物。 */
    private void scatterResourcePlants(World world, Random rand, Chunk chunk,
                                       MacroPackageId macro) {
        ResourceClusterDef[] defs = resourcePlantsFor(macro);
        if (defs == null) {
            return;
        }
        ResourceClusterDef def = defs[rand.nextInt(defs.length)];
        Block block = BlockIntermediateResources.getBlock(def);
        if (block == null) {
            return;
        }
        new TalosBoundedFeatures.ResourcePlantCluster(
            block, def.validGround).generate(world, rand, chunk, 8, 8);
    }

    /** 宏包 -> 资源植物列表（用户拍板的最终分组）。 */
    private static ResourceClusterDef[] resourcePlantsFor(MacroPackageId macro) {
        switch (macro) {
            case TROPICAL_HUMID:
            case RIFT_TROPICAL:
                return new ResourceClusterDef[] {
                    ResourceClusterDef.JIN_CAO,
                    ResourceClusterDef.YA_ZHEN
                };
            case TEMPERATE_LOWLAND:
            case TEMPERATE_FORESTED:
            case COOL_FORESTED:
            case RIFT_TEMPERATE:
                return new ResourceClusterDef[] {
                    ResourceClusterDef.QIAO_HUA,
                    ResourceClusterDef.GAN_SHI
                };
            case TEMPERATE_HIGHLAND:
                return new ResourceClusterDef[] {
                    ResourceClusterDef.SHA_YE
                };
            case TROPICAL_DRY:
                return new ResourceClusterDef[] {
                    ResourceClusterDef.TONG_HUA_GUAN_MU
                };
            default:
                return null;
        }
    }

    /** 酸雨湖避让河流：覆盖范围内的河流 mask 必须全部等于 0。 */
    private boolean isFullyOutsideRiver(int x, int z, int worldSeedInt) {
        for (int dz = -8; dz <= 8; dz += 4) {
            for (int dx = -8; dx <= 8; dx += 4) {
                TalosRiverSystem.HydroSample hydro =
                    TalosRiverSystem.sampleHydroField(
                        x + dx, z + dz, worldSeedInt
                    );
                if (hydro == null) {
                    continue;
                }
                // 河流影响或任何水体（湖 / 湿地 / 穿河湖 / 牛轭湖）都算占用
                if (hydro.mask > 0.0 || hydro.body != null) {
                    return false;
                }
            }
        }
        return true;
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
