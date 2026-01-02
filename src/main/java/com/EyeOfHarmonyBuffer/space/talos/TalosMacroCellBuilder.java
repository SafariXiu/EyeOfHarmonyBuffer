package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;

import java.util.Objects;

public final class TalosMacroCellBuilder {
    private static final MacroBiome[] MACRO_VALUES = MacroBiome.values();

    private final MacroBiomeField macroField;
    private final CoastlineAtlas coastline;
    private final SimplexNoiseOctave terrainNoise;

    public TalosMacroCellBuilder(MacroBiomeField field,
                                 CoastlineAtlas coastline,
                                 SimplexNoiseOctave terrainNoise) {
        this.macroField = Objects.requireNonNull(field);
        this.coastline = Objects.requireNonNull(coastline);
        this.terrainNoise = Objects.requireNonNull(terrainNoise);
    }

    public ChunkProviderTalos2.ChunkShoreCache build(int chunkX, int chunkZ) {
        ChunkProviderTalos2.ChunkShoreCache cache = new ChunkProviderTalos2.ChunkShoreCache();
        populate(chunkX, chunkZ, cache);
        return cache;
    }

    public void populate(int chunkX, int chunkZ, ChunkProviderTalos2.ChunkShoreCache out) {
        macroField.sample(chunkX, chunkZ, out);
        fillCoastlineAndMacroCells(chunkX, chunkZ, out);
    }

    private void fillCoastlineAndMacroCells(int chunkX, int chunkZ,
                                            ChunkProviderTalos2.ChunkShoreCache out) {
        for (int lx = 0; lx <= 16; lx++) {
            for (int lz = 0; lz <= 16; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                MacroBiome primary = safeMacro(out.macroPrimary[lx][lz]);
                MacroBiome secondary = safeMacro(out.macroSecondary[lx][lz]);
                double blend = (out.macroBlend[lx][lz] & 0xFF) / 255.0;

                boolean isLand = coastline.isLand(gx, gz);
                int dist = clamp(coastline.distanceToCoast(gx, gz));
                int beach = clamp(coastline.beachWidth(gx, gz, primary));
                int shelf = clamp(coastline.shelfWidth(gx, gz, primary));

                ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = out.macroContext[lx][lz];
                cell.primary = primary;
                cell.secondary = secondary;
                cell.blendPrimary = blend;
                cell.tier = out.macroTier[lx][lz];
                cell.plateId = out.macroPlateId[lx][lz];
                cell.plateauAnchor = out.macroPlateau[lx][lz];
                cell.isLand = isLand;
                cell.distToCoast = (short) dist;
                cell.beachWidth = (short) beach;
                cell.shelfWidth = (short) shelf;
                cell.patchVariant = out.macroPatchVariant[lx][lz];
                cell.patchSingleBiome = (out.macroPatchFlags[lx][lz] & 0x1) != 0;
                cell.patchEdgeBlend = (out.macroPatchEdge[lx][lz] & 0xFF) / 255.0D;

                short macroBase = (short) Math.round(sampleMacroBaseHeight(
                    gx, gz, primary, secondary, blend));
                cell.macroBaseHeight = macroBase;
                out.macroBaseHeight[lx][lz] = macroBase;
                out.isLand[lx][lz] = isLand;
                out.dist[lx][lz] = (short) dist;
                out.beachW[lx][lz] = (short) beach;
                out.shelfW[lx][lz] = (short) shelf;
            }
        }
    }

    private double sampleMacroBaseHeight(int gx, int gz,
                                         MacroBiome primary,
                                         MacroBiome secondary,
                                         double blend) {
        MacroBiome.MacroHeightProfile hpA = primary.height;
        MacroBiome.MacroHeightProfile hpB = secondary.height;

        double t = clamp01(blend);
        double base = lerp(hpA.absoluteMin, hpB.absoluteMin, t);
        double top = lerp(hpA.absoluteMax, hpB.absoluteMax, t);
        double offset = lerp(hpA.baseHeightOffset, hpB.baseHeightOffset, t);
        double variation = lerp(hpA.heightVariation, hpB.heightVariation, t);

        double macro = lerp(base, top, t);
        double plateau = offset * 32.0D;
        double micro = terrainNoise.noise(gx * 0.0008D, gz * 0.0008D) * variation * 3.0D;

        return clamp(macro + plateau + micro, base, top);
    }

    private MacroBiome safeMacro(byte id) {
        MacroBiome b = MACRO_VALUES[id & 0xFF];
        return (b != null) ? b : MacroBiome.PLAINS_TEMPERATE;
    }

    private int clamp(int v) {
        if (v < 0) return 0;
        if (v > 65535) return 65535;
        return v;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
