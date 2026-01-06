package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.MacroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.MacroFieldSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class NoiseMacroFieldProvider implements MacroFieldProvider {

    private static final long SALT_CONTINENTAL = 0xCAFE_BABE_0000_0001L;
    private static final long SALT_HUMIDITY    = 0xCAFE_BABE_0000_0002L;
    private static final long SALT_TEMPERATURE = 0xCAFE_BABE_0000_0003L;
    private static final long SALT_RIDGE       = 0xCAFE_BABE_0000_0004L;
    private static final long SALT_VARIANT     = 0xCAFE_BABE_0000_00FFL;

    private final long seed;
    private final MacroFieldSettings settings;

    public NoiseMacroFieldProvider(long seed, MacroFieldSettings settings) {
        this.seed = seed;
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public MacroSample sample(int blockX, int blockZ) {
        double x = blockX;
        double z = blockZ;

        double continental = remapSigned(
            NoiseUtil.fractal(seed, SALT_CONTINENTAL, x, z,
                settings.continentalFrequency(), 4, 2.0, 0.55));
        double humidity = remapSigned(
            NoiseUtil.fractal(seed, SALT_HUMIDITY, x, z,
                settings.humidityFrequency(), 3, 2.3, 0.6));
        double temperature = remapSigned(
            NoiseUtil.fractal(seed, SALT_TEMPERATURE, x, z,
                settings.temperatureFrequency(), 3, 2.1, 0.58));

        double ridges = remapSigned(
            NoiseUtil.fractal(seed, SALT_RIDGE, x, z,
                settings.ridgeFrequency(), 2, 2.6, 0.5));

        MacroTag primary = MacroTag.pick(continental, humidity, temperature);
        MacroTag secondary = MacroTag.pick(
            continental + ridges * 0.15,
            humidity * 0.75 + ridges * 0.1,
            temperature - ridges * 0.05);

        double blendPrimary = MathHelper.clamp_double(
            0.5 + (continental - ridges * 0.35) * 0.35, 0.0D, 1.0D);

        byte tier = (byte) MathHelper.clamp_int(
            (int) Math.round(3 * (continental + 1.0) / 2.0), 0, 3);

        short plateId = (short) (Math.abs(
            NoiseUtil.hashToInt(seed, blockX >> 4, blockZ >> 4, SALT_VARIANT)) % Short.MAX_VALUE);

        float anchorWeight = (float) MathHelper.clamp_double(
            Math.abs(continental) * 0.85 + Math.abs(ridges) * 0.15, 0.0D, 1.0D);

        float hardEdge = (float) MathHelper.clamp_double(
            1.0D - Math.abs(humidity - 0.25D) * 0.9D, 0.0D, 1.0D);

        int baseRange = settings.macroBaseHeightMax() - settings.macroBaseHeightMin();
        short macroBaseHeight = (short) MathHelper.clamp_int(
            (int) (settings.macroBaseHeightMin() +
                (continental + 1.0) * 0.5 * baseRange +
                ridges * 12.0),
            0, 255);

        float plateauHeight = (float) MathHelper.clamp_double(
            settings.plateauBaseHeight() + ridges * settings.plateauVariance(),
            0.0D, 255.0D);

        byte patchVariant = (byte) (Math.abs(
            NoiseUtil.hashToInt(seed, blockX, blockZ, SALT_VARIANT + 77L)) % 16);

        boolean patchSingleBiome = Math.abs(secondary.ordinal() - primary.ordinal()) <= 1;

        double patchEdgeBlend = MathHelper.clamp_double(
            0.35 + hardEdge * 0.5 - anchorWeight * 0.2, 0.0D, 1.0D);

        return new MacroSample(
            primary,
            secondary,
            blendPrimary,
            tier,
            plateId,
            plateauHeight,
            anchorWeight,
            hardEdge,
            macroBaseHeight,
            patchVariant,
            patchSingleBiome,
            patchEdgeBlend
        );
    }

    private static double remapSigned(double noise) {
        return noise * 2.0 - 1.0;
    }
}
