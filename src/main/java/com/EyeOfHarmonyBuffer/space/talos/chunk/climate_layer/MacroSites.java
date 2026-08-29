package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.NoiseUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 宏群系站点生成：一个 macro cell -> 一个站点：
 *   - (sx, sz) 站点世界坐标
 *   - isLandSite 站点处是陆 or 海
 *   - belt       站点纬度带
 *   - pkgId      选中的宏群系 ID
 *
 *   扩展：
 *   - 每个站点内部再切若干“子块” SubPatch：
 *       * 每个子块有自己的中心 (px, pz)
 *       * 每个子块绑定一个具体 BiomeGenBase
 *   - 查询时，通过最近的 SubPatch 决定站点内部的真实群系。
 */

public final class MacroSites {

    /**
     * 站点内部的子块（真实群系的一个“势力范围中心”）。
     */
    public static final class SubPatch {
        public final double px, pz;
        public final BiomeGenBase biome;

        public SubPatch(double px, double pz, BiomeGenBase biome) {
            this.px = px;
            this.pz = pz;
            this.biome = biome;
        }
    }

    public static final class Site {
        public final int mx, mz;
        public final double sx, sz;
        public final boolean isLandSite;
        public final ClimateLatitudes.Belt belt;
        public final MacroPackageId pkgId;
        public final SubPatch[] patches;

        public Site(int mx, int mz,
                    double sx, double sz,
                    boolean isLandSite,
                    ClimateLatitudes.Belt belt,
                    MacroPackageId pkgId,
                    SubPatch[] patches) {
            this.mx = mx;
            this.mz = mz;
            this.sx = sx;
            this.sz = sz;
            this.isLandSite = isLandSite;
            this.belt = belt;
            this.pkgId = pkgId;
            this.patches = patches;
        }
    }

    private final int worldSeedInt;

    private final Long2ObjectOpenHashMap<Site> siteCache = new Long2ObjectOpenHashMap<>();

    public MacroSites(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
    }

    private static long packCell(int mx, int mz) {
        return (((long) mx) & 0xffffffffL) << 32
            | (((long) mz) & 0xffffffffL);
    }

    /**
     * 为一个宏 cell 生成站点：
     *   1. 先确定站点中心 / 海陆 / 纬度带 / 宏群系 ID；
     *   2. 再在站点内部根据该宏群系的成员群系，切出若干随机子块 SubPatch。
     */
    public Site makeSiteForCell(int mx, int mz) {
        long key = packCell(mx, mz);
        Site cached = siteCache.get(key);
        if (cached != null) {
            return cached;
        }

        double cx = MacroCells.cellCenter(mx);
        double cz = MacroCells.cellCenter(mz);

        double rx = hash01(mx * 2 + 0, mz * 2 + 0, 0x13572468) - 0.5;
        double rz = hash01(mx * 2 + 1, mz * 2 + 1, 0x24681357) - 0.5;

        double sx = cx + rx * MacroCells.MACRO_CELL_SIZE * 0.8;
        double sz = cz + rz * MacroCells.MACRO_CELL_SIZE * 0.8;

        int ix = (int) Math.round(sx);
        int iz = (int) Math.round(sz);

        boolean isLandSite = TalosLandMask.isLandCheap(ix, iz, worldSeedInt);
        ClimateLatitudes.Belt belt = ClimateLatitudes.getBelt(iz);

        List<MacroPackageId> candidates = MacroClimateConfig.getPackagesFor(isLandSite, belt);
        MacroPackageId pkgId = pickPackageFromList(candidates, mx, mz, belt);

        SubPatch[] patches = initSubPatchesForSite(mx, mz, sx, sz, pkgId);

        Site site = new Site(mx, mz, sx, sz, isLandSite, belt, pkgId, patches);
        siteCache.put(key, site);
        return site;
    }

