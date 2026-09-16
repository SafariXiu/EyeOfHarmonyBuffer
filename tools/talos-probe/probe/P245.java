package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P245（M1 收尾）：**先把成本模型算清楚，再决定跑什么**。
 *
 * <p>P244 的盆宽扫描失败在 Rossby CFL 上（§11）：域越大 DT 上限越小。
 * 本轮改成**在固定采样框（2400 km）内把盆变宽** —— 唯一的旋钮是陆壳阈值
 * {@code contThreshold}（越低 ⇒ 陆地越少 ⇒ 海盆越宽），板块格固定 600 km。
 *
 * <h3>成本模型（先算，后跑）</h3>
 * <pre>
 *   1. 洪泛填出最大的连通海区 -> 包围盒 -> L_max
 *   2. DT_cfl = Δx·π²/(10·β·L_max²)          （P244 实测：CFL 余量 ~3 必发散）
 *   3. DT = min(35, DT_cfl)，steps = PSEUDO_T/DT
 *   4. cost ≈ N² · steps · SEC_PER_CELLSTEP  （由 P244 基线反标定）
 *   5. cost > BUDGET 则**跳过**，只打印估算
 * </pre>
 *
 * 度量与 P244 v2 相同：G3-聚合（面积加权的西输运/东输运）、
 * G3-中位（面积加权）、G4（宽盆西带均速、面积加权中位；参照 P242 理想盆 0.0663）。
 */
public class P245 {

