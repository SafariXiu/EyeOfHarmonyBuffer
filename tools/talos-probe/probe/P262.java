package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P262：Z_CYCLE=20M 之后的三件事。
 *
 * <ol>
 *   <li><b>受控不变性检验</b>：同一个合成海盆、同一套几何，只把 Z_CYCLE 从 1M 换到 20M
 *       （curl 与 beta 同时 /20），看西边界流是否**逐位不变**。这是 §29.6 那条断言的关键检验。</li>
 *   <li><b>宽度->速度定律</b>：带内均速 = psi_max/W = curl*L/(rho0*H*beta*W)，扫 L。</li>
 *   <li><b>生产世界的分布</b>：沿一条纬线取很多条行，报西带均速的分位数
 *       —— 因为「达标」现在是**统计口径**，不是唯一值。</li>
 * </ol>
 */
public class P262 {

    static final int SEED = 1022228679;
    static final int ZC_NEW = WorldContract.Z_CYCLE;
    static final int ZC_OLD = 1_000_000;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double A_H = 1.9e4;
    static final double OMEGA = WorldContract.OMEGA;
    static final double H = 5000.0;
    static final double W = 100_000.0;
    static final double TGT = 75.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p262_report.txt"), "UTF-8");
        say("P262：Z_CYCLE=20M 的不变性检验 + 速度分布");
        say(String.format(LF, "  Z_CYCLE=%d (旧 %d)  MAX_D=%d km  R_eff=%.0f km",
            ZC_NEW, ZC_OLD, WorldContract.MAX_D / 1000, WorldContract.R_EFF / 1000));
        say("");

        // ---------- A. 受控不变性 ----------
        say("A. 受控检验：同一个合成海盆（L=9000 km、均匀 curl、f=30 度），只换 Z_CYCLE");
        say(String.format(LF, "   %-10s %12s %12s %10s %14s %14s %14s",
            "Z_CYCLE", "beta", "curl Pa/m", "delta_M km", "psi_max m^2/s", "西带均mm/s", "峰值mm/s"));
        double[] keep = new double[4];
        for (int k = 0; k < 2; k++) {
            int zc = k == 0 ? ZC_OLD : ZC_NEW;
            double lat = Math.toRadians(30.0);
            double beta = 2.0 * OMEGA * Math.PI / zc * Math.cos(lat);
            double curl = -(3.0 * Math.PI * TAU0 / (zc / 2.0)) * Math.sin(Math.PI / 2.0);
            int n = (int) (9_000_000 / H) + 1;
            double[] c = new double[n];
            Arrays.fill(c, curl);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_TOTAL; p.aH = A_H; p.beta = beta;
            GyreRow.Row r = GyreRow.solveSpan(0, (int) ((n - 1) * H), c, p);
            double cum = 0, pm = 0;
            for (int i = 0; i < n; i++) { cum += r.v[i] * H; if (Math.abs(cum) > Math.abs(pm)) pm = cum; }
            int q = (int) (W / H);
            double s = 0;
            for (int i = 0; i < q; i++) s += r.v[i];
            double wbm = s / q * 1000;
            double pk = 0;
            for (int i = 1; i < n - 1; i++) if (Math.abs(r.v[i]) > Math.abs(pk)) pk = r.v[i];
            say(String.format(LF, "   %-10d %12.4e %12.4e %10.1f %14.1f %14.4f %14.4f",
                zc, beta, curl, p.delta() / 1000, pm, wbm, Math.abs(pk) * 1000));
            if (k == 0) { keep[0] = pm; keep[1] = wbm; keep[2] = Math.abs(pk) * 1000; keep[3] = p.delta() / 1000; }
            else {
                say(String.format(LF, "   ⇒ 比值（新/旧）：psi_max %.6f   西带均 %.6f   峰值 %.6f   delta_M %.6f",
                    pm / keep[0], wbm / keep[1], (Math.abs(pk) * 1000) / keep[2], (p.delta() / 1000) / keep[3]));
            }
        }
        say("   理论预期：curl/beta 不变 ⇒ psi_max 与带内均速**完全不变**（比值 = 1.000000）；");
        say("             delta_M = cbrt(A_H/beta) ⇒ 比值 = 20^(1/3) = 2.714");
        say("");

