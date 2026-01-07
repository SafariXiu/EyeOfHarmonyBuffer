package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class MacroSite {

    private final long id;
    private final int cellX;
    private final int cellZ;
    private final int centerX;
    private final int centerZ;
    private final MacroTag macroTag;
    private final MacroBiome macroBiome;
    private final double continentalScore;
    private final double humidity;
    private final double temperature;

    public MacroSite(long id,
                     int cellX,
                     int cellZ,
                     int centerX,
                     int centerZ,
                     MacroTag macroTag,
                     MacroBiome macroBiome,
                     double continentalScore,
                     double humidity,
                     double temperature) {

        this.id = id;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.macroTag = Objects.requireNonNull(macroTag, "macroTag");
        this.macroBiome = Objects.requireNonNull(macroBiome, "macroBiome");
        this.continentalScore = continentalScore;
        this.humidity = humidity;
        this.temperature = temperature;
    }

    public long id() {
        return id;
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

    public MacroTag macroTag() {
        return macroTag;
    }

    public MacroBiome macroBiome() {
        return macroBiome;
    }

    public double continentalScore() {
        return continentalScore;
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
}
