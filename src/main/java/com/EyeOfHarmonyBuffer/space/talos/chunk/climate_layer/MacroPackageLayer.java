package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TectonicStyle;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 宏群系主入口（陆/海两套 Worley 场叠加版）：
 *   - landSites / oceanSites 分别维护各自完整的宏群系 Worley 站点图（互不干扰）；
 *   - 对任意 (x,z)，都会在两套图中各自找到最近站点；
 *   - 最后再用 TalosLandMask 的逐方块 isLand 精确裁剪：
 *       * isLandHere == true  → 使用陆地站点的宏群系 / Biome；
 *       * isLandHere == false → 使用海洋站点的宏群系 / Biome；
 *
 * 这样可以保证：
 *   - 海洋 Worley 结构只由 oceanSites 决定，不会被任何陆站点打断；
 *   - 小岛（在 isLand==true 的 block 上）仍然使用陆宏群系/真实陆地 Biome；
 *   - 海岸线由 TalosLandMask 精确切割，不存在“海 Biom 在陆地上/反之”的情况。
 */

public class MacroPackageLayer {

    private final int worldSeedInt;
    /** 网格级构造风格层（板块边界决策的唯一来源）。 */
    private final TectonicStyleLayer tectonicStyles;

    /** 陆地 Worley 站点图（只负责陆宏群系候选及其子块）。 */
    private final MacroSitesSeparated landSites;
    /** 海洋 Worley 站点图（只负责海宏群系候选及其子块）。 */
    private final MacroSitesSeparated oceanSites;

    public MacroPackageLayer(int worldSeedInt, TectonicStyleLayer tectonicStyles) {
        this.worldSeedInt = worldSeedInt;
        this.tectonicStyles = tectonicStyles;
        this.landSites  = new MacroSitesSeparated(worldSeedInt, true,  0x1234ABCD);
        this.oceanSites = new MacroSitesSeparated(worldSeedInt, false, 0x5678EF90);
    }

    /**
     * 在某一套 MacroSitesSeparated 图中寻找 (x,z) 的 owner 站点。
     * 不看海陆，只在给定 sites 内做 Worley 最近点搜索。
     */
    private MacroSitesSeparated.Site findOwnerInLayer(MacroSitesSeparated sites,
                                                      int x, int z,
                                                      int mx, int mz) {
        MacroSitesSeparated.Site bestSite = null;
        double bestDist2 = Double.POSITIVE_INFINITY;

        final int MAX_RADIUS = 3;

        for (int radius = 1; radius <= MAX_RADIUS; radius++) {
            boolean foundInThisRadius = false;

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int cx = mx + dx;
                    int cz = mz + dz;

                    MacroSitesSeparated.Site site = sites.makeSiteForCell(cx, cz);
                    if (site == null) {
                        continue;
                    }

                    foundInThisRadius = true;

                    double dxw = x - site.sx;
                    double dzw = z - site.sz;
                    double dist2 = dxw * dxw + dzw * dzw;

                    if (dist2 < bestDist2) {
                        bestDist2 = dist2;
                        bestSite = site;
                    }
                }
            }

