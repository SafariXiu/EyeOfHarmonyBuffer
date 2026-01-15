package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

public final class ContinuousHeightField {

    @Desugar
    public record HeightContext(
        double continentalScore,
        double continental01,
        double ruggedness01,
        double baseY,
        double upliftY,
        double carveY,
        double wetDepressY,
        double worldY,
        double detailAmpY
    ) {}

    private final MacroSelectorConfig.HeightProfile heightProfile;

    private final double upliftMaxY;
    private final double upliftPower;

    private final double carveMaxY;
    private final double carveRadiusBlocks;

    private final double wetlandMaxY;

    public ContinuousHeightField(MacroSelectorConfig.HeightProfile heightProfile) {
        this(heightProfile,
            96.0d,
            2.0d,
            48.0d,
            192.0d,
            8.0d
        );
    }

    public ContinuousHeightField(
        MacroSelectorConfig.HeightProfile heightProfile,
        double upliftMaxY,
        double upliftPower,
        double carveMaxY,
        double carveRadiusBlocks,
        double wetlandMaxY
    ) {
        this.heightProfile = Objects.requireNonNull(heightProfile, "heightProfile");
        this.upliftMaxY = upliftMaxY;
        this.upliftPower = upliftPower;
        this.carveMaxY = carveMaxY;
        this.carveRadiusBlocks = carveRadiusBlocks;
        this.wetlandMaxY = wetlandMaxY;
    }
    public HeightContext sample(
        double continentalScore,
        double saturation,
        double riverStrength,
        double riverDistanceBlocks,
        double slope01,
        double roughness01
    ) {
        double continental01 = clamp01(0.5d * (continentalScore + 1.0d));

        double baseCurve = smoothstep(continental01);
        double baseY = heightProfile.terrainFloorY() + baseCurve * heightProfile.terrainRange();

        double rug = clamp01(roughness01);
        double upliftY = Math.pow(rug, upliftPower) * upliftMaxY;

        double rs = clamp01(riverStrength);
        double rd = Math.max(0.0d, riverDistanceBlocks);
        double riverMask = rs * Math.exp(-rd / Math.max(1.0d, carveRadiusBlocks));
        double carveY = riverMask * carveMaxY;

        double sat = clamp01(saturation);
        double flatMask = 1.0d - clamp01(slope01);
        double wetMask = sat * flatMask;
        double wetDepressY = wetMask * wetlandMaxY;

        double worldY = baseY + upliftY - carveY - wetDepressY;

        double detailAmpY = lerp(6.0d, 22.0d, rug) * lerp(1.0d, 0.7d, sat);

        return new HeightContext(
            continentalScore,
            continental01,
            rug,
            baseY,
            upliftY,
            carveY,
            wetDepressY,
            worldY,
            detailAmpY
        );
    }

    private static double clamp01(double v) {
        if (v < 0.0d) return 0.0d;
        if (v > 1.0d) return 1.0d;
        return v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smoothstep(double t) {
        t = clamp01(t);
        return t * t * (3.0d - 2.0d * t);
    }
}
