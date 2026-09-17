package probe;

import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P533：**新算法原型 —— 极坐标星形域（设计冻结 §290/§291）**。第一步只做海陆。
 *
 * 陆地 = union over 抖动网格上的中心 c_i of { |p - c_i| < R_i(theta) }，
 *   R_i(theta) = min( R_base * aniso(theta) * (1 + AMP*fbm(cos t*RHO, sin t*RHO)),
 *                     0.49 * (到最近邻中心的距离) )      <-- 逐格常数 ⇒ 保证互不相交
 *
 * **结构性定理**：每个 { |p-c| < R(theta) } 是星形域（单连通、补集连通）；
 * 且两两不相交 ⇒ **不可能有内海、不可能有岛屿**。粗糙度全部落在 1 维 R(theta) 上，对拓扑零影响。
 *
 * 本探针**不动生产代码**，自带 isLandR。
 */
public class P533 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P533] " + s); rep.flush(); System.out.println("[P533] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static int SEED = 1022228679;

    // ---- 新算法参数 ----
    static int DCELL = 7_000_000;        // 大陆格边长（block = m）= 7000 km
    static double JIT = 0.25;            // 站点抖动（单位 = 格边长）
    static double RBASE_FRAC = 0.30;     // R_base = RBASE_FRAC * DCELL
    static double AMP = 0.35;            // R(theta) 的起伏幅度（不影响拓扑）
    static double RHO = 40.0;            // 噪声圆半径（控制最细尺度）
    static int OCT = 5;                  // R(theta) 的八度数
    static double ANISO = 0.50;          // 经向拉长：aniso = 1 + ANISO*|sin(theta)|
    static final long SITE_SALT = 0x5EED_0101L, COAST_SALT = 0x5EED_0102L;

    static int G0X, G0Z, GNX, GNZ;
    static double[] cxs, czs, rcaps;

    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    // ---- 与生产同款的哈希/噪声（逐字抄自 PlateField）----
    static double rnd01(long h) {
        long v = h; v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL; v ^= (v >>> 33);
        v *= 0xC4CEB9FE1A85EC53L; v ^= (v >>> 33);
        return (v >>> 11) * 0x1.0p-53;
    }
    static long hash2(long s, int a, int b) {
        long h = s; h = h * 0x9E3779B97F4A7C15L + a; h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L;
        h = h * 0x9E3779B97F4A7C15L + b; h ^= (h >>> 32); h *= 0x94D049BB133111EBL; h ^= (h >>> 29);
        return h;
    }
    static double vnoise(double x, double z, long s) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double tx = x - xi, tz = z - zi;
        double u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(hash2(s, xi, zi)) * 2 - 1, b = rnd01(hash2(s, xi + 1, zi)) * 2 - 1;
        double c = rnd01(hash2(s, xi, zi + 1)) * 2 - 1, d = rnd01(hash2(s, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
    }
    static double fbm(double x, double z, long s, int oct, double f0, double amp) {
        double r = 0, a = amp, f = f0;
        for (int i = 0; i < oct; i++) { r += a * vnoise(x * f, z * f, s + i * 7919L); a *= 0.5; f *= 2.0; }
        return r;
    }
    static double smoothstep01(double t) { return t <= 0 ? 0 : (t >= 1 ? 1 : t * t * (3 - 2 * t)); }

    /** 预计算窗口内所有格的中心与「到最近邻的距离」（逐格常数 ⇒ 保证互不相交）。 */
    static void buildTable() {
        int gx0 = (int) Math.floor((X0 - 2L * DCELL) / DCELL), gx1 = (int) Math.floor((X0 + SPAN + 2L * DCELL) / DCELL);
        int gz0 = (int) Math.floor((Z0 - 2L * DCELL) / DCELL), gz1 = (int) Math.floor((Z0 + SPAN + 2L * DCELL) / DCELL);
        G0X = gx0; G0Z = gz0; GNX = gx1 - gx0 + 1; GNZ = gz1 - gz0 + 1;
        cxs = new double[GNX * GNZ]; czs = new double[GNX * GNZ]; rcaps = new double[GNX * GNZ];
        for (int j = 0; j < GNZ; j++) for (int i = 0; i < GNX; i++) {
            int gx = gx0 + i, gz = gz0 + j;
            long h = hash2(seed ^ SITE_SALT, gx, gz);
            cxs[j * GNX + i] = (gx + 0.5 + JIT * (rnd01(h) * 2 - 1)) * DCELL;
            czs[j * GNX + i] = (gz + 0.5 + JIT * (rnd01(hash2(h, 7, 3)) * 2 - 1)) * DCELL;
        }
        for (int j = 0; j < GNZ; j++) for (int i = 0; i < GNX; i++) {
            double best = Double.MAX_VALUE;
            for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                if (di == 0 && dj == 0) continue;
                int a = i + di, b = j + dj; if (a < 0 || b < 0 || a >= GNX || b >= GNZ) continue;
                double d = Math.hypot(cxs[j * GNX + i] - cxs[b * GNX + a], czs[j * GNX + i] - czs[b * GNX + a]);
                if (d < best) best = d;
            }
            rcaps[j * GNX + i] = 0.49 * (best == Double.MAX_VALUE ? DCELL : best);
        }
    }

    /** **新算法**：星形域并集。 */
    static boolean isLandR(int x, int z) {
        int cx = Math.floorDiv(x, DCELL) - G0X, cz = Math.floorDiv(z, DCELL) - G0Z;
        double Rbase = RBASE_FRAC * DCELL;
        for (int dz = -1; dz <= 1; dz++) {
            int j = cz + dz; if (j < 0 || j >= GNZ) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int i = cx + dx; if (i < 0 || i >= GNX) continue;
                int id = j * GNX + i;
                double ddx = x - cxs[id], ddz = z - czs[id];
                double r2 = ddx * ddx + ddz * ddz;
                double cap = rcaps[id];
                if (r2 >= cap * cap) continue;                       // 早退：连上限都够不着
                double th = Math.atan2(ddz, ddx);
                double an = 1.0 + ANISO * Math.abs(Math.sin(th));
                double R = Rbase * an * (1.0 + AMP * fbm(Math.cos(th) * RHO, Math.sin(th) * RHO, seed ^ COAST_SALT, OCT, 1.0, 1.0));
                if (R > cap) R = cap;
                if (r2 < R * R) return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P533_star.txt"), "UTF-8");
        S("=== P533 新算法原型：极坐标星形域（只做海陆）===");
        S(String.format(LF, "D_CELL=%.0f km  JIT=%.2f  AMP=%.2f  RHO=%.0f  ANISO=%.2f  图幅 %.0f km ⇒ %.2f x %.2f 个格",
            DCELL / 1000.0, JIT, AMP, RHO, ANISO, SPAN / 1000.0, SPAN / (double) DCELL, SPAN / (double) DCELL));
        S("");
        // ★ 判据：R(theta) 的最细【角】波长必须 >= 采样尺度的 5~10 倍，否则混叠成椒盐。
        //   最细角波长(km) ≈ 2*pi*R_base / (RHO * 2^{OCT-1}) ；采样 = 5 km/px。
        double[][] rc = { { 40, 5 }, { 12, 3 }, { 8, 3 }, { 5, 2 } };
        for (int q = 0; q < rc.length; q++) {
            RHO = rc[q][0]; OCT = (int) rc[q][1];
            double lamKm = 2 * Math.PI * (0.30 * DCELL / 1000.0) / (RHO * Math.pow(2, OCT - 1));
            S("##### RHO=" + RHO + " OCT=" + OCT + "  ⇒ 最细角波长约 " + String.format(LF, "%.1f", lamKm) + " km（采样 5 km/px）#####");
            SEED = 1022228679; seed = SimTerrain.seedOf(SEED); buildTable();
            RBASE_FRAC = tune(0.30);
            byte[] lnd = new byte[W * W]; long nl2 = 0;
            for (int r = 0; r < W; r++) { int z = zOf(r); for (int c = 0; c < W; c++) { boolean l = isLandR(xOf(c), z); lnd[r * W + c] = (byte) (l ? 1 : 0); if (l) nl2++; } }
            int n1b = 0, n2b = 0, nb = 0;
            int[] lb = new int[W * W]; int[] sk = new int[W * W];
            for (int i = 0; i < W * W; i++) {
                if (lnd[i] == 0 || lb[i] != 0) continue;
                int sp = 0; sk[sp++] = i; lb[i] = 1; long a = 0;
                while (sp > 0) { int p = sk[--sp]; int r = p / W, c2 = p - r * W; a++;
                    int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                    for (int x : nn) if (lnd[x] == 1 && lb[x] == 0) { lb[x] = 1; sk[sp++] = x; } }
                n2b++; if (a > 0.02 * W * W) nb++; }
            for (int i = 0; i < W * W; i++) {
                if (lnd[i] == 1 || lb[i] != 0) continue;
                int sp = 0; sk[sp++] = i; lb[i] = 2; boolean lr = false;
                while (sp > 0) { int p = sk[--sp]; int r = p / W, c2 = p - r * W; if (c2 == 0 || c2 == W - 1) lr = true;
                    int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                    for (int x : nn) if (lnd[x] == 0 && lb[x] == 0) { lb[x] = 2; sk[sp++] = x; } }
                if (!lr) n1b++; }
            byte[] bd = new byte[W * W]; long nbp2 = 0;
            for (int r = 0; r < W; r++) for (int c = 0; c < W; c++) {
                if (lnd[r * W + c] == 0) continue;
                if (lnd[r * W + wr(c + 1)] == 1 && lnd[r * W + wr(c - 1)] == 1 && lnd[wr(r + 1) * W + c] == 1 && lnd[wr(r - 1) * W + c] == 1) continue;
                bd[r * W + c] = 1; nbp2++; }
            MapWriter.writePng(new File(MAPDIR, "sealandR_rho" + (int) RHO + "oct" + OCT + ".png"), W, W,
                toD(lnd), 0.0, 1.0, new MapWriter.ColorMap() { @Override public int rgb(double x) { return x < 0.5 ? 0x1B3B6F : 0xE0D5B0; } });
            S(String.format(LF, "  陆地 %.2f%%  **N-1 = %d  N-2 = %d**  N-6 = %d  海岸线 %d px  D = %.3f",
                100.0 * nl2 / (W * W), n1b, n2b, nb, nbp2, boxD(bd)));
        }
        if (true) { rep.flush(); rep.close(); System.out.println("JAVA_EXIT=0"); return; }
        int[] seeds = { 1022228679 };
        double[] targets = { 0.30 };
        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        for (int t = 0; t < targets.length; t++) {
            S("########## 目标陆地占比 " + (int) (targets[t] * 100) + "% ##########");
            S(String.format(LF, "%-11s %9s %10s %7s %7s %7s %9s %9s %9s", "seed", "R_base/D", "陆地%", "N-1", "N-2", "N-6", "海岸线px", "D", "G1b"));
            double[] n1s = new double[seeds.length], n2s = new double[seeds.length], n6s = new double[seeds.length];
            for (int k = 0; k < seeds.length; k++) {
                SEED = seeds[k]; seed = SimTerrain.seedOf(SEED);
                buildTable();
                RBASE_FRAC = tune(targets[t]);
                byte[] land = new byte[W * W];
                long nl = 0; double wsum = 0, lw = 0;
                for (int r = 0; r < W; r++) { int z = zOf(r); double cw = Math.cos(WorldContract.latOf(z));
                    for (int c = 0; c < W; c++) {
                        boolean l = isLandR(xOf(c), z);
                        land[r * W + c] = (byte) (l ? 1 : 0); wsum += cw; if (l) { nl++; lw += cw; }
                    } }
                int n1 = 0, n2 = 0, nbig = 0;
                for (int i = 0; i < W * W; i++) {
                    if (land[i] == 0 || lab[i] != 0) continue;
                    int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
                    while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; a++;
                        int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                        for (int x : nn) if (land[x] == 1 && lab[x] == 0) { lab[x] = 1; stk[sp++] = x; } }
                    n2++; if (a > 0.02 * W * W) nbig++;
                }
                for (int i = 0; i < W * W; i++) {
                    if (land[i] == 1 || lab[i] != 0) continue;
                    int sp = 0; stk[sp++] = i; lab[i] = 2; boolean lr = false;
                    while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; if (c2 == 0 || c2 == W - 1) lr = true;
                        int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                        for (int x : nn) if (land[x] == 0 && lab[x] == 0) { lab[x] = 2; stk[sp++] = x; } }
                    if (!lr) n1++;
                }
                java.util.Arrays.fill(lab, 0);
                double[] v = new double[W * W];
                for (int i = 0; i < W * W; i++) v[i] = land[i];
                MapWriter.writePng(new File(MAPDIR, "sealandR_t" + (int) (targets[t] * 100) + "_s" + SEED + ".png"), W, W, v, 0.0, 1.0,
                    new MapWriter.ColorMap() { @Override public int rgb(double x) { return x < 0.5 ? 0x1B3B6F : 0xE0D5B0; } });
                // 海岸线 px + D
                byte[] bnd = new byte[W * W]; long nbp = 0;
                for (int r = 0; r < W; r++) for (int c = 0; c < W; c++) {
                    if (land[r * W + c] == 0) continue;
                    if (land[r * W + wr(c + 1)] == 1 && land[r * W + wr(c - 1)] == 1 && land[wr(r + 1) * W + c] == 1 && land[wr(r - 1) * W + c] == 1) continue;
                    bnd[r * W + c] = 1; nbp++;
                }
                double D = boxD(bnd);
                p243(land);
                n1s[k] = n1; n2s[k] = n2; n6s[k] = nbig;
                S(String.format(LF, "%-11d %9.4f %9.2f%% %7d %7d %7d %9d %9.3f %9s",
                    SEED, RBASE_FRAC, 100.0 * nl / (W * W), n1, n2, nbig, nbp, D, G1B));
            }
            S(String.format(LF, "  极差：N-1 %.0f   N-2 %.0f   N-6 %.0f", rng(n1s), rng(n2s), rng(n6s)));
            S("");
        }
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
    static String G1B = "-";
    static double[] toD(byte[] b) { double[] v = new double[b.length]; for (int i = 0; i < b.length; i++) v[i] = b[i]; return v; }
    static double rng(double[] a) { double mn = 1e9, mx = -1e9; for (double x : a) { if (x < mn) mn = x; if (x > mx) mx = x; } return mx - mn; }

    /** 二分 RBASE_FRAC 命中目标陆地占比（200x200 粗网格）。 */
    static double tune(double target) {
        double lo = 0.0, hi = 0.45;
        for (int it = 0; it < 20; it++) {
            double mid = 0.5 * (lo + hi);
            double f = coarse(mid);
            if (f < target) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }
    static double coarse(double frac) {
        int G = 200; long n = 0; double saved = RBASE_FRAC; RBASE_FRAC = frac;
        for (int r = 0; r < G; r++) for (int c = 0; c < G; c++) {
            int x = (int) Math.round(X0 + (c + 0.5) * SPAN / G), z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (isLandR(x, z)) n++;
        }
        RBASE_FRAC = saved;
        return n / (double) (G * G);
    }

    static double boxD(byte[] bnd) {
        int[] boxes = { 5, 10, 20, 40, 80, 160 };
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int bi = 0; bi < boxes.length; bi++) {
            int s = boxes[bi]; long cnt = 0; int g = W / s;
            for (int br = 0; br < g; br++) for (int bc = 0; bc < g; bc++) {
                boolean hit = false;
                for (int rr = br * s; rr < (br + 1) * s && !hit; rr++) for (int cc = bc * s; cc < (bc + 1) * s; cc++) if (bnd[rr * W + cc] == 1) { hit = true; break; }
                if (hit) cnt++; }
            double X = Math.log(1.0 / s), Y = Math.log(Math.max(1, cnt));
            sx += X; sy += Y; sxx += X * X; sxy += X * Y;
        }
        int n = boxes.length;
        return (n * sxy - sx * sy) / (n * sxx - sx * sx);
    }

    /** P243 口径的 G1b（经向海岸连贯度，面积加权）。 */
    static void p243(byte[] a) {
        final int MAXI = 512;
        int[] pxw = new int[MAXI], pxe = new int[MAXI], pCoast = new int[MAXI];
        long[] pArea = new long[MAXI];
        long wTot = 0, wLong = 0;
        int pm = 0;
        double MW = 300_000.0, G1 = 400_000.0, TOL = 60_000.0;
        for (int iz = 0; iz < W; iz++) {
            int[] xw = new int[MAXI], xe = new int[MAXI], wt = new int[MAXI];
            int m = 0, ix = 0;
            while (ix < W) {
                if (a[iz * W + ix] == 1) { ix++; continue; }
                int j = ix; while (j + 1 < W && a[iz * W + j + 1] == 0) j++;
                if ((j - ix + 1) * step >= MW && m < MAXI) { xw[m] = ix; xe[m] = j; wt[m] = j - ix + 1; m++; }
                ix = j + 1;
            }
            int[] coast = new int[m]; long[] area = new long[m];
            boolean[] matched = new boolean[pm];
            for (int c2 = 0; c2 < m; c2++) {
                int best = -1, bestOv = 0;
                for (int p = 0; p < pm; p++) { int lo = Math.max(xw[c2], pxw[p]), hi = Math.min(xe[c2], pxe[p]); if (hi - lo > bestOv) { bestOv = hi - lo; best = p; } }
                if (best >= 0 && bestOv > 0) {
                    double dd = Math.abs(xw[c2] - pxw[best]) * step;
                    coast[c2] = dd <= TOL ? pCoast[best] + (int) step : (int) step;
                    area[c2] = pArea[best] + (long) wt[c2] * (long) step; matched[best] = true;
                } else { coast[c2] = (int) step; area[c2] = (long) wt[c2] * (long) step; }
            }
            if (pm > 0) for (int p = 0; p < pm; p++) if (!matched[p]) { wTot += pArea[p]; if (pCoast[p] >= G1) wLong += pArea[p]; }
            System.arraycopy(xw, 0, pxw, 0, m); System.arraycopy(xe, 0, pxe, 0, m);
            System.arraycopy(coast, 0, pCoast, 0, m); System.arraycopy(area, 0, pArea, 0, m); pm = m;
        }
        for (int p = 0; p < pm; p++) { wTot += pArea[p]; if (pCoast[p] >= G1) wLong += pArea[p]; }
        G1B = String.format(LF, "%.4f", wTot > 0 ? wLong / (double) wTot : 0);
    }
}
