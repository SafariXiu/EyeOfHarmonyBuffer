package com.EyeOfHarmonyBuffer.sim.litho;

/**
 * TalosField —— V8 陆地生成器的 Java 落地版（设计冻结 §305~§310）。
 *
 * <p><b>与 PlateField 的关系：完全独立。</b>本类不被任何现有代码调用，
 * 因此**不改变任何现有行为**（无需开关即可安全共存）。等性能与形态都达标后再整体切换。
 *
 * <p><b>硬约束（全部满足的结构）</b>：
 * <ul>
 *   <li>X 无限、Z 无限、**都无周期**（WorldContract：地形在 Z 上不重复）；</li>
 *   <li>**真 O(1)**：格点属性由 hash(i,j,seed) 派生，**无任何全局数组、无全图 min/max**；</li>
 *   <li>负坐标正确：晶格索引用 floor（E112）；</li>
 *   <li>每世界一次自标定（1024 点，与列数无关）得到海平面 LEVEL。</li>
 * </ul>
 *
 * <p><b>单位：block。</b>契约锁死 1 格 = 1 米，Z_CYCLE = 20,000,000 block = 20,000 km。
 */
public final class TalosField {

    private TalosField() {}

    // ================= 世界尺度 =================</br>
    /** 世界跨度（block）：对应原型里的 L = 20,000 km。 */
    public static final double L = 20_000_000.0;
    /** 格距：原型 NC = 9。 */
    public static final double DCELL = L / 9.0;
    public static final double SWS = 1200.0 * 1000.0;   // 顶点弯曲（km -> block）
    public static final double JIT = 0.40;
    public static double AN = 0.320;                 // 可调：海岸线粗糙度振幅（默认 0.320）
    public static double HH = 0.55;                  // 可调：细节层 Hurst（默认 0.55）
    /** 改 H 必须同时刷新预计算的倍频增益。 */
    public static void setHurst(double h) { HH = h; GHH = Math.pow(2, -h); }
    public static final double USx = 3.0;
    public static final double LAM = 0.9;
    public static final double RW  = 2.2 * DCELL;       // 紧支撑核半径（E113）
    static final double RW2 = RW * RW;                  // 核用 d^2 的多项式：省掉每列 25 次 sqrt

