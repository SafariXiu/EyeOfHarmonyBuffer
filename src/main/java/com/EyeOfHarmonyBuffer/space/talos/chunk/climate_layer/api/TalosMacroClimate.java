package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Talos 宏气候 / 宏群系 统一入口。
 *
 * 所有 Minecraft 侧代码（chunk 生成、群系、结构、装饰等）应只通过这里访问宏气候相关信息，
 * 以保证和 MacroRegionLayer / MacroPackageLayer / BiomeRegionLayer 完全一致。
 *
 * 功能：
 *   - worldSeedInt 统一计算；
 *   - 按 worldSeedInt 缓存 MacroRegionLayer / MacroPackageLayer / BiimeRegionLayer 实例；
 *   - 提供：
 *       * 原始宏群系 ID（未平滑，纯 MacroPackageLayer 结果）；
 *       * 平滑后的宏群系 ID（小块吞并后，来自 MacroRegionLayer）；
 *       * 原始 Biome（MacroPackageLayer + SubPatch 直接输出）；
 *       * 平滑后的 Biome（在上述基础上再做一次小块吞并，来自 BiomeRegionLayer）；
 *       * 当前纬度带 Belt 以及带内插值 t。
 */

public final class TalosMacroClimate {

    private TalosMacroClimate() {}

    /**
     * 将 World.getSeed() 压成 int，用于宏气候系统。
     * 建议和 TalosLandMask.getWorldSeedInt 保持一致写法。
     */
    public static int getWorldSeedInt(World world) {
        return (int) (world.getSeed() & 0x7FFFFFFFL);
    }

    /** 按 worldSeedInt 缓存宏群系平滑层。 */
    private static final Int2ObjectOpenHashMap<MacroRegionLayer> LAYERS =
        new Int2ObjectOpenHashMap<>();

    /** 按 worldSeedInt 缓存原始 MacroPackageLayer（用于 SubPatch 选 biome / raw pkg）。 */
    private static final Int2ObjectOpenHashMap<MacroPackageLayer> BASE_LAYERS =
        new Int2ObjectOpenHashMap<>();

    /** 按 worldSeedInt 缓存真实群系平滑层。 */
    private static final Int2ObjectOpenHashMap<BiomeRegionLayer> BIOME_LAYERS =
        new Int2ObjectOpenHashMap<>();

    private static MacroRegionLayer getLayer(int worldSeedInt) {
        MacroRegionLayer layer = LAYERS.get(worldSeedInt);
        if (layer == null) {
            layer = new MacroRegionLayer(worldSeedInt);
            LAYERS.put(worldSeedInt, layer);
        }
        return layer;
    }

    private static MacroPackageLayer getBaseLayer(int worldSeedInt) {
        MacroPackageLayer layer = BASE_LAYERS.get(worldSeedInt);
        if (layer == null) {
            layer = new MacroPackageLayer(worldSeedInt);
            BASE_LAYERS.put(worldSeedInt, layer);
        }
        return layer;
    }

    private static BiomeRegionLayer getBiomeLayer(int worldSeedInt) {
        BiomeRegionLayer layer = BIOME_LAYERS.get(worldSeedInt);
        if (layer == null) {
            layer = new BiomeRegionLayer(worldSeedInt);
            BIOME_LAYERS.put(worldSeedInt, layer);
        }
        return layer;
    }

    /**
     * 平滑后的宏群系 ID（做了 tile 级别的小块吞并 / 尖刺清理）。
     * 推荐所有实际玩法逻辑都用这个。
     */
    public static MacroPackageId getMacroPackageId(int worldX, int worldZ, int worldSeedInt) {
        MacroRegionLayer layer = getLayer(worldSeedInt);
        return layer.getSmoothedMacroPackageIdAt(worldX, worldZ);
    }

    /**
     * 已知该点 isLand 时的平滑宏群系 ID。
     * 与 getMacroPackageId 结果完全一致，只是省掉内部重复的 isLand 计算。
     */
    public static MacroPackageId getMacroPackageId(int worldX, int worldZ,
                                                   int worldSeedInt,
                                                   boolean isLandKnown) {
        MacroRegionLayer layer = getLayer(worldSeedInt);
        return layer.getSmoothedMacroPackageIdAt(worldX, worldZ, isLandKnown);
    }

    /**
     * 返回“原始 Biome”：
     *   - 直接走 MacroPackageLayer.getBiomeAt；
     *   - 只做 Worley 站点 + SubPatch 的划分，不做任何二次平滑。
     *
     * 主要用于：BiomeRegionLayer 采样原始数据，避免递归。
     */
    public static BiomeGenBase getRawBiome(int worldX, int worldZ, int worldSeedInt) {
        MacroPackageLayer baseLayer = getBaseLayer(worldSeedInt);
        return baseLayer.getBiomeAt(worldX, worldZ);
    }

    /**
     * 返回“平滑后的 Biome”：
     *
     * 逻辑：
     *   1. 基于 MacroPackageLayer + SubPatch 得到原始 Biome（通过 getRawBiome）；
     *   2. 在 tile 级别做一次连通分量分析 + 小块吞并（BiomeRegionLayer）；
     *
     * 效果：
     *   - 站点内部仍然是较大块的 SubPatch；
     *   - 不同站点 / 海岸线附近的小块真实群系会被合并到附近更大的 Biome 块中；
     *   - 严格遵守海陆，不会跨海陆吞并。
     */
    public static BiomeGenBase getBiome(int worldX, int worldZ, int worldSeedInt) {
        BiomeRegionLayer layer = getBiomeLayer(worldSeedInt);
        return layer.getSmoothedBiomeAt(worldX, worldZ);
    }

