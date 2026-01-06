package com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.MacroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class TalosMacroCellBuilder implements IMacroCellProvider {

    public static final int GRID_SIZE = 17;

    private final MacroFieldProvider macroField;
    private final CoastlineProvider coastlineProvider;

    public TalosMacroCellBuilder(MacroFieldProvider macroField,
                                 CoastlineProvider coastlineProvider) {
        this.macroField = Objects.requireNonNull(macroField, "macroField");
        this.coastlineProvider = Objects.requireNonNull(coastlineProvider, "coastlineProvider");
    }

    @Override
    public ChunkProviderTalos2.ChunkShoreCache build(int chunkX, int chunkZ) {
        ChunkProviderTalos2.ChunkShoreCache cache = new ChunkProviderTalos2.ChunkShoreCache();
        populate(chunkX, chunkZ, cache);
        return cache;
    }

    @Override
    public ChunkProviderTalos2.ChunkShoreCache peekCached(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public void invalidate(int chunkX, int chunkZ) {
        // raw builder没有缓存
    }

    @Override
    public void invalidateAll() {
        // raw builder没有缓存
    }

    public void populate(int chunkX, int chunkZ, ChunkProviderTalos2.ChunkShoreCache out) {
        for (int lx = 0; lx < GRID_SIZE; lx++) {
            for (int lz = 0; lz < GRID_SIZE; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                MacroSample sample = macroField.sample(gx, gz);
                CoastlineSample coast = coastlineProvider.sample(gx, gz, sample.primary());

                out.macroPrimary[lx][lz]   = sample.primary();
                out.macroSecondary[lx][lz] = sample.secondary();
                out.macroBlend[lx][lz]     = (float) sample.blendPrimary();
                out.macroTier[lx][lz]      = sample.tier();
                out.macroPlateId[lx][lz]   = sample.plateId();
                out.macroPlateau[lx][lz]   = sample.plateauHeight();
                out.anchorWeight[lx][lz]   = sample.anchorWeight();
                out.hardEdge[lx][lz]       = sample.hardEdge();
                out.macroBaseHeight[lx][lz] = sample.macroBaseHeight();
                out.macroPatchVariant[lx][lz] = sample.patchVariant();
                out.macroPatchSingle[lx][lz]  = sample.patchSingleBiome();
                out.macroPatchEdge[lx][lz]    = (float) sample.patchEdgeBlend();

                out.macroWet[lx][lz]  = sample.secondary().isHumid() ? 0.85F : 0.2F;
                out.macroCold[lx][lz] = (sample.primary() == MacroTag.TUNDRA) ? 0.95F : 0.1F;
                out.macroCoast[lx][lz] = (float) MathHelper.clamp_double(
                    1.0D - (coast.distanceToCoast() / 96.0D), 0.0D, 1.0D);

                out.isLand[lx][lz] = coast.isLand();
                out.dist[lx][lz]   = clamp16(coast.distanceToCoast());
                out.beachW[lx][lz] = clamp16(coast.beachWidth());
                out.shelfW[lx][lz] = clamp16(coast.shelfWidth());

                ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = out.macroContext[lx][lz];
                cell.primary = sample.primary();
                cell.secondary = sample.secondary();
                cell.blendPrimary = sample.blendPrimary();
                cell.tier = sample.tier();
                cell.plateId = sample.plateId();
                cell.plateauAnchor = sample.plateauHeight();
                cell.macroBaseHeight = sample.macroBaseHeight();
                cell.anchorWeight = sample.anchorWeight();
                cell.hardEdge = sample.hardEdge();
                cell.patchVariant = sample.patchVariant();
                cell.patchSingleBiome = sample.patchSingleBiome();
                cell.patchEdgeBlend = sample.patchEdgeBlend();
                cell.isLand = coast.isLand();
                cell.distToCoast = clamp16(coast.distanceToCoast());
                cell.beachWidth = clamp16(coast.beachWidth());
                cell.shelfWidth = clamp16(coast.shelfWidth());
            }
        }
    }

    private static short clamp16(int value) {
        if (value < 0) {
            return 0;
        }
        return (short) Math.min(value, 0xFFFF);
    }
}
