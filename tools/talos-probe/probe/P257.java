package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P257：**修正 beta 之后的四条流符号矩阵 + 量级**，并在两个板块尺度下各跑一遍（D5 的证据）。
 *
 * <p>与 P251 的区别：
 * <ol>
 *   <li>**不设置 Params.beta** —— 故意留空，验证 §24 的自动推导（同时打印解析值做对照）；</li>
 *   <li>带宽钉死 **W = 100 km**（§23.2：真实参照 30 Sv/(100 km x 4000 m) 用的就是它）；</li>
 *   <li>取样窗口拉到 0~11,000 km（§24.6：窄窗口会给出有偏的盆宽）；</li>
 *   <li>同时报正压与 x5 表层（**不含埃克曼** —— §22 已证埃克曼对西/东差异贡献恒为 0）。</li>
 * </ol>
 */
public class P257 {

    static final int SEED = 1022228679;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double HD = 5000.0;
    static final double OMEGA = 7.2921e-5;
    static final double W = 100_000.0;
    static final double SURF = 5.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;
    static final int[] CELLS = {600_000, 2_400_000};
    static final int NCOL = 12;
    static final int XSTEP = 1_000_000;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p257_report.txt"), "UTF-8");
        say("P257：修正 beta 后的四条流矩阵（W=100 km；取样 0~11,000 km）");
        say(String.format(LF, "  h=%.0f m tau0=%.4f H=%.0f m 带宽=%.0f km 表层因子=%.1f",
            HD, TAU0, H_TOTAL, W / 1000, SURF));
        say("");

        // ---- A. beta 自动推导自检 ----
        say("A. 自检：不设置 Params.beta，看 solve() 是否按纬度推导（对照解析 2*Omega*pi/ZC*cos(phi)）");
        GyreRow.Params pchk = new GyreRow.Params();
        pchk.h = HD; pchk.rhoH = 1025.0 * H_TOTAL;
        boolean betaOk = true;
        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double lat = GyreRow.latOf(z, ZC);
            double got = pchk.betaAt(z);
            double want = 2.0 * OMEGA * Math.PI / ZC * Math.cos(lat);
            boolean ok = Math.abs(got - want) < 1e-18 && got > 0;
            if (!ok) betaOk = false;
            say(String.format(LF, "   bandD %.2f  z=%7d  lat=%7.2f度  betaAt=%.4e  解析=%.4e  %s",
                BANDS[bi], z, Math.toDegrees(lat), got, want, ok ? "OK" : "错"));
        }
        say(String.format(LF, "   ⇒ beta 自动推导：%s；delta_M 随纬度 %.1f ~ %.1f km",
            betaOk ? "全部正确" : "**有错**", pchk.deltaAt(0) / 1000, pchk.deltaAt(ZC / 2 - 1) / 1000));
        say("");

        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            say(String.format(LF, "B. 四条流矩阵  PLATE_CELL = %d", cell));
            say(String.format(LF, "   %-7s %4s %9s %11s %11s %11s %9s %5s %5s %11s %8s",
                "bandD", "n", "盆宽中位", "psi_max中位", "西带均", "东带均", "西/东", "西符", "东符", "x5西带", "量级"));
            int okW = 0, okE = 0, okA7 = 0, ok50 = 0, ok100 = 0, tot = 0;
            for (int bi = 0; bi < NB; bi++) {
                int z = (int) (BANDS[bi] * (ZC / 2));
                GyreRow.Params p = new GyreRow.Params();   // beta 故意留空
                p.h = HD; p.rhoH = 1025.0 * H_TOTAL; p.zCycle = ZC;
                double gate = Math.PI * p.deltaAt(z);
                int q = (int) (W / HD);
                double[] wv = new double[NCOL], ev = new double[NCOL], pv = new double[NCOL], lv = new double[NCOL];
                int c = 0;
                for (int ix = 0; ix < NCOL; ix++) {
                    GyreRow.Row r = GyreRow.solve(ix * XSTEP, z, SEED, cell, wind, p);
                    if (!r.valid) continue;
                    if ((r.eastX - r.westX) < 2 * gate) continue;
                    int n = r.n;
                    if (n < 2 * q) continue;
                    double ws = 0, es = 0, cum = 0, pm = 0;
                    for (int i = 0; i < n; i++) {
                        cum += r.v[i] * HD;
                        if (Math.abs(cum) > Math.abs(pm)) pm = cum;
                    }
                    for (int i = 0; i < q; i++) ws += r.v[i];
                    for (int i = n - q; i < n; i++) es += r.v[i];
                    wv[c] = ws / q * 1000; ev[c] = es / q * 1000; pv[c] = pm; lv[c] = (r.eastX - r.westX) / 1000.0;
                    c++;
                }
                tot++;
                if (c == 0) { say(String.format(LF, "   %-7.2f  （无合格盆）", BANDS[bi])); continue; }
                double wm = med(wv, c), em = med(ev, c), pm = med(pv, c), lm = med(lv, c);
                double curl = wind.at(0, z);
                boolean expWpos = curl < 0;                      // 西带符号 = -sign(curl)
                boolean sW = (wm > 0) == expWpos;
                boolean sE = (em > 0) == !expWpos;               // 东带必须反号（闭合行硬约束）
                if (sW) okW++;
                if (sE) okE++;
                double ratio = Math.abs(em) > 1e-12 ? wm / em : Double.NaN;
                if (Math.abs(ratio) >= 4) okA7++;
                double sf = Math.abs(wm) * SURF;
                if (sf >= 50) ok50++;
                if (sf >= 100) ok100++;
                String mag = sf >= 100 ? ">=100" : (sf >= 50 ? ">=50" : "差");
                say(String.format(LF, "   %-7.2f %4d %8.0f %11.0f %8.2f mm/s %8.2f mm/s %9.1f %5s %5s %8.1f mm/s %8s",
                    BANDS[bi], c, lm, pm, wm, em, ratio, sW ? "OK" : "错", sE ? "OK" : "错", sf, mag));
            }
            say(String.format(LF, "   ⇒ 西符号 %d/%d  东符号 %d/%d  A7(>=4) %d/%d  西带x5>=50 %d/%d  >=100 %d/%d",
                okW, tot, okE, tot, okA7, tot, ok50, tot, ok100, tot));
            say("");
        }
        say("C. 说明");
        say("  - 西带符号期望 = -sign(curl)；东带必须与西带反号，这是闭合行硬约束（不是约定）。");
        say("  - x5 表层只含「层厚强化」，不含埃克曼：§22 已证埃克曼是行内常数，对西/东差异贡献恒为 0。");
        say("  - 量级目标：§23.2 建议钉 W=100 km，真实同口径 75 mm/s（正压）。");
        rep.close();
    }

    static double med(double[] a, int n) {
        double[] b = Arrays.copyOf(a, n);
        Arrays.sort(b);
        return b[n / 2];
    }

    static void say(String s) { System.out.println("[P257] " + s); rep.println("[P257] " + s); }
}
