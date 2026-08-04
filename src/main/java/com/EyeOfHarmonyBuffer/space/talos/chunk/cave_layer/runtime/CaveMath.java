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
