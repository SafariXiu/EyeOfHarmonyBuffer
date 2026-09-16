package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P244（M1 闸门后半段）：**把已标定的求解器放到新板块几何上，量 G3/G4**。
 *
 * <p>P243 量的是几何（G1b 0.589 vs 旧 0.000）。本探针量**合力**：
 * 同一把已标定的求解器（P242 复现 MITgcm 算例，吻合 5~10%），
 * 同一套生产参数（β=3.24e-10, A_H=1.9e4, H=100），同一个理想双涡旋风场，
 * **唯一变量就是海陆几何**。
 *
 * <pre>
 *   G3  西/东 强化比        >= 4
 *   G4  西边界流峰值        >= 0.05 m/s
 * </pre>
 *
 * <h3>域与口径</h3>
 * 细格 = 粗格 = 120x120 @20 km = 2400x2400 km（1:1，粗化采样无偏差）。
 * 外圈一圈强制成陆 ⇒ **X 环绕被真正封死**（这正是旧架构做不到的那一步，见设计冻结 §3.1）。
 * 风：τx(y)=τ0·sin(πy/Ly)，τ0=0.1 ⇒ curl 南半负、北半正 ⇒ 应出现暖/冷两条西边界流。
 * DT=35（P242 实测：DT=140 在 2400 km 量级的盆里会慢增长发散）。
 */
public class P244 {

    static final int SEED = 1022228679;
    static final int N = 120;
    static final double DX = 20_000.0;
    static final double LY = (N - 2) * DX;
    static final double TAU0 = 0.1;
    static final double BETA = 3.24e-10;
    static final double AH = 1.9e4, HMIX = 100.0;
    static final double DT = 35.0;
    static final int MACRO = 80_000;
    static final double MW = 122_000.0;      // Munk 宽 pi*(A_H/beta)^(1/3)，副热带
    static final double G3_TARGET = 4.0, G4_TARGET = 0.05;

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p244_report.txt"), "UTF-8");
        say("M1 闸门后半段：已标定求解器 x 两种大陆几何（唯一变量 = 海陆）");
        say(String.format(L, "域 %dx%d @%.0f km = %.0f x %.0f km；外圈一圈陆封死 X；DT=%.0f MACRO=%d",
            N, N, DX / 1000, N * DX / 1000, N * DX / 1000, DT, MACRO));
        say(String.format(L, "解析参照：Munk 宽 M_w=%.0f km；v_Sverdrup 峰=%.3f mm/s；强化比预估 L/M_w-1=%.1f",
            MW / 1000, (TAU0 * Math.PI / LY) / (1025 * HMIX * BETA) * 1000, (N - 2) * DX / MW - 1));