    static final int SEED = 1022228679;
    static final int N = 120;
    static final double DX = 20_000.0;
    static final double LY = (N - 2) * DX;
    static double TAU0 = 0.1;
    static final double BETA = 3.24e-10;
    static final double AH = 1.9e4, HMIX = 100.0;
    static final double MW = 122_000.0;
    static final double CFL_SAFETY = 10.0;
    static final double PSEUDO_T = 2.8e6;
    static final double SEC_PER_CELLSTEP = 7.0e-8;
    static final double BUDGET_S = 300.0;
    static final int CELL = 600_000;
    static final double G3_TARGET = 4.0, G4_TARGET = 0.05;

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p245b_report.txt"), "UTF-8");
        say("M1 收尾：固定采样框内扫陆壳阈值（= 盆宽），DT 按 Rossby CFL 自适应");
        say(String.format(L, "框 %dx%d @%.0f km = %.0f km；板块格 %d km；DT 上限规则 = Δx·π²/(%.0f·β·L²)；成本模型 %.1e s/(格·步)",
            N, N, DX / 1000, N * DX / 1000, CELL / 1000, CFL_SAFETY, SEC_PER_CELLSTEP));
        say("（陆壳阈值扫描已在 p245_report.txt 记录，本轮跳过）");
        say("");
        say("########## 风场强度对照：P246 实测中纬【连贯 curl】是 MIT 参照的 3.6~4.9 倍 ##########");
        say("上一轮 contTh=0.10（陆地 0.208）用 MIT 强度的风得到 G4=0.0422；若风强 3.6 倍，线性预期 0.152");
        TAU0 = 0.36;   // 生产风场的连贯 curl 是中纬 MIT 参照的 3.6~4.9 倍，取保守的 3.6
        for (double th : new double[]{0.10, -0.05}) {
            say("");
            costAndMaybeRun(th);
        }
        rep.close();
    }

    static void say(String s) { System.out.println("[P245] " + s); rep.println("[P245] " + s); }

    static boolean[] buildLand(double th) {
        boolean[] land = new boolean[N * N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                boolean edge = (x == 0 || x == N - 1 || y == 0 || y == N - 1);
                land[y * N + x] = edge || PlateField.isLandFull(x * (int) DX, y * (int) DX, SEED, CELL, th);
            }
        }
        return land;
    }

    /** 洪泛最大连通海区 -> {面积, 包围盒宽, 包围盒高}。 */
    static int[] largestBasin(boolean[] land) {
        boolean[] seen = new boolean[N * N];
        int[] stack = new int[N * N];
        int bestArea = 0, bw = 0, bh = 0;
        for (int s = 0; s < N * N; s++) {
            if (land[s] || seen[s]) continue;
            int sp = 0; stack[sp++] = s; seen[s] = true;
            int area = 0, x0 = N, x1 = -1, y0 = N, y1 = -1;
            while (sp > 0) {
                int i = stack[--sp];
                int x = i % N, y = i / N;
                area++;
                if (x < x0) x0 = x; if (x > x1) x1 = x;
                if (y < y0) y0 = y; if (y > y1) y1 = y;
                if (x > 0) { int j = i - 1; if (!land[j] && !seen[j]) { seen[j] = true; stack[sp++] = j; } }
                if (x < N - 1) { int j = i + 1; if (!land[j] && !seen[j]) { seen[j] = true; stack[sp++] = j; } }
                if (y > 0) { int j = i - N; if (!land[j] && !seen[j]) { seen[j] = true; stack[sp++] = j; } }
                if (y < N - 1) { int j = i + N; if (!land[j] && !seen[j]) { seen[j] = true; stack[sp++] = j; } }
            }
            if (area > bestArea) { bestArea = area; bw = x1 - x0 + 1; bh = y1 - y0 + 1; }
        }
        return new int[]{bestArea, bw, bh};
    }

    static void costAndMaybeRun(double th) {
        say("");
        say("===== contThreshold = " + th + "   τ0 = " + TAU0 + " N/m² =====");
        boolean[] land = buildLand(th);
        int landIn = 0;
        for (int y = 1; y < N - 1; y++) for (int x = 1; x < N - 1; x++) if (land[y * N + x]) landIn++;
        int[] lb = largestBasin(land);
        double lmax = Math.max(lb[1], lb[2]) * DX;
        double dtCfl = DX * Math.PI * Math.PI / (CFL_SAFETY * BETA * lmax * lmax);
        double dt = Math.min(35.0, dtCfl);
        int steps = (int) Math.ceil(PSEUDO_T / dt);
        double cost = (double) N * N * steps * SEC_PER_CELLSTEP;
        say(String.format(L, "  内部陆地占比 %.3f   最大连通海区: 面积 %d 格  包围盒 %d x %d 格 ⇒ L_max = %.0f km",
            landIn / (double) ((N - 2) * (N - 2)), lb[0], lb[1], lb[2], lmax / 1000));
        say(String.format(L, "  DT_cfl=%.1f s ⇒ DT=%.1f s  steps=%d  估算成本 %.0f s  %s",
            dtCfl, dt, steps, cost, cost <= BUDGET_S ? "⇒ 跑" : "⇒ 超预算，跳过"));
        if (cost > BUDGET_S) return;
        solveAndMeasure(land, dt, steps, th);
    }

    static void solveAndMeasure(boolean[] land, double dt, int steps, double th) {
        BarotropicGyre.NCX = N; BarotropicGyre.NCZ = N;
        BarotropicGyre.A_H = AH; BarotropicGyre.H_MIXED = HMIX;
        BarotropicGyre.BETA_SCALE = 1.0;
        BarotropicGyre.V_CYCLES = 1; BarotropicGyre.FINAL_CYCLES = 300;
        BarotropicGyre.DT = dt; BarotropicGyre.MACRO = steps;
        BarotropicGyre.CONV_TOL = 1e-3; BarotropicGyre.MIN_STEPS = 150;
        double[] fRow = new double[N], betaRow = new double[N];
        for (int y = 0; y < N; y++) { fRow[y] = 0.0; betaRow[y] = BETA; }
        double den = BarotropicGyre.RHO_AIR * BarotropicGyre.C_D * BarotropicGyre.WIND_MS * BarotropicGyre.WIND_MS;
        double[] uW = new double[N * N], vW = new double[N * N];
        for (int y = 0; y < N; y++) {
            double yp = (y - 1) * DX;
            double s = Math.sin(Math.PI * yp / LY);
            if (s < 0) s = 0;
            double uu = Math.sqrt(s * TAU0 / den);
            for (int x = 0; x < N; x++) uW[y * N + x] = uu;
        }
        double[] uo = new double[N * N], vo = new double[N * N];
        long t0 = System.nanoTime();
        BarotropicGyre.solve(N, N, DX, DX, land, null, uW, vW, fRow, betaRow, uo, vo, 0);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        boolean ok = BarotropicGyre.lastPoisRes <= 1e-4 && BarotropicGyre.lastResidual <= 1e-7;
        say(String.format(L, "  实测 %d ms  ζ残差=%.3e  泊松残差=%.3e  %s",
            ms, BarotropicGyre.lastResidual, BarotropicGyre.lastPoisRes, ok ? "收敛" : "⚠ 不可用（发散）"));
        if (!ok) return;
        measure(land, vo);
    }

    static void measure(boolean[] land, double[] vo) {
        int nMw = (int) (MW / DX), nWide = 4 * nMw;
        double[] rv = new double[40000], rw = new double[40000];
        double[] gv = new double[40000], gw = new double[40000];
        int nr = 0, ng = 0, quals = 0, wides = 0;
        double sumW = 0, sumE = 0, peak = 0, sSW = 0, sSE = 0, nSW = 0, nSE = 0; int sN = 0, nN = 0;
        for (int y = 1; y < N - 1; y++) {
            int x = 1;
            while (x < N - 1) {
                if (land[y * N + x]) { x++; continue; }
                int e = x;
                while (e + 1 < N - 1 && !land[y * N + e + 1]) e++;
                int w = e - x + 1;
                if (w >= 2 * nMw) {
                    quals++;
                    double bw = 0, be = 0, sw = 0, se = 0;
                    for (int k = x; k < x + nMw; k++) { bw += Math.abs(vo[y * N + k]); sw += vo[y * N + k]; }
                    for (int k = e - nMw + 1; k <= e; k++) { be += Math.abs(vo[y * N + k]); se += vo[y * N + k]; }
                    bw /= nMw; be /= nMw; sw /= nMw; se /= nMw;
                    sumW += bw * w; sumE += be * w;
                    if (bw > peak) peak = bw;
                    if (be > 1e-7 && nr < rv.length) { rv[nr] = bw / be; rw[nr] = w; nr++; }
                    if (w >= nWide) { wides++; if (ng < gv.length) { gv[ng] = bw; gw[ng] = w; ng++; } }
                    if (y < N / 2) { sSW += sw; sSE += se; sN++; } else { nSW += sw; nSE += se; nN++; }
                }
                x = e + 1;
            }
        }
        if (quals == 0) { say("  【无合格盆】"); return; }
        double g3agg = sumE > 0 ? sumW / sumE : Double.NaN;
        say(String.format(L, "  合格海区间 %d（真盆 %d）", quals, wides));
        say(String.format(L, "  【G3-聚合】%.2f  %s", g3agg, g3agg >= G3_TARGET ? "PASS" : "FAIL"));
        say(String.format(L, "  【G3-中位】面积加权 %.2f  %s", wMedian(rv, rw, nr), wMedian(rv, rw, nr) >= G3_TARGET ? "PASS" : "FAIL"));
        say(String.format(L, "  【G4】宽盆西带均速 面积加权中位 %.4f m/s  %s   （参照 P242 理想盆 0.0663）",
            wMedian(gv, gw, ng), wMedian(gv, gw, ng) >= G4_TARGET ? "PASS" : "FAIL"));
        say(String.format(L, "  补充：西带峰值 %.4f m/s（会被海峡污染）", peak));
        double mSW = sN > 0 ? sSW / sN * 1000 : Double.NaN;
        double mSE = sN > 0 ? sSE / sN * 1000 : Double.NaN;
        double mNW = nN > 0 ? nSW / nN * 1000 : Double.NaN;
        double mNE = nN > 0 ? nSE / nN * 1000 : Double.NaN;
        int ok = 0;
        if (mSW > 0) ok++;
        if (mSE < 0) ok++;
        if (mNW < 0) ok++;
        if (mNE > 0) ok++;
        say("  【四条流符号矩阵】（+ 为向极；期望 = 南半 西+/东-，北半 西-/东+）");
        say(String.format(L, "    南半(副热带)  西边界 %+10.4f mm/s（暖流·应+）   东边界 %+10.4f mm/s（寒流·应-）", mSW, mSE));
        say(String.format(L, "    北半(副极地)  西边界 %+10.4f mm/s（寒流·应-）   东边界 %+10.4f mm/s（暖流·应+）", mNW, mNE));
        say(String.format(L, "    符号正确 %d / 4   %s", ok, ok == 4 ? "PASS" : "FAIL"));
    }

    static double wMedian(double[] v, double[] w, int n) {
        int tot = 0;
        for (int i = 0; i < n; i++) tot += (int) w[i];
        if (tot == 0) return Double.NaN;
        double[] a = new double[tot];
        int k = 0;
        for (int i = 0; i < n; i++) for (int q = 0; q < (int) w[i]; q++) a[k++] = v[i];
        Arrays.sort(a);
        return a[tot / 2];
    }
}
