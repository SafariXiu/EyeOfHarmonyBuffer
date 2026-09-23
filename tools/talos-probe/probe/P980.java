package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P980 (SS669 step 1-2): the SS668 literature form, SPLIT BY MASK, with the degenerate-branch rate. */
public class P980 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double E_P = 0.24, SIGMA_UP = 0.30;   // SS667: the constant IS the updraught area fraction (Siebesma convention)
    static final double MMD = 86400.0 * 1000.0;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final String[] MN = {"OCEAN(k<0.02)", "LAND(k>0.98)", "ALL"};

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p975_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        // SS674 rule 2: round-trip assertion FIRST, tolerance matched to the integer-block resolution.
        int nBad = 0;
        for (int ld = 5; ld <= 85; ld += 5) {
            double rt = Math.toDegrees(WorldContract.latOf(WorldContract.zOfLat(ld)));
            if (Math.abs(rt - ld) > 1e-3) nBad++;
        }
        say(rep, "P980 v2: SS668 literature form split by mask + degenerate-branch (no cloud) rate");
        say(rep, String.format(LF, "  COORD_ROUNDTRIP=%s (bad=%d)  MAX_D=%d  Z_CYCLE=%d",
            nBad == 0 ? "PASS" : "FAIL", nBad, WorldContract.MAX_D, WorldContract.Z_CYCLE));
        say(rep, "  P = E_P*SIGMA_UP*RHO_AIR*w_* *(q*_LCL - q*_ct)/RHO_WATER ; z_ct = H_BL");
        String[] sn = {"JJA", "DJF"};
        for (int mi = 0; mi < 3; mi++) {
            say(rep, "");
            say(rep, "################ mask = " + MN[mi]);
            double[] best = new double[2], at = new double[2];
            int[] ngeo = new int[2];
            for (int s = 0; s < 2; s++) {
                double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
                say(rep, "  === " + sn[s]);
                say(rep, String.format(LF, "    %-7s %6s %10s %10s %10s %8s", "latN", "nPts", "P_lit", "oldSh", "dqs", "noCloud"));
                best[s] = -1;
                for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                    int z = WorldContract.zOfLat(latDeg);   // SS674: the correct converter
                    double lat = WorldContract.latOf(z);
                    double sLit = 0, sSh = 0, sD = 0; int n = 0, ndeg = 0;
                    for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                        double k = Atmosphere.kappaMemo(x, z, SD, cell);
                        if (mi == 0 && k >= 0.02) continue;
                        if (mi == 1 && k <= 0.98) continue;
                        double mm = PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                        double q = PrecipField.DIAG.get()[5];
                        double pSh = PrecipField.DIAG.get()[10] * MMD;
                        double tSfc = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                        double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                        double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                        double tD = ParcelLift.dewpoint(ParcelLift.vaporPressure(q, PrecipField.P_SURF));
                        double tLcl = ParcelLift.lclTempDaviesJones(tSfc, tD);
                        double pLcl = PrecipField.P_SURF * Math.pow(tLcl / tSfc, ParcelLift.CP_D / ParcelLift.R_D);
                        double qLcl = ParcelLift.qs(tLcl, pLcl);
                        double zLcl = ParcelLift.zOfP(pLcl, tSfc, Atmosphere.GAMMA);
                        double pCt = ParcelLift.pOfZ(Atmosphere.H_BL, tSfc, Atmosphere.GAMMA);
                        double tCt = tLcl - ParcelLift.gammaMoist(tLcl, pLcl, qLcl) * (Atmosphere.H_BL - zLcl);
                        double dqs = qLcl - ParcelLift.qs(tCt, pCt);
                        double w = PrecipField.wStarK(tSfc, PrecipField.qSat(tSfc), q, lat, k, vEff);
                        double lit = (pCt >= pLcl || dqs <= 0.0) ? 0.0
                                   : E_P * SIGMA_UP * Atmosphere.RHO_AIR * w * dqs / PrecipField.RHO_WATER * MMD;
                        if (pCt >= pLcl || dqs <= 0.0) ndeg++;
                        sLit += lit; sSh += pSh; sD += dqs; n++;
                    }
                    if (n == 0) continue;
                    ngeo[s] += ndeg;
                    double aLit = sLit / n;
                    if (aLit > best[s]) { best[s] = aLit; at[s] = latDeg; }
                    say(rep, String.format(LF, "    %-7.1f %6d %10.3f %10.3f %10.6f %4d/%d", latDeg, n, aLit, sSh / n, sD / n, ndeg, n));
                }
                say(rep, String.format(LF, "    ARGMAX %.3f mm/day @ %.1f N", best[s], at[s]));
            }
            say(rep, String.format(LF, "  mask=%s : JJA argmax %.3f  DJF argmax %.3f  seasonRatio %.3f", MN[mi], best[0], best[1], best[0] / Math.max(1e-12, best[1])));
            say(rep, String.format(LF, "            noCloud samples: JJA %d  DJF %d", ngeo[0], ngeo[1]));
        }
        say(rep, "");
        say(rep, "  SIGMA_UP = 0.30 (Siebesma convention; SS667) instead of 0.065 (Pergaud, near-surface)");
        say(rep, "  GPCP anchors (0..25N, from refs/_eqpeak_masked.py):");
        say(rep, "    LAND+OCEAN  JJA 7.754 @8.75   DJF 4.805 @3.75   ratio 1.614");
        say(rep, "    OCEAN       JJA 7.958 @8.75   DJF 4.956 @6.25   ratio 1.606");
        say(rep, "    LAND        JJA 8.368 @6.25   DJF 4.837 @1.25   ratio 1.730");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P980] " + s); rep.println("[P980] " + s); }
}