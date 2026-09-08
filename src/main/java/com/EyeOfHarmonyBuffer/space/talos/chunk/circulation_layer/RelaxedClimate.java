package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.util.concurrent.ConcurrentHashMap;

/**
 * M5+M6 核心：环面离线松弛求解的气候场（按世界种子缓存，运行时查表插值）。
 *
 * 物理：在 400k×200k 环面的粗网格（2k/格）上做**嵌套定点迭代**：
 *   内层1：固定流场下把海温收敛到"逆流输运 + 向 seaTeq 弛豫"的不动点；
 *   内层2：同流场下把**空气温度/湿度/海洋性**收敛（海上目标=已解海温，陆上目标=landTeq/干平衡）；
 *   外层：用新海温更新气压（暖池低压/冷舌高压，增益 2.2）→ 地转风 + 摩擦
 *         （风速欠弛豫 0.5 抑制追逐）→ 埃克曼转向 + 岸墙折射的洋流。
 * 产出：副热带环流圈、西岸暖/东岸冷、寒舌暖池、迎风岸湿舌等由耦合自身涌现。
 *
 * 查询（全部双线性 O(1)≈100ns）：samplePressure/sampleWind/sampleCurrent/sampleSst/
 * sampleHumidity/sampleAirTemp/sampleMaritime。首次访问某种子执行求解（约 5s，一次性）。
 * 确定性：固定迭代上限 + 阈值早停。
 */
public final class RelaxedClimate {

    private RelaxedClimate() {}

    /** 网格分辨率（block/格）。 */
    public static final int CELL = 2000;
    private static final int OUTER = 22;
    private static final int INNER = 30;
    private static final int AIR_INNER = 14;
    private static final double SST_P_GAIN = 2.2;
    private static final double INNER_TOL = 5.0e-4;

    private static final ConcurrentHashMap<Integer, ClimateGridData> CACHE =
        new ConcurrentHashMap<Integer, ClimateGridData>();

    /** 网格场数据。 */
    static final class ClimateGridData {
        final int nx = 400_000 / CELL;
        final int ny = 200_000 / CELL;
        final boolean[] land = new boolean[nx * ny];
        final double[] sst = new double[nx * ny];
        final double[] teqSea = new double[nx * ny];
        final double[] teqLand = new double[nx * ny];
        final double[] qLandEq = new double[nx * ny];
        final double[] p = new double[nx * ny];
        final double[] u = new double[nx * ny];
        final double[] v = new double[nx * ny];
        final double[] fu = new double[nx * ny];
        final double[] fv = new double[nx * ny];
        // 空气场（随耦合流场收敛）
        final double[] tAir = new double[nx * ny];
        final double[] q = new double[nx * ny];
        final double[] mar = new double[nx * ny];
        final double[] p0z = new double[ny];
        final double[] wave = new double[nx * ny];
        final double[] fRow = new double[ny];
        final double[] dampRow = new double[ny];
        // 中间态
        final double[] pu = new double[nx * ny];
        final double[] pv = new double[nx * ny];
        final double[] sstNew = new double[nx * ny];
        final double[] tNew = new double[nx * ny];
        final double[] qNew = new double[nx * ny];
        final double[] marNew = new double[nx * ny];

        int idx(int ix, int iy) {
            int wy = ((iy % ny) + ny) % ny;
            return wy * nx + ((ix % nx + nx) % nx);
        }
    }

    // ================= 查询 API =================

    public static ClimateGridData ensure(int worldSeedInt) {
        ClimateGridData d = CACHE.get(worldSeedInt);
        if (d != null) {
            return d;
        }
        return CACHE.computeIfAbsent(worldSeedInt, RelaxedClimate::solve);
    }

    public static double samplePressure(int x, int z, int worldSeedInt) {
        return bilinear(ensure(worldSeedInt).p, x, z);
    }

    public static double[] sampleWind(int x, int z, int worldSeedInt) {
        ClimateGridData d = ensure(worldSeedInt);
        return new double[] { bilinear(d.u, x, z), bilinear(d.v, x, z) };
    }