        say("本轮唯一变量 = 板块格边长（即盆宽尺度）。上一轮 cell=600km 的基线：G3 7.40 / G4 0.0165");
        caseRun("P600  z0=2.0M（上轮基线）", true, 2_000_000, 80_000,   600_000);
        caseRun("P900  z0=0",              true, 0,         80_000,   900_000);
        caseRun("P900  z0=2.0M",           true, 2_000_000, 80_000,   900_000);
        caseRun("P1200 z0=0",              true, 0,         80_000, 1_200_000);
        caseRun("P1200 z0=2.0M",           true, 2_000_000, 80_000, 1_200_000);
        rep.close();
    }

    static void say(String s) { System.out.println("[P244] " + s); rep.println("[P244] " + s); }

    static void caseRun(String tag, boolean plate, int z0) { caseRun(tag, plate, z0, MACRO, PlateField.PLATE_CELL); }

    static void caseRun(String tag, boolean plate, int z0, int macro) { caseRun(tag, plate, z0, macro, PlateField.PLATE_CELL); }

    static void caseRun(String tag, boolean plate, int z0, int macro, int cell) {
        say("");
        say("===== " + tag + " =====");
        boolean[] land = new boolean[N * N];
        int landIn = 0;
        for (int y = 0; y < N; y++) {
            int z = z0 + y * (int) DX;
            for (int x = 0; x < N; x++) {
                boolean edge = (x == 0 || x == N - 1 || y == 0 || y == N - 1);
                boolean l = edge;
                if (!edge) {
                    int wx = x * (int) DX;
                    l = plate ? PlateField.isLandWithCell(wx, z, SEED, cell)
                              : (NoiseContinentGrid.landResidual(wx, z, SEED) >= 0.0);
                }
                land[y * N + x] = l;
                if (l && !edge) landIn++;
            }
        }
        say(String.format(L, "  内部陆地占比 %.3f（%d / %d）", landIn / (double) ((N - 2) * (N - 2)), landIn, (N - 2) * (N - 2)));

        BarotropicGyre.NCX = N; BarotropicGyre.NCZ = N;
        BarotropicGyre.A_H = AH; BarotropicGyre.H_MIXED = HMIX;
        BarotropicGyre.BETA_SCALE = 1.0;
        // A2/A 的对比暴露：120x120 上 FINAL_CYCLES=32 不够（A 的泊松残差 2.2e-3，好解是 1e-7 量级）
        // ⇒ 最终泊松的 V-cycle 数从 32 提到 300。这只影响「最后一次解 ψ」，不影响物理。
        BarotropicGyre.V_CYCLES = 1; BarotropicGyre.FINAL_CYCLES = 300;
        BarotropicGyre.DT = DT; BarotropicGyre.MACRO = macro;
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
        long st0 = BarotropicGyre.TOTAL_MACRO_STEPS.get();
        long t0 = System.nanoTime();
        BarotropicGyre.solve(N, N, DX, DX, land, null, uW, vW, fRow, betaRow, uo, vo, 0);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        long steps = BarotropicGyre.TOTAL_MACRO_STEPS.get() - st0;
        say(String.format(L, "  用时 %d ms  MACRO=%d  steps=%d  ζ残差=%.3e  泊松残差=%.3e  %s",
            ms, macro, steps, BarotropicGyre.lastResidual, BarotropicGyre.lastPoisRes,
            (BarotropicGyre.lastPoisRes > 1e-4 || BarotropicGyre.lastResidual > 1e-7)
                ? "⚠ 不可用（ψ 未解好 或 ζ 未收敛）" : "收敛"));
        measure(tag, land, vo);
    }

    /** 面积加权中位：权重是整数海格数，直接展开后排序（总数 <= 域内海格数）。 */
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

    static void measure(String tag, boolean[] land, double[] vo) {
        int nMw = (int) (MW / DX);
        int nWide = 4 * nMw;                       // 「真盆」门槛：>= 4*M_w
        double[] rv = new double[40000], rw = new double[40000];
        double[] gv = new double[40000], gw = new double[40000];
        int nr = 0, ng = 0, dropped = 0;
        double sumW = 0, sumE = 0, wTot = 0, peak = 0;
        double sSum = 0, nSum = 0; int sN = 0, nN = 0, quals = 0, wides = 0;
        for (int y = 1; y < N - 1; y++) {
            int x = 1;
            while (x < N - 1) {
                if (land[y * N + x]) { x++; continue; }
                int e = x;
                while (e + 1 < N - 1 && !land[y * N + e + 1]) e++;
                int w = e - x + 1;
                if (w >= 2 * nMw) {
                    quals++;
                    double bw = 0, be = 0, sw = 0;
                    for (int k = x; k < x + nMw; k++) { bw += Math.abs(vo[y * N + k]); sw += vo[y * N + k]; }
                    for (int k = e - nMw + 1; k <= e; k++) be += Math.abs(vo[y * N + k]);
                    bw /= nMw; be /= nMw; sw /= nMw;
                    sumW += bw * w; sumE += be * w; wTot += w;
                    if (bw > peak) peak = bw;
                    if (be > 1e-7 && nr < rv.length) { rv[nr] = bw / be; rw[nr] = w; nr++; } else if (be <= 1e-7) dropped++;
                    if (w >= nWide) { wides++; if (ng < gv.length) { gv[ng] = bw; gw[ng] = w; ng++; } }
                    if (y < N / 2) { sSum += sw; sN++; } else { nSum += sw; nN++; }
                }
                x = e + 1;
            }
        }
        if (quals == 0) { say("  【无合格盆】宽度 >= 2*M_w 的海区间一个都没有"); return; }
        double g3agg = sumE > 0 ? sumW / sumE : Double.NaN;
        double g3m = wMedian(rv, rw, nr);
        double[] plain = Arrays.copyOf(rv, nr);
        Arrays.sort(plain);
        double g4 = wMedian(gv, gw, ng);
        say(String.format(L, "  合格海区间 %d 个（其中宽 >= 4*M_w=%d km 的「真盆」 %d 个）；比值被丢弃 %d",
            quals, nWide * (int) DX / 1000, wides, dropped));
        say(String.format(L, "  【G3-聚合】面积加权 西输运/东输运 = %.2f   %s", g3agg, g3agg >= G3_TARGET ? "PASS" : "FAIL"));
        say(String.format(L, "  【G3-中位】面积加权 %.2f（不加权 %.2f）   %s",
            g3m, nr > 0 ? plain[nr / 2] : Double.NaN, g3m >= G3_TARGET ? "PASS" : "FAIL"));
        say(String.format(L, "  【G4】宽盆西带均速 面积加权中位 %.4f m/s   %s   （参照：P242 理想闭合盆 0.0663）",
            g4, g4 >= G4_TARGET ? "PASS" : "FAIL"));
        say(String.format(L, "  补充：西带峰值 %.4f m/s（会被海峡射流污染，仅参考）", peak));
        say(String.format(L, "  方向检查（西带均值 v）：南半 %.4f mm/s（应 +）  北半 %.4f mm/s（应 -）  符号%s",
            sN > 0 ? sSum / sN * 1000 : Double.NaN, nN > 0 ? nSum / nN * 1000 : Double.NaN,
            (sSum > 0 && nSum < 0) ? "正确" : "错误"));
    }

}
