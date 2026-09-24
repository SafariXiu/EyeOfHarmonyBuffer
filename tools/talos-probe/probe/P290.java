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

/**
 * P290：A3 的**方向 vs 量级**取舍 —— 沿岸累积的"记忆长度"与"涡旋分段"。
 *
 * <p>P289 定位：东岸近岸 tau_y 在 55~82 度冬季变成强向极（+0.90 Pa @55.5 度），
 * 而 `L_RELAX = MAX_D = 10,000 km` 让这份向极的累积**污染到副热带**，
 * 于是 30 度的 h_c 在冬季翻号 ⇒ A3 方向翻转。
 *
 * <p>试四种闭合：L_RELAX 10,000 / 3,000 km x 是否"先平滑再按 tau 符号分段"。
 */
public class P290 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int NM = 240, NQ = 4;
    static int[] eastX = new int[NM * NQ];
    static int[] eastZ = new int[NM * NQ];
    static boolean[] ok = new boolean[NM * NQ];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p290_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        say("P290：A3 方向/量级取舍（沿岸累积的记忆长度 x 涡旋分段）");
        say(String.format(LF, "  NM=%d 纬线 x %d x 起点；G_PRIME=%.3f H1=%.0f H=%.0f W=%d km",
            NM, NQ, CoastalLayer.G_PRIME, CoastalLayer.H_THERMOCLINE, CoastalLayer.H_TOTAL, (int)(W/1000)));

        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            for (int q = 0; q < NQ; q++) {
                int i = r * NQ + q;
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 3_100_000 + r * 811_000) % 9_000_000, z, SD, cell, band, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                eastX[i] = row.eastX; eastZ[i] = z; ok[i] = true;
            }
        }
        int nOk = 0; for (int i = 0; i < NM*NQ; i++) if (ok[i]) nOk++;
        say(String.format(LF, "  合格行 %d / %d", nOk, NM*NQ));
        say("");

        double ds = (double) ZC / NM;
        double savedRelax = CoastalLayer.L_RELAX;
        double[] relaxes = {10_000_000.0, 5_000_000.0, 3_000_000.0};
        boolean[] segs = {false, true};
        double[] correctFrac = new double[relaxes.length * 2];
        double[] pass20Frac = new double[relaxes.length * 2];
        int vi = 0;

        for (int ri = 0; ri < relaxes.length; ri++) {
            for (int sg = 0; sg < 2; sg++) {
                CoastalLayer.L_RELAX = relaxes[ri];
                say(String.format(LF, "V%d. L_RELAX = %d km   涡旋分段 = %s", vi, (int)(relaxes[ri]/1000), segs[sg] ? "是（先平滑再按符号重置）" : "否"));
                say(String.format(LF, "    %-8s %10s %10s %10s %10s %10s %10s %10s",
                    "季节", "lat10", "lat20", "lat30", "lat45", "-10", "-20", "-30", "-45"));
                int nCorr = 0, nTot = 0;
                int n20 = 0, nAll = 0;
                for (int si = 0; si < 4; si++) {
                    double th = Atmosphere.theta(si * WorldContract.DAYS_PER_YEAR / 4.0);
                    double[] tauRow = new double[NM];
                    for (int r = 0; r < NM; r++) {
                        double s = 0; int c = 0;
                        for (int q = 0; q < NQ; q++) {
                            int i = r * NQ + q;
                            if (!ok[i]) continue;
                            for (int k = 1; k <= 3; k++) { s += Atmosphere.windStress(eastX[i] - k * 25_000, eastZ[i], SD, cell, th, GRAD)[1]; c++; }
                        }
                        tauRow[r] = c > 0 ? s / c : 0;
                    }
                    double[] hc = segs[sg] ? smoothThenSegment(tauRow, ds) : CoastalLayer.steadySmoothed(tauRow, ds);
                    StringBuilder sb = new StringBuilder();
                    int[] lats = {10, 20, 30, 45, -10, -20, -30, -45};
                    for (int latDeg : lats) {
                        int za = WorldContract.zOfLat(Math.abs((double) latDeg));
                        double lat = WorldContract.latOf(WorldContract.zOfLat(latDeg), ZC);
                        double f = WorldContract.coriolis(lat);
                        double hcv = hc[Math.max(0, Math.min(NM - 1, za * NM / ZC))];
                        double mm = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
                        sb.append(String.format(LF, " %10.1f", mm));
                        boolean eq = latDeg >= 0 ? mm < 0 : mm > 0;
                        if (eq) nCorr++;
                        nTot++;
                        if (Math.abs(mm) >= 20) n20++;
                        nAll++;
                    }
                    say(String.format(LF, "    %-8s%s", "day=" + (int)(si * WorldContract.DAYS_PER_YEAR / 4.0), sb.toString()));
                }
                correctFrac[vi] = nCorr * 100.0 / nTot;
                pass20Frac[vi] = n20 * 100.0 / nAll;
                say(String.format(LF, "    ⇒ 向赤道正确 %d/%d = %.0f%%    |东带| >= 20 mm/s 的比例 %.0f%%", nCorr, nTot, correctFrac[vi], pass20Frac[vi]));
                say("");
                vi++;
            }
        }
        CoastalLayer.L_RELAX = savedRelax;
        say("汇总（32 个 (季节,纬度) 样本）");
        say(String.format(LF, "  %-14s %-26s %14s %14s", "L_RELAX", "分段", "方向正确%", ">=20mm/s%"));
        vi = 0;
        for (int ri = 0; ri < relaxes.length; ri++) {
            for (int sg = 0; sg < 2; sg++) {
                say(String.format(LF, "  %-14s %-26s %13.0f%% %13.0f%%", (int)(relaxes[ri]/1000) + " km",
                    segs[sg] ? "是" : "否", correctFrac[vi], pass20Frac[vi]));
                vi++;
            }
        }
        rep.close();
    }

    /** 先按涡旋尺度平滑，再按平滑后的 tau 符号分段累积（取代"逐点符号重置"的噪声问题）。 */
    static double[] smoothThenSegment(double[] tau, double ds) {
        int n = tau.length;
        int half = Math.max(1, (int) (CoastalLayer.SMOOTH_KM * 1000.0 / ds / 2.0));
        double[] sm = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = -half; j <= half; j++) s += tau[((i + j) % n + n) % n];
            sm[i] = s / (2 * half + 1);
        }
        double a = Math.exp(-ds / CoastalLayer.L_RELAX);
        double k = ds / (CoastalLayer.RHO * CoastalLayer.G_PRIME * CoastalLayer.H_THERMOCLINE);
        double[] h = new double[n];
        double acc = 0;
        for (int pass = 0; pass < 6; pass++) {
            acc = 0; int prev = 0;
            for (int i = 0; i < n; i++) {
                int sgn = sm[i] > 0 ? 1 : (sm[i] < 0 ? -1 : 0);
                if (sgn != 0 && prev != 0 && sgn != prev) acc = 0;
                if (sgn != 0) prev = sgn;
                acc = acc * a + sm[i] * k;
                h[i] = acc;
            }
        }
        return h;
    }

    static void say(String s) { System.out.println("[P290] " + s); rep.println("[P290] " + s); }
}