    /** 洋流方向 [fx, fz]（海上；陆上 null）。 */
    public static double[] sampleCurrent(int x, int z, int worldSeedInt) {
        ClimateGridData d = ensure(worldSeedInt);
        if (isLandCell(d, x, z)) {
            return null;
        }
        return new double[] { bilinear(d.fu, x, z), bilinear(d.fv, x, z) };
    }

    /** 耦合海温（海上；陆上 NaN）。 */
    public static double sampleSst(int x, int z, int worldSeedInt) {
        ClimateGridData d = ensure(worldSeedInt);
        if (isLandCell(d, x, z)) {
            return Double.NaN;
        }
        return bilinear(d.sst, x, z);
    }

    /** 空气温度（全定义域）。 */
    public static double sampleAirTemp(int x, int z, int worldSeedInt) {
        return bilinear(ensure(worldSeedInt).tAir, x, z);
    }

    /** 空气湿度 [0,1]（全定义域）。 */
    public static double sampleHumidity(int x, int z, int worldSeedInt) {
        double q = bilinear(ensure(worldSeedInt).q, x, z);
        return q < 0 ? 0 : (q > 1 ? 1 : q);
    }

    /** 海洋性记忆 [0,1]（全定义域）。 */
    public static double sampleMaritime(int x, int z, int worldSeedInt) {
        return bilinear(ensure(worldSeedInt).mar, x, z);
    }

    private static boolean isLandCell(ClimateGridData d, int x, int z) {
        return d.land[d.idx(x / CELL, z / CELL)];
    }

    private static double bilinear(double[] fld, double wx, double wz) {
        int XC = 400_000, ZC = 200_000;
        int fx = (int) (((wx % XC) + XC) % XC);
        int fz = (int) (((wz % ZC) + ZC) % ZC);
        double gx = fx / (double) CELL, gz = fz / (double) CELL;
        int ix0 = (int) Math.floor(gx), iz0 = (int) Math.floor(gz);
        double tx = gx - ix0, tz = gz - iz0;
        int nx = XC / CELL, ny = ZC / CELL;
        int ix1 = (ix0 + 1) % nx, iz1 = (iz0 + 1) % ny;
        double s00 = fld[iz0 * nx + ix0], s10 = fld[iz0 * nx + ix1];
        double s01 = fld[iz1 * nx + ix0], s11 = fld[iz1 * nx + ix1];
        return s00 * (1 - tx) * (1 - tz) + s10 * tx * (1 - tz)
             + s01 * (1 - tx) * tz + s11 * tx * tz;
    }

    // ================= 离线求解 =================

