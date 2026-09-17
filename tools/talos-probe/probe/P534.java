package probe;

import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P534：**势场森林 + 星形域（Nerve 定理）** —— 设计冻结 §294。
 *
 * 每个格：w = 低频势；parent = 8 邻居里 w 最高的那个（⇒ 森林，无环）。
 * R_i(theta) = min( R0(i) * (1 + GAIN*lobe(theta, theta_parent)) * shape(theta), rcapDir(theta), CAP_ABS )
 *   lobe(t)   = max(0, cos(t - t_p))^3               // 朝 parent 的叶瓣 ⇒ 合并成枝状
 *   rcapDir   = 沿 theta 方向到最近【非家族】格的射线距离（只允许家族之间相交）
 * **⇒ 相交图 ⊆ 森林 ⇒ 无环 ⇒ 无洞（Nerve）⇒ 零内海；有限有界团块 ⇒ 补集连通 ⇒ 零岛屿。**
 */
public class P534 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P534] " + s); rep.flush(); System.out.println("[P534] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static int SEED = 1022228679;

    static int DCELL = 3_000_000;        // 3000 km —— 网格密 ⇒ 树大、分叉多
    static double JIT = 0.12;
    static double RBASE_FRAC = 0.42;     // R0 = RBASE_FRAC * DCELL * (1 + 0.5*w)
    static double GAIN = 1.30;           // 朝 parent 的叶瓣强度
    static double CAP_ABS = 0.40;        // 绝对上限（单位 DCELL）—— 必须 <= 0.45 才保证非家族不相交
    static final double WSC = 2.2;       // 势场尺度（单位 DCELL）
    static final int OCTW = 3;
    static final double RHO = 12.0;      // shape(theta) 的噪声圆半径
    static final int OCTS = 3;           // shape 的八度数（最细角波长须远大于采样）
    static final long SITE_SALT = 0x5EED_0201L, CONT_SALT = 0x5EED_0202L, WSALT = 0x5EED_0203L;
    static final int MARGIN = 3;

    static int G0X, G0Z, GNX, GNZ;
    static double[] cxs, czs, ws, r0s, tps, rcaps;

    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    static double rnd01(long h) { long v = h; v ^= (v >>> 33); v *= 0xFF51AFD7ED558CCDL; v ^= (v >>> 33);
        v *= 0xC4CEB9FE1A85EC53L; v ^= (v >>> 33); return (v >>> 11) * 0x1.0p-53; }
    static long hash2(long s, int a, int b) { long h = s; h = h * 0x9E3779B97F4A7C15L + a; h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L; h = h * 0x9E3779B97F4A7C15L + b; h ^= (h >>> 32); h *= 0x94D049BB133111EBL; h ^= (h >>> 29); return h; }
    static double vnoise(double x, double z, long s) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double tx = x - xi, tz = z - zi;
        double u = tx * tx * (3 - 2 * tx), v = tz * tz * (3 - 2 * tz);
        double a = rnd01(hash2(s, xi, zi)) * 2 - 1, b = rnd01(hash2(s, xi + 1, zi)) * 2 - 1;
        double c = rnd01(hash2(s, xi, zi + 1)) * 2 - 1, d = rnd01(hash2(s, xi + 1, zi + 1)) * 2 - 1;
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v; }
    static double fbm(double x, double z, long s, int oct, double f0, double amp) {
        double r = 0, a = amp, f = f0;
        for (int i = 0; i < oct; i++) { r += a * vnoise(x * f, z * f, s + i * 7919L); a *= 0.5; f *= 2.0; } return r; }
    static double clamp01(double t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    static int idOf(int gx, int gz) { return (gz - G0Z) * GNX + (gx - G0X); }
    static boolean in(int gx, int gz) { return gx >= G0X && gz >= G0Z && gx < G0X + GNX && gz < G0Z + GNZ; }
    static double cxOf(int gx, int gz) { return cxs[idOf(gx, gz)]; }
    static double czOf(int gx, int gz) { return czs[idOf(gx, gz)]; }
    static int parentOf(int gx, int gz) {
        int id = idOf(gx, gz); double best = ws[id]; int bp = -1;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            if (!in(gx + dx, gz + dz)) continue;
            int j = idOf(gx + dx, gz + dz);
            if (ws[j] > best + 1e-12) { best = ws[j]; bp = j; }
        }
        return bp;
    }

    static void buildTable() {
        int gx0 = (int) Math.floor((X0 - (long) (MARGIN + 2) * DCELL) / DCELL), gx1 = (int) Math.floor((X0 + SPAN + (long) (MARGIN + 2) * DCELL) / DCELL);
        int gz0 = (int) Math.floor((Z0 - (long) (MARGIN + 2) * DCELL) / DCELL), gz1 = (int) Math.floor((Z0 + SPAN + (long) (MARGIN + 2) * DCELL) / DCELL);
        G0X = gx0; G0Z = gz0; GNX = gx1 - gx0 + 1; GNZ = gz1 - gz0 + 1;
        int n = GNX * GNZ;
        cxs = new double[n]; czs = new double[n]; ws = new double[n]; r0s = new double[n]; tps = new double[n]; rcaps = new double[n];
        for (int j = 0; j < GNZ; j++) for (int i = 0; i < GNX; i++) {
            int gx = gx0 + i, gz = gz0 + j, id = j * GNX + i;
            long h = hash2(seed ^ SITE_SALT, gx, gz);
            cxs[id] = (gx + 0.5 + JIT * (rnd01(h) * 2 - 1)) * DCELL;
            czs[id] = (gz + 0.5 + JIT * (rnd01(hash2(h, 7, 3)) * 2 - 1)) * DCELL;
        }
        for (int j = 0; j < GNZ; j++) for (int i = 0; i < GNX; i++) {
            int id = j * GNX + i;
            ws[id] = fbm(cxs[id] / (WSC * DCELL), czs[id] / (WSC * DCELL), seed ^ WSALT, OCTW, 1.0, 1.0) / 1.75;
        }
        // 家族（parent / children）之外的方向性上限：投到 8 个方向桶
        for (int j = 0; j < GNZ; j++) for (int i = 0; i < GNX; i++) {
            int gx = gx0 + i, gz = gz0 + j, id = j * GNX + i;
            int par = parentOf(gx, gz);
            // ⚠ E111：R0 不在这里乘 RBASE_FRAC —— 它是被 tune() 扫的变量，烘焙进表会让二分扫一个常数函数。
            r0s[id] = DCELL * (1.0 + 0.5 * ws[id]);
            tps[id] = (par >= 0) ? Math.atan2(czs[par] - czs[id], cxs[par] - cxs[id]) : 0.0;
            rcaps[id] = CAP_ABS * DCELL;
            for (int dz = -MARGIN; dz <= MARGIN; dz++) for (int dx = -MARGIN; dx <= MARGIN; dx++) {
                if (dx == 0 && dz == 0) continue;
                if (!in(gx + dx, gz + dz)) continue;
                int j2 = idOf(gx + dx, gz + dz);
                boolean fam = (j2 == par) || (parentOf(gx + dx, gz + dz) == id);
                if (fam) continue;
                double d = Math.hypot(cxs[j2] - cxs[id], czs[j2] - czs[id]);
                double a = Math.atan2(czs[j2] - czs[id], cxs[j2] - cxs[id]);
                for (int k = 0; k < 16; k++) {                     // 16 个方向桶
                    double th = -Math.PI + 2 * Math.PI * k / 16.0;
                    double c = Math.cos(th - a);
                    if (c > 0.25) { double r = d / c; if (r < rcaps[id]) rcaps[id] = r; }
                }
            }
        }
    }

    static boolean isLandR(int x, int z) {
        int gx = Math.floorDiv(x, DCELL), gz = Math.floorDiv(z, DCELL);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int a = gx + dx, b = gz + dz;
            if (!in(a, b)) continue;
            int id = idOf(a, b);
            double ddx = x - cxs[id], ddz = z - czs[id];
            double r2 = ddx * ddx + ddz * ddz;
            double cap = rcaps[id];
            if (r2 >= cap * cap) continue;
            double th = Math.atan2(ddz, ddx);
            double lobe = Math.max(0.0, Math.cos(th - tps[id])); lobe = lobe * lobe * lobe;
            double shape = 1.0 + 0.30 * fbm(Math.cos(th) * RHO, Math.sin(th) * RHO, seed ^ CONT_SALT, OCTS, 1.0, 1.0) / 1.75;
            double R = RBASE_FRAC * r0s[id] * (1.0 + GAIN * lobe) * shape;
            if (R > cap) R = cap;
            if (r2 < R * R) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P534_forest.txt"), "UTF-8");
        S("=== P534 势场森林 + 星形域（Nerve）===");
        S(String.format(LF, "D_CELL=%.0f km JIT=%.2f GAIN=%.2f R0=%.2f*DCELL*(1+0.5w) CAP=%.2f*DCELL WSC=%.1f RHO=%.0f OCTS=%d",
            DCELL / 1000.0, JIT, GAIN, RBASE_FRAC, CAP_ABS, WSC, RHO, OCTS));
        S("");
        int[] seeds = { 1022228679, 1, 42, 987654321 };
        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        S(String.format(LF, "%-11s %9s %9s %7s %7s %7s %9s %8s", "seed", "R0frac", "陆地%", "N-1", "N-2", "N-6", "海岸线px", "D"));
        for (int k = 0; k < seeds.length; k++) {
            SEED = seeds[k]; seed = SimTerrain.seedOf(SEED);
            buildTable();
            RBASE_FRAC = tune(0.30);
            byte[] land = new byte[W * W]; long nl = 0;
            for (int r = 0; r < W; r++) { int z = zOf(r);
                for (int c = 0; c < W; c++) { boolean l = isLandR(xOf(c), z); land[r * W + c] = (byte) (l ? 1 : 0); if (l) nl++; } }
            int n1 = 0, n2 = 0, nb = 0;
            for (int i = 0; i < W * W; i++) {
                if (land[i] == 0 || lab[i] != 0) continue;
                int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
                while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; a++;
                    int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                    for (int x : nn) if (land[x] == 1 && lab[x] == 0) { lab[x] = 1; stk[sp++] = x; } }
                n2++; if (a > 0.02 * W * W) nb++; }
            for (int i = 0; i < W * W; i++) {
                if (land[i] == 1 || lab[i] != 0) continue;
                int sp = 0; stk[sp++] = i; lab[i] = 2; boolean lr = false;
                while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; if (c2 == 0 || c2 == W - 1) lr = true;
                    int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                    for (int x : nn) if (land[x] == 0 && lab[x] == 0) { lab[x] = 2; stk[sp++] = x; } }
                if (!lr) n1++; }
            java.util.Arrays.fill(lab, 0);
            byte[] bd = new byte[W * W]; long nbp = 0;
            for (int r = 0; r < W; r++) for (int c = 0; c < W; c++) {
                if (land[r * W + c] == 0) continue;
                if (land[r * W + wr(c + 1)] == 1 && land[r * W + wr(c - 1)] == 1 && land[wr(r + 1) * W + c] == 1 && land[wr(r - 1) * W + c] == 1) continue;
                bd[r * W + c] = 1; nbp++; }
            double[] v = new double[W * W];
            for (int i = 0; i < W * W; i++) v[i] = land[i];
            MapWriter.writePng(new File(MAPDIR, "sealandF_s" + SEED + ".png"), W, W, v, 0.0, 1.0,
                new MapWriter.ColorMap() { @Override public int rgb(double x) { return x < 0.5 ? 0x1B3B6F : 0xE0D5B0; } });
            S(String.format(LF, "%-11d %9.4f %8.2f%% %7d %7d %7d %9d %8.3f", SEED, RBASE_FRAC, 100.0 * nl / (W * W), n1, n2, nb, nbp, boxD(bd)));
        }
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static double tune(double target) {
        double lo = 0.0, hi = 0.75;
        for (int it = 0; it < 18; it++) { double mid = 0.5 * (lo + hi); if (coarse(mid) < target) lo = mid; else hi = mid; }
        return 0.5 * (lo + hi);
    }
    static double coarse(double frac) {
        int G = 200; long n = 0; double saved = RBASE_FRAC; RBASE_FRAC = frac;
        for (int r = 0; r < G; r++) for (int c = 0; c < G; c++) {
            int x = (int) Math.round(X0 + (c + 0.5) * SPAN / G), z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (isLandR(x, z)) n++; }
        RBASE_FRAC = saved; return n / (double) (G * G);
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
            sx += X; sy += Y; sxx += X * X; sxy += X * Y; }
        int n = boxes.length;
        return (n * sxy - sx * sy) / (n * sxx - sx * sx);
    }
}
