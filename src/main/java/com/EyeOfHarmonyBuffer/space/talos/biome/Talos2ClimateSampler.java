package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.Talos2NoiseConfig;
import net.minecraft.world.World;

public final class Talos2ClimateSampler {

    private static final double MICRO_TEMP_NOISE_WEIGHT = 0.12D;
    private static final double MICRO_HUMID_NOISE_WEIGHT = 0.15D;

    private final MacroBiomeField macroField;

    private final SimplexNoiseOctave tempNoise;
    private final SimplexNoiseOctave humidNoise;

    private final double tempScale;
    private final double humidScale;

    private final double latitudePeriod;
    private final double latitudeBiasStrength;
    private final double latitudeMixWeight;
    private final double latitudeBaseBias;

    public Talos2ClimateSampler(World world, MacroBiomeField macroField) {
        this.macroField = macroField;

        long seed = world.getSeed();
        MacroBiomeField.MacroBiomeConfig cfg = Talos2NoiseConfig.currentMacroConfig();

        this.tempNoise = new SimplexNoiseOctave(seed ^ 0xA5A5A5A5A5A5A5A5L, 4);
        this.humidNoise = new SimplexNoiseOctave(seed ^ 0x5A5A5A5A5A5A5A5AL, 4);

        this.tempScale = cfg.macroScale * 2.0D;
        this.humidScale = cfg.macroScale * 2.0D;

        this.latitudePeriod = cfg.latitudePeriod;
        this.latitudeBiasStrength = cfg.latitudeBiasStrength * 0.6D;
        this.latitudeMixWeight = Math.min(0.85D, cfg.latitudeMixWeight + 0.15D);
        this.latitudeBaseBias = cfg.latitudeBaseBias;
    }

    public ClimateSample sample(int x, int z) {
        MacroBiomeField.SampleDual macro = macroField.sampleDual(x, z);
        MacroBiome primaryBiome = (macro != null && macro.primary != null)
            ? macro.primary
            : MacroBiome.PLAINS_TEMPERATE;
        MacroBiome secondaryBiome = (macro != null && macro.secondary != null)
            ? macro.secondary
            : primaryBiome;
        double primaryWeight = (macro != null)
            ? clamp01(macro.primaryWeight)
            : 1.0D;
        MacroBiome.ClimateProfile primaryProfile = primaryBiome.climate;
        MacroBiome.ClimateProfile secondaryProfile = secondaryBiome.climate;
        double macroTemp = lerp(secondaryProfile.temperature, primaryProfile.temperature, primaryWeight);
        double macroHumid = lerp(secondaryProfile.humidity, primaryProfile.humidity, primaryWeight);

        double normalizedLat = (latitudePeriod != 0.0D) ? (z / latitudePeriod) : 0.0D;
        double latitudeTerm = Math.cos(Math.PI * normalizedLat);
        latitudeTerm = latitudeTerm * latitudeBiasStrength + latitudeBaseBias;
        latitudeTerm = clampSigned(latitudeTerm);
        double latitude01 = clamp01(0.5D + latitudeTerm * 0.5D);

        double tempMacroLat = lerp(macroTemp, latitude01, latitudeMixWeight);
        double humidMacroLat = macroHumid;

        double noiseTemp = clamp01(0.5D + tempNoise.noise(x * tempScale, z * tempScale) * 0.5D);
        double noiseHumid = clamp01(0.5D + humidNoise.noise(x * humidScale, z * humidScale) * 0.5D);

        double temperature = clamp01(lerp(tempMacroLat, noiseTemp, MICRO_TEMP_NOISE_WEIGHT));
        double humidity = clamp01(lerp(humidMacroLat, noiseHumid, MICRO_HUMID_NOISE_WEIGHT));

        double continentalness = macroField.sampleContinentalness(x, z);

        int primaryMacroId = primaryBiome.getId();

        return new ClimateSample(temperature, humidity, primaryProfile.roughness,
            continentalness, primaryMacroId);
    }

    public static final class ClimateSample {
        public final double temperature;
        public final double humidity;
        public final float roughness;
        public final double continentalness;
        public final int primaryMacroId;

        private ClimateSample(double temperature,
                              double humidity,
                              float roughness,
                              double continentalness,
                              int primaryMacroId) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.roughness = roughness;
            this.continentalness = continentalness;
            this.primaryMacroId = primaryMacroId;
        }
    }

    private static double lerp(double a, double b, double w) {
        return a * (1.0D - w) + b * w;
    }

    private static double clampSigned(double v) {
        if (v < -1.0D) return -1.0D;
        if (v >  1.0D) return  1.0D;
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }
}
