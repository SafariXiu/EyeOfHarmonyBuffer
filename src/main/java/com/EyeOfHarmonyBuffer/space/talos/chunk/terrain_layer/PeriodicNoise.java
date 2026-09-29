package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

/**
 * 可选周期噪声原语。**"周期"是每轴可选的**：PERIOD_X = 100,000、PERIOD_Z = 1,000,000
 * （⚠ 该值**不是**任何纬度循环 —— 原注释曾写「= ClimateLatitudes.LAT_CYCLE」，
 * 那是概念挂错；`ClimateLatitudes` 与整个旧栈已于 2026-09-18 退役），
 * 但 `INFINITE_X` / `INFINITE_Z` 两个开关**默认都为 true**，
 * 也就是当前世界在两个方向上都不折叠（见下方两段 javadoc）。
 *
 * **为什么需要**：底层噪声的 lattice 哈希原本不取模，f(x,z) ≠ f(x+周期,z)；
 * 早期世界是环面（群系 LUT / 山层网格按周期环绕求解），超出一个周期后群系与地形错位，
 * 所以本原语提供"按整格数取模"的选项。契约变更后（X 无限、Z 只重复气候）默认走不折叠那条。
 *
 * **两类原语**：
 *   - {@link #gradient2} / {@link #gradientFbm2} / {@link #ridged2} / {@link #warpedFbm2}：
 *     方格 lattice 梯度噪声（地形塑形用）。经典 simplex 的斜 lattice 无法与直角世界周期对齐
 *     （斜率为无理数，任何轴向平移都映射不到整数格位移），故改用 quintic 淡入淡出 +
 *     12 个均匀梯度，消除轴向伪影；谱形与 simplex 同量级。
 *
 * **格数约定**：波长 λ → 每轴格数 n = round(周期/λ)（≥1），有效波长 = 周期/n。
 * 每层噪声独立量化，八度把格数 ×2（仍是整数）→ 任意组合都严格周期。
 */
public final class PeriodicNoise {

    public static final double PERIOD_X = 100_000.0;
    /**
     * ⚠ 2026-09-18（顶死一套·第 2 段）：**本常量是惰性的**。
     *
     * <p>它不是「纬度循环」，而是**早期环面世界**留下的 Z 折叠周期；
     * 原注释写「= ClimateLatitudes.LAT_CYCLE（纬度循环）」是**概念挂错**：
     * 地形塑形噪声的周期与纬度气候周期本来就是两件事。
     * （`ClimateLatitudes` 与整个 1M 旧栈已于 2026-09-18 顶死一套时退役，本常量**不属于任何契约**。）
     *
     * <p>{@link #INFINITE_Z} 默认为 true ⇒ {@link #cellsZ} 一律返回 ≤0 ⇒
     * <b>当前契约下 PERIOD_Z 一次都不参与计算</b>（见本类 javadoc 的格数约定）。
     * <p>退役计划：第 6 段删旧栈时，本常量与 {@code TalosContract} 里那条
     * 「PERIOD_Z == LAT_CYCLE」的自检一起删除（那条自检是在给一套死代码做自检）。
     */
    public static final double PERIOD_Z = 1_000_000.0;
    private static final double INV_PX = 1.0 / PERIOD_X;
    private static final double INV_PZ = 1.0 / PERIOD_Z;

    /**
     * **X 方向无限**（C1 世界：X 不重复；Z 的周期只由 LAT_CYCLE 管气候，见 INFINITE_Z）。
     *
     * true 时 {@link #cellsX}/{@link #cellsXFromFreq} 一律返回 ≤0（"不折叠"），
     * 于是所有走本原语的噪声自动变成 X 无界；Z 方向不受影响。
     * 周期 PERIOD_X（100k）内的取值与 false 时**逐位相同**（取模在周期内是恒等变换）。
     */
    public static boolean INFINITE_X = true;

    /**
     * **Z 方向也无限**（契约：Z_CYCLE = 1M 只控制【气候/纬度】，地形/海陆/洋流必须在两个方向上
     * 都逐块不同）。true 时 {@link #cellsZ}/{@link #cellsZFromFreq} 一律返回 ≤0（"不折叠"），
     * 于是所有走本原语的塑形噪声在 Z 上不再每 1M 复读；false = 旧行为（每 1M 逐位相同）。
     *
     * 注意：{@code gradient2} 的 lattice 取模本来就是 {@code nz > 0} 才走，
     * 所以这里只要把格数取负即可，不必改噪声本体。
     */
    public static boolean INFINITE_Z = true;

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

    /**
     * 波长 → z 方向格数。
     * 返回值 > 0 = 按格数取模折叠（每 1M 复读）；**< 0 = 不折叠但保留 |格数| 的尺度**。
     */
    public static int cellsZ(double wavelength) {
        int n = (int) Math.round(PERIOD_Z / wavelength);
        if (n < 1) {
            n = 1;
        }
        return INFINITE_Z ? -n : n;
    }

    /**
     * 值噪声：**两轴都任意波长、都不折叠**（无限平面世界）。
     * 大陆/海洋/洞穴等"空间"层用它；只有纬度带（bandD）保留 LAT_CYCLE（1M）循环。
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

    /** 频率（1/blocks）→ z 方向格数（>0 折叠 / <0 不折叠但保尺度）。 */
    public static int cellsZFromFreq(double freq) {
        int n = (int) Math.round(PERIOD_Z * freq);
        if (n < 1) {
            n = 1;
        }
        return INFINITE_Z ? -n : n;
    }

    // 值噪声：大陆/海洋/洞穴层统一走 value2XZ（两轴都不折叠）。此处曾有一个零引用的
    // value2(seed,x,z,nx,nz) 重载，已作为死代码删除。

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
     * 探针 P83 标定（当时的采样域 400k×400k、覆盖 133×133 个 lattice 格 —— 这个 400k 是采样域，与世界周期无关）：
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