    private MacroPackageId pickPackageFromList(List<MacroPackageId> list,
                                               int mx, int mz,
                                               ClimateLatitudes.Belt belt) {
        if (list == null || list.isEmpty()) {
            return MacroPackageId.TEMPERATE_LOWLAND;
        }
        final int SALT = 0x0BADC0DE;
        double r = hash01(mx, mz, SALT ^ belt.ordinal());
        int idx = (int) Math.floor(r * list.size());
        if (idx < 0) idx = 0;
        if (idx >= list.size()) idx = list.size() - 1;
        return list.get(idx);
    }

    /**
     * 为某个站点生成内部子块：
     *   - 在站点周围随机撒 numPatches 个点（SubPatch 中心）；
     *   - 给每个点生成一个角度 angle，并按 angle 排序；
     *   - 再把这些点按“连续段”分配给宏群系的各个成员 Biome，保证：
     *       * 每种 Biome 是“一整段连续 patch”，而不是 A B A B 乱插；
     *       * numPatches 会在 3–7 之间（上限视成员群系数量而定）。
     */
    private SubPatch[] initSubPatchesForSite(int mx, int mz,
                                             double sx, double sz,
                                             MacroPackageId pkgId) {
        BiomeGenBase[] biomes = MacroPackageDefs.getBiomes(pkgId);
        if (biomes == null || biomes.length == 0) {
            return new SubPatch[0];
        }

        int biomeCount = biomes.length;

        int minPatches = Math.min(3, biomeCount);
        int maxPatches = Math.min(7, biomeCount * 2);

        int numPatches = minPatches;
        if (maxPatches > minPatches) {
            int salt = (pkgId.ordinal() * 0x9E3779B9);
            int seed = (mx * 734287 + mz * 912931) ^ worldSeedInt ^ salt;
            Random rand = new Random(seed);
            numPatches = minPatches + rand.nextInt(maxPatches - minPatches + 1);

            class Tmp {
                double angle;
                double px, pz;
            }

            Tmp[] tmp = new Tmp[numPatches];

            double cellRadius = MacroCells.MACRO_CELL_SIZE * 0.5;
            double inner = cellRadius * 0.4;
            double outer = cellRadius * 0.9;

            for (int i = 0; i < numPatches; i++) {
                Tmp t = new Tmp();

                double theta = rand.nextDouble() * Math.PI * 2.0;

                double r = inner + rand.nextDouble() * (outer - inner);

                t.angle = theta;
                t.px = sx + Math.cos(theta) * r;
                t.pz = sz + Math.sin(theta) * r;

                tmp[i] = t;
            }

            Arrays.sort(tmp, Comparator.comparingDouble(o -> o.angle));

            SubPatch[] patches = new SubPatch[numPatches];

            int baseRun = numPatches / biomeCount;
            int extra = numPatches % biomeCount;

            int index = 0;
            for (int b = 0; b < biomeCount; b++) {
                int run = baseRun + (b < extra ? 1 : 0);
                BiomeGenBase biome = biomes[b];

                for (int k = 0; k < run && index < numPatches; k++) {
                    Tmp t = tmp[index];
                    patches[index] = new SubPatch(t.px, t.pz, biome);
                    index++;
                }
            }

            while (index < numPatches) {
                Tmp t = tmp[index];
                patches[index] = new SubPatch(t.px, t.pz, biomes[biomeCount - 1]);
                index++;
            }

            return patches;
        } else {
            SubPatch[] patches = new SubPatch[numPatches];

            double cellRadius = MacroCells.MACRO_CELL_SIZE * 0.4;
            for (int i = 0; i < numPatches; i++) {
                double theta = (Math.PI * 2.0 / numPatches) * i;
                double px = sx + Math.cos(theta) * cellRadius;
                double pz = sz + Math.sin(theta) * cellRadius;
                BiomeGenBase biome = biomes[i % biomeCount];
                patches[i] = new SubPatch(px, pz, biome);
            }
            return patches;
        }
    }

    /**
     * 基于 NoiseUtil.hash2 的 [0,1) 哈希。
     *
     * @param ix,iz 任意整数坐标（我们用 cell 坐标或其线性组合）
     * @param salt  额外盐值，用于区分不同用途
     */
    private double hash01(int ix, int iz, int salt) {
        int seed = worldSeedInt ^ salt;
        return NoiseUtil.hash2(ix, iz, seed);
    }
}
