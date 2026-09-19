package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P624 -- 扫 80~91N x 全经度 x 4 季节，找 surfaceTemp 的非有限值。
public class P624 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P624] " + s); System.out.println("[P624] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p624_report.txt"), "UTF-8");
        say("P624: 极区 NaN 扫描（80~91N 全经度 4 季节）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double[] ths = {0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};
        String[] thn = {"JJA", "eq1", "DJF", "eq2"};
        say("");
        say("      纬度   z          kappaN  elevN    stN    saN    slN   （N = 非有限点数 / 288）");
        for (double latd = 80; latd <= 91; latd += 1.0) {
            int z = WorldContract.zOfLat(latd);
            int nK = 0, nE = 0, nS = 0, nA = 0, nL = 0, nTot = 0;
            double wMin = 1e9, wMax = -1e9;
            for (int t = 0; t < 4; t++) {
                double th = ths[t];
                for (int c = 0; c < 72; c++) {
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double k = Atmosphere.kappaMemo(x, z, sd, cell);
                    double e = PlateField.elevationWithCell(x, z, sd, cell);
                    double lat = WorldContract.latOf(z);
                    double sa = Atmosphere.seasonalAnomaly(lat, k, th);
                    double sl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z));
                    double st = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    if (!isFinite(k)) nK++;
                    if (!isFinite(e)) nE++;
                    if (!isFinite(st)) nS++;
                    if (!isFinite(sa)) nA++;
                    if (!isFinite(sl)) nL++;
                    nTot++;
                    double w = Math.cos(Math.toRadians(latd));
                    wMin = Math.min(wMin, w); wMax = Math.max(wMax, w);
                }
            }
            say(String.format(LF, "      %5.1f  %9d  %4d  %4d  %4d  %4d  %4d    (n=%d)", latd, z, nK, nE, nS, nA, nL, nTot));
            if (latd >= 89.5) say(String.format(LF, "             cos(lat) = %.6f  （负权重会让加权平均失真）", Math.cos(Math.toRadians(latd))));
        }
        say("");
        say("  [B] 90N 附近逐点（找第一个非有限的点）");
        int found = 0;
        for (double latd = 88; latd <= 91 && found < 8; latd += 0.5) {
            int z = WorldContract.zOfLat(latd);
            double lat = WorldContract.latOf(z);
            for (int c = 0; c < 72 && found < 8; c++) {
                int x = (int) Math.round((c + 0.5) * CIRC / 72);
                double st = Atmosphere.surfaceTemp(x, z, sd, cell, 0.0);
                if (!isFinite(st) || Math.abs(st) > 400) {
                    double k = Atmosphere.kappaMemo(x, z, sd, cell);
                    double e = PlateField.elevationWithCell(x, z, sd, cell);
                    say(String.format(LF, "      lat输入=%.1f z=%d latOf=%+.4f lon=%5.1f  kappa=%.4g elev=%.4g st=%.6g",
                        latd, z, lat, (c + 0.5) * 5.0, k, e, st));
                    found++;
                }
            }
        }
        if (found == 0) say("      88~91N 未发现非有限或 |st|>400 的点");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static boolean isFinite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
}
