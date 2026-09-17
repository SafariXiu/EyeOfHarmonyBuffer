package probe;

import com.EyeOfHarmonyBuffer.sim.litho.TalosField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P537：**TalosField 的形态与物理验证**（设计冻结 §305~§311，目标 ④）。
 *
 * 为什么必须在 Java 里重做一遍：Python 原型的 vnoise 用 float32 插值权重、Java 用 double，
 * 且两者的随机数发生器不同 ⇒ **不能假设形态一致，必须实测**。
 *
 * 口径与 V8（§305 表）逐项对齐：窗口 20,000 km、1000x1000、20 km/px、盒 1..64。
 */
public class P537 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void S(String s) { rep.println("[P537] " + s); rep.flush(); System.out.println("[P537] " + s); System.out.flush(); }

    static final int N = 1000;
    static final double PXKM = 20.0;                 // km per pixel（= 20,000 km / 1000）
    static final double PXBLK = 20_000.0;            // block per pixel
    static final double CELLKM2 = PXKM * PXKM;       // 400 km2
    static final long SPANBLK = 20_000_000L;         // 窗口跨度 = 20,000 km

    // ---------- 并查集连通分量 ----------
    static int[] lab; static int[] par;
    static int find(int a) { while (par[a] != a) { par[a] = par[par[a]]; a = par[a]; } return a; }
    static void uni(int a, int b) { int ra = find(a), rb = find(b); if (ra != rb) par[rb] = ra; }

    static int label(boolean[] m, boolean diag) {
        par = new int[N * N + 1]; for (int i = 0; i < par.length; i++) par[i] = i;
        lab = new int[N * N];
        int next = 1;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int i = y * N + x; if (!m[i]) continue;
            int best = 0;
            if (x > 0 && m[i - 1]) best = lab[i - 1];
            if (y > 0 && m[i - N]) { int v = lab[i - N]; if (v != 0) { if (best == 0) best = v; else uni(best, v); } }
            if (diag) {
                if (y > 0 && x > 0 && m[i - N - 1]) { int v = lab[i - N - 1]; if (v != 0) { if (best == 0) best = v; else uni(best, v); } }
                if (y > 0 && x < N - 1 && m[i - N + 1]) { int v = lab[i - N + 1]; if (v != 0) { if (best == 0) best = v; else uni(best, v); } }
            }
            if (best == 0) best = next++;
            lab[i] = best;
        }
        for (int i = 0; i < N * N; i++) if (lab[i] != 0) lab[i] = find(lab[i]);
        return next;
    }

    /** 某域直方图里「高度 >= frac * 最大值」的局部极大个数（单峰时应为 1）。 */
    static int peakCount(int[] b, double frac) {
        int mx = 0; for (int v : b) if (v > mx) mx = v;
        int c = 0;
        for (int i = 0; i < b.length; i++) {
            boolean left = (i == 0) || b[i] >= b[i - 1];
            boolean right = (i == b.length - 1) || b[i] > b[i + 1];
            if (left && right && b[i] > frac * mx) c++;
        }
        return c;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) TalosField.AN = Double.parseDouble(args[0]);
        if (args.length > 1) TalosField.setHurst(Double.parseDouble(args[1]));
        boolean quiet = args.length > 2;
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P537_morph.txt"), "UTF-8");
        long[] seeds = {1022228679L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 42L, 12345L, 777L, 2024L, 31337L, 999983L};
        double[] dvals = new double[seeds.length]; double[] lvals = new double[seeds.length]; double[] densv = new double[seeds.length];
        boolean[] biPass = new boolean[seeds.length]; double[] separ = new double[seeds.length];
        int[] mopo = new int[seeds.length], mlan = new int[seeds.length];
        S("=== P537 TalosField 形态与物理验证（目标 4 / 方案 A+B）===");
        S(String.format(LF, "窗口 %d x %d px, %.0f km/px, 盒 1..64  (= V8 §305 的同口径)", N, N, PXKM));
        S("");
        S("seed        陆地%%   陆块  >=1Mkm2 >=1e5km2 top1%%  top3%%  岸/陆%%   D      大内海 密度/50Mkm2  大域P(F>L)");
        double[] margins = new double[seeds.length];
        for (int si = 0; si < seeds.length; si++) {
            long seed = seeds[si];
            double lvl = TalosField.level(seed);
            boolean[] land = new boolean[N * N];
            double[] H = new double[N * N];
            long t0 = System.nanoTime();
            for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
                double bx = (x + 0.5) * PXBLK, bz = (y + 0.5) * PXBLK;
                H[y * N + x] = TalosField.elevation(bx, bz, seed);
                land[y * N + x] = H[y * N + x] >= 0.0;
            }
            double genMs = (System.nanoTime() - t0) / 1e6;
            int nland = 0; for (boolean b : land) if (b) nland++;
            double landPct = 100.0 * nland / (N * N);
            int nl = label(land, false);
            int[] sz = new int[nl + 1];
            for (int i = 0; i < N * N; i++) if (lab[i] != 0) sz[lab[i]]++;
            java.util.Arrays.sort(sz);
            long tot = 0; for (int v : sz) tot += v;
            int big1 = 0, big2 = 0;
            for (int v : sz) { if (v * CELLKM2 >= 1e6) big1++; if (v * CELLKM2 >= 1e5) big2++; }
            double top1 = tot > 0 ? 100.0 * sz[nl] / tot : 0, top3 = tot > 0 ? 100.0 * (sz[nl] + sz[nl-1] + sz[nl-2]) / tot : 0;
            int comps = 0; for (int v : sz) if (v > 0) comps++;
            // 岸/陆 + 盒计数
            boolean[] bd = new boolean[N * N]; int nbd = 0;
            for (int y = 1; y < N - 1; y++) for (int x = 1; x < N - 1; x++) { int i = y * N + x;
                if (land[i] && (!land[i-1] || !land[i+1] || !land[i-N] || !land[i+N])) { bd[i] = true; nbd++; } }
            double coastLand = nland > 0 ? 100.0 * nbd / nland : 0;
            double[] ks = new double[7], ns = new double[7];
            for (int k = 0; k < 7; k++) { int b = 1 << k; java.util.HashSet<Long> hs = new java.util.HashSet<>();
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (bd[y * N + x]) hs.add((long) (y / b) * 100000L + (x / b));
                ks[k] = Math.log(1.0 / b); ns[k] = Math.log(hs.size()); }
            double mx = 0, my = 0; for (int k = 0; k < 7; k++) { mx += ks[k]; my += ns[k]; } mx /= 7; my /= 7;
            double num = 0, den = 0; for (int k = 0; k < 7; k++) { num += (ks[k] - mx) * (ns[k] - my); den += (ks[k] - mx) * (ks[k] - mx); }
            double D = num / den; dvals[si] = D; lvals[si] = landPct;
            // 内海（水 8 连通、不触边 = 封闭）
            boolean[] water = new boolean[N * N]; for (int i = 0; i < N * N; i++) water[i] = !land[i];
            int nw = label(water, true);
            int[] wsz = new int[nw + 1]; boolean[] touch = new boolean[nw + 1];
            for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { int i = y * N + x; if (!water[i]) continue;
                wsz[lab[i]]++; if (x == 0 || y == 0 || x == N - 1 || y == N - 1) touch[lab[i]] = true; }
            int enc = 0; for (int v = 1; v <= nw; v++) if (v < wsz.length && wsz[v] > 0 && !touch[v] && wsz[v] * CELLKM2 >= 1e5) enc++;
            double landMkm2 = nland * CELLKM2 / 1e6;
            double dens = landMkm2 > 0 ? enc / (landMkm2 / 50.0) : 0; densv[si] = dens;
            // 大域边缘分布（独立于窗口）
            double[] f = new double[1024]; java.util.Random r = new java.util.Random(seed * 7919L + 31L);
            for (int i = 0; i < 1024; i++) f[i] = TalosField.fieldValue((r.nextDouble()*2-1)*1.0e8, (r.nextDouble()*2-1)*1.0e8, seed);
            int above = 0; for (double v : f) if (v > lvl) above++;
            margins[si] = 100.0 * above / 1024;
            if (!quiet) S(String.format(LF, "%-11d %5.2f  %4d  %6d  %7d  %5.1f  %5.1f  %5.2f  %.4f  %5d  %8.2f     %5.2f",
                    seed, landPct, comps, big1, big2, top1, top3, coastLand, D, enc, dens, margins[si]));
            if (!quiet) S(String.format(LF, "             (生成耗时 %.0f ms = %.1f ns/列)", genMs, genMs * 1e6 / (N * N)));
            int[] bins = new int[23];
            for (double v : H) { int bi = (int) Math.floor((v + 6000) / 500); if (bi >= 0 && bi < 23) bins[bi]++; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 23; i++) { double pct = 100.0 * bins[i] / (N * N); if (pct > 3.0) sb.append(String.format(LF, "%d..%d:%.1f%%  ", -6000 + i * 500, -6000 + (i + 1) * 500, pct)); }
            // ---- 双峰：按域各自归一化（判据与陆地占比无关）----
            int[] ob = new int[23], lb = new int[23]; int no = 0, nlz = 0;
            for (double v : H) { int bi = (int) Math.floor((v + 6000) / 500); if (bi < 0 || bi >= 23) continue;
                if (v < 0) { ob[bi]++; no++; } else { lb[bi]++; nlz++; } }
            int po = 0, pl = 0; for (int i = 1; i < 23; i++) { if (ob[i] > ob[po]) po = i; if (lb[i] > lb[pl]) pl = i; }
            double peakO = -6000 + (po + 0.5) * 500, peakL = -6000 + (pl + 0.5) * 500;
            int mo = peakCount(ob, 0.30), ml = peakCount(lb, 0.30);
            boolean biOk = (mo == 1 && ml == 1 && (peakL - peakO) > 3000.0);
            biPass[si] = biOk; separ[si] = peakL - peakO; mopo[si] = mo; mlan[si] = ml;
            if (!quiet) S(String.format(LF, "             按域归一化双峰: 洋峰(海域内)%.0f m 单峰数%d | 陆峰(陆域内)%.0f m 单峰数%d | 峰间隔 %.0f m  => %s",
                    peakO, mo, peakL, ml, peakL - peakO, biOk ? "PASS" : "FAIL"));
            if (!quiet) S("             双峰直方图(>3%): " + sb);
        }
        double mn = margins[0], mx2 = margins[0]; for (double v : margins) { if (v < mn) mn = v; if (v > mx2) mx2 = v; }
        S("");
        S(String.format(LF, "=== 判据 ===", 0));
        S(String.format(LF, "大域边缘分布 跨度 = %.2f pt   （判据: <= 10 pt）", mx2 - mn));
        S(String.format(LF, "大域边缘分布 均值 = %.2f%%   （判据: 30%% +- 2.75 pt(1 sigma)）", (mn + mx2) / 2));
        S(String.format(LF, "盒计数 D 范围 = 见上表  （判据: 1.15~1.19）"));
        S(String.format(LF, "大内海密度   （判据: <= 4 每 50 Mkm2 陆地；地球 = 1）"));
        // ---- D 的分布统计（16 种子）----
        double[] ds = dvals.clone(); java.util.Arrays.sort(ds);
        int inBand = 0; for (double v : dvals) if (v >= 1.15 && v <= 1.19) inBand++;
        double medD = ds.length % 2 == 1 ? ds[ds.length / 2] : 0.5 * (ds[ds.length / 2 - 1] + ds[ds.length / 2]);
        double sumD = 0; for (double v : dvals) sumD += v;
        S(String.format(LF, "", 0));
        S(String.format(LF, "=== D 分布（%d 种子）  AN=%.3f HH=%.2f ===", dvals.length, TalosField.AN, TalosField.HH));
        S(String.format(LF, "min=%.4f  max=%.4f  中位=%.4f  均值=%.4f  极差=%.4f", ds[0], ds[ds.length - 1], medD, sumD / dvals.length, ds[ds.length - 1] - ds[0]));
        S(String.format(LF, "落在 1.15~1.19 的种子数 = %d / %d", inBand, dvals.length));
        double[] dns = densv.clone(); java.util.Arrays.sort(dns);
        int dnOk = 0; for (double v : densv) if (v <= 4.0) dnOk++;
        S(String.format(LF, "大内海密度: 中位=%.2f  最大=%.2f  <=4 的有 %d/%d   （地球=1）", dns[dns.length/2], dns[dns.length-1], dnOk, densv.length));
        // 与陆地占比的相关性（检验 D 是否被占比污染）
        double ml = 0; for (double v : lvals) ml += v; ml /= lvals.length;
        double cov = 0, vl = 0, vd = 0;
        for (int i = 0; i < dvals.length; i++) { cov += (lvals[i] - ml) * (dvals[i] - sumD / dvals.length); vl += (lvals[i] - ml) * (lvals[i] - ml); vd += (dvals[i] - sumD / dvals.length) * (dvals[i] - sumD / dvals.length); }
        S(String.format(LF, "corr(D, 陆地占比) = %+.3f", cov / Math.sqrt(vl * vd)));
        int bp = 0; for (boolean b : biPass) if (b) bp++;
        S(String.format(LF, "按域归一化双峰: 通过 %d/%d  峰间隔 %.0f~%.0f m   单峰数(洋/陆) 最大 %d/%d",
                bp, biPass.length, java.util.Arrays.stream(separ).min().getAsDouble(), java.util.Arrays.stream(separ).max().getAsDouble(),
                java.util.Arrays.stream(mopo).max().getAsInt(), java.util.Arrays.stream(mlan).max().getAsInt()));
        rep.close();
    }
}