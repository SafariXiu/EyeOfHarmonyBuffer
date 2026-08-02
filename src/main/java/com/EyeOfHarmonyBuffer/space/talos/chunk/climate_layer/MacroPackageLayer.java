package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;
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

    /** 挤压带核心强度阈值：超过该值时全部选高山（山脉），形成连续山脊。 */
    private static final double CONVERGENT_MOUNTAIN_STRENGTH = 0.5;

    /** 挤压带最高峰强度阈值：大于等于该值时全部选最高峰群系。 */
    private static final double CONVERGENT_PEAK_STRENGTH = 0.7;

    private final int worldSeedInt;

    /** 陆地 Worley 站点图（只负责陆宏群系候选及其子块）。 */
    private final MacroSitesSeparated landSites;
    /** 海洋 Worley 站点图（只负责海宏群系候选及其子块）。 */
    private final MacroSitesSeparated oceanSites;

    public MacroPackageLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
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
     * 板块边界对群系选择的覆盖（宏包覆盖的细化）：
     *   - 挤压带外缘（0.2 ≤ 强度 ≤ 0.5）：高原 / 山脉低频混合；
     *   - 挤压带核心（0.5 &lt; 强度 &lt; 0.7）：全部为高山（山脉），形成连续山脊；
     *   - 挤压带主峰（强度 ≥ 0.7）：全部为最高峰群系（高山雪峰），形成最高主峰带；
     *   - 分离带：对应纬度峡谷变体的低频连贯群系；
     *   - 分离带排他：只要缝合线中存在强度 ≥ 阈值的 DIVERGENT 影响，
     *     就优先整带覆盖为裂谷（即使挤压影响更强），防止山脉侵入裂谷。
     * 不满足覆盖条件时返回 null。
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

        // 分离带排他：DIVERGENT 缝合线影响进入阈值即整带覆盖为裂谷
        double divergent = TalosLandMask.maxBoundaryStrength(
            PlateBoundaryState.DIVERGENT, s);
        if (divergent >= TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return MacroPackageDefs.pickCoherentBiome(
                riftVariantAt(z), x, z, worldSeedInt
            );
        }

        double w = s.plateBoundaryWeight;
        if (w < TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return null;
        }

        switch (s.plateBoundaryState) {
            case CONVERGENT:
                if (w >= CONVERGENT_PEAK_STRENGTH) {
                    return TalosBiomes.TALOS_ALPINE;
                }
                if (w > CONVERGENT_MOUNTAIN_STRENGTH) {
                    return TalosBiomes.TALOS_MOUNTAINS;
                }
                return MacroPackageDefs.pickCoherentBiome(
                    MacroPackageId.TEMPERATE_HIGHLAND, x, z, worldSeedInt
                );
            case DIVERGENT:
                return MacroPackageDefs.pickCoherentBiome(
                    riftVariantAt(z), x, z, worldSeedInt
                );
            default:
                return null;
        }
    }

    /**
     * 板块边界对陆地宏包的覆盖：
     *   - 挤压（CONVERGENT）+ 强度 ≥ 0.7 → 最高峰宏包（MOUNTAIN_PEAK）；
     *   - 挤压（CONVERGENT）+ 强度 ≥ 阈值 → 高山宏包（TEMPERATE_HIGHLAND）；
     *   - 分离（DIVERGENT）+ 强度 ≥ 阈值 → 按纬度带选择峡谷宏包变体；
     *   - 分离带排他：只要缝合线中存在强度 ≥ 阈值的 DIVERGENT 影响，
     *     就优先覆盖为裂谷宏包，防止多板块交汇处山脉侵入裂谷带。
     * 其余状态不覆盖。返回覆盖后的宏包，不满足条件时返回 null。
     */
    private MacroPackageId plateBoundaryOverride(int x, int z, MacroPackageId landPkg) {
        if (landPkg == null || landPkg == MacroPackageId.OCEANIC) {
            return null;
        }

        TalosLandMask.Sample s = TalosLandMask.sampleFull(x, z, worldSeedInt);
        if (s == null || !s.isLand) {
            return null;
        }

        // 分离带排他：DIVERGENT 缝合线影响进入阈值即整带覆盖为裂谷
        double divergent = TalosLandMask.maxBoundaryStrength(
            PlateBoundaryState.DIVERGENT, s);
        if (divergent >= TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return riftVariantAt(z);
        }

        if (s.plateBoundaryWeight < TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return null;
        }

        switch (s.plateBoundaryState) {
            case CONVERGENT:
                if (s.plateBoundaryWeight >= CONVERGENT_PEAK_STRENGTH) {
                    return MacroPackageId.MOUNTAIN_PEAK;
                }
                return MacroPackageId.TEMPERATE_HIGHLAND;
            case DIVERGENT:
                return riftVariantAt(z);
            default:
                return null;
        }
    }

    /** 按纬度带选择峡谷宏包变体。 */
    private MacroPackageId riftVariantAt(int z) {
        switch (ClimateLatitudes.getBelt(z)) {
            case TROPIC:
            case SUBTROPIC:
                return MacroPackageId.RIFT_TROPICAL;
            case TEMPERATE:
                return MacroPackageId.RIFT_TEMPERATE;
            case SUBPOLAR:
            case POLAR:
            default:
                return MacroPackageId.RIFT_POLAR;
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
