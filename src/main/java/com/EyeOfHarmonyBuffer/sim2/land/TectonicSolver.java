package com.EyeOfHarmonyBuffer.sim2.land;

/**
 * 时间积分的构造瓦片求解器（方案 A）。
 *
 * <p>动机：Cortial et al. 2019 的模型（{@code LandV3.upliftPaper}）是<b>时间步进</b>的，
 * 平滑性来自「地壳随板块移动 + 抬升沿路径累积」。把它当静态场求值，{@code f(d)} 的等值线
 * 会原样暴露成多边形碎裂面（实测）。
 *
 * <p>本类做真正的时间积分：
 * <pre>
 *   每步 dt = 2 My:
 *     z' = advect(z, x − v·dt)         半拉格朗日回溯（双线性）
 *     z' += u(x)/ITER                  抬升累积（量级与静态模型一致）
 *     z' −= z'·ε·dt                    侵蚀（朝海平面指数衰减）
 * </pre>
 *
 * <p>瓦片化、确定性、可缓存：同一 (seed, tileX, tileZ) 永远得到同一结果。
 */
public final class TectonicSolver {

    private TectonicSolver() {}

    /** 总开关（默认关）。 */
    public static boolean ENABLED = false;

    // ---- 采样与瓦片 ----
    /** 采样间距（m），论文用 ≈50 km，我们用 25 km 以保留细节。 */
    public static double SAMPLE = 25_000.0;
    /** 求解边长（格）与缓冲区。 */
    public static int N = 160, BUF = 70;
    /** 时间步数与步长（年）。 */
    public static int ITER = 40;
    public static double DT_YEARS = 2.0e6;

    // ---- 物理 ----
    /** 侵蚀速率（/My，朝海平面）。论文 ε_c = 3e-2。 */
    public static double EROSION = 0.030;
    /** 洋壳额外下沉（/My）。 */
    public static double OCEAN_DAMP = 0.040;
    /** 抬升总量相对静态模型的倍率。 */
    public static double UPLIFT_SCALE = 0.185;
    public static double BASEMENT_KM = 0.0, SHAPE_GAIN = 1.0;
    /** one-shot erosion fractions */
    public static double LAND_ERODE_ONCE = 0.30, OCEAN_ERODE_ONCE = 0.12;
    /** post-integration 3x3 smoothing passes (kills cell-scale ripple). */
    public static int SMOOTH_PASSES = 2;
    /** cells of cross-fade at tile edges (buffer is BUF, so this is safe). */
    public static double BLEND_CELLS = 10.0;
    /** Years of accumulated drift for the path integral (My). */
    public static double PATH_MY = 30.0;

    private static final class Tile {
        float[] z;      // elevation, km
        double x0, z0, step;
        int n;
        int inner;   // buffer offset of the tile's own area
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, Tile> CACHE =
            new java.util.concurrent.ConcurrentHashMap<Long, Tile>();
    public static int CACHE_TILES = 256;

    /** 清缓存（换种子时调用）。 */
    public static void reset() { CACHE.clear(); }

