package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 宏群系主入口：
 *   - 基于 Worley 细胞噪声选站点；
 *   - 每个站点在生成时就决定好：
 *       * 宏群系 ID
 *       * 站点海陆类型
 *       * 纬度带
 *       * 若干子块 SubPatch（每个绑定一个具体 Biome）
 *   - 查询时：
 *       * 首先看当前 (x,z) 的海陆；
 *       * 只在“海陆类型一致”的站点中选最近的一个作为 owner；
 *       * 再在该站点的 SubPatch 中选最近的一个，作为真实群系。
 */

public class MacroPackageLayer {

    private final int worldSeedInt;
    private final MacroSites sites;

    public MacroPackageLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
        this.sites = new MacroSites(worldSeedInt);
    }

    /**
     * 内部工具：返回 (x,z) 对应的 owner 站点。
     */
    private MacroSites.Site findOwnerSite(int x, int z) {
        boolean isLandHere = TalosLandMask.isLand(x, z, worldSeedInt);

        int mx = MacroCells.worldToMacroCell(x);
        int mz = MacroCells.worldToMacroCell(z);

        MacroSites.Site bestSite = null;
        double bestDist2 = Double.POSITIVE_INFINITY;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = mx + dx;
                int cz = mz + dz;

                MacroSites.Site site = sites.makeSiteForCell(cx, cz);
                if (site.isLandSite != isLandHere) {
                    continue;
                }

                double dxw = x - site.sx;
                double dzw = z - site.sz;
                double dist2 = dxw * dxw + dzw * dzw;

                if (dist2 < bestDist2) {
                    bestDist2 = dist2;
                    bestSite = site;
                }
            }
        }

        if (bestSite == null) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = mx + dx;
                    int cz = mz + dz;

                    MacroSites.Site site = sites.makeSiteForCell(cx, cz);

                    double dxw = x - site.sx;
                    double dzw = z - site.sz;
                    double dist2 = dxw * dxw + dzw * dzw;

                    if (dist2 < bestDist2) {
                        bestDist2 = dist2;
                        bestSite = site;
                    }
                }
            }
        }

        return bestSite;
    }

    /**
     * 主接口：返回 (x,z) 对应的宏群系 ID。
     * 语义不变，只是内部复用 findOwnerSite。
     */
    public MacroPackageId getMacroPackageIdAt(int x, int z) {
        MacroSites.Site bestSite = findOwnerSite(x, z);
        if (bestSite == null) {
            return MacroPackageId.TEMPERATE_LOWLAND;
        }
        return bestSite.pkgId;
    }

    /**
     * 便捷接口：直接返回一个具体的 BiomeGenBase。
     *
     * 逻辑：
     *   1. 找到 owner 站点；
     *   2. 在该站点的 SubPatch 数组中选与 (x,z) 距离最近的一个；
     *   3. 直接返回该 SubPatch 绑定的 Biome。
     *
     * 这样：
     *   - 大尺度由 Worley 站点决定（宏群系块）；
     *   - 小尺度由站点内部的 SubPatch 决定（宏群系内的少数大块真实群系），
     *   - 不再出现“每几个方块就换一个 biome”的高频噪声。
     */
    public BiomeGenBase getBiomeAt(int x, int z) {
        MacroSites.Site site = findOwnerSite(x, z);
        if (site == null) {
            BiomeGenBase[] list = MacroPackageDefs.getBiomes(MacroPackageId.TEMPERATE_LOWLAND);
            if (list == null || list.length == 0) {
                return null;
            }
            return list[0];
        }

        MacroSites.SubPatch[] patches = site.patches;
        if (patches == null || patches.length == 0) {
            BiomeGenBase[] list = MacroPackageDefs.getBiomes(site.pkgId);
            if (list == null || list.length == 0) {
                return null;
            }
            return list[0];
        }

        MacroSites.SubPatch bestPatch = null;
        double bestDist2 = Double.POSITIVE_INFINITY;

        for (MacroSites.SubPatch p : patches) {
            double dx = x - p.px;
            double dz = z - p.pz;
            double dist2 = dx * dx + dz * dz;
            if (dist2 < bestDist2) {
                bestDist2 = dist2;
                bestPatch = p;
            }
        }

        if (bestPatch == null || bestPatch.biome == null) {
            BiomeGenBase[] list = MacroPackageDefs.getBiomes(site.pkgId);
            if (list == null || list.length == 0) {
                return null;
            }
            return list[0];
        }

        return bestPatch.biome;
    }
}
