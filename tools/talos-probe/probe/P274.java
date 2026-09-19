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

/** P274：板块尺度重扫 —— 判据换成「**真东岸的纬线比例**」+ 海盆宽度分布。 */
public class P274 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int[] CELLS = {600_000, 1_000_000, 1_600_000, 2_400_000};
    static final int NM = 48;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p274_report.txt"), "UTF-8");
        double th = Atmosphere.theta(0.0);
        int GRAD = 15_000;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say("P274：板块尺度重扫（maxRow=24M，CELL_GAIN=%.2f）", Atmosphere.CELL_GAIN);
        say(String.format(LF, "  %-9s %10s %10s %10s %10s %12s %12s %12s", "PLATE_CELL", "真东岸比", "盆宽p50", "盆宽p90",
            "盆宽max", "西带p50", "东带最好", "沿岸tau中位"));
        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            int nReal = 0, nOk = 0;
            double[] wv = new double[NM], bw = new double[NM], nz = new double[NM * 3];
            int nw = 0, nb = 0, nn = 0;
            double[] tau = new double[NM];
            for (int r = 0; r < NM; r++) {
                int z = (int) ((r + 0.5) / NM * ZC);
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((r * 411_000) % 9_000_000, z, SD, cell, wind, p);
                if (!row.valid) continue;
                double gate = Math.PI * p.deltaAt(z);
                if ((row.eastX - row.westX) < 2 * gate) continue;
                nOk++;
                bw[nb++] = (row.eastX - row.westX) / 1000.0;
                int q = Math.min(row.n, (int) (W / H));
                double s = 0;
                for (int i = 0; i < q; i++) s += row.v[i];
                wv[nw++] = Math.abs(s / q * 1000);
                boolean realE = row.eastX < p.maxRow - 20_000;
                if (realE) {
                    nReal++;
                    double t = 0; int c = 0;
                    for (int k = 1; k <= 3; k++) {
                        double v = Atmosphere.windStress(row.eastX - k * 60_000, z, SD, cell, th, GRAD)[1];
                        t += v; c++;
                        nz[nn++] = Math.abs(v);
                    }
                    tau[r] = t / c;
                }
            }
            double[] w2 = Arrays.copyOf(wv, nw); Arrays.sort(w2);
            double[] b2 = Arrays.copyOf(bw, nb); Arrays.sort(b2);
            double[] n2 = Arrays.copyOf(nz, nn); Arrays.sort(n2);
            double[] hc = CoastalLayer.steadySmoothed(tau, (double) ZC / NM);
            double best = 0;
            for (double b : new double[]{0.15, 0.25, 0.40, 0.55}) {
                int z = (int) (b * (ZC / 2));
                double lat = WorldContract.latOf(z, ZC);
                double f = WorldContract.coriolis(lat);
                double hcv = hc[Math.min(NM - 1, (int) ((double) z / ZC * NM))];
                double e = Math.abs(CoastalLayer.eastBandContribution(hcv, f, W) * 1000);
                if (e > best) best = e;
            }
            say(String.format(LF, "  %-9d %9.0f%% %10.0f %10.0f %10.0f %12.1f %12.2f %12.5f",
                cell, nReal * 100.0 / Math.max(1, nOk), q(b2, .5), q(b2, .9), b2[Math.max(0, nb - 1)],
                q(w2, .5), best, nn > 0 ? n2[nn / 2] : 0));
        }
        say("");
        say("判据说明：");
        say("  「真东岸比」= 合格行里东端是**真海岸**（不是 maxRow 截断）的比例；");
        say("  它决定沿岸上升流层能不能启动 —— 没有东岸就没有东边界流。");
        say("  地球上这个比例应当是 100%（每个海盆都有东西两岸）。");
        rep.close();
    }

    static double q(double[] s, double p) { return s.length == 0 ? 0 : s[Math.min(s.length - 1, (int) (p * s.length))]; }

    static void say(String fmt, Object... a) {
        String s = a.length == 0 ? fmt : String.format(LF, fmt, a);
        System.out.println("[P274] " + s); rep.println("[P274] " + s);
    }
}
