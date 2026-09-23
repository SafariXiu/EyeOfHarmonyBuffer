package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P979 (SS675 step 1 alternative): replace `z_ct := H_BL = 1000 m` with the model's OWN cloud top.
 * ParcelLift.lift() returns zLnb = the level of neutral buoyancy, i.e. the physical cloud top
 * (SS498: 'the height where buoyancy is zero ... zero free parameters').
 * lift() has loops, so this is PROBE-ONLY (rule 6 constrains the production path, not measurements).
 *
 * Question: does using zLnb instead of H_BL bring the magnitude gate into [0.5, 2.0]?
 */
public class P979 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double E_P = 0.24, SIGMA_UP = 0.065;
    static final double MMD = 86400.0 * 1000.0;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    static double litRate(double tSfc, double q, double lat, double k, double vEff, double zCt) {
        if (!(zCt > 0.0)) return 0.0;
        double tD = ParcelLift.dewpoint(ParcelLift.vaporPressure(q, PrecipField.P_SURF));
        double tLcl = ParcelLift.lclTempDaviesJones(tSfc, tD);
        double pLcl = PrecipField.P_SURF * Math.pow(tLcl / tSfc, ParcelLift.CP_D / ParcelLift.R_D);
        double qLcl = ParcelLift.qs(tLcl, pLcl);
        double zLcl = ParcelLift.zOfP(pLcl, tSfc, Atmosphere.GAMMA);
        double pCt = ParcelLift.pOfZ(zCt, tSfc, Atmosphere.GAMMA);
        if (pCt >= pLcl) return 0.0;
        double tCt = tLcl - ParcelLift.gammaMoist(tLcl, pLcl, qLcl) * (zCt - zLcl);
        double dqs = qLcl - ParcelLift.qs(tCt, pCt);
        if (dqs <= 0.0) return 0.0;
        double w = PrecipField.wStarK(tSfc, PrecipField.qSat(tSfc), q, lat, k, vEff);
        return E_P * SIGMA_UP * Atmosphere.RHO_AIR * w * dqs / PrecipField.RHO_WATER;
    }

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p979_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        int nBadRt = 0;
        for (int ld = 5; ld <= 85; ld += 5) { if (Math.abs(Math.toDegrees(WorldContract.latOf(WorldContract.zOfLat(ld))) - ld) > 1e-3) nBadRt++; }
        say(rep, "P979: z_ct = zLnb (model's own cloud top) instead of H_BL");
        say(rep, String.format(LF, "  COORD_ROUNDTRIP=%s(bad=%d)  E_P=%.3f SIGMA_UP=%.3f H_BL=%.1f", nBadRt == 0 ? "PASS" : "FAIL", nBadRt, E_P, SIGMA_UP, Atmosphere.H_BL));
        String[] sn = {"JJA", "DJF"};
        double[][] best = new double[2][2]; double[][] at = new double[2][2];
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "  === " + sn[s]);
            say(rep, String.format(LF, "    %-6s %5s %8s %8s %8s %10s %10s %6s", "latN", "nPts", "zLcl", "zLnb", "conv%", "Plit(zLnb)", "Plit(H_BL)", "ratio"));
            best[s][0] = -1; best[s][1] = -1;
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = WorldContract.zOfLat(latDeg);
                double lat = WorldContract.latOf(z);
                double sA = 0, sB = 0, szL = 0, szN = 0; int n = 0, nConv = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k >= 0.02) continue;
                    PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    double q = PrecipField.DIAG.get()[5];
                    double tSfc = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                    ParcelLift.Result r = ParcelLift.lift(tSfc, q, Atmosphere.GAMMA, PrecipField.H_MOIST, 4000.0, 50.0);
                    double zN = r.convective ? r.zLnb : Double.NaN;
                    if (r.convective && zN > 0) nConv++;
                    sA += litRate(tSfc, q, lat, k, vEff, (zN > 0 ? zN : Atmosphere.H_BL)) * MMD;
                    sB += litRate(tSfc, q, lat, k, vEff, Atmosphere.H_BL) * MMD;
                    szL += r.zLcl; if (zN > 0) szN += zN;
                    n++;
                }
                if (n == 0) continue;
                double aA = sA / n, aB = sB / n;
                if (aA > best[s][0]) { best[s][0] = aA; at[s][0] = latDeg; }
                if (aB > best[s][1]) { best[s][1] = aB; at[s][1] = latDeg; }
                say(rep, String.format(LF, "    %-6.1f %5d %8.1f %8.1f %7.0f%% %10.3f %10.3f %6.3f",
                    latDeg, n, szL / n, nConv > 0 ? szN / nConv : Double.NaN, 100.0 * nConv / n, aA, aB, aA / Math.max(1e-12, aB)));
            }
        }
        say(rep, "");
        say(rep, "  GPCP OCEAN 0..25N anchors: JJA 7.958 @8.75   DJF 4.956 @6.25   ratio 1.606");
        for (int s = 0; s < 2; s++) {
            say(rep, String.format(LF, "  %s  Plit(zLnb) %.3f @%.1f  ->  ratio %.3f   |   Plit(H_BL) %.3f @%.1f  ->  ratio %.3f",
                sn[s], best[s][0], at[s][0], best[s][0] / (s == 0 ? 7.958 : 4.956), best[s][1], at[s][1], best[s][1] / (s == 0 ? 7.958 : 4.956)));
        }
        double rA = best[0][0] / 7.958, rB = best[0][1] / 7.958;
        say(rep, String.format(LF, "  GATE_ZCT_LNB=%s     JJA ratio with zLnb = %.3f", (rA >= 0.5 && rA <= 2.0) ? "PASS" : "FAIL", rA));
        say(rep, String.format(LF, "  GATE_ZCT_HBL=%s     JJA ratio with H_BL = %.3f  (previous run)", (rB >= 0.5 && rB <= 2.0) ? "PASS" : "FAIL", rB));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P979] " + s); rep.println("[P979] " + s); }
}