    /**
     * 已知该点 isLand 时的平滑群系查询，省掉内部重复的 isLandCheap。
     * 结果与 getBiome(...) 完全一致。
     */
    public static BiomeGenBase getBiome(int worldX, int worldZ,
                                        int worldSeedInt,
                                        boolean isLandKnown) {
        BiomeRegionLayer layer = getBiomeLayer(worldSeedInt);
        return layer.getSmoothedBiomeAt(worldX, worldZ, isLandKnown);
    }

    /**
     * 为某个 chunk 一次性采样 16×16 的「最终平滑群系」表。
     *
     * 数组索引约定：idx = localX * 16 + localZ（0..255）。
     * 与逐点调用 getBiome 完全一致（同一确定性函数），只是 superId 侧的
     * isLand 直接复用 LandSample 表，供地形生成器按群系查地表配置。
     */
    public static BiomeGenBase[] getBiomeChunk(
        int chunkX, int chunkZ, int worldSeedInt,
        TalosLandMask.Sample[] landSamples
    ) {
        BiomeGenBase[] out = new BiomeGenBase[16 * 16];
        BiomeRegionLayer layer = getBiomeLayer(worldSeedInt);

        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkZ * 16 + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int idx = localX * 16 + localZ;
                int worldX = chunkX * 16 + localX;

                boolean isLand = false;
                TalosLandMask.Sample s =
                    (landSamples != null) ? landSamples[idx] : null;
                if (s != null) {
                    isLand = s.isLand;
                }

                out[idx] = layer.getSmoothedBiomeAt(worldX, worldZ, isLand);
            }
        }

        return out;
    }

    /**
     * 返回当前 Z 所在的纬度带（TROPIC / SUBTROPIC / TEMPERATE / SUBPOLAR / POLAR）。
     * 注意：只和 worldZ 有关，与 worldSeedInt 无关，这里只是顺手放在统一入口。
     */
    public static ClimateLatitudes.Belt getLatitudeBelt(int worldZ) {
        return ClimateLatitudes.getBelt(worldZ);
    }

    /**
     * 返回当前 Z 在所在纬度带内的插值参数 t ∈ [0,1]。
     *   t = 0 : 靠近该带“内侧边界”（更接近热带一侧）；
     *   t = 1 : 靠近该带“外侧边界”（更接近寒带一侧）。
     */
    public static double getLatitudeBeltT(int worldZ) {
        return ClimateLatitudes.computeBeltT(worldZ);
    }

    /**
     * 返回当前 Z 到最近“热带中线”的绝对距离 d ∈ [0, MAX_D]，
     * 可用于更细的温度 / 湿度插值。
     */
    public static int getDistanceToLatitudeCenter(int worldZ) {
        return ClimateLatitudes.getDistanceToCenter(worldZ);
    }

    /**
     * 一个位置附近的“宏群系混合条目”：id + 权重。
     * weight ∈ (0,1]，整组 entries 的 weight 之和约为 1。
     */
    public static final class MacroBlendEntry {
        public final MacroPackageId id;
        public final double weight;

        public MacroBlendEntry(MacroPackageId id, double weight) {
            this.id = id;
            this.weight = weight;
        }
    }

    /**
     * 某点的宏群系混合结果。
     * entries 按 weight 从大到小排序。
     */
    public static final class MacroBlendSample {
        public final MacroBlendEntry[] entries;

        public MacroBlendSample(MacroBlendEntry[] entries) {
            this.entries = entries;
        }
    }

    /**
     * 对外统一入口：在宏群系平滑层上，对 (worldX,worldZ) 做一个
     * 小邻域空间加权统计，返回最多 maxEntries 个宏群系及其权重。
     *
     * 典型用法：
     *   - maxEntries = 1：只要主宏群系（等价于 getMacroPackageId）；
     *   - maxEntries = 2：主 + 次，用于地形 preset 混合；
     *   - maxEntries ≥3：后续更复杂玩法可用。
     */
    public static MacroBlendSample sampleMacroBlend(int worldX, int worldZ,
                                                    int worldSeedInt,
                                                    int maxEntries) {
        if (maxEntries <= 0) {
            return new MacroBlendSample(new MacroBlendEntry[0]);
        }
        MacroRegionLayer layer = getLayer(worldSeedInt);
        return layer.sampleBlendAt(worldX, worldZ, maxEntries);
    }

    /**
     * 已知该点 isLand 的宏群系混合采样，省掉内部重复的 isLandCheap 计算。
     * 结果与 sampleMacroBlend(...) 完全一致。
     */
    public static MacroBlendSample sampleMacroBlend(
        int worldX, int worldZ, int worldSeedInt, int maxEntries,
        boolean isLandHere
    ) {
        if (maxEntries <= 0) {
            return new MacroBlendSample(new MacroBlendEntry[0]);
        }
        MacroRegionLayer layer = getLayer(worldSeedInt);
        return layer.sampleBlendAt(worldX, worldZ, maxEntries, isLandHere);
    }
}
