package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import net.minecraft.util.MathHelper;

public final class MicroSite {

    private final long id;
    private final long macroSiteId;
    private final int cellX;
    private final int cellZ;
    private final int centerX;
    private final int centerZ;
    private final int variantIndex;
    private final MacroBiome.MacroBiomeVariant variant;
    private final double humidity;
    private final double temperature;

    public MicroSite(long id,
                     long macroSiteId,
                     int cellX,
                     int cellZ,
                     int centerX,
                     int centerZ,
                     int variantIndex,
                     MacroBiome.MacroBiomeVariant variant,
                     double humidity,
                     double temperature) {

        this.id = id;
        this.macroSiteId = macroSiteId;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.variantIndex = variantIndex;
        this.variant = variant;
        this.humidity = humidity;
        this.temperature = temperature;
    }

    public long id() {
        return id;
    }

    public long macroSiteId() {
        return macroSiteId;
    }

    public int cellX() {
        return cellX;
    }

    public int cellZ() {
        return cellZ;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int variantIndex() {
        return variantIndex;
    }

    public MacroBiome.MacroBiomeVariant variant() {
        return variant;
    }

    public boolean hasVariant() {
        return variantIndex >= 0 && variant != null;
    }

    public double humidity() {
        return humidity;
    }

    public double temperature() {
        return temperature;
    }

    public double distanceTo(int blockX, int blockZ) {
        double dx = blockX - centerX;
        double dz = blockZ - centerZ;
        return MathHelper.sqrt_double(dx * dx + dz * dz);
    }

    public static int safeVariantIndex(MacroBiome macroBiome,
                                       MacroBiome.MacroBiomeVariant variant) {
        if (macroBiome == null || variant == null || macroBiome.variants == null) {
            return -1;
        }
        int idx = macroBiome.variants.indexOf(variant);
        return Math.max(-1, idx);
    }

    @Override
    public String toString() {
        return "MicroSite{" +
            "id=" + id +
            ", macroSiteId=" + macroSiteId +
            ", cell=(" + cellX + "," + cellZ + ")" +
            ", center=(" + centerX + "," + centerZ + ")" +
            ", variantIndex=" + variantIndex +
            '}';
    }
}
