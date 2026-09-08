package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * 噪声大陆高度场（海陆分布核心）。
 *
 * 单一低频分形高度场（fbm）+ 低频域扭曲 + 中频棱角，等值线切割出连片大陆。
 * 鞍部抬升把紧邻的小块合并成片（不吞孤立小岛）。
 *
 * 提供（block 级精确、O(1)、无连通域/洪水填充）：
 *   - isLand(x, z, seed)   : 是否陆地
 *   - height(x, z, seed)   : 高程场（供河流 WatershedBuilder / 山带 / 地形）
 *   - signedDist(x, z, seed): 有符号海岸距离（<0 陆、>0 海；供海岸带 / 洋流沿岸）
 *
 * 说明：不再需要 superId（连通大陆块编号）。旧 superId 只服务于 RVR2 河流模板，
 * 现水系改用 WatershedBuilder（直接消费 height），故 superId 移除。
 */
public final class NoiseContinentGrid {

    // --- 高度场参数（与探针验证一致） ---
    private static final double LOW_WAV = 160_000.0;
    private static final double MED_AMP = 0.06;
    private static final double LAND_THRESHOLD = 0.607;
    private static final double LIFT_WINDOW = 0.06;

    private NoiseContinentGrid() {}

    // ======== 高程场（纯函数，全局单一场） ========

    private static double hashUnit(long seed, int gx, int gz) {
        long h = TectonicMath.hashLongs(seed, (gx & 0xFFFFFFFFL), (gz & 0xFFFFFFFFL));
        long m = (h & 0xFFFFFFFFFFFFFFFFL) >>> (64 - 23);
        return m / (double) (1L << 23);
    }

    private static double valueNoise2D(double x, double z, long seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double xf = x - xi, zf = z - zi;
        double u = xf * xf * (3.0 - 2.0 * xf);
        double v = zf * zf * (3.0 - 2.0 * zf);
        double a = hashUnit(seed, xi, zi), b = hashUnit(seed, xi + 1, zi);
        double c = hashUnit(seed, xi, zi + 1), d = hashUnit(seed, xi + 1, zi + 1);
        double ab = a + (b - a) * u, cd = c + (d - c) * u;
        return ab + (cd - ab) * v;
    }

    /** 标准 fbm（归一化）。 */
    private static double fbm(double x, double z, long seed, int octaves, double lacunarity, double gain, double baseFreq) {
        double sum = 0.0, amp = 1.0, freq = baseFreq, total = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * valueNoise2D(x * freq, z * freq, seed + i);
            total += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / total;
    }

    /** 低频域扭曲. */
    private static double[] warp(double x, double z, long seed) {
        double freq = 1.0 / 1_000_000.0, amp = 50_000.0;
        double ox = amp * valueNoise2D(x * freq, z * freq, seed + 1000);
        double oz = amp * valueNoise2D(x * freq, z * freq, seed + 2000);
        return new double[] { x + ox, z + oz };
    }

    /** 高程场 h. */
    public static double height(int x, int z, int worldSeedInt) {
        double[] w = warp(x, z, worldSeedInt);
        double low = fbm(w[0], w[1], worldSeedInt, 3, 2.0, 0.5, 1.0 / LOW_WAV);
        double med = fbm(w[0], w[1], worldSeedInt + 500, 2, 2.0, 0.5, 4.0 / LOW_WAV);
        return low + med * MED_AMP;
    }

    /** 有符号海岸距离（含鞍部抬升合并）。<0 陆、>0 海、0 海岸线. */
    public static double signedDist(int x, int z, int worldSeedInt) {
        double h = height(x, z, worldSeedInt);
        if (h > LAND_THRESHOLD - LIFT_WINDOW) {
            h += LIFT_WINDOW - (LAND_THRESHOLD - h);
        }
        return -(h - LAND_THRESHOLD);
    }

    /** isLand：signedDist <= 0. */
    public static boolean isLand(int x, int z, int worldSeedInt) {
        return signedDist(x, z, worldSeedInt) <= 0.0;
    }
}
