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
    private final MacroDomain domain;
    private final double latitude01;
    private final int latitudeBandIndex;
    private final double coastSoftness;
    private final double baseHeight;
    private final double macroVariance;
    private final double microVariance;
    private final double heightVariation;
    private final double[] baseHeightGrid;
    private final double[] macroVarianceGrid;
    private final double[] microVarianceGrid;
    private final int gridResolution;
    private final double gridStep;

    public MacroSite(long id,
                     int cellX,
                     int cellZ,
                     int centerX,
                     int centerZ,
                     MacroTag macroTag,
                     MacroBiome macroBiome,
                     double continentalScore,
                     double humidity,
                     double temperature,
                     MacroDomain domain,
                     double latitude01,
                     int latitudeBandIndex,
                     double coastSoftness,
                     double baseHeight,
                     double macroVariance,
                     double microVariance,
                     double heightVariation,
                     double[] baseHeightGrid,
                     double[] macroVarianceGrid,
                     double[] microVarianceGrid,
                     int gridResolution,
                     double gridStep) {

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
        this.domain = Objects.requireNonNull(domain, "domain");
        this.latitude01 = latitude01;
        this.latitudeBandIndex = latitudeBandIndex;
        this.coastSoftness = coastSoftness;
        this.baseHeight = baseHeight;
        this.macroVariance = macroVariance;
        this.microVariance = microVariance;
        this.heightVariation = Double.isNaN(heightVariation) ? 0.0d : Math.max(0.0d, heightVariation);
        this.baseHeightGrid = baseHeightGrid;
        this.macroVarianceGrid = macroVarianceGrid;
        this.microVarianceGrid = microVarianceGrid;
        this.gridResolution = gridResolution;
        this.gridStep = gridStep;
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

    public MacroDomain domain() {
        return domain;
    }

    public double latitude01() {
        return latitude01;
    }

    public int latitudeBandIndex() {
        return latitudeBandIndex;
    }

    public double coastSoftness() {
        return coastSoftness;
    }

    public double baseHeight() {
        return baseHeight;
    }

    public double macroVariance() {
        return macroVariance;
    }

    public double microVariance() {
        return microVariance;
    }

    public double heightVariation() {
        return heightVariation;
    }

    public double distanceTo(int blockX, int blockZ) {
        double dx = blockX - centerX;
        double dz = blockZ - centerZ;
        return MathHelper.sqrt_double(dx * dx + dz * dz);
    }

    public HeightSample sampleHeightField(int blockX, int blockZ, int macroGridSize) {
        if (gridResolution <= 1 || baseHeightGrid == null) {
            return new HeightSample(baseHeight, macroVariance, microVariance, heightVariation);
        }

        double localX = (double) (blockX - cellX * macroGridSize);
        double localZ = (double) (blockZ - cellZ * macroGridSize);
        double u = MathHelper.clamp_double(localX / ((gridResolution - 1) * gridStep), 0.0d, 1.0d);
        double v = MathHelper.clamp_double(localZ / ((gridResolution - 1) * gridStep), 0.0d, 1.0d);

        return HeightSample.bilinear(baseHeightGrid, macroVarianceGrid, microVarianceGrid, gridResolution, u, v, heightVariation);
    }

    public static final class HeightSample {
        private final double base;
        private final double macroVariance;
        private final double microVariance;
        private final double heightVariation;

        public HeightSample(double base,
                            double macroVariance,
                            double microVariance,
                            double heightVariation) {
            this.base = base;
            this.macroVariance = macroVariance;
            this.microVariance = microVariance;
            this.heightVariation = heightVariation;
        }

        public double base() { return base; }
        public double macroVariance() { return macroVariance; }
        public double microVariance() { return microVariance; }
        public double variation() { return heightVariation; }

        static HeightSample bilinear(double[] baseGrid,
                                     double[] macroGrid,
                                     double[] microGrid,
                                     int res,
                                     double u,
                                     double v,
                                     double variation) {
            int maxIdx = res - 1;
            double x = u * maxIdx;
            double z = v * maxIdx;
            int x0 = (int) Math.floor(x);
            int z0 = (int) Math.floor(z);
            int x1 = Math.min(maxIdx, x0 + 1);
            int z1 = Math.min(maxIdx, z0 + 1);
            double fx = x - x0;
            double fz = z - z0;

            int idx00 = z0 * res + x0;
            int idx10 = z0 * res + x1;
            int idx01 = z1 * res + x0;
            int idx11 = z1 * res + x1;

            double base = lerp2D(baseGrid[idx00], baseGrid[idx10], baseGrid[idx01], baseGrid[idx11], fx, fz);
            double macro = lerp2D(macroGrid[idx00], macroGrid[idx10], macroGrid[idx01], macroGrid[idx11], fx, fz);
            double micro = lerp2D(microGrid[idx00], microGrid[idx10], microGrid[idx01], microGrid[idx11], fx, fz);

            return new HeightSample(base, macro, micro, variation);
        }

        private static double lerp2D(double v00, double v10, double v01, double v11, double fx, double fz) {
            double a = v00 + fx * (v10 - v00);
            double b = v01 + fx * (v11 - v01);
            return a + fz * (b - a);
        }
    }

}