            if (foundInThisRadius && bestSite != null) {
                break;
            }
        }

        return bestSite;
    }

    /** 在陆地 Worley 场中找到 owner 站点（不看当前块是否为陆）。 */
    private MacroSitesSeparated.Site findLandOwnerSite(int x, int z) {
        int mx = MacroCells.worldToMacroCell(x);
        int mz = MacroCells.worldToMacroCell(z);
        return findOwnerInLayer(landSites, x, z, mx, mz);
    }

    /** 在海洋 Worley 场中找到 owner 站点（不看当前块是否为海）。 */
    private MacroSitesSeparated.Site findOceanOwnerSite(int x, int z) {
        int mx = MacroCells.worldToMacroCell(x);
        int mz = MacroCells.worldToMacroCell(z);
        return findOwnerInLayer(oceanSites, x, z, mx, mz);
    }

    /**
     * 主接口：返回 (x,z) 对应的宏群系 ID。
     *
     * 逻辑：
     *   - 对当前 block 精确计算 isLandHere（TalosLandMask，方块级）；
     *   - 同时在陆/海两套 Worley 图中各自找到最近站点（landSite / oceanSite）；
     *   - isLandHere == true  → 使用 landSite 的 pkgId（若找不到，用 TEMPERATE_LOWLAND 兜底）；
     *   - isLandHere == false → 使用 oceanSite 的 pkgId（若找不到，用 OCEANIC 兜底）。
     *
     * 这样：
     *   - 海洋宏观结构只由 oceanSites 控制，不会被陆站点打断；
     *   - 小岛上的 block（isLandHere==true）仍然使用 landSites 的宏群系。
     */
    public MacroPackageId getMacroPackageIdAt(int x, int z) {
        boolean isLandHere = TalosLandMask.isLandCheap(x, z, worldSeedInt);

        MacroSitesSeparated.Site landSite  = findLandOwnerSite(x, z);
        MacroSitesSeparated.Site oceanSite = findOceanOwnerSite(x, z);

        MacroPackageId result;
        if (isLandHere) {
            result = (landSite != null && landSite.pkgId != null)
                ? landSite.pkgId
                : MacroPackageId.TEMPERATE_LOWLAND;
        } else {
            result = (oceanSite != null && oceanSite.pkgId != null)
                ? oceanSite.pkgId
                : MacroPackageId.OCEANIC;
        }

        MacroPackageId overridden = plateBoundaryOverride(x, z, result);
        return (overridden != null) ? overridden : result;
    }

    /**
     * 便捷接口：直接返回一个具体的 BiomeGenBase。
     *
     * 逻辑：
     *   1. 精确计算当前 block 的 isLandHere；
     *   2. 在陆/海两套 Worley 场中分别找到最近站点 landSite / oceanSite；
     *   3. 如果 isLandHere == true：
     *        - 优先使用 landSite 的 SubPatch：
     *            * 在 landSite.patches 中找最近的一个 SubPatch；
     *            * 返回该 SubPatch 绑定的 Biome；
     *        - 若 landSite 为空或无子块，则从其 pkgId 对应宏包的 Biome 列表中取第一个；
     *        - 若仍为空，兜底 TALOS_PLAINS。
     *      如果 isLandHere == false（海）：
     *        - 同理使用 oceanSite 的 SubPatch / 宏包。
     *
     * 这样可以保证：
     *   - 海域的 Biome 完全从海宏包中选，连续、干净；
     *   - 小岛上的 Biome 完全从陆宏包中选，岛内小块由上层 BiomeRegionLayer 再统一。
     */
    public BiomeGenBase getBiomeAt(int x, int z) {
        boolean isLandHere = TalosLandMask.isLandCheap(x, z, worldSeedInt);

        MacroSitesSeparated.Site landSite  = findLandOwnerSite(x, z);
        MacroSitesSeparated.Site oceanSite = findOceanOwnerSite(x, z);

        if (isLandHere) {
            MacroPackageId baseId = (landSite != null && landSite.pkgId != null)
                ? landSite.pkgId
                : MacroPackageId.TEMPERATE_LOWLAND;

            BiomeGenBase boundaryBiome = plateBoundaryBiomeOverride(x, z, baseId);
            if (boundaryBiome != null) {
                return boundaryBiome;
            }

            // 群系覆盖钩子（组合根注册的山地层实现；气候层不依赖山地层）
            BiomeGenBase overrideBiome = TalosMacroClimate
                .getBiomeOverride(x, z, worldSeedInt);
            if (overrideBiome != null) {
                return overrideBiome;
            }

            if (landSite != null) {
                BiomeGenBase biome = pickBiomeFromSite(landSite, x, z);
                if (biome != null) {
                    return biome;
                }
            }

            BiomeGenBase[] list = MacroPackageDefs.getBiomes(MacroPackageId.TEMPERATE_LOWLAND);
            if (list != null && list.length > 0) {
                return list[0];
            }
            return null;
        } else {
            if (oceanSite != null) {
                BiomeGenBase biome = pickBiomeFromSite(oceanSite, x, z);
                if (biome != null) {
                    return biome;
                }
            }

            BiomeGenBase[] list = MacroPackageDefs.getBiomes(MacroPackageId.OCEANIC);
            if (list != null && list.length > 0) {
                return list[0];
            }
            return null;
        }
    }

    /**
     * 板块边界对群系选择的覆盖：
     * 读取网格级构造风格（TectonicStyleLayer），按风格返回对应群系：
     *   - RIFT → 纬度裂谷变体；HIGHLAND → 高原/山脉混合；
     *   - MOUNTAINS → 高山；PEAK → 最高峰；NONE → 不覆盖。
     * 判定规则与阈值已全部收敛到风格层，这里不再逐点采样。
     */
    private BiomeGenBase plateBoundaryBiomeOverride(int x, int z,
                                                    MacroPackageId landPkg) {
        if (landPkg == null || landPkg == MacroPackageId.OCEANIC) {
            return null;
        }

        TalosLandMask.Sample s = TalosLandMask.sampleFull(x, z, worldSeedInt);
        if (s == null || !s.isLand) {
            return null;
        }

        TectonicStyle style = this.tectonicStyles.sampleAt(x, z).style;
        switch (style) {
            case MOUNTAINS:
                return TalosBiomes.TALOS_MOUNTAINS;
            case PEAK:
                return TalosBiomes.TALOS_ALPINE;
            case HIGHLAND:
                return MacroPackageDefs.pickCoherentBiome(
                    MacroPackageId.TEMPERATE_HIGHLAND, x, z, worldSeedInt
                );
            case NONE:
            default:
                return null;
        }
    }

    /**
     * 板块边界对陆地宏包的覆盖：
     * 读取网格级构造风格：RIFT → 裂谷变体；PEAK → MOUNTAIN_PEAK；
     * HIGHLAND / MOUNTAINS → TEMPERATE_HIGHLAND；NONE → 不覆盖。
     */
    private MacroPackageId plateBoundaryOverride(int x, int z, MacroPackageId landPkg) {
        if (landPkg == null || landPkg == MacroPackageId.OCEANIC) {
            return null;
        }

        TalosLandMask.Sample s = TalosLandMask.sampleFull(x, z, worldSeedInt);
        if (s == null || !s.isLand) {
            return null;
        }

        TectonicStyle style = this.tectonicStyles.sampleAt(x, z).style;
        switch (style) {
            case PEAK:
                return MacroPackageId.MOUNTAIN_PEAK;
            case HIGHLAND:
            case MOUNTAINS:
                return MacroPackageId.TEMPERATE_HIGHLAND;
            case NONE:
            default:
                return null;
        }
    }

    /**
     * 在给定站点的 SubPatch 数组中选与 (x,z) 最近的一个，返回其 biome。
     * 若子块为空或都没有 biome，则退回宏包的第一个 Biome。
     */
    private BiomeGenBase pickBiomeFromSite(MacroSitesSeparated.Site site, int x, int z) {
        if (site == null) {
            return null;
        }

        MacroSitesSeparated.SubPatch[] patches = site.patches;
        if (patches != null && patches.length > 0) {
            MacroSitesSeparated.SubPatch bestPatch = null;
            double bestDist2 = Double.POSITIVE_INFINITY;

            for (MacroSitesSeparated.SubPatch p : patches) {
                double dx = x - p.px;
                double dz = z - p.pz;
                double dist2 = dx * dx + dz * dz;
                if (dist2 < bestDist2) {
                    bestDist2 = dist2;
                    bestPatch = p;
                }
            }

            if (bestPatch != null && bestPatch.biome != null) {
                return bestPatch.biome;
            }
        }

        BiomeGenBase[] list = MacroPackageDefs.getBiomes(site.pkgId);
        if (list != null && list.length > 0) {
            return list[0];
        }
        return null;
    }
}
