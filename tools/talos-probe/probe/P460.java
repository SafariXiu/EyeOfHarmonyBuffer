package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P460：**副热带干带（27.5~37.5 度）3.4 倍过干的归因拆解**。
 *
 * <p>观测锚（GPCP v2.2 LTM 1991-2020，等纬距口径）：27.5~37.5 度 夏 **2.301** / 冬 **2.391** mm/day。
 * 模型（P296，指纹 A0E9638E）：夏 **0.67** / 冬 **1.14** ⇒ **过干 70.9% / 52.3%**。
 *
 * <p>拆解口径（全部走生产入口，不手工重写）：
 * <pre>
 *   wZm_shifted = PrecipField.wEff(lat, theta, 0)            // divU=0 时只剩 w_zm(移位纬度)
 *   wLoc        = wEff(lat, theta, divU) - wZm_shifted
 *   P_total     = PrecipField.mmPerDay(..., withEddy=true)
 *   P_noEddy    = PrecipField.mmPerDay(..., withEddy=false)
 *   P_eddy      = P_total - P_noEddy
 *   gate        = PrecipField.stormGate(lat, theta)
 *   mfc         = PrecipField.eddyMfc(lat, theta)
 * </pre>
 * 对照带：45~55（已标定）、2.5~12.5（赤道带）。
 */
