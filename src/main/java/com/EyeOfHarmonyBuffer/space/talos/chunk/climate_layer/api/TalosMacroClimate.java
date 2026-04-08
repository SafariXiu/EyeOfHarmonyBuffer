package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroRegionLayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Talos 宏气候 / 宏群系 统一入口。
 *
 * 所有 Minecraft 侧代码（chunk 生成、群系、结构、装饰等）应只通过这里访问宏气候相关信息，
 * 以保证和 MacroRegionLayer / MacroPackageLayer 完全一致。
 *
 * 功能：
 *   - worldSeedInt 统一计算；
 *   - 按 worldSeedInt 缓存 MacroRegionLayer 实例；
 *   - 提供：
 *       * 原始宏群系 ID（未平滑，纯 Worley 结果）；
 *       * 平滑后的宏群系 ID（小块吞并后）；
 *       * 根据平滑宏群系 ID 选出的 BiomeGenBase；
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

    private static final Int2ObjectOpenHashMap<MacroRegionLayer> LAYERS =
        new Int2ObjectOpenHashMap<>();

    private static MacroRegionLayer getLayer(int worldSeedInt) {
        MacroRegionLayer layer = LAYERS.get(worldSeedInt);
        if (layer == null) {
            layer = new MacroRegionLayer(worldSeedInt);
            LAYERS.put(worldSeedInt, layer);
        }
        return layer;
    }

    /**
     * 原始宏群系 ID（不做平滑，只是 MacroPackageLayer 的直接输出）。
     * 主要用于 debug 对比 “raw vs smoothed”。
     */
    public static MacroPackageId getRawMacroPackageId(int worldX, int worldZ, int worldSeedInt) {
        MacroRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRawMacroPackageIdAt(worldX, worldZ);
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
     * 方便直接拿到一个具体 Biome：
     *   - 内部使用的是 MacroRegionLayer.getBiomeAt：
     *       * 当前版本：先取平滑后的宏群系 ID，再通过 MacroPackageDefs.pickDeterministicBiome 选群系。
     */
    public static BiomeGenBase getBiome(int worldX, int worldZ, int worldSeedInt) {
        MacroRegionLayer layer = getLayer(worldSeedInt);
        return layer.getBiomeAt(worldX, worldZ);
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
}