    // ================= hash 噪声（无界、非周期、负坐标安全） =================
    static long mix(long s, long a, long b) {
        long h = s * 0x9E3779B97F4A7C15L + a;
        h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L;
        h = h * 0x9E3779B97F4A7C15L + b;
        h ^= (h >>> 32); h *= 0x94D049BB133111EBL; h ^= (h >>> 29);
        return h;
    }
    static double rnd01(long h) {
        long v = h;
        v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL;
        v ^= (v >>> 33); v *= 0xC4CEB9FE1A85EC53L;
        v ^= (v >>> 33);
        return (v >>> 11) * 0x1.0p-53;
    }
    static double vnoise(double x, double z, long s) {
        double xf = Math.floor(x), zf = Math.floor(z);
        long xi = (long) xf, zi = (long) zf;
        double tx = x - xf, tz = z - zf;
        double u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(mix(s, xi, zi)) * 2 - 1, b = rnd01(mix(s, xi + 1, zi)) * 2 - 1;
        double c = rnd01(mix(s, xi, zi + 1)) * 2 - 1, d = rnd01(mix(s, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
    }
    /** 归一化 fbm：振幅按 H 衰减、总和归一（与原型逐位同构）。 */
    /** 倍频增益：Math.pow 在热路径上是纯浪费 —— 两个 H 都是编译期常量，直接预算。 */
    static final double G08 = Math.pow(2, -0.8);
    static double GHH = Math.pow(2, -HH);
    static double fbm(double x, double z, long s, int oct, double wl0, double gain) {
        double r = 0, a = 1, f = 1.0 / wl0, tot = 0;
        for (int o = 0; o < oct; o++) { r += a * vnoise(x * f, z * f, s + o * 7919L); tot += a; a *= gain; f *= 2; }
        return r / tot;
    }

    // ================= 格点属性（全部 hash 派生） =================
    static double cellX(long I, long J, long seed) {
        double nx = (I + 0.5) * DCELL, nz = (J + 0.5) * DCELL, dx = 0;
        for (int o = 0; o < 3; o++) { double aa = SWS * Math.pow(2, -0.8 * o), wl = 7_000_000.0 / (1 << o); dx += aa * vnoise(nx / wl, nz / wl, (seed ^ 0x11L) + o * 7919L); }
        return nx + dx + (rnd01(mix(seed ^ 0x44L, I, J)) - 0.5) * JIT * DCELL;
    }
    static double cellZ(long I, long J, long seed) {
        double nx = (I + 0.5) * DCELL, nz = (J + 0.5) * DCELL, dz = 0;
        for (int o = 0; o < 3; o++) { double aa = SWS * Math.pow(2, -0.8 * o), wl = 7_000_000.0 / (1 << o); dz += aa * vnoise(nx / wl + 0.37, nz / wl - 0.21, (seed ^ 0x22L) + o * 7919L); }
        return nz + dz + (rnd01(mix(seed ^ 0x55L, I, J)) - 0.5) * JIT * DCELL;
    }
    static double cellPHI(double cx, double cz, long seed) { return fbm(cx, cz, seed ^ 0x66L, 3, 9_000_000.0, G08); }
    static double cellPSI(long I, long J, long seed) { return fbm(cellX(I, J, seed), cellZ(I, J, seed), seed ^ 0x77L, 2, 7_000_000.0, G08); }

    static final int[][] NB = {{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}};

    // ================= O1：格窗缓存（每 139,000 个区块才失效一次） =================
    static final int WIN = 9, WOFF = 4;
    static long winCI = Long.MIN_VALUE, winCJ = Long.MIN_VALUE, winSeed = Long.MIN_VALUE;
    // 分块记忆化：XY 全块算一次，PHI/PSI 各算一次 —— 杜绝重复求值（乱序 3.7x 的根因）
    static final double[] wCX = new double[WIN * WIN], wCZ = new double[WIN * WIN];
    static final double[] wPHI = new double[WIN * WIN], wPSI = new double[WIN * WIN];
    static final double[] wNRM = new double[WIN * WIN], wB = new double[WIN * WIN];

    static void buildWindow(long ci, long cj, long seed) {
        winCI = ci; winCJ = cj; winSeed = seed;
        // 1) XY 全 9x9（唯一会调用 cellX/cellZ 的地方）
        for (int p = 0; p < WIN; p++) for (int q = 0; q < WIN; q++) {
            int k = p * WIN + q; long I = ci - WOFF + p, J = cj - WOFF + q;
            wCX[k] = cellX(I, J, seed); wCZ[k] = cellZ(I, J, seed);
        }
        // 2) PHI 内 7x7、PSI 全 9x9
        for (int p = 1; p < WIN - 1; p++) for (int q = 1; q < WIN - 1; q++) {
            int k = p * WIN + q; wPHI[k] = cellPHI(wCX[k], wCZ[k], seed);
        }
        for (int k = 0; k < WIN * WIN; k++) wPSI[k] = fbm(wCX[k], wCZ[k], seed ^ 0x77L, 2, 7_000_000.0, G08);
        // 3) 逐内格（5x5）：父、汇聚、预算噪声 —— 全部走记忆化数组
        for (int p = 2; p < WIN - 2; p++) for (int q = 2; q < WIN - 2; q++) {
            int k = p * WIN + q; double phi = wPHI[k]; int bi = p, bj = q; double best = phi;
            for (int[] d : NB) { int kk = (p + d[0]) * WIN + (q + d[1]); double pn = wPHI[kk];
                if (pn > best) { best = pn; bi = p + d[0]; bj = q + d[1]; } }
            double conv = 0;
            if (best > phi) {
                double gxs = (wPSI[(p+1)*WIN+q] - wPSI[(p-1)*WIN+q]) / (2 * DCELL);
                double gzs = (wPSI[p*WIN+q+1] - wPSI[p*WIN+q-1]) / (2 * DCELL);
                double gxb = (wPSI[(bi+1)*WIN+bj] - wPSI[(bi-1)*WIN+bj]) / (2 * DCELL);
                double gzb = (wPSI[bi*WIN+bj+1] - wPSI[bi*WIN+bj-1]) / (2 * DCELL);
                double dx = wCX[bi*WIN+bj] - wCX[k], dz = wCZ[bi*WIN+bj] - wCZ[k];
                double nn = Math.sqrt(dx * dx + dz * dz) + 1e-6;
                double vrel = (gxs - gxb) * dx / nn + (gzs - gzb) * dz / nn;
                conv = vrel > 0 ? vrel : 0;
            }
            wNRM[k] = fbm(wCX[k], wCZ[k], seed ^ 0x88L, 2, 12_000_000.0, G08);
            wB[k] = conv;
        }
        double[] bn = wNRM;
        // 解析归一化（E116）：SREF 取该窗的 sigma，CREF 用固定解析尺度（见 §308）
        double mu = 0; for (double v : bn) mu += v; mu /= bn.length;
        double s2 = 0; for (double v : bn) s2 += (v - mu) * (v - mu); double sref = Math.sqrt(s2 / bn.length);
        double c2 = 0; for (double v : wB) c2 += v * v; double cref = 1.5 * Math.sqrt(c2 / wB.length) + 1e-12;
        for (int k = 0; k < bn.length; k++) {
            double t = 0.65 * (bn[k] / (3.0 * sref)) + 0.35 * Math.tanh(wB[k] / cref);
            double v = 0.5 + 0.5 * t;
            wB[k] = v < 0 ? 0 : (v > 1 ? 1 : v);
        }
    }

    static void ensureWindow(long ci, long cj, long seed) {
        if (seed == winSeed && Math.abs(ci - winCI) <= 2 && Math.abs(cj - winCJ) <= 2) return;
        buildWindow(ci, cj, seed);
    }

    /** 预算场：紧支撑 C3 核插值（E113 —— 进出支撑区权重为 0，无格缝）。 */
    public static double bfield(double x, double z, long seed) {
        long ci = (long) Math.floor(x / DCELL), cj = (long) Math.floor(z / DCELL);
        ensureWindow(ci, cj, seed);
        double num = 0, den = 0;
        for (int di = -2; di <= 2; di++) for (int dj = -2; dj <= 2; dj++) {
            int p = (int) (ci + di - (winCI - WOFF)), q = (int) (cj + dj - (winCJ - WOFF));
            // ensureWindow 保证 |ci-winCI|<=2 ⇒ p,q 必在 [0,8]，边界检查是死代码（已删）
            int k = p * WIN + q;
            double dx = x - wCX[k], dz = z - wCZ[k];
            double t = 1 - (dx * dx + dz * dz) / RW2; if (t <= 0) continue;
            double w = t * t * t * t;
            num += w * wB[k]; den += w;
        }
        return den > 0 ? num / den : 0.5;
    }

    /** 细节层（宏观海岸线粗糙度）。 */
    public static double hf(double x, double z, long seed) { return fbm(x, z, seed ^ 0x99L, 7, 3_000_000.0, GHH); }
    /** 洋壳年龄场（与骨架解耦，E114）。 */
    public static double age(double x, double z, long seed) {
        double t = 0.5 + 0.5 * fbm(x, z, seed ^ 0xAAL, 3, 9_000_000.0, G08);
        return 70.0 * (t < 0 ? 0 : (t > 1 ? 1 : t));
    }
    /** 未减海平面的场值。 */
    public static double fieldValue(double x, double z, long seed) { return bfield(x, z, seed) + AN * hf(x, z, seed); }

    // ================= 每世界自标定（O(1) 于列数） =================
    static long lvlSeed = Long.MIN_VALUE; static double lvl = 0;
    /** 1024 点、跨 +-100,000 km（= 200,000,000 block）的固定采样上取 70% 分位。 */
    public static synchronized double level(long seed) {
        if (seed == lvlSeed) return lvl;
        final int G = 32, N = G * G;
        double[] f = new double[N];
        java.util.Random r = new java.util.Random(seed * 1000003L + 17L);
        for (int i = 0; i < N; i++) {
            double ox = (r.nextDouble() * 2 - 1) * 1.0e8, oz = (r.nextDouble() * 2 - 1) * 1.0e8;
            f[i] = fieldValue(ox, oz, seed);
        }
        java.util.Arrays.sort(f);
        lvl = f[(int) Math.floor(0.70 * N)];
        lvlSeed = seed;
        return lvl;
    }

    // ================= 高程：单调三次样条（Fritsch-Carlson，含平台段） =================
    static final double[] KX = {-1.60,-0.60,-0.100,-0.040,-0.016,-0.005,0.0,0.03,0.14,0.34,0.52,0.72,0.90,1.05,1.60};
    static final double[] KY = {-5600,-4400,-4180,-2150,-320,-110,0,160,430,760,1250,2300,3500,4600,5600};
    static final double[] MS = new double[KX.length];
    static {
        int n = KX.length; double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) d[i] = (KY[i + 1] - KY[i]) / (KX[i + 1] - KX[i]);
        MS[0] = d[0]; MS[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) MS[i] = (d[i - 1] + d[i]) / 2;
        for (int i = 0; i < n - 1; i++) {
            if (d[i] == 0) { MS[i] = 0; MS[i + 1] = 0; }
            else { double a = MS[i] / d[i], b = MS[i + 1] / d[i], s = a * a + b * b;
                   if (s > 9) { double t = 3 / Math.sqrt(s); MS[i] = t * a * d[i]; MS[i + 1] = t * b * d[i]; } }
        }
    }
    static double hyp(double u) {
        if (u <= KX[0]) return KY[0];
        if (u >= KX[KX.length - 1]) return KY[KY.length - 1];
        int lo = 0, hi = KX.length - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (u >= KX[mid]) lo = mid; else hi = mid; }
        int i = lo;
        double h = KX[i + 1] - KX[i], t = (u - KX[i]) / h;
        double t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * KY[i] + (t3 - 2 * t2 + t) * h * MS[i]
             + (-2 * t3 + 3 * t2) * KY[i + 1] + (t3 - t2) * h * MS[i + 1];
    }

    static double lvlFast(long seed) { return seed == lvlSeed ? lvl : level(seed); }

    /** 完整高程（米）。 */
    public static double elevation(double x, double z, long seed) {
        double v = fieldValue(x, z, seed) - lvlFast(seed);
        double h = hyp(USx * v);
        double a = age(x, z, seed);
        double ridge = -(2200.0 + 320.0 * Math.sqrt(a));
        return h > ridge ? h : ridge;
    }
    public static boolean isLand(double x, double z, long seed) { return fieldValue(x, z, seed) > lvlFast(seed); }
}
