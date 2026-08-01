package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.LandMask16;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverTerrainModifier;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosBaseTerrain;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 单个 chunk 的世界生成「采样上下文」。
 *
 * 目标：把原来每个方块各自重复计算的采样（海陆、宏群系、河流、基础高度）
 * 收敛成每 chunk 只算一次、之后全流程（地形填充 / 河岸塑形 / 河流挖掘）只读表。
 *
 * 所有值都是 (worldX, worldZ, worldSeedInt) 的确定性函数，
 * 因此本类只做缓存、不改变任何生成结果。
 *
 * 数组索引约定：idx = localX * 16 + localZ（0..255）。
 */
public final class TalosChunkContext {

    public static final int CHUNK_SIZE = 16;

    public final int chunkX;
    public final int chunkZ;
    public final int worldSeedInt;
    public final int seaLevel;

    /** chunk 级海陆掩码（与 TalosLandMask.getLandMaskForChunk 一致）。 */
    public final LandMask16 landMask;

    /** 每列一次完整海陆采样（含 superId / 权重）。 */
    public final TalosLandMask.Sample[] land;

    /** 每列平滑后的宏群系 ID（OCEANIC 表示海洋宏群系）。 */
    public final MacroPackageId[] macroPkg;

    /** 每列最终（平滑后）的群系，供地表 / 装饰逻辑按群系查配置。 */
    public final BiomeGenBase[] biomes;

    /** 每列水文场（河流距离 / 宽度 / mask / 源口坐标等）。 */
    public TalosRiverSystem.HydroSample[] hydro;

    /** 每列「未考虑河流」的基础地形高度。 */
    public final double[] baseHeight;

    /** 每列经过 R=2 盒式模糊的河岸强度 bankIntensity（宏群系边界平滑用）。 */
    public final double[] bankIntensity;

