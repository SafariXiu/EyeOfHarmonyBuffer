package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.NoiseUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 分离的宏群系站点层：
 *   - 这一套站点要么全是“陆层”，要么全是“海层”（由 isLandLayer 决定）；
 *   - 不在内部调用 TalosLandMask；
 *   - 候选宏群系从 MacroClimateConfig.getPackagesFor(isLandLayer, belt) 来；
 *   - 查询时由调用者先用 TalosLandMask 判定当前位置海/陆，再决定用哪一层。
 */

public final class MacroSitesSeparated {

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
        public final boolean isLandSite; // 等于本层 isLandLayer
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
    private final boolean isLandLayer; // true = 陆层, false = 海层
    private final int layerSalt; // 区分陆/海两层用的盐

    private final Long2ObjectOpenHashMap<Site> siteCache = new Long2ObjectOpenHashMap<>();

    public MacroSitesSeparated(int worldSeedInt, boolean isLandLayer, int layerSalt) {
        this.worldSeedInt = worldSeedInt;
        this.isLandLayer = isLandLayer;
        this.layerSalt = layerSalt;
    }

    private static long packCell(int mx, int mz) {
        return (((long) mx) & 0xffffffffL) << 32
            | (((long) mz) & 0xffffffffL);
    }

    /**
     * 为一个宏 cell 生成站点：
     *   1. 先确定站点中心 / 纬度带 / 宏群系 ID（不在这里判海陆）；
     *   2. 再在站点内部根据该宏群系的成员群系，切出若干随机子块 SubPatch。
     *
     * 注意：
     *   - 这一层的所有站点 isLandSite == isLandLayer；
     *   - 真正 (x,z) 是海是陆，由调用者在外面用 TalosLandMask 判定。
     */
    public Site makeSiteForCell(int mx, int mz) {
        long key = packCell(mx, mz);
        Site cached = siteCache.get(key);
        if (cached != null) {
            return cached;
        }

        double cx = MacroCells.cellCenter(mx);
        double cz = MacroCells.cellCenter(mz);

        double rx = hash01(mx * 2 + 0, mz * 2 + 0, 0x13572468 ^ layerSalt) - 0.5;
        double rz = hash01(mx * 2 + 1, mz * 2 + 1, 0x24681357 ^ layerSalt) - 0.5;

        double sx = cx + rx * MacroCells.MACRO_CELL_SIZE * 0.8;
        double sz = cz + rz * MacroCells.MACRO_CELL_SIZE * 0.8;

        int iz = (int) Math.round(sz);
        ClimateLatitudes.Belt belt = ClimateLatitudes.getBelt(iz);

        List<MacroPackageId> candidates =
            MacroClimateConfig.getPackagesFor(isLandLayer, belt);
        MacroPackageId pkgId = pickPackageFromList(candidates, mx, mz, belt);

        SubPatch[] patches = initSubPatchesForSite(mx, mz, sx, sz, pkgId);

        Site site = new Site(mx, mz, sx, sz, isLandLayer, belt, pkgId, patches);
        siteCache.put(key, site);
        return site;
    }

    private MacroPackageId pickPackageFromList(List<MacroPackageId> list,
                                               int mx, int mz,
                                               ClimateLatitudes.Belt belt) {
        if (list == null || list.isEmpty()) {
            return isLandLayer ? MacroPackageId.TEMPERATE_LOWLAND : MacroPackageId.OCEANIC;
        }
        final int SALT = 0x0BADC0DE;
        double r = hash01(mx, mz, SALT ^ belt.ordinal() ^ layerSalt);
        int idx = (int) Math.floor(r * list.size());
        if (idx < 0) idx = 0;
        if (idx >= list.size()) idx = list.size() - 1;
        return list.get(idx);
    }

    /**
     * SubPatch 生成逻辑基本照抄你原来的 MacroSites.initSubPatchesForSite，
     * 只是随机种子里额外加入了 layerSalt。
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
            int salt = (pkgId.ordinal() * 0x9E3779B9) ^ layerSalt;
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
        int seed = worldSeedInt ^ salt ^ layerSalt;
        return NoiseUtil.hash2(ix, iz, seed);
    }
}
