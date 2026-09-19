package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P251：① 确认 §19.2 那个 2 倍的归属；② 出**四条流的完整符号矩阵**（目标 2 的验收条款）。
 *
 * <pre>
 * [1] 合成算例：稠密高斯解 与 GyreRow 解析解 并排打印 psi[0..6] 与 v[0..6]
 * [2] 真实板块行：按 bandD 扫，统计西/东边界的【有符号】带内均值
 *     期望（三带风）：副热带 西+ / 东-   ；副极地 西- / 东+
 * </pre>
 */
public class P251 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p251_report.txt"), "UTF-8");
        say("① 2 倍归属确认  ② 四条流符号矩阵");

        // ================= [1] 合成算例并排对照 =================
        say("");
        say("  [1] 合成算例（L=1200 km, beta=1e-11, A_H=400, rho0*H=5.125e6, curl=-1.851e-7, h=2.5 km）");
        double beta = 1.0e-11, aH = 400.0, rhoH = 1025.0 * 5000.0, h = 2500.0;
        int n = (int) (1_200_000.0 / h) + 1;
        double[] curl = new double[n];
        for (int i = 0; i < n; i++) curl[i] = -1.851e-07;
        double[] dense = gaussSolve(buildDense(curl, h, beta, aH, rhoH), rhs(curl, rhoH));
        GyreRow.Params pp = new GyreRow.Params();
        pp.beta = beta; pp.aH = aH; pp.rhoH = rhoH; pp.h = h;
        GyreRow.Row rr = GyreRow.solveSpan(0, (int) ((n - 1) * h), curl, pp);
        say("      i     x(km)      psi_dense    psi_GyreRow     v_dense    v_GyreRow    v_2ndOrder(GyreRow psi)");
        for (int i = 0; i < 8; i++) {
            double vd = i == 0 ? (dense[1] - 0) / h : (dense[i + 1] - dense[i - 1]) / (2 * h);
            double v2 = i == 0 ? (-3 * rr.psi[0] + 4 * rr.psi[1] - rr.psi[2]) / (2 * h)
                               : (rr.psi[i + 1] - rr.psi[i - 1]) / (2 * h);
            say(String.format(LF, "      %d %9.1f %13.2f %13.2f %12.4f %12.4f %14.4f",
                i, i * h / 1000, dense[i], rr.psi[i], vd * 1000, rr.v[i] * 1000, v2 * 1000));
        }
        say(String.format(LF, "      峰值：dense(单侧) %.2f mm/s   GyreRow(解析) %.2f mm/s   GyreRow(二阶单侧) %.2f mm/s",
            (dense[1] / h) * 1000, rr.v[0] * 1000, ((-3 * rr.psi[0] + 4 * rr.psi[1] - rr.psi[2]) / (2 * h)) * 1000));

        // ================= [2] 四条流符号矩阵 =================
        say("");
        say("  [2] 真实板块行的四条流符号矩阵（H=4000 m；三带风 tau0=0.05；h=5 km）");
        GyreRow.Params p2 = new GyreRow.Params();
        p2.h = 5000.0;
        GyreRow.WindCurl wind = new GyreRow.BandedWind(0.05, ZC);
        say(String.format(LF, "      delta_M(赤道)=%.1f km  delta_M(45度)=%.1f km（门槛按纬度行算）",
            p2.deltaAt(0) / 1000, p2.deltaAt(250_000) / 1000));
        say(String.format(LF, "      %-12s %5s %10s %13s %13s %9s %9s %s",
            "bandD", "n", "盆宽km", "西带均mm/s", "东带均mm/s", "西/东", "curl符号", "判定"));
        double[] bands = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
        for (double b : bands) {
            int z = (int) (b * (ZC / 2));
            double mw = Math.PI * p2.deltaAt(z);
            int cnt = 0; double wSum = 0, eSum = 0, wAbs = 0, eAbs = 0, widthSum = 0;
            for (int ix = 0; ix < 8; ix++) {
                int x = ix * 1_500_000;
                GyreRow.Row r = GyreRow.solve(x, z, SD, CELL, wind, p2);
                if (!r.valid) continue;
                if ((r.eastX - r.westX) < 2 * mw) continue;
                int nb = (int) (mw / p2.h);
                double ws = 0, es = 0, wa = 0, ea = 0;
                for (int i = 0; i < Math.min(nb, r.n); i++) { ws += r.v[i]; wa += Math.abs(r.v[i]); }
                for (int i = Math.max(0, r.n - nb); i < r.n; i++) { es += r.v[i]; ea += Math.abs(r.v[i]); }
                cnt++; wSum += ws / nb; eSum += es / nb; wAbs += wa / nb; eAbs += ea / nb;
                widthSum += (r.eastX - r.westX);
            }
            if (cnt == 0) { say(String.format(LF, "      %.2f        （无合格盆）", b)); continue; }
            double wm = wSum / cnt * 1000, em = eSum / cnt * 1000;
            double curlSign = -Math.sin(3 * Math.PI * b);
            boolean expW = curlSign < 0;
            boolean ok = (expW && wm > 0) || (!expW && wm < 0);
            say(String.format(LF, "      %-12.2f %5d %10.0f %13.4f %13.4f %9.2f %9s %s",
                b, cnt, widthSum / cnt / 1000.0, wm, em, wAbs / Math.max(1e-12, eAbs),
                curlSign < 0 ? "-" : "+", ok ? "符号正确" : "**符号错误**"));
        }
        rep.close();
    }

    static void say(String s) { System.out.println("[P251] " + s); rep.println("[P251] " + s); }

    static double[][] buildDense(double[] curl, double h, double beta, double aH, double rhoH) {
        int n = curl.length;
        double[][] M = new double[n][n];
        double c4 = aH / (h * h * h * h), c1 = beta / (2.0 * h);
        for (int i = 0; i < n; i++) {
            M[i][i] += 6.0 * c4;
            if (i - 1 >= 0) M[i][i - 1] += -4.0 * c4;
            if (i + 1 < n)  M[i][i + 1] += -4.0 * c4;
            if (i - 2 >= 0) M[i][i - 2] += c4;
            if (i + 2 < n)  M[i][i + 2] += c4;
            if (i == 0) M[i][i] += -c4;
            if (i == n - 1) M[i][i] += -c4;
            if (i - 1 >= 0) M[i][i - 1] += c1;
            if (i + 1 < n)  M[i][i + 1] += -c1;
        }
        return M;
    }
    static double[] rhs(double[] curl, double rhoH) {
        double[] b = new double[curl.length];
        for (int i = 0; i < curl.length; i++) b[i] = -curl[i] / rhoH;
        return b;
    }
    static double[] gaussSolve(double[][] M, double[] b) {
        int n = b.length;
        for (int k = 0; k < n; k++) {
            int piv = k;
            for (int i = k + 1; i < n; i++) if (Math.abs(M[i][k]) > Math.abs(M[piv][k])) piv = i;
            double[] t = M[k]; M[k] = M[piv]; M[piv] = t;
            double tt = b[k]; b[k] = b[piv]; b[piv] = tt;
            for (int i = k + 1; i < n; i++) {
                double f = M[i][k] / M[k][k];
                if (f == 0) continue;
                for (int j = k; j < n; j++) M[i][j] -= f * M[k][j];
                b[i] -= f * b[k];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = b[i];
            for (int j = i + 1; j < n; j++) s -= M[i][j] * x[j];
            x[i] = s / M[i][i];
        }
        return x;
    }
}
