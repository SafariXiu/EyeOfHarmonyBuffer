package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

/**
 * 环面周期噪声原语（世界周期 400k(x) × 200k(z)）。
 *
 * **为什么需要**：世界是环面，但底层噪声的 lattice 哈希原本不取模，f(x,z) ≠ f(x+400k,z)；
 * 而群系 LUT / 山层网格都是按周期环绕求解的 → 超出一个周期后群系与地形错位。
 *
 * **两类原语**：
 *   - {@link #value2}：值噪声（大陆层用）。哈希与旧实现完全一致，lattice 索引按格数取模，
 *     第一周期内与原实现**逐位相同**。
 *   - {@link #gradient2} / {@link #gradientFbm2} / {@link #ridged2} / {@link #warpedFbm2}：
 *     方格 lattice 梯度噪声（地形塑形用）。经典 simplex 的斜 lattice 无法与直角世界周期对齐
 *     （斜率为无理数，任何轴向平移都映射不到整数格位移），故改用 quintic 淡入淡出 +
 *     12 个均匀梯度，消除轴向伪影；谱形与 simplex 同量级。
 *
 * **格数约定**：波长 λ → 每轴格数 n = round(周期/λ)（≥1），有效波长 = 周期/n。
 * 每层噪声独立量化，八度把格数 ×2（仍是整数）→ 任意组合都严格周期。
 */
public final class PeriodicNoise {

    public static final double PERIOD_X = 400_000.0;
    public static final double PERIOD_Z = 4_000_000.0;   // = ClimateLatitudes.LAT_CYCLE（纬度循环）
    private static final double INV_PX = 1.0 / PERIOD_X;
    private static final double INV_PZ = 1.0 / PERIOD_Z;

    /**
     * **X 方向无限**（C1 世界：X 不重复、Z 仍是 200k 纬度循环）。
     *
     * true 时 {@link #cellsX}/{@link #cellsXFromFreq} 一律返回 ≤0（"不折叠"），
     * 于是所有走本原语的噪声自动变成 X 无界；Z 方向不受影响。
     * 周期 400k 内的取值与 false 时**逐位相同**（取模在周期内是恒等变换）。
     */
    public static boolean INFINITE_X = true;

    private PeriodicNoise() {}

    /**
     * 波长 → x 方向格数。
     * 返回值 > 0 = 该轴按格数取模折叠；**< 0 = 不折叠但保留 |格数| 的尺度**（X 无限时用）。
     */
    public static int cellsX(double wavelength) {
        int n = (int) Math.round(PERIOD_X / wavelength);
        if (n < 1) {
            n = 1;
        }
        return INFINITE_X ? -n : n;
    }

    /** 波长 → z 方向格数（≥1）。 */
    public static int cellsZ(double wavelength) {
        int n = (int) Math.round(PERIOD_Z / wavelength);
        return n < 1 ? 1 : n;
    }

    /**
     * 值噪声：**两轴都任意波长、都不折叠**（无限平面世界）。
     * 大陆/海洋/洞穴等"空间"层用它；只有纬度带（bandD）保留 200k 循环。
     */
    public static double value2XZ(long seed, double x, double z, double wavX, double wavZ) {
        double sx = x / wavX, sz = z / wavZ;
        int xi = fastFloor(sx), zi = fastFloor(sz);
        double fx = sx - xi, fz = sz - zi;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fz * fz * (3.0 - 2.0 * fz);
        int x1 = xi + 1, z1 = zi + 1;
        double a = hashUnit(seed, xi, zi), b = hashUnit(seed, x1, zi);
        double c = hashUnit(seed, xi, z1), dd = hashUnit(seed, x1, z1);
        double ab = a + (b - a) * u, cd = c + (dd - c) * u;
        return ab + (cd - ab) * v;
    }

    /** 频率（1/blocks）→ x 方向格数（>0 折叠 / <0 不折叠但保尺度）。 */
    public static int cellsXFromFreq(double freq) {
        int n = (int) Math.round(PERIOD_X * freq);
        if (n < 1) {
            n = 1;
        }
        return INFINITE_X ? -n : n;
    }

    /** 频率（1/blocks）→ z 方向格数（≥1）。 */
    public static int cellsZFromFreq(double freq) {
        int n = (int) Math.round(PERIOD_Z * freq);
        return n < 1 ? 1 : n;
    }

    // ================= 值噪声（大陆层，[0,1]） =================

    /**
     * 周期值噪声 [0,1]（哈希与 NoiseContinentGrid 原实现一致 → 第一周期逐位相同）。
     * **格数 ≤ 0 表示该轴不折叠**（无限世界 / X 无限时用）。
     */
    public static double value2(long seed, double x, double z, int nx, int nz) {
        int ax = nx < 0 ? -nx : (nx == 0 ? 1 : nx);
        int az = nz < 0 ? -nz : (nz == 0 ? 1 : nz);
        double sx = x * (ax * INV_PX);
        double sz = z * (az * INV_PZ);
        int xi = fastFloor(sx), zi = fastFloor(sz);
        double fx = sx - xi, fz = sz - zi;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fz * fz * (3.0 - 2.0 * fz);
        int x0 = nx > 0 ? mod(xi, ax) : xi, x1 = nx > 0 ? mod(xi + 1, ax) : xi + 1;
        int z0 = nz > 0 ? mod(zi, az) : zi, z1 = nz > 0 ? mod(zi + 1, az) : zi + 1;
        double a = hashUnit(seed, x0, z0), b = hashUnit(seed, x1, z0);
        double c = hashUnit(seed, x0, z1), d = hashUnit(seed, x1, z1);
        double ab = a + (b - a) * u, cd = c + (d - c) * u;
        return ab + (cd - ab) * v;
    }

