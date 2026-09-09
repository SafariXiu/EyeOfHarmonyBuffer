package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/**
 * 洞穴层共用确定性数学工具：
 *   - 64 位混合哈希（同项目其它层同一套写法）；
 *   - 3D 值噪声（洞壁粗糙度用）；
 *   - 3D 点到线段最近距离（通道雕刻用）。
 * 全部纯函数：相同输入必得相同输出，无随机状态。
 */
public final class CaveMath {

    private CaveMath() {}

    public static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    /** 确定性 [0,1) 哈希：坐标 / 种子 / 盐 任意组合。 */
    public static double hash01(long a, long b, long c,
                                long seed, int salt) {
        long h = seed;
        h = mix64(h ^ (a * 0x9E3779B97F4A7C15L));
        h = mix64(h ^ (b * 0xBF58476D1CE4E5B9L));
        h = mix64(h ^ (c * 0x94D049BB133111EBL));
        h = mix64(h ^ salt);
        return (h >>> 11) / (double) (1L << 53);
    }

    /** 确定性范围哈希。 */
    public static double hashRange(long a, long b, long c,
                                   long seed, int salt,
                                   double lo, double hi) {
        return lo + (hi - lo) * hash01(a, b, c, seed, salt);
    }

    /**
     * 3D 值噪声（trilinear + smoothstep），返回 [0,1]。
     * 洞壁粗糙度 / 断面扰动用。
     */
    public static double valueNoise3D(int x, int y, int z,
                                      long seed, double scale,
                                      int salt) {
        double sx = x / scale;
        double sy = y / scale;
        double sz = z / scale;
        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        int z0 = (int) Math.floor(sz);
        double fx = sx - x0;
        double fy = sy - y0;
        double fz = sz - z0;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fy * fy * (3.0 - 2.0 * fy);
        double w = fz * fz * (3.0 - 2.0 * fz);

        double c000 = hash01(x0, y0, z0, seed, salt);
        double c100 = hash01(x0 + 1, y0, z0, seed, salt);
        double c010 = hash01(x0, y0 + 1, z0, seed, salt);
        double c110 = hash01(x0 + 1, y0 + 1, z0, seed, salt);
        double c001 = hash01(x0, y0, z0 + 1, seed, salt);
        double c101 = hash01(x0 + 1, y0, z0 + 1, seed, salt);
        double c011 = hash01(x0, y0 + 1, z0 + 1, seed, salt);
        double c111 = hash01(x0 + 1, y0 + 1, z0 + 1, seed, salt);

        double x00 = c000 + (c100 - c000) * u;
        double x10 = c010 + (c110 - c010) * u;
        double x01 = c001 + (c101 - c001) * u;
        double x11 = c011 + (c111 - c011) * u;
        double y0v = x00 + (x10 - x00) * v;
        double y1v = x01 + (x11 - x01) * v;
        return y0v + (y1v - y0v) * w;
    }

    /**
     * 3D Perlin 噪声，返回约 [-1,1]。
     * 梯度由哈希决定，连续、可复现，适合做地形。
     */
    public static double perlin3D(double x, double y, double z,
                                  long seed, int salt) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fy = y - y0;
        double fz = z - z0;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fy * fy * (3.0 - 2.0 * fy);
        double w = fz * fz * (3.0 - 2.0 * fz);

        double n000 = perlinGrad(x0, y0, z0, fx, fy, fz, seed, salt);
        double n100 = perlinGrad(x0 + 1, y0, z0,
            fx - 1.0, fy, fz, seed, salt);
        double n010 = perlinGrad(x0, y0 + 1, z0,
            fx, fy - 1.0, fz, seed, salt);
        double n110 = perlinGrad(x0 + 1, y0 + 1, z0,
            fx - 1.0, fy - 1.0, fz, seed, salt);
        double n001 = perlinGrad(x0, y0, z0 + 1,
            fx, fy, fz - 1.0, seed, salt);
        double n101 = perlinGrad(x0 + 1, y0, z0 + 1,
            fx - 1.0, fy, fz - 1.0, seed, salt);
        double n011 = perlinGrad(x0, y0 + 1, z0 + 1,
            fx, fy - 1.0, fz - 1.0, seed, salt);
        double n111 = perlinGrad(x0 + 1, y0 + 1, z0 + 1,
            fx - 1.0, fy - 1.0, fz - 1.0, seed, salt);