public class P460 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P460] " + s); System.out.println("[P460] " + s); }

    static int zOfLat(double latDeg) {
        return (int) Math.round(latDeg / 90.0 * (WorldContract.Z_CYCLE / 2));
    }

    static double[] band(String name, double lo, double hi, double theta, int nx) {
        double sP = 0, sPn = 0, sZm = 0, sLoc = 0, sGate = 0, sMfc = 0;
        int n = 0, nZero = 0, nMfcPos = 0, nLand = 0;
        double worst = 0;
        double lP = 0, oP = 0, lPn = 0, oPn = 0, lZm = 0, oZm = 0, lLoc = 0, oLoc = 0;
        int nL = 0, nO = 0, nLZero = 0, nOZero = 0, nLEddy = 0, nOEddy = 0;
        for (double latDeg = lo + 0.5; latDeg <= hi; latDeg += 1.0) {
            int z = zOfLat(latDeg);
            double lat = WorldContract.latOf(z);
            for (int c = 0; c < nx; c++) {
                int x = (int) ((long) c * (12_000_000L / nx)) - 6_000_000;
                double p = PrecipField.mmPerDay(x, z, SD, CELL, theta, GRAD, true);
                double pn = PrecipField.mmPerDay(x, z, SD, CELL, theta, GRAD, false);
                double[] u0 = Atmosphere.windAt(x, z, SD, CELL, theta, GRAD);
                double[] ux = Atmosphere.windAt(x + GRAD, z, SD, CELL, theta, GRAD);
                double[] uw = Atmosphere.windAt(x - GRAD, z, SD, CELL, theta, GRAD);
                double[] un = Atmosphere.windAt(x, z + GRAD, SD, CELL, theta, GRAD);
                double[] us = Atmosphere.windAt(x, z - GRAD, SD, CELL, theta, GRAD);
                double divU = (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);
                double wTot = PrecipField.wEff(lat, theta, divU);
                double wZm = PrecipField.wEff(lat, theta, 0.0);
                sP += p; sPn += pn; sZm += wZm; sLoc += (wTot - wZm);
                sGate += PrecipField.stormGate(lat, theta);
                double m = PrecipField.eddyMfc(lat, theta);
                sMfc += m;
                if (m > 0) nMfcPos++;
                if (wTot <= 0) nZero++;
                if (p > worst) worst = p;
                if (PlateField.isLandWithCell(x, z, SD, CELL)) {
                    nLand++; nL++; lP += p; lPn += pn; lZm += wZm; lLoc += (wTot - wZm);
                    if (wTot <= 0) nLZero++; if (p > pn + 1e-12) nLEddy++;
                } else {
                    nO++; oP += p; oPn += pn; oZm += wZm; oLoc += (wTot - wZm);
                    if (wTot <= 0) nOZero++; if (p > pn + 1e-12) nOEddy++;
                }
                n++;
            }
        }
        return new double[]{sP / n, sPn / n, (sP - sPn) / n, sZm / n, sLoc / n,
                            sGate / n, sMfc / n, (double) n, 100.0 * nZero / n, 100.0 * nMfcPos / n,
                            worst, 100.0 * nLand / n,
                            nL > 0 ? lP / nL : 0, nO > 0 ? oP / nO : 0,
                            nL > 0 ? lPn / nL : 0, nO > 0 ? oPn / nO : 0,
                            nL > 0 ? lZm / nL : 0, nO > 0 ? oZm / nO : 0,
                            nL > 0 ? lLoc / nL : 0, nO > 0 ? oLoc / nO : 0,
                            nL > 0 ? 100.0 * nLZero / nL : 0, nO > 0 ? 100.0 * nOZero / nO : 0,
                            nL > 0 ? 100.0 * nLEddy / nL : 0, nO > 0 ? 100.0 * nOEddy / nO : 0};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p460_report.txt"), "UTF-8");
        say("P460：副热带干带 27.5~37.5 度过干的归因拆解（观测锚 GPCP 2.301 夏 / 2.391 冬）");
        say(String.format(LF, "  EDDY_MIX=%.3f  W_LOC_MAX=%.3e  U0_STORM=%.2f  EDDY_DEPL_H=%.0f  gradStep=%d km",
            PrecipField.EDDY_MIX, PrecipField.W_LOC_MAX, PrecipField.U0_STORM, PrecipField.EDDY_DEPL_H, GRAD / 1000));
        say("");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] names = {"副热带 27.5~37.5", "中纬 45~55", "赤道 2.5~12.5"};
        double[][] bnd = {{27.5, 37.5}, {45.0, 55.0}, {2.5, 12.5}};
        double[][] gpcpS = {{2.301, 2.301}, {2.565, 2.565}, {6.585, 6.585}};
        double[][] gpcpW = {{2.391, 2.391}, {2.608, 2.608}, {3.580, 3.580}};
        int nx = 60;
        say(String.format(LF, "  %-16s %-6s %8s %8s %8s %9s %9s %8s %9s %8s %8s %7s %6s",
            "band", "season", "P", "P_noEddy", "P_eddy", "wZm", "wLoc", "gate", "mfc", "wEff<=0", "mfc>0", "max", "land"));
        for (int bi = 0; bi < 3; bi++) {
            for (int si = 0; si < 2; si++) {
                double th = si == 0 ? thS : thW;
                double[] r = band(names[bi], bnd[bi][0], bnd[bi][1], th, nx);
                say(String.format(LF, "  %-16s %-6s %8.3f %8.3f %8.3f %9.2e %9.2e %8.3f %9.2e %7.1f%% %7.1f%% %7.2f %5.0f%%",
                    names[bi], si == 0 ? "夏" : "冬", r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[8], r[9], r[10], r[11]));
            }
            double[] rS = band(names[bi], bnd[bi][0], bnd[bi][1], thS, nx);
            say(String.format(LF, "      ⇒ %s：夏 P=%.3f 对 GPCP %.3f（%+.1f%%）",
                names[bi], rS[0], gpcpS[bi][0], 100 * (rS[0] / gpcpS[bi][0] - 1)));
            say(String.format(LF, "         陆 %.3f（noEddy %.3f｜wZm %+.2e｜wLoc %+.2e｜wEff<=0 %.1f%%｜有涡动 %.1f%%）",
                rS[12], rS[14], rS[16], rS[18], rS[20], rS[22]));
            say(String.format(LF, "         海 %.3f（noEddy %.3f｜wZm %+.2e｜wLoc %+.2e｜wEff<=0 %.1f%%｜有涡动 %.1f%%）",
                rS[13], rS[15], rS[17], rS[19], rS[21], rS[23]));
        }
        say("");
        say("B. 副热带带内逐纬度（夏）");
        say(String.format(LF, "  %-7s %8s %8s %8s %9s %9s %8s %8s", "lat", "P", "P_noEddy", "P_eddy", "wZm", "wLoc", "gate", "mfc"));
        for (double la = 25.0; la <= 40.0; la += 2.5) {
            double[] r = band("x", la, la + 2.5, thS, 30);
            say(String.format(LF, "  %-7.1f %8.3f %8.3f %8.3f %9.2e %9.2e %8.3f %8.2e",
                la, r[0], r[1], r[2], r[3], r[4], r[5], r[6]));
        }
        say("");
        say("C. stormGate 与 zonal u_zm 的零线（决定涡动项在副热带是否被关掉）");
        for (double la = 20.0; la <= 50.0; la += 2.5) {
            say(String.format(LF, "  lat %5.1f   uZm(summer)=%+7.3f  uZm(winter)=%+7.3f   stormGate 夏 %.4f 冬 %.4f   mfc 夏 %+.3e 冬 %+.3e",
                la, ZonalTables.uZm(la, thS), ZonalTables.uZm(la, thW),
                PrecipField.stormGate(Math.toRadians(la), thS), PrecipField.stormGate(Math.toRadians(la), thW),
                PrecipField.eddyMfc(Math.toRadians(la), thS), PrecipField.eddyMfc(Math.toRadians(la), thW)));
        }
        rep.flush();
        say("DONE");
    }
}