    private static double hashUnit(long seed, int gx, int gz) {
        long h = hash2(seed, gx, gz);
        long m = (h & 0xFFFFFFFFFFFFFFFFL) >>> (64 - 23);
        return m / (double) (1L << 23);
    }

    /**
     * 2 输入哈希（等价于 TectonicMath.hashLongs 的混合强度，但**无 varargs 数组分配**）。
     * 热路径实测：hashLongs 的 long[] 分配是列耗时里可测的一块。
     */
    private static long hash2(long seed, int gx, int gz) {
        long h = seed + 0x9E3779B97F4A7C15L;
        h = mix64(h ^ ((gx & 0xFFFFFFFFL) * 0x9E3779B97F4A7C15L));
        h = mix64(h ^ ((gz & 0xFFFFFFFFL) * 0xC2B2AE3D27D4EB4FL));
        return h;
    }

    private static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    // ================= 梯度噪声（地形塑形，[-1,1]） =================

    /** 12 个均匀分布的单位梯度（30° 间隔，消除轴向伪影）。 */
    private static final double[] GRAD = new double[24];
    static {
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI / 6.0);
            GRAD[i * 2] = Math.cos(a);
            GRAD[i * 2 + 1] = Math.sin(a);
        }
    }

    /**
     * 归一化系数：使 rms 与旧 simplex（Gustavson ×70）一致。
     * 探针 P83 标定（400k×400k 采样域、覆盖 133×133 个 lattice 格）：
     * simplex 单层 rms 0.4415 / 梯度噪声 0.21565 → 2.047；fbm3 归一后 0.508 vs 0.502（差 1%）。
     */
    public static double GRAD_NORM = 2.047;

    /** 周期梯度噪声 [-1,1]（方格 lattice + quintic 淡入淡出 + lattice 取模）。 */
    public static double gradient2(long seed, double x, double z, int nx, int nz) {
        int ax = nx < 0 ? -nx : (nx == 0 ? 1 : nx);
        int az = nz < 0 ? -nz : (nz == 0 ? 1 : nz);
        double sx = x * (ax * INV_PX);
        double sz = z * (az * INV_PZ);
        int xi = fastFloor(sx), zi = fastFloor(sz);
        double fx = sx - xi, fz = sz - zi;
        double u = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
        double v = fz * fz * fz * (fz * (fz * 6.0 - 15.0) + 10.0);
        int x0 = nx > 0 ? mod(xi, ax) : xi, x1 = nx > 0 ? mod(xi + 1, ax) : xi + 1;
        int z0 = nz > 0 ? mod(zi, az) : zi, z1 = nz > 0 ? mod(zi + 1, az) : zi + 1;
        double n00 = gradDot(seed, x0, z0, fx, fz);
        double n10 = gradDot(seed, x1, z0, fx - 1.0, fz);
        double n01 = gradDot(seed, x0, z1, fx, fz - 1.0);
        double n11 = gradDot(seed, x1, z1, fx - 1.0, fz - 1.0);
        double a = n00 + (n10 - n00) * u;
        double b = n01 + (n11 - n01) * u;
        return (a + (b - a) * v) * GRAD_NORM;
    }

    private static double gradDot(long seed, int gx, int gz, double dx, double dz) {
        long h = hash2(seed, gx, gz);
        int gi = (int) ((h >>> 40) % 12L) * 2;
        return GRAD[gi] * dx + GRAD[gi + 1] * dz;
    }

    /**
     * 周期梯度 fbm，返回**未归一化的振幅和**（与 TerrainNoise.fbm2DS 语义一致：
     * 调用方自行除以 octaveSum）。格数逐层 ×2。
     */
    public static double gradientFbm2(long seed, double x, double z, int nx, int nz, int octaves) {
        double sum = 0.0, amp = 1.0;
        int cx = nx, cz = nz;
        for (int i = 0; i < octaves; i++) {
            sum += amp * gradient2(seed + i * 0x9E3779B97F4A7C15L, x, z, cx, cz);
            amp *= 0.5;
            cx *= 2;
            cz *= 2;
        }
        return sum;
    }

    /** 周期域扭曲 fbm（先按 warp 噪声扭曲世界坐标，再取 fbm），返回未归一化和。 */
    public static double warpedFbm2(long seed, double x, double z,
                                    int nx, int nz, int octaves,
                                    int wnx, int wnz, double warpAmp) {
        double wx = x + warpAmp * gradient2(seed + 0x51ED270BL, x, z, wnx, wnz);
        double wz = z + warpAmp * gradient2(seed + 0x27D4EB2FL, x, z, wnx, wnz);
        return gradientFbm2(seed, wx, wz, nx, nz, octaves);
    }

    /** 周期脊状噪声 [0,1]（(1-|n|)²，归一化到 [0,1]；与 V2TerrainGen.ridged 同口径）。 */
    public static double ridged2(long seed, double x, double z, int nx, int nz, int octaves) {
        double sum = 0.0, amp = 1.0, norm = 0.0;
        int cx = nx, cz = nz;
        for (int i = 0; i < octaves; i++) {
            double n = gradient2(seed + i * 0x9E3779B9L, x, z, cx, cz);
            double v = 1.0 - Math.abs(n);
            sum += v * v * amp;
            norm += amp;
            amp *= 0.5;
            cx *= 2;
            cz *= 2;
        }
        return norm > 0 ? sum / norm : 0.0;
    }

    // ================= 工具 =================

    private static int fastFloor(double x) {
        int i = (int) x;
        return x < i ? i - 1 : i;
    }

    private static int mod(int v, int m) {
        int r = v % m;
        return r < 0 ? r + m : r;
    }
}
