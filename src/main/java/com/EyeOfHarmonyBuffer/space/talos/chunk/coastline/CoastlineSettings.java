package com.EyeOfHarmonyBuffer.space.talos.chunk.coastline;

public final class CoastlineSettings {

    private final double seaLevel;
    private final double primaryFrequency;
    private final double detailFrequency;
    private final int maxDistance;
    private final int distanceSampleStep;
    private final int baseBeachWidth;
    private final int baseShelfWidth;

    private CoastlineSettings(double seaLevel,
                              double primaryFrequency,
                              double detailFrequency,
                              int maxDistance,
                              int distanceSampleStep,
                              int baseBeachWidth,
                              int baseShelfWidth) {

        this.seaLevel = seaLevel;
        this.primaryFrequency = primaryFrequency;
        this.detailFrequency = detailFrequency;
        this.maxDistance = maxDistance;
        this.distanceSampleStep = distanceSampleStep;
        this.baseBeachWidth = baseBeachWidth;
        this.baseShelfWidth = baseShelfWidth;
    }

    public static CoastlineSettings defaults() {
        return new CoastlineSettings(
            0.0D,
            1.0 / 2048.0,
            1.0 / 128.0,
            96,
            6,
            6,
            12
        );
    }

    public double seaLevel() { return seaLevel; }
    public double primaryFrequency() { return primaryFrequency; }
    public double detailFrequency() { return detailFrequency; }
    public int maxDistance() { return maxDistance; }
    public int distanceSampleStep() { return distanceSampleStep; }
    public int baseBeachWidth() { return baseBeachWidth; }
    public int baseShelfWidth() { return baseShelfWidth; }

    public CoastlineSettings withSeaLevel(double level) {
        return new CoastlineSettings(level, primaryFrequency, detailFrequency, maxDistance,
            distanceSampleStep, baseBeachWidth, baseShelfWidth);
    }

    public CoastlineSettings withDistance(int maxDistance, int step) {
        return new CoastlineSettings(seaLevel, primaryFrequency, detailFrequency,
            Math.max(8, maxDistance), Math.max(2, step), baseBeachWidth, baseShelfWidth);
    }
}
