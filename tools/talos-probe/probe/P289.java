package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P289：A3 季节反方向的**定位** —— 东岸近岸 tau_y 的纬度剖面 x 4 季，并做分项归因。
 */
public class P289 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int cell;

    static final int NM = 60, NQ = 4;
    static int[] eastX = new int[NM * NQ];
    static int[] eastZ = new int[NM * NQ];
    static double[] kap = new double[NM * NQ];
    static boolean[] ok = new boolean[NM * NQ];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p289_report.txt"), "UTF-8");
        cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        say("P289：东岸近岸 tau_y 的纬度剖面 x 4 季（定位 A3 季节反方向）");
        say("");

        for (int r = 0; r < NM; r++) {
            int z = (int) ((r + 0.5) / NM * ZC);
            for (int q = 0; q < NQ; q++) {
                int i = r * NQ + q;
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 2_300_000 + r * 2_119_000) % 9_000_000, z, SEED, cell, band, p);
                if (!row.valid) continue;
                if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) continue;
                if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                eastX[i] = row.eastX; eastZ[i] = z;
                kap[i] = Atmosphere.kappaAt(row.eastX - 50_000, z, SEED, cell);
                ok[i] = true;
            }
        }

        double[][] tau = new double[4][NM];
        double[][] tauTherm = new double[4][NM];
        double[][] tauCell = new double[4][NM];
        double[][] hcv = new double[4][NM];
        for (int si = 0; si < 4; si++) {
            double day = si * WorldContract.DAYS_PER_YEAR / 4.0;
            double th = Atmosphere.theta(day);
            for (int r = 0; r < NM; r++) {
                double s = 0, st = 0, sc = 0; int c = 0;
                for (int q = 0; q < NQ; q++) {
                    int i = r * NQ + q;
                    if (!ok[i]) continue;
                    for (int k = 1; k <= 3; k++) {
                        int xx = eastX[i] - k * 25_000;
                        int zz = eastZ[i];
                        s += Atmosphere.windStress(xx, zz, SEED, cell, th, GRAD)[1];
                        double[] pt = gradTherm(xx, zz, th);
                        double[] pc = gradCell(xx, zz, th);
                        double lat = WorldContract.latOf(zz);
                        st += Atmosphere.wind(pt[0], pt[1], kap[i], lat, th)[1];
                        sc += Atmosphere.wind(pc[0], pc[1], kap[i], lat, th)[1];
                        c++;
                    }
                }
                tau[si][r] = c > 0 ? s / c : 0;
                tauTherm[si][r] = c > 0 ? st / c : 0;
                tauCell[si][r] = c > 0 ? sc / c : 0;
            }
            hcv[si] = CoastalLayer.steadySmoothed(tau[si], (double) ZC / NM);
        }

        say(String.format(LF, "  %-8s %9s %9s %9s %9s %9s %9s %9s", "纬度", "tau d0", "tau d91", "tau d182", "tau d273", "h_c d0", "h_c d182", "向赤道d0/d182"));
        for (int r = 0; r < NM; r += 3) {
            int z = (int) ((r + 0.5) / NM * ZC);
            double latDeg = Math.toDegrees(WorldContract.latOf(z, ZC));
            double eq0 = latDeg >= 0 ? -tau[0][r] : tau[0][r];
            double eq2 = latDeg >= 0 ? -tau[2][r] : tau[2][r];
            say(String.format(LF, "  %-8.1f %9.5f %9.5f %9.5f %9.5f %9.1f %9.1f   %s / %s",
                latDeg, tau[0][r], tau[1][r], tau[2][r], tau[3][r], hcv[0][r], hcv[2][r],
                eq0 > 0 ? "向赤道" : "向极  ", eq2 > 0 ? "向赤道" : "向极  "));
        }
        say("");
        say("B. 分项（只热力梯度 / 只cell梯度 驱动出的 tau_y），lat 10~45 与 -45~-10 的均值");
        say(String.format(LF, "  %-8s %14s %14s %14s", "季节", "全量", "只热力", "只cell"));
        for (int si = 0; si < 4; si++) {
            double a = 0, b = 0, c = 0; int n = 0;
            for (int r = 0; r < NM; r++) {
                int z = (int) ((r + 0.5) / NM * ZC);
                double latDeg = Math.toDegrees(WorldContract.latOf(z, ZC));
                if (Math.abs(latDeg) < 10 || Math.abs(latDeg) > 45) continue;
                a += tau[si][r]; b += tauTherm[si][r]; c += tauCell[si][r]; n++;
            }
            say(String.format(LF, "  %-8s %14.5f %14.5f %14.5f", "day=" + (int)(si * WorldContract.DAYS_PER_YEAR / 4.0), a/n, b/n, c/n));
        }
        say("");
        say("C. 近岸 kappa 与海盆宽度");
        double ks = 0; int kn = 0, wn = 0; double ws = 0;
        for (int i = 0; i < NM * NQ; i++) { if (ok[i]) { ks += kap[i]; kn++; } }
        say(String.format(LF, "  近岸 50 km 处 kappa 均值 %.3f（n=%d，1=陆、0=洋；应当接近 0 才是「东岸近海」）", ks / Math.max(1, kn), kn));
        rep.close();
    }

    static double[] gradTherm(int x, int z, double th) {
        return new double[]{
            (pTherm(x + GRAD, z, th) - pTherm(x - GRAD, z, th)) / (2.0 * GRAD),
            (pTherm(x, z + GRAD, th) - pTherm(x, z - GRAD, th)) / (2.0 * GRAD)};
    }
    static double[] gradCell(int x, int z, double th) {
        return new double[]{
            (pCell(x + GRAD, z, th) - pCell(x - GRAD, z, th)) / (2.0 * GRAD),
            (pCell(x, z + GRAD, th) - pCell(x, z - GRAD, th)) / (2.0 * GRAD)};
    }
    static double pTherm(int x, int z, double th) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, SEED, cell);
        return -Atmosphere.K_P * Atmosphere.CHI * Atmosphere.seasonalAnomaly(lat, k, th);
    }
    static double pCell(int x, int z, double th) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, SEED, cell);
        return Atmosphere.cellPressure(lat, k, th);
    }

    static void say(String s) { System.out.println("[P289] " + s); rep.println("[P289] " + s); }
}
