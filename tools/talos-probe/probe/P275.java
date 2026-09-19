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

/** P275：**沿岸风做厚**（每行多个海岸、z 加密到 96），给出可信的 tau 剖面与东带。 */
public class P275 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int NM = 96, NQ = 5;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p275_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double day = Double.parseDouble(System.getProperty("eoh.day", "0"));
        double th = Atmosphere.theta(day);
        int GRAD = 500_000;   // R2：真实大气对海陆对比的响应尺度 1000~2000 km
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say(String.format(LF, "P275：沿岸风做厚  PLATE_CELL=%d  NM=%d NQ=%d",
            cell, NM, NQ));
        say("");
        double[] tau = new double[NM];
        int[] cnt = new int[NM];
        double[] allEq = new double[NM * NQ * 3];
        int ne = 0; int capped = 0, nsp = 0;
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            double lat = WorldContract.latOf(z, ZC);
            double s = 0; int c = 0;
            for (int q = 0; q < NQ; q++) {
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 2_300_000 + r * 97_000) % 9_000_000, z, SD, cell, wind, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000) continue;
                if (row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                for (int k = 1; k <= 3; k++) {
                    int xx = row.eastX - k * 25_000;
                    double[] uv0 = Atmosphere.windAt(xx, z, SD, cell, th, GRAD);
                    if (Math.hypot(uv0[0], uv0[1]) >= 24.5) capped++;
                    nsp++;
                    double tz = Atmosphere.windStress(xx, z, SD, cell, th, GRAD)[1];
                    s += tz; c++;
                    // 向赤道为正：北半球向赤道 = 南向 = tau_z < 0
                    double eq = Math.toDegrees(lat) >= 0 ? -tz : tz;
                    if (Math.abs(lat) < Math.toRadians(48)) allEq[ne++] = eq;
                }
            }
            cnt[r] = c;
            tau[r] = c > 0 ? s / c : 0;
        }
        int tot = 0; for (int v : cnt) tot += v;
        say(String.format(LF, "  采样点总数 %d（平均每纬线 %.1f 个）", tot, tot / (double) NM));
        say(String.format(LF, "  ⚠ |U| >= 24.5 m/s（撞 U_MAX=25 上限）的比例：%d/%d = %.0f%%", capped, nsp, nsp > 0 ? capped * 100.0 / nsp : 0));
        double[] e2 = Arrays.copyOf(allEq, ne);
        Arrays.sort(e2);
        double mn = 0; for (double v : e2) mn += v;
        say(String.format(LF, "  |lat|<48 度的「向赤道分量」：中位 %+.5f Pa  均值 %+.5f Pa  正（上升流有利）比例 %d/%d = %.0f%%",
            ne > 0 ? e2[ne / 2] : 0, ne > 0 ? mn / ne : 0,
            count(e2), ne, ne > 0 ? count(e2) * 100.0 / ne : 0));
        say("");
        say(String.format(LF, "   %-9s %6s %14s %14s %10s", "纬度", "样本", "tau_z Pa", "向赤道 Pa", "h_c 累积 m"));
        double[] hc = CoastalLayer.steadySmoothed(tau, (double) ZC / NM);
        for (int r = 0; r < NM; r += 4) {
            int z = (int) ((r + 0.5) / NM * ZC);
            double lat = WorldContract.latOf(z, ZC);
            double eq = Math.toDegrees(lat) >= 0 ? -tau[r] : tau[r];
            say(String.format(LF, "   %-9.1f %6d %14.5f %14.5f %10.1f",
                Math.toDegrees(lat), cnt[r], tau[r], eq, hc[r]));
        }
        say("");
        say("  东带（沿岸层 + 斯维尔德鲁普）@100km：");
        for (double b : new double[]{0.15, 0.25, 0.40, 0.55, 0.70, 0.85}) {
            int z = (int) (b * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            double hcv = hc[Math.min(NM - 1, (int) ((double) z / ZC * NM))];
            double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
            double sv = wind.at(0, z) / (1025.0 * H_T * WorldContract.betaForLatitude(lat, ZC)) * 1000;
            say(String.format(LF, "   %-9.1f 沿岸层 %8.2f  斯维尔德鲁普 %8.2f  合计 %8.2f mm/s  对30 %s",
                Math.toDegrees(lat), cb, sv, cb + sv,
                Math.abs(cb + sv) >= 30 ? "达标" : String.format(LF, "%.2fx", 30 / Math.max(1e-9, Math.abs(cb + sv)))));
        }
        // ---- 西边界：同一协议 ----
        say("");
        say("  西边界 @100km 全深均（同一批 96 条纬线）：");
        double[] wv2 = new double[NM];
        int nw2 = 0;
        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            GyreRow.Row row = GyreRow.solve((r * 97_000) % 9_000_000, z, SD, cell, wind, p);
            if (!row.valid) continue;
            if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
            int q = Math.min(row.n, (int) (W / H));
            double s2 = 0;
            for (int i = 0; i < q; i++) s2 += row.v[i];
            wv2[nw2++] = Math.abs(s2 / q * 1000);
        }
        double[] w3 = Arrays.copyOf(wv2, nw2);
        Arrays.sort(w3);
        int pass75 = 0; for (double v : w3) if (v >= 75) pass75++;
        say(String.format(LF, "   合格行 %d    p10 %.1f  p25 %.1f  **p50 %.1f**  p75 %.1f  p90 %.1f  max %.1f mm/s",
            nw2, w3[(int) (0.10 * nw2)], w3[(int) (0.25 * nw2)], w3[nw2 / 2], w3[(int) (0.75 * nw2)],
            w3[(int) (0.90 * nw2)], w3[nw2 - 1]));
        say(String.format(LF, "   >=75 占比 %d/%d = %.0f%%   目标 75，中位 %s",
            pass75, nw2, pass75 * 100.0 / nw2, w3[nw2 / 2] >= 75 ? "达标 ✓" : String.format(LF, "差 %.2fx", 75 / w3[nw2 / 2])));
        rep.close();
    }

    static int count(double[] s) { int c = 0; for (double v : s) if (v > 0) c++; return c; }

    static void say(String s) { System.out.println("[P275] " + s); rep.println("[P275] " + s); }
}
