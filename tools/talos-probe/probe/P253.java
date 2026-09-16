package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.SurfaceLayer;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P253：**表层强化层到底修好了什么、没修好什么**。
 *
 * <p>对 P251 的六个纬度带，同时给出【正压（深度平均）】与【表层】的西/东带内均值与符号。
 * 表层 = 正压 x (H_total/H_thermocline) + 埃克曼漂流。
 */
public class P253 {

    static final int SEED = 1022228679;
    static final int CELL = PlateField.PLATE_CELL;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
    static final double BAND_KM = 122.0;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p253_report.txt"), "UTF-8");
        say("表层强化层评估（正压 vs 表层；H_total=4000 m，温跃层 800 m，风 tau0=0.0685）");
        GyreRow.Params p = new GyreRow.Params();
        p.h = 5000.0; p.rhoH = 1025.0 * H_TOTAL;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        int nb = (int) (BAND_KM * 1000.0 / p.h);
        say(String.format(LF, "  带=%d 格  delta_M(赤道)=%.1f km / (45度)=%.1f km  强化因子 H/Ht=%.1f",
            nb, p.deltaAt(0) / 1000, p.deltaAt(250_000) / 1000, H_TOTAL / SurfaceLayer.H_TRANSPORT));
        say("");
        say(String.format(LF, "  %-7s %6s %14s %14s %14s %14s %9s",
            "bandD", "n", "正压西带", "正压东带", "表层西带", "表层东带", "符号"));
        for (double b : BANDS) {
            int z = (int) (b * (ZC / 2));
            double mwGate = Math.PI * p.deltaAt(z);
            double hs = ((z % ZC) < ZC / 2) ? 1.0 : -1.0;
            double latRad = hs * b * Math.PI / 2.0;
            double f = 2.0 * SurfaceLayer.OMEGA * Math.sin(latRad);
            double tx = wind.tauX(z), ty = wind.tauY(z);
            int cnt = 0;
            double bw = 0, be = 0, sw = 0, se = 0;
            for (int ix = 0; ix < 8; ix++) {
                GyreRow.Row r = GyreRow.solve(ix * 1_500_000, z, SEED, CELL, wind, p);
                if (!r.valid) continue;
                if ((r.eastX - r.westX) < 2 * mwGate) continue;
                int q = Math.min(nb, r.n);
                double a1 = 0, a2 = 0, c1 = 0, c2 = 0;
                for (int i = 0; i < q; i++) {
                    a1 += r.v[i];
                    c1 += SurfaceLayer.at(0, r.v[i], tx, ty, f, H_TOTAL)[1];
                }
                for (int i = Math.max(0, r.n - q); i < r.n; i++) {
                    a2 += r.v[i];
                    c2 += SurfaceLayer.at(0, r.v[i], tx, ty, f, H_TOTAL)[1];
                }
                cnt++; bw += a1 / q; be += a2 / q; sw += c1 / q; se += c2 / q;
            }
            if (cnt == 0) { say(String.format(LF, "  %-7.2f   （无合格盆）", b)); continue; }
            double curlSign = -Math.sin(3 * Math.PI * b);
            boolean expWpos = curlSign < 0;
            boolean okB = (expWpos && bw > 0) || (!expWpos && bw < 0);
            boolean okS = (expWpos && sw > 0) || (!expWpos && sw < 0);
            say(String.format(LF, "  %-7.2f %6d %11.4f mm/s %11.4f mm/s %11.4f mm/s %11.4f mm/s %9s",
                b, cnt, bw / cnt * 1000, be / cnt * 1000, sw / cnt * 1000, se / cnt * 1000,
                (okB ? "正压OK" : "正压错") + "/" + (okS ? "表层OK" : "表层错")));
        }
        say("");
        say(String.format(LF, "  埃克曼参考：tau=%.4f Pa, f(0.15)=%.3e ⇒ u_e=%.4f m/s（上限 %.2f）",
            TAU0, 2 * SurfaceLayer.OMEGA * Math.sin(0.15 * Math.PI / 2),
            TAU0 / (SurfaceLayer.RHO * Math.sqrt(SurfaceLayer.A_Z * 2 * SurfaceLayer.OMEGA * Math.sin(0.15 * Math.PI / 2))),
            SurfaceLayer.EKMAN_MAX));
        rep.close();
    }

    static void say(String s) { System.out.println("[P253] " + s); rep.println("[P253] " + s); }
}
