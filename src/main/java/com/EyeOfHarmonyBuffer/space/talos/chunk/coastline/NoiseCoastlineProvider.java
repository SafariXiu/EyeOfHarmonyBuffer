package com.EyeOfHarmonyBuffer.space.talos.chunk.coastline;

import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class NoiseCoastlineProvider implements CoastlineProvider {

    private static final long SALT_COAST = 0xBEEFF00D00FFL;
    private static final int[][] OFFSETS = {
        { 1, 0}, {-1, 0}, {0, 1}, {0,-1},
        { 1, 1}, {-1, 1}, {1,-1}, {-1,-1}
    };

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

        int distance = estimateDistance(blockX, blockZ, isLand);
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

    private int estimateDistance(int x, int z, boolean currentLand) {
        final int step = settings.distanceSampleStep();
        final int max = settings.maxDistance();

        for (int radius = step; radius <= max; radius += step) {
            for (int[] offset : OFFSETS) {
                int dx = x + offset[0] * radius;
                int dz = z + offset[1] * radius;
                boolean land = coastHeight(dx, dz) >= settings.seaLevel();
                if (land != currentLand) {
                    return radius;
                }
            }
        }
        return max;
    }

    private int beachWidth(int distance, MacroTag macroTag) {
        int width = settings.baseBeachWidth();
        if (macroTag.isHumid()) {
            width += 2;
        }
        width += Math.max(0, 12 - distance / 2);
        return MathHelper.clamp_int(width, 2, 32);
    }

    private int shelfWidth(int distance, MacroTag macroTag, boolean isLand) {
        int width = settings.baseShelfWidth();
        if (!isLand || macroTag.isOceanic()) {
            width += 4;
        }
        width += distance / 3;
        return MathHelper.clamp_int(width, 6, 64);
    }
}