    private static long key(long ws, int tx, int tz) {
        return ws * 0x9E3779B97F4A7C15L ^ ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    /** 世界坐标高度（km）；未命中时求解所在瓦片。 */
    public static double heightKm(long ws, double x, double z) {
        double step = SAMPLE;
        double span = N * step;
        int tx = (int) Math.floor(x / span);
        int tz = (int) Math.floor(z / span);
        Tile t = get(ws, tx, tz);
        double fx = (x - t.x0) / step;      // in BUFFER coords
        double fz = (z - t.z0) / step;
        double v = sampleTile(t, x, z);
        double gx = fx - t.inner, gz = fz - t.inner;
        double M = BLEND_CELLS;
        double wx = 0.0, wz = 0.0;
        if (gx < M) wx = 0.5 * (1.0 - gx / M);
        else if (gx > N - M) wx = 0.5 * (gx - (N - M)) / M;
        if (gz < M) wz = 0.5 * (1.0 - gz / M);
        else if (gz > N - M) wz = 0.5 * (gz - (N - M)) / M;
        if (wx > 0.0 || wz > 0.0) {
            int sx = gx < M ? -1 : 1, sz = gz < M ? -1 : 1;
            Tile tx1 = wx > 0.0 ? get(ws, tx + sx, tz) : t;
            Tile tz1 = wz > 0.0 ? get(ws, tx, tz + sz) : t;
            Tile t11 = (wx > 0.0 && wz > 0.0) ? get(ws, tx + sx, tz + sz) : t;
            // full bilinear blend over up to 4 tiles: the old sequential x-then-z blend
            // left a 1 km step exactly at tile CORNERS (12,000 / 8,000).
            double a = 1.0 - wx, b = 1.0 - wz;
            v = a * b * sampleTile(t, x, z)
              + wx * b * sampleTile(tx1, x, z)
              + a * wz * sampleTile(tz1, x, z)
              + wx * wz * sampleTile(t11, x, z);
        }
        return v;
    }

    private static double sampleTile(Tile t, double x, double z) {
        double fx = (x - t.x0) / t.step;
        double fz = (z - t.z0) / t.step;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double ax = fx - i, az = fz - j;
        i = Math.max(0, Math.min(t.n - 2, i));
        j = Math.max(0, Math.min(t.n - 2, j));
        // Catmull-Rom bicubic: bilinear leaves linear creases along cell edges on a 25 km
        // grid, which show as a hatching pattern once the hillshade gain amplifies them.
        return bicubic(t, i, j, ax, az);
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    private static double at(Tile t, int i, int j) {
        i = Math.max(0, Math.min(t.n - 1, i));
        j = Math.max(0, Math.min(t.n - 1, j));
        return t.z[j * t.n + i];
    }

    private static double bicubic(Tile t, int i, int j, double ax, double az) {
        double[] c = new double[4];
        for (int m = 0; m < 4; m++) {
            c[m] = cr(at(t, i - 1, j - 1 + m), at(t, i, j - 1 + m),
                      at(t, i + 1, j - 1 + m), at(t, i + 2, j - 1 + m), ax);
        }
        return cr(c[0], c[1], c[2], c[3], az);
    }

    private static Tile get(long ws, int tx, int tz) {
        long k = key(ws, tx, tz);
        Tile t = CACHE.get(k);
        if (t != null) return t;
        t = solve(ws, tx, tz);
        if (CACHE.size() > CACHE_TILES) CACHE.clear();
        CACHE.put(k, t);
        return t;
    }

    private static Tile solve(long ws, int tx, int tz) {
        int n = N;
        double step = SAMPLE;
        double x0 = tx * n * step - BUF * step;
        double z0 = tz * n * step - BUF * step;
        int m = n + 2 * BUF;

        double[] z = new double[m * m];
        double[] z2 = new double[m * m];
        double[] u = new double[m * m];
        double[] uArr = new double[m * m];
        double[] vx = new double[m * m];
        double[] vz = new double[m * m];
        boolean[] ocean = new boolean[m * m];

        // 采样初始状态与静态抬升率
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < m; i++) {
                double x = x0 + i * step, zz = z0 + j * step;
                int idx = j * m + i;
                z[idx] = LandV3.height(ws, x, zz) / 1000.0;
                u[idx] = LandV3.upliftPaper(ws, x, zz) / 1000.0 * UPLIFT_SCALE;
                uArr[idx] = LandV3.upliftPaperSmooth(ws, x, zz);
                double[] v = new double[2];
                LandV3.velocity(ws, x, zz, v);
                // V_SCALE: m/yr per velocity unit
                vx[idx] = v[0] * LandContract.V_SCALE * DT_YEARS / 1000.0;   // km per step
                vz[idx] = v[1] * LandContract.V_SCALE * DT_YEARS / 1000.0;
                ocean[idx] = LandV3.continentality(ws, x, zz) < LandV3.cut(ws);
            }
        }

        // ---- path integral instead of time stepping ----
        // A constant per-plate velocity makes a fixed advection offset every step, which
        // aliases into regular stripes. The integral form has no such artifact:
        //     dz(x) = INT_{-T}^{0} u(x + v*s) ds
        double ero = EROSION * (DT_YEARS / 1.0e6);
        double ocd = OCEAN_DAMP * (DT_YEARS / 1.0e6);
        int K = 64;
        double tMax = PATH_MY * 1.0e6;      // years of accumulated drift
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < m; i++) {
                int idx = j * m + i;
                double x = x0 + i * step, zz = z0 + j * step;
                double vxm = vx[idx] / (DT_YEARS / 1000.0);   // km per year
                double vzm = vz[idx] / (DT_YEARS / 1000.0);
                // power mean (p=4): keeps the crest of the belt while staying smooth.
                // A plain average dilutes the peak (window max fell from 1598 to 351).
                double acc = 0.0;
                for (int k = 0; k < K; k++) {
                    double s = -tMax + (k + 0.5) * (tMax / K);
                    double sx = x + vxm * s * 1000.0;
                    double sz = zz + vzm * s * 1000.0;
                    // sample the PRECOMPUTED array (bilinear), not the analytic field:
                    // the analytic path samples sit at a fixed phase relative to the 25 km
                    // grid for every cell of a plate, so the sampling error repeated per
                    // cell and produced a 129 m sawtooth with a 25 km period.
                    double fi = (sx - x0) / step, fj = (sz - z0) / step;
                    int ai = (int) Math.floor(fi), aj = (int) Math.floor(fj);
                    double ax = fi - ai, az = fj - aj;
                    ai = Math.max(0, Math.min(m - 2, ai));
                    aj = Math.max(0, Math.min(m - 2, aj));
                    double q00 = uArr[aj * m + ai], q10 = uArr[aj * m + ai + 1];
                    double q01 = uArr[(aj + 1) * m + ai], q11 = uArr[(aj + 1) * m + ai + 1];
                    acc += (q00 * (1 - ax) + q10 * ax) * (1 - az) + (q01 * (1 - ax) + q11 * ax) * az;
                }
                // PATH AVERAGE of the uplift (same units as the static field): the material
                // samples the belt while it drifts through it, so the result is the smoothed
                // uplift, not an integral of a rate (the old code integrated it as a rate and
                // produced 39 km of relief).
                double dz = (acc / K) / 1000.0 * UPLIFT_SCALE;
                // shape: strip a constant basement and re-expand, so the mid elevations
                // (3-5 km) shrink while the crests survive -- otherwise >3km covers 6.3%.
                if (dz > 0.0) dz = Math.max(0.0, dz - BASEMENT_KM) * SHAPE_GAIN;
                double z0v = z[idx];
                double zz2 = z0v + dz;
                zz2 += LandV3.detailCoarse(ws, x, zz) / 1000.0;
                // One-shot erosion, NOT ero*ITER (that removed 96% and made land/ocean
                // cells differ by a huge step, which showed up as grid-scale checkering).
                double eAmt = ocean[idx] ? OCEAN_ERODE_ONCE : LAND_ERODE_ONCE;
                if (ocean[idx]) zz2 -= (zz2 + 5.0) * eAmt;
                else zz2 -= zz2 * eAmt;
                z2[idx] = zz2;
            }
        }
        double[] tmp = z; z = z2; z2 = tmp;
        // one 3x3 [1 2 1]^2 pass: kills the residual +-15 m cell-scale ripple without
        // touching the 100 km-scale terrain (it is a 25 km smoothing on a 25 km grid).
        if (SMOOTH_PASSES > 0) {
            for (int pass = 0; pass < SMOOTH_PASSES; pass++) {
                for (int j = 1; j < m - 1; j++) {
                    for (int i = 1; i < m - 1; i++) {
                        z2[j * m + i] = (z[(j - 1) * m + i - 1] + 2 * z[(j - 1) * m + i] + z[(j - 1) * m + i + 1]
                                + 2 * z[j * m + i - 1] + 4 * z[j * m + i] + 2 * z[j * m + i + 1]
                                + z[(j + 1) * m + i - 1] + 2 * z[(j + 1) * m + i] + z[(j + 1) * m + i + 1]) / 16.0;
                    }
                }
                for (int j = 0; j < m; j++) { z2[j * m] = z[j * m]; z2[j * m + m - 1] = z[j * m + m - 1]; }
                for (int i = 0; i < m; i++) { z2[i] = z[i]; z2[(m - 1) * m + i] = z[(m - 1) * m + i]; }
                double[] sw = z; z = z2; z2 = sw;
            }
        }
        // Keep the WHOLE buffered array: the neighbour's buffer is what makes the
        // cross-fade at a tile edge possible (with only the inner N x N, sampling the
        // neighbour just outside its area clamps to its edge cell -> a 1 km step at
        // every tile boundary).
        Tile t = new Tile();
        t.n = m;
        t.step = step;
        t.x0 = x0;
        t.z0 = z0;
        t.inner = BUF;
        t.z = new float[m * m];
        for (int k = 0; k < m * m; k++) t.z[k] = (float) z[k];
        return t;
    }
}
