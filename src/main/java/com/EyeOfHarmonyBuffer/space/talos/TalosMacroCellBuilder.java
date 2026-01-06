package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.chunk.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class TalosMacroCellBuilder {
    public static final int GRID_SIZE = 17;

    private final MacroBiomeField macroField;
    private final CoastlineAtlas coastline;

    public TalosMacroCellBuilder(MacroBiomeField field, CoastlineAtlas coastline) {
        this.macroField = Objects.requireNonNull(field, "macroField");
        this.coastline = Objects.requireNonNull(coastline, "coastline");
    }

    public ChunkProviderTalos2.ChunkShoreCache build(int chunkX, int chunkZ) {
        ChunkProviderTalos2.ChunkShoreCache cache = new ChunkProviderTalos2.ChunkShoreCache();
        populate(chunkX, chunkZ, cache);
        return cache;
    }

    public void populate(int chunkX, int chunkZ, ChunkProviderTalos2.ChunkShoreCache out) {
        for (int lx = 0; lx < GRID_SIZE; lx++) {
            for (int lz = 0; lz < GRID_SIZE; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                MacroBiomeField.MacroSample sample = macroField.sampleWorldCell(gx, gz);

                // write raw arrays
                out.macroPrimary[lx][lz]   = (byte) sample.primary.ordinal();
                out.macroSecondary[lx][lz] = (byte) sample.secondary.ordinal();
                out.macroBlend[lx][lz]     = encodeToByte(sample.blendPrimary);
                out.macroTier[lx][lz]      = sample.tier;
                out.macroPlateId[lx][lz]   = sample.plateId;
                out.macroPlateau[lx][lz]   = sample.plateauHeight;
                out.anchorWeight[lx][lz]   = sample.anchorWeight;
                out.hardEdge[lx][lz]       = sample.hardEdge;
                out.macroBaseHeight[lx][lz] = sample.macroBaseHeight;

                out.macroPatchVariant[lx][lz] = sample.patchVariant;
                out.macroPatchFlags[lx][lz]   = (byte) (sample.patchSingleBiome ? 1 : 0);
                out.macroPatchEdge[lx][lz]    = encodeToByte(sample.patchEdgeBlend);

                // coastline + MacroCell context
                boolean isLand = coastline.isLand(gx, gz);
                int dist  = clamp16(coastline.distanceToCoast(gx, gz));
                int beach = clamp16(coastline.beachWidth(gx, gz, sample.primary));
                int shelf = clamp16(coastline.shelfWidth(gx, gz, sample.primary));

                out.isLand[lx][lz] = isLand;
                out.dist[lx][lz]   = (short) dist;
                out.beachW[lx][lz] = (short) beach;
                out.shelfW[lx][lz] = (short) shelf;

                ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = out.macroContext[lx][lz];
                cell.primary = sample.primary;
                cell.secondary = sample.secondary;
                cell.blendPrimary = sample.blendPrimary;
                cell.tier = sample.tier;
                cell.plateId = sample.plateId;
                cell.plateauAnchor = sample.plateauHeight;
                cell.macroBaseHeight = sample.macroBaseHeight;
                cell.anchorWeight = sample.anchorWeight;
                cell.hardEdge = sample.hardEdge;

                cell.patchVariant = sample.patchVariant;
                cell.patchSingleBiome = sample.patchSingleBiome;
                cell.patchEdgeBlend = sample.patchEdgeBlend;

                cell.isLand = isLand;
                cell.distToCoast = (short) dist;
                cell.beachWidth = (short) beach;
                cell.shelfWidth = (short) shelf;
            }
        }

        if (MacroBiomeField.TalosDebugFlags.DEBUG_PLATE_ID) {
            System.out.printf(
                "[PlatePopulate] chunk=(%d,%d)%n",
                chunkX, chunkZ
            );
        }
    }

    private static byte encodeToByte(double value) {
        return (byte) Math.round(MathHelper.clamp_double(value, 0.0D, 1.0D) * 255.0D);
    }

    private static int clamp16(int value) {
        if (value < 0) return 0;
        return Math.min(value, 0xFFFF);
    }
}
