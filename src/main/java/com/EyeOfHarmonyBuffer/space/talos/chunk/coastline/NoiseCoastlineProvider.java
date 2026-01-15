package com.EyeOfHarmonyBuffer.space.talos.chunk.coastline;

import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class NoiseCoastlineProvider implements CoastlineProvider {

    private static final long SALT_COAST = 0xBEEFF00D00FFL;

    private final long seed;
    private final CoastlineSettings settings;

    public NoiseCoastlineProvider(long seed, CoastlineSettings settings) {
        this.seed = seed;
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public CoastlineSample sample(int blockX, int blockZ, MacroTag macroTag) {
        double height = coastHeight(blockX, blockZ);
        boolean isLand = height >= settings.seaLevel();

        double distance = estimateDistanceContinuous(blockX, blockZ);

        int beach = beachWidth(distance, macroTag);
        int shelf = shelfWidth(distance, macroTag, isLand);

        return new CoastlineSample(isLand, distance, beach, shelf);
    }

    private double coastHeight(int x, int z) {
        double primary = NoiseUtil.fractal(seed, SALT_COAST, x, z,
            settings.primaryFrequency(), 4, 2.0, 0.55);
        double detail = NoiseUtil.fractal(seed, SALT_COAST + 1L, x, z,
            settings.detailFrequency(), 2, 3.1, 0.45);
        return (primary * 2.0 - 1.0) + (detail - 0.5) * 0.4;
    }

    private double estimateDistanceContinuous(int x, int z) {
        final double sea = settings.seaLevel();

        final double f0 = coastHeight(x, z) - sea;

        final double h = 2.0;

        final double fx1 = coastHeight((int) (x + h), z) - sea;
        final double fx0 = coastHeight((int) (x - h), z) - sea;
        final double fz1 = coastHeight(x, (int) (z + h)) - sea;
        final double fz0 = coastHeight(x, (int) (z - h)) - sea;

        final double dfdx = (fx1 - fx0) / (2.0 * h);
        final double dfdz = (fz1 - fz0) / (2.0 * h);

        final double grad = Math.sqrt(dfdx * dfdx + dfdz * dfdz);

        final double dist = Math.abs(f0) / Math.max(1e-4, grad);

        return Math.min(dist, settings.maxDistance());
    }

    private int beachWidth(double distance, MacroTag macroTag) {
        double width = settings.baseBeachWidth();
        if (macroTag.isHumid()) {
            width += 2.0;
        }

        width += Math.max(0.0, 12.0 - (distance / 2.0));

        return MathHelper.clamp_int((int) Math.round(width), 2, 32);
    }

    private int shelfWidth(double distance, MacroTag macroTag, boolean isLand) {
        double width = settings.baseShelfWidth();
        if (!isLand || macroTag.isOceanic()) {
            width += 4.0;
        }

        width += distance / 3.0;

        return MathHelper.clamp_int((int) Math.round(width), 6, 64);
    }
}