    private TalosChunkContext(int chunkX, int chunkZ, int worldSeedInt, int seaLevel,
                              LandMask16 landMask,
                              TalosLandMask.Sample[] land) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldSeedInt = worldSeedInt;
        this.seaLevel = seaLevel;
        this.landMask = landMask;
        this.land = land;
        this.macroPkg = new MacroPackageId[CHUNK_SIZE * CHUNK_SIZE];
        this.biomes = new BiomeGenBase[CHUNK_SIZE * CHUNK_SIZE];
        this.hydro = new TalosRiverSystem.HydroSample[CHUNK_SIZE * CHUNK_SIZE];
        this.baseHeight = new double[CHUNK_SIZE * CHUNK_SIZE];
        this.bankIntensity = new double[CHUNK_SIZE * CHUNK_SIZE];
    }

    /**
     * 构建某个 chunk 的采样上下文。
     *
     * 流程：
     *   1. 每列做一次完整海陆采样（LandSample[256]）；
     *   2. 每列做一次平滑宏群系采样（已知 isLand，省掉重复的 isLand 判断）；
     *   3. 每列做一次河流水文采样（superId 直接读 LandSample，整链只查表）；
     *   4. 每列做一次基础高度（复用 LandSample + 宏群系邻域采样缓存）；
     *   5. 计算河岸强度模糊图（内部 16x16 读宏群系表，halo 直接采样）。
     */
    public static TalosChunkContext create(int chunkX, int chunkZ,
                                           int worldSeedInt, int seaLevel) {
        TalosLandMask.Sample[] land =
            TalosLandMask.sampleChunk(chunkX, chunkZ, worldSeedInt);
        LandMask16 landMask =
            TalosLandMask.getLandMaskForChunk(chunkX, chunkZ, worldSeedInt);

        TalosChunkContext ctx = new TalosChunkContext(
            chunkX, chunkZ, worldSeedInt, seaLevel, landMask, land
        );
        ctx.build();
        return ctx;
    }

    private void build() {
        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            int worldZ = chunkZ * CHUNK_SIZE + localZ;
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int idx = localX * CHUNK_SIZE + localZ;
                int worldX = chunkX * CHUNK_SIZE + localX;

                TalosLandMask.Sample s = land[idx];
                boolean isLand = s != null && s.isLand;

                macroPkg[idx] = TalosMacroClimate.getMacroPackageId(
                    worldX, worldZ, worldSeedInt, isLand
                );
            }
        }

        // 群系表：与群系管理器输出完全一致的最终平滑群系
        BiomeGenBase[] biomeGrid = TalosMacroClimate.getBiomeChunk(
            chunkX, chunkZ, worldSeedInt, land
        );
        System.arraycopy(biomeGrid, 0, biomes, 0, biomeGrid.length);

        hydro = TalosRiverSystem.sampleHydroFieldChunk(
            chunkX, chunkZ, worldSeedInt, land
        );

        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            int worldZ = chunkZ * CHUNK_SIZE + localZ;
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int idx = localX * CHUNK_SIZE + localZ;
                int worldX = chunkX * CHUNK_SIZE + localX;

                baseHeight[idx] = TalosBaseTerrain.sampleBaseHeight(
                    worldX, worldZ, worldSeedInt, seaLevel,
                    land[idx]
                );
            }
        }

        computeBlurredBank();
    }

    /** 与 ChunkProviderTalos2 原 computeBlurredBankForChunk 完全一致的 R=2 盒式模糊。 */
    private void computeBlurredBank() {
        final int R = 2;
        final int EXT_SIZE = CHUNK_SIZE + 2 * R;

        double[][] rawExt = new double[EXT_SIZE][EXT_SIZE];

        for (int extZ = 0; extZ < EXT_SIZE; extZ++) {
            int worldZ = chunkZ * CHUNK_SIZE + (extZ - R);
            for (int extX = 0; extX < EXT_SIZE; extX++) {
                int worldX = chunkX * CHUNK_SIZE + (extX - R);

                int innerX = extX - R;
                int innerZ = extZ - R;

                double k;
                if (innerX >= 0 && innerX < CHUNK_SIZE
                    && innerZ >= 0 && innerZ < CHUNK_SIZE) {
                    // 内部 16x16 直接读宏群系表
                    k = TalosRiverTerrainModifier.bankIntensityFor(
                        macroPkg[innerX * CHUNK_SIZE + innerZ]
                    );
                } else {
                    // halo 一圈（±2）直接采样，结果与原来 20x20 全量采样一致
                    k = TalosRiverTerrainModifier.bankIntensityFor(
                        TalosMacroClimate.getMacroPackageId(
                            worldX, worldZ, worldSeedInt
                        )
                    );
                }

                rawExt[extX][extZ] = k;
            }
        }

        double[][] tmpExt = new double[EXT_SIZE][EXT_SIZE];
        for (int z = 0; z < EXT_SIZE; z++) {
            for (int x = 0; x < EXT_SIZE; x++) {
                double sum = 0.0;
                int count = 0;
                for (int dx = -R; dx <= R; dx++) {
                    int sx = x + dx;
                    if (sx < 0 || sx >= EXT_SIZE) continue;
                    sum += rawExt[sx][z];
                    count++;
                }
                tmpExt[x][z] = sum / count;
            }
        }

        double[][] blurExt = new double[EXT_SIZE][EXT_SIZE];
        for (int x = 0; x < EXT_SIZE; x++) {
            for (int z = 0; z < EXT_SIZE; z++) {
                double sum = 0.0;
                int count = 0;
                for (int dz = -R; dz <= R; dz++) {
                    int sz = z + dz;
                    if (sz < 0 || sz >= EXT_SIZE) continue;
                    sum += tmpExt[x][sz];
                    count++;
                }
                blurExt[x][z] = sum / count;
            }
        }

        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            int extZ = localZ + R;
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int extX = localX + R;
                bankIntensity[localX * CHUNK_SIZE + localZ] =
                    blurExt[extX][extZ];
            }
        }
    }

}