    private static ClimateGridData solve(int worldSeedInt) {
        ClimateGridData d = new ClimateGridData();
        for (int iy = 0; iy < d.ny; iy++) {
            int z = iy * CELL;
            int zm = GlobalCirculation.foldZ(z);
            double b = GlobalCirculation.bandD(z);
            double latRad = (zm <= 100_000 ? b : -b) * Math.PI / 2.0;
            double f = Math.sin(latRad);
            double fa = Math.abs(f);
            d.fRow[iy] = fa < 1.0e-4 ? 0.0 : f;
            d.dampRow[iy] = fa / (fa + 0.30);
            d.p0z[iy] = profileP0(b);
        }
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int x = ix * CELL, z = iy * CELL;
                double b = GlobalCirculation.bandD(z);
                d.land[i] = NoiseContinentGrid.landResidual(x, z, worldSeedInt) >= 0.0;
                d.teqSea[i] = ThermalForcing.seaTeq(x, z, worldSeedInt);
                d.teqLand[i] = ThermalForcing.landTeq(x, z, worldSeedInt);
                d.qLandEq[i] = 0.08 + 0.30 * ThermalForcing.insolation01(b);   // 湿润热带陆平衡升（雨林水汽）
                double n = NoiseContinentGrid.bandNoise(x, z, worldSeedInt, 0xABC_1234L, 1.0 / 140_000.0, 2);
                d.wave[i] = (n * 2.0 - 1.0) * 0.28 * Math.sin(Math.PI * b);
                if (d.land[i]) {
                    d.tAir[i] = d.teqLand[i];
                    d.q[i] = d.qLandEq[i];
                    d.mar[i] = 0.0;
                } else {
                    d.sst[i] = d.teqSea[i];
                    d.tAir[i] = d.teqSea[i];
                    d.q[i] = 0.60 + 0.35 * d.teqSea[i];
                    d.mar[i] = 1.0;
                }
            }
        }
        updateP(d, worldSeedInt);
        computeWind(d, 1.0, worldSeedInt);
        updateFlow(d, worldSeedInt);
        for (int it = 0; it < OUTER; it++) {
            updateP(d, worldSeedInt);
            computeWind(d, 0.5, worldSeedInt);
            updateFlow(d, worldSeedInt);
            for (int k = 0; k < INNER; k++) {
                if (advectField(d, worldSeedInt, true) < INNER_TOL) {
                    break;
                }
            }
            for (int k = 0; k < AIR_INNER; k++) {
                advectAir(d, worldSeedInt);
            }
        }
        updateP(d, worldSeedInt);
        return d;
    }

    private static double profileP0(double b) {
        return -0.95 * gauss(b, 0.00, 0.17) + 1.00 * gauss(b, 0.30, 0.085)
             - 0.80 * gauss(b, 0.62, 0.075) + 0.55 * gauss(b, 0.95, 0.09);
    }

    private static double gauss(double b, double c, double s) {
        double x = (b - c) / s;
        return Math.exp(-0.5 * x * x);
    }

    private static void updateP(ClimateGridData d, int worldSeedInt) {
        double[] zmean = new double[d.ny];
        int[] cnt = new int[d.ny];
        for (int iy = 0; iy < d.ny; iy++) {
            double s = 0;
            int c = 0;
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (!d.land[i]) {
                    s += d.sst[i];
                    c++;
                }
            }
            zmean[iy] = c > 0 ? s / c : 0;
            cnt[iy] = c;
        }
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int x = ix * CELL, z = iy * CELL;
                double pv = d.p0z[iy] + d.wave[i];
                if (d.land[i]) {
                    double b = GlobalCirculation.bandD(z);
                    double teq = ThermalForcing.landTeq(x, z, worldSeedInt);
                    pv += -1.5 * (teq - ThermalForcing.zonalMeanSeaTeq(b));
                } else if (cnt[iy] > 0) {
                    pv -= SST_P_GAIN * (d.sst[i] - zmean[iy]);
                }
                d.p[i] = pv;
            }
        }
    }

    private static void computeWind(ClimateGridData d, double blendNew, int worldSeedInt) {
        double scale = 14_000.0;
        double ca = Math.cos(0.42), sa = Math.sin(0.42);
        double[] nu = new double[d.nx * d.ny];
        double[] nv = new double[d.nx * d.ny];
        for (int iy = 0; iy < d.ny; iy++) {
            double f = d.fRow[iy];
            if (f == 0.0) {
                for (int ix = 0; ix < d.nx; ix++) {
                    nu[d.idx(ix, iy)] = 0;
                    nv[d.idx(ix, iy)] = 0;
                }
                continue;
            }
            double damp = d.dampRow[iy];
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int ip = d.idx(ix + 1, iy), im = d.idx(ix - 1, iy);
                int jp = d.idx(ix, iy + 1), jm = d.idx(ix, iy - 1);
                double dpdx = (d.p[ip] - d.p[im]) / (2.0 * CELL);
                double dpdz = (d.p[jp] - d.p[jm]) / (2.0 * CELL);
                double ug = -(dpdz / f) * scale * damp;
                double vg = (dpdx / f) * scale * damp;
                double sp = Math.sqrt(ug * ug + vg * vg);
                if (sp < 1e-9) {
                    nu[i] = 0;
                    nv[i] = 0;
                    continue;
                }
                double gl = Math.sqrt(dpdx * dpdx + dpdz * dpdz) + 1e-12;
                double gxu = -dpdx / gl, gzu = -dpdz / gl;
                double sx = (ug / sp) * ca + gxu * sa;
                double sz = (vg / sp) * ca + gzu * sa;
                double sl = Math.sqrt(sx * sx + sz * sz) + 1e-12;
                nu[i] = (sx / sl) * sp * 0.88;
                nv[i] = (sz / sl) * sp * 0.88;
            }
        }
        double w1 = blendNew, w2 = 1.0 - blendNew;
        for (int i = 0; i < d.nx * d.ny; i++) {
            d.u[i] = nu[i] * w1 + d.pu[i] * w2;
            d.v[i] = nv[i] * w1 + d.pv[i] * w2;
            d.pu[i] = d.u[i];
            d.pv[i] = d.v[i];
        }
    }

    private static void updateFlow(ClimateGridData d, int worldSeedInt) {
        double ca = Math.cos(0.35), sa = Math.sin(0.35);
        for (int iy = 0; iy < d.ny; iy++) {
            double s = (iy * CELL <= 100_000) ? 1.0 : -1.0;
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    d.fu[i] = 0;
                    d.fv[i] = 0;
                    continue;
                }
                double sp = Math.sqrt(d.u[i] * d.u[i] + d.v[i] * d.v[i]);
                if (sp < 1e-6) {
                    d.fu[i] = 0;
                    d.fv[i] = 0;
                    continue;
                }
                double ux = d.u[i] / sp, uz = d.v[i] / sp;
                double fx = ux * ca + s * uz * sa;
                double fz = -s * ux * sa + uz * ca;
                double dist = NoiseContinentGrid.coastDistBlocks(ix * CELL, iy * CELL, worldSeedInt);
                if (dist < 26_000) {
                    int k = 2;
                    double dp = NoiseContinentGrid.coastDistBlocks((ix + k) * CELL, iy * CELL, worldSeedInt)
                              - NoiseContinentGrid.coastDistBlocks((ix - k) * CELL, iy * CELL, worldSeedInt);
                    double dz = NoiseContinentGrid.coastDistBlocks(ix * CELL, (iy + k) * CELL, worldSeedInt)
                              - NoiseContinentGrid.coastDistBlocks(ix * CELL, (iy - k) * CELL, worldSeedInt);
                    double gl = Math.sqrt(dp * dp + dz * dz);
                    if (gl > 1e-9) {
                        double nx = dp / gl, nz = dz / gl;
                        double vn = fx * nx + fz * nz;
                        if (vn < 0) {
                            double inf = (26_000 - dist) / (26_000 - 4000);
                            if (inf > 1) inf = 1;
                            if (inf < 0) inf = 0;
                            fx -= vn * nx * inf;
                            fz -= vn * nz * inf;
                            double l2 = Math.sqrt(fx * fx + fz * fz);
                            if (l2 > 1e-9) {
                                fx /= l2;
                                fz /= l2;
                            }
                        }
                    }
                }
                d.fu[i] = fx;
                d.fv[i] = fz;
            }
        }
    }

    /** 海温输运步（仅海上）；返回 RMS 变化。 */
    private static double advectField(ClimateGridData d, int worldSeedInt, boolean unused) {
        int steps = 6;
        double dt = 6000.0;
        double relaxL = 55_000.0;
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    d.sstNew[i] = d.sst[i];
                    continue;
                }
                double px = ix * CELL, pz = iy * CELL;
                boolean stopped = false;
                for (int k = 0; k < steps && !stopped; k++) {
                    double fx = bilinear(d.fu, px, pz), fz = bilinear(d.fv, px, pz);
                    double sp = Math.sqrt(fx * fx + fz * fz);
                    if (sp < 1e-6) {
                        stopped = true;
                        break;
                    }
                    px -= (fx / sp) * dt;
                    pz -= (fz / sp) * dt;
                    int lx = (int) Math.floor(px / CELL);
                    int lz = (int) Math.floor(pz / CELL);
                    if (d.land[d.idx(lx, lz)]) {
                        stopped = true;
                    }
                }
                double tAd = bilinear(d.sst, px, pz);
                double f = 1.0 - Math.exp(-steps * dt / relaxL);
                d.sstNew[i] = tAd + (d.teqSea[i] - tAd) * f;
            }
        }
        double sum = 0;
        int n = 0;
        for (int i = 0; i < d.nx * d.ny; i++) {
            if (!d.land[i]) {
                double dd = d.sstNew[i] - d.sst[i];
                sum += dd * dd;
                n++;
            }
        }
        double rms = Math.sqrt(sum / Math.max(1, n));
        for (int iy = 1; iy < d.ny - 1; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    continue;
                }
                double lap = (d.sstNew[d.idx(ix + 1, iy)] + d.sstNew[d.idx(ix - 1, iy)]
                            + d.sstNew[d.idx(ix, iy + 1)] + d.sstNew[d.idx(ix, iy - 1)]
                            - 4 * d.sstNew[i]) * 0.04;
                d.sstNew[i] += lap;
            }
        }
        for (int i = 0; i < d.nx * d.ny; i++) {
            if (!d.land[i]) {
                d.sst[i] = d.sstNew[i];
                if (d.sst[i] > 1) d.sst[i] = 1;
                if (d.sst[i] < -1) d.sst[i] = -1;
            }
        }
        return rms;
    }

    /** 空气三场输运步（全定义域；海上目标=已解海温、陆地目标=landTeq/干平衡）。 */
    private static void advectAir(ClimateGridData d, int worldSeedInt) {
        int steps = 5;
        double dt = 6000.0;
        double lt = 40_000.0, lqSea = 20_000.0, lqLand = 55_000.0, lm = 30_000.0;
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                double px = ix * CELL, pz = iy * CELL;
                boolean stopped = false;
                // 先取路径最远点做"源地初始化"（用当前场近似），再从远到近弛豫
                double[][] path = new double[steps + 1][2];
                path[0][0] = px;
                path[0][1] = pz;
                for (int k = 1; k <= steps && !stopped; k++) {
                    double fx = bilinear(d.u, px, pz), fz = bilinear(d.v, px, pz);
                    double sp = Math.sqrt(fx * fx + fz * fz);
                    if (sp < 1e-6) {
                        stopped = true;
                        for (int j = k; j <= steps; j++) {
                            path[j][0] = px;
                            path[j][1] = pz;
                        }
                        break;
                    }
                    px -= (fx / sp) * dt;
                    pz -= (fz / sp) * dt;
                    path[k][0] = px;
                    path[k][1] = pz;
                }
                int sxi = (int) Math.floor(path[steps][0] / CELL);
                int szi = (int) Math.floor(path[steps][1] / CELL);
                int si = d.idx(sxi, szi);
                double t = d.land[si] ? d.teqLand[si] : d.sst[si];
                double q = d.land[si] ? d.qLandEq[si] : 0.60 + 0.35 * d.sst[si];
                double m = d.land[si] ? 0.0 : 1.0;
                for (int k = steps - 1; k >= 0; k--) {
                    int cxi = (int) Math.floor(path[k][0] / CELL);
                    int czi = (int) Math.floor(path[k][1] / CELL);
                    int ci = d.idx(cxi, czi);
                    boolean cl = d.land[ci];
                    double eqT = cl ? d.teqLand[ci] : d.sst[ci];
                    double eqQ = cl ? d.qLandEq[ci] : 0.60 + 0.35 * d.sst[ci];
                    double ft = 1.0 - Math.exp(-dt / lt);
                    double fq = 1.0 - Math.exp(-dt / (cl ? lqLand : lqSea));
                    double fm = 1.0 - Math.exp(-dt / lm);
                    t += (eqT - t) * ft;
                    q += (eqQ - q) * fq;
                    m += ((cl ? 0.0 : 1.0) - m) * fm;
                }
                d.tNew[i] = t;
                d.qNew[i] = q;
                d.marNew[i] = m;
            }
        }
        // 拷回（轻微限幅）
        for (int i = 0; i < d.nx * d.ny; i++) {
            double t = d.tNew[i];
            double q = d.qNew[i];
            double m = d.marNew[i];
            d.tAir[i] = t < -1 ? -1 : (t > 1 ? 1 : t);
            d.q[i] = q < 0 ? 0 : (q > 1 ? 1 : q);
            d.mar[i] = m < 0 ? 0 : (m > 1 ? 1 : m);
        }
    }
}