        double x00 = n000 + (n100 - n000) * u;
        double x10 = n010 + (n110 - n010) * u;
        double x01 = n001 + (n101 - n001) * u;
        double x11 = n011 + (n111 - n011) * u;
        double y0v = x00 + (x10 - x00) * v;
        double y1v = x01 + (x11 - x01) * v;
        return y0v + (y1v - y0v) * w;
    }

    /** 分形柏林（fBm）：多层 Perlin 叠加，输出连续且分布更自然。 */
    public static double fbm3D(double x, double y, double z,
                               long seed, int salt, int octaves,
                               double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * perlin3D(
                x * freq, y * freq, z * freq,
                seed, salt + i * 131);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /**
     * 大块岩性：低频 fBm 分档。
     * y<=30 为深层：4=深板岩（约七成），5=凝灰岩（约三成）；
     * y>30 为浅层：0=石头（约七成），1=花岗岩，2=闪长岩，3=安山岩。
     */
    public static final int DEEP_ROCK_MAX_Y = 30;

    public static byte rockVariant3D(int wx, int wy, int wz, long seed) {
        return rockVariantFromValue(rockValue3D(wx, wy, wz, seed), wy);
    }

    /**
     * 岩性原始场值（fBm，特征尺度 240 格）。
     * 与 {@link #rockVariantFromValue} 配合，供区块级 3D 网格缓存插值使用
     * （240 格尺度的场按 8/8/16 格采样 + 三线性插值，误差远小于阈值带宽度）。
     */
    public static double rockValue3D(int wx, int wy, int wz, long seed) {
        return fbm3D(wx / 240.0, wy / 240.0, wz / 240.0, seed, 0xCC, 2, 2.0, 0.5);
    }

    /** 由原始场值 + 深度定岩性（口径与 {@link #rockVariant3D} 完全一致）。 */
    public static byte rockVariantFromValue(double n, int wy) {
        if (wy <= DEEP_ROCK_MAX_Y) {
            return (byte) (n < 0.3 ? 4 : 5);
        }
        if (n < -0.13) {
            return 1;
        }
        if (n < -0.04) {
            return 2;
        }
        if (n > 0.13) {
            return 3;
        }
        return 0;
    }

    private static double perlinGrad(int ix, int iy, int iz,
                                     double dx, double dy, double dz,
                                     long seed, int salt) {
        // 预生成的非轴向随机单位向量表：每次哈希只查一次，速度翻倍。
        double h = hash01(ix, iy, iz, seed, salt);
        int idx = (int) (h * PERLIN_GRADS.length);
        if (idx >= PERLIN_GRADS.length) {
            idx = PERLIN_GRADS.length - 1;
        }
        double[] g = PERLIN_GRADS[idx];
        return g[0] * dx + g[1] * dy + g[2] * dz;
    }

    private static final double[][] PERLIN_GRADS = buildPerlinGrads();

    private static double[][] buildPerlinGrads() {
        double[][] g = new double[32][3];
        for (int i = 0; i < 32; i++) {
            double theta = i * 0.6180339887498949 * 2.0 * Math.PI;
            double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / 32.0);
            double sp = Math.sin(phi);
            g[i][0] = sp * Math.cos(theta);
            g[i][1] = sp * Math.sin(theta);
            g[i][2] = Math.cos(phi);
        }
        return g;
    }

    /**
     * 3D 点到线段最近距离。
     *
     * @param outT 长度为 1 的数组，返回最近点参数 t ∈ [0,1]（半径插值用）
     * @return 距离平方
     */
    public static double closestDistSq(double px, double py, double pz,
                                       double ax, double ay, double az,
                                       double bx, double by, double bz,
                                       double[] outT) {
        double abx = bx - ax;
        double aby = by - ay;
        double abz = bz - az;
        double apx = px - ax;
        double apy = py - ay;
        double apz = pz - az;
        double lenSq = abx * abx + aby * aby + abz * abz;
        double t;
        if (lenSq <= 1.0e-12) {
            t = 0.0;
        } else {
            t = (apx * abx + apy * aby + apz * abz) / lenSq;
            if (t < 0.0) {
                t = 0.0;
            } else if (t > 1.0) {
                t = 1.0;
            }
        }
        double cx = ax + abx * t;
        double cy = ay + aby * t;
        double cz = az + abz * t;
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        outT[0] = t;
        return dx * dx + dy * dy + dz * dz;
    }
}
