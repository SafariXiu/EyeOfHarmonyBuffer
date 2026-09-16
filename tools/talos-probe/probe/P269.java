package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/** P269：**联合验收测量** —— 西边界 75 mm/s（分布）与 东边界 30 mm/s。 */
public class P269 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p269_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say("P269：联合验收测量  CELL_GAIN=%.1f  PLATE_CELL=%d  Z_CYCLE=%d".replace("%.1f", "" + Atmosphere.CELL_GAIN)
            .replace("%d", "" + cell).replace("Z_CYCLE=" + ZC, "Z_CYCLE=" + ZC));
        say(String.format(LF, "  CELL_GAIN=%.1f  PLATE_CELL=%d  Z_CYCLE=%d  目标：西 75 / 东 30 mm/s",
            Atmosphere.CELL_GAIN, cell, ZC));
        say("");

        // ---------- 东边界 ----------
        int NM = 64;
        double ds = (double) ZC / NM;
        double[] tauZ = new double[NM];
        GyreRow.Row[] rows = new GyreRow.Row[NM];
        int[] zs = new int[NM];
        for (int r = 0; r < NM; r++) {
            zs[r] = (int) ((r + 0.5) / NM * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            rows[r] = GyreRow.solve(5_500_000, zs[r], SEED, cell, wind, p);
            double s = 0; int c = 0;
            if (rows[r].valid) {
                for (int k = 1; k <= 4; k++) {
                    double[] a = Atmosphere.windStress(rows[r].eastX - k * 120_000, zs[r], SEED, cell,
                        Atmosphere.theta(0.0), 15_000);
                    s += a[1]; c++;
                }
            }
            tauZ[r] = c > 0 ? s / c : 0;
        }
        double ma = 0; for (double v : tauZ) ma += Math.abs(v);
        double[] hc = CoastalLayer.steadySmoothed(tauZ, ds);
        say(String.format(LF, "东边界：沿岸 |tau_z| 均值 %.5f Pa（目标 0.090）  h_c 极值 %.1f m", ma / NM, ext(hc)));
        say(String.format(LF, "   %-8s %14s %16s %14s %10s", "纬度", "沿岸层mm/s", "斯维尔德鲁普", "合计mm/s", "对30"));
        int pass = 0;
        for (int bi = 0; bi < BANDS.length; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            double hcv = hc[Math.min(NM - 1, (int) ((double) z / ZC * NM))];
            double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
            double sv = wind.at(0, z) / (1025.0 * H_T * WorldContract.betaForLatitude(lat, ZC)) * 1000;
            double tot = Math.abs(cb + sv);
            if (tot >= 30) pass++;
            say(String.format(LF, "   %-8.1f %14.2f %16.4f %14.2f %10s", Math.toDegrees(lat), cb, sv, cb + sv,
                tot >= 30 ? "达标" : String.format(LF, "%.2fx", 30 / tot)));
        }
        say(String.format(LF, "   ⇒ 东边界 >=30 mm/s 的带：%d/%d", pass, BANDS.length));
        say("");

        // ---------- 西边界 ----------
        int NS = 40;
        double[] wv = new double[NS];
        int c2 = 0;
        for (int k = 0; k < NS; k++) {
            int z = (int) ((k + 0.5) / NS * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            GyreRow.Row r = GyreRow.solve((k * 733_000) % 11_000_000, z, SEED, cell, wind, p);
            if (!r.valid || (r.eastX - r.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
            int q = Math.min(r.n, (int) (W / H));
            double s = 0;
            for (int i = 0; i < q; i++) s += r.v[i];
            wv[c2++] = Math.abs(s / q * 1000);
        }
        double[] w2 = Arrays.copyOf(wv, c2);
        Arrays.sort(w2);
        int p75 = 0; for (double v : w2) if (v >= 75) p75++;
        say(String.format(LF, "西边界（%d 条合格纬度行，@100km 全深均）：", c2));
        say(String.format(LF, "   p10 %.1f  p25 %.1f  **p50 %.1f**  p75 %.1f  p90 %.1f  max %.1f mm/s",
            q(w2, .10), q(w2, .25), q(w2, .50), q(w2, .75), q(w2, .90), w2[c2 - 1]));
        say(String.format(LF, "   >=75 mm/s 占比 %d/%d = %.0f%%   中位数对 75：%s",
            p75, c2, p75 * 100.0 / c2, q(w2, .50) >= 75 ? "达标" : String.format(LF, "差 %.2fx", 75 / q(w2, .50))));
        rep.close();
    }

    static double q(double[] s, double p) { return s[Math.min(s.length - 1, (int) (p * s.length))]; }
    static double ext(double[] v) { double m = 0; for (double x : v) if (Math.abs(x) > Math.abs(m)) m = x; return m; }
    static void say(String s) { System.out.println("[P269] " + s); rep.println("[P269] " + s); }
}