        // ---------- B. 宽度 -> 速度 ----------
        say("B. 宽度 -> 速度（新契约，f=30 度，均匀 curl）");
        double beta30 = 2.0 * OMEGA * Math.PI / ZC_NEW * Math.cos(Math.toRadians(30.0));
        double curl30 = 3.0 * Math.PI * TAU0 / (ZC_NEW / 2.0) * Math.sin(Math.PI / 3.0);
        say(String.format(LF, "  beta(30)=%.4e  curl(30)=%.4e  rho0*H*beta=%.4e", beta30, curl30, 1025 * H_TOTAL * beta30));
        say(String.format(LF, "   %10s %14s %14s %12s", "盆宽 km", "psi_max", "西带均mm/s", "对75"));
        double[] Ls = {1000e3, 2000e3, 3000e3, 5000e3, 7000e3, 9000e3, 12000e3, 16000e3, 20000e3};
        for (double L : Ls) {
            double psi = curl30 * L / (1025.0 * H_TOTAL * beta30);
            double v = psi / W * 1000;
            say(String.format(LF, "   %10.0f %14.0f %14.2f %12s", L / 1000, psi, v,
                v >= TGT ? "达标" : String.format(LF, "差%.2fx", TGT / v)));
        }
        double lNeed = TGT / 1000 * W * 1025.0 * H_TOTAL * beta30 / curl30;
        say(String.format(LF, "   ⇒ 要 75 mm/s 需要盆宽 >= %.0f km（地球上太平洋 10,000 km）", lNeed / 1000));
        say("");

        // ---------- C. 生产世界的分布 ----------
        say("C. 生产世界的分布：PLATE_CELL=2400000，沿 z 均匀取 40 条纬度行");
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC_NEW);
        double[] vals = new double[40];
        double[] widths = new double[40];
        int c = 0;
        for (int k = 0; k < 40; k++) {
            int z = (int) ((k + 0.5) / 40.0 * WorldContract.MAX_D);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_TOTAL; p.aH = A_H; p.zCycle = ZC_NEW;
            double gate = Math.PI * p.deltaAt(z);
            GyreRow.Row r = GyreRow.solve((k * 733_000) % 11_000_000, z, SEED, PlateField.PLATE_CELL, wind, p);
            if (!r.valid || (r.eastX - r.westX) < 2 * gate) continue;
            int q = Math.min(r.n, (int) (W / H));
            double s = 0;
            for (int i = 0; i < q; i++) s += r.v[i];
            vals[c] = Math.abs(s / q * 1000);
            widths[c] = (r.eastX - r.westX) / 1000.0;
            c++;
        }
        if (c == 0) { say("   （无合格盆）"); }
        else {
            double[] v2 = Arrays.copyOf(vals, c); Arrays.sort(v2);
            double[] w2 = Arrays.copyOf(widths, c); Arrays.sort(w2);
            say(String.format(LF, "   合格行 %d/40；西带均速 mm/s 分位：p10 %.1f  p25 %.1f  p50 %.1f  p75 %.1f  p90 %.1f  max %.1f",
                c, q(v2, 0.10), q(v2, 0.25), q(v2, 0.50), q(v2, 0.75), q(v2, 0.90), v2[c - 1]));
            say(String.format(LF, "   盆宽 km 分位：p10 %.0f  p50 %.0f  p90 %.0f", q(w2, 0.10), q(w2, 0.50), q(w2, 0.90)));
            int pass = 0;
            for (int i = 0; i < c; i++) if (v2[i] >= TGT) pass++;
            say(String.format(LF, "   >= %.0f mm/s 的行占比 %d/%d = %.0f%%", TGT, pass, c, pass * 100.0 / c));
        }
        rep.close();
    }

    static double q(double[] s, double p) { return s[Math.min(s.length - 1, (int) (p * s.length))]; }

    static void say(String s) { System.out.println("[P262] " + s); rep.println("[P262] " + s); }
}
