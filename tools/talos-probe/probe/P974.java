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
 * P974 (SS668): the FULL literature form, O(1), same caliber as the GPCP anchors.
 *
 *   P = E_P * SIGMA_UP * RHO_AIR * w_* * (q*_LCL - q*_ct) / RHO_WATER
 *
 *   q*_LCL via the dry-adiabat LCL pressure: p_LCL = P_SURF*(t_LCL/tSfc)^(CP_D/R_D)
 *   everything from ParcelLift's O(1) entries (lclTempDaviesJones / qs(T,p) / pOfZ / zOfP / gammaMoist)
 *   NO loops, NO integration, NO new cache (rule 6).
 *
 * Caliber (SS636 four elements) aligned with refs/_eqpeak_gpcp.py BY DESIGN:
 *   window = ALL longitudes (land+ocean), band 0..25N ; level = surface precipitation;
 *   phase = JJA / DJF ; statistic = argmax of the zonal-mean profile.
 * Anchors: GPCP 0..25 JJA peak = 7.754 @8.75, DJF = 4.805 @3.75 (ratio 1.614).
 *
 * Sources: E_P = 0.24 (SS666, read off Fig.3D of Liu et al. 2024, range 0.19..0.29);
 *          SIGMA_UP = 0.065 (Pergaud et al. 2009, LES -- defined near the surface, not at the LCL);
 *          M = sigma_up*rho*w_up (Grant 2001 / Soares 2004 form, SS667).
 */
public class P974 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double E_P = 0.24, SIGMA_UP = 0.065;
    static final double MMD = 86400.0 * 1000.0;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    /** O(1) literature-form precipitation rate [m/s]. Returns 0 when the cloud top is below the LCL. */
    static double litRate(double tSfc, double q, double lat, double k, double vEff) {
        double e = ParcelLift.vaporPressure(q, PrecipField.P_SURF);
        double tD = ParcelLift.dewpoint(e);
        double tLcl = ParcelLift.lclTempDaviesJones(tSfc, tD);
        double pLcl = PrecipField.P_SURF * Math.pow(tLcl / tSfc, ParcelLift.CP_D / ParcelLift.R_D);
        double qLcl = ParcelLift.qs(tLcl, pLcl);
        double zLcl = ParcelLift.zOfP(pLcl, tSfc, Atmosphere.GAMMA);
        double zCt = Atmosphere.H_BL;
        double pCt = ParcelLift.pOfZ(zCt, tSfc, Atmosphere.GAMMA);
        if (pCt >= pLcl) return 0.0;                       // cloud top below LCL => no cloud
        double tCt = tLcl - ParcelLift.gammaMoist(tLcl, pLcl, qLcl) * (zCt - zLcl);
        double qCt = ParcelLift.qs(tCt, pCt);
        double dqs = qLcl - qCt;
        if (dqs <= 0.0) return 0.0;
        double w = PrecipField.wStarK(tSfc, PrecipField.qSat(tSfc), q, lat, k, vEff);
        return E_P * SIGMA_UP * Atmosphere.RHO_AIR * w * dqs / PrecipField.RHO_WATER;
    }

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p974_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        say(rep, "P974: literature form  P = E_P*sigma_up*rho*w_* *(q*_LCL - q*_ct) / rho_w");
        say(rep, String.format(LF, "  E_P=%.3f  SIGMA_UP=%.3f  H_BL=%.1f  GAMMA=%.6f", E_P, SIGMA_UP, Atmosphere.H_BL, Atmosphere.GAMMA));
        double[] best = new double[2]; double[] at = new double[2];
        int nNegQc = 0, nTot = 0;
        String[] sn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "=== " + sn[s] + "  (all longitudes, band 0..25N) ===");
            say(rep, String.format(LF, "  %-7s %6s %11s %11s %11s %11s", "latN", "nPts", "P_lit", "oldSh", "wStar", "dqs"));
            best[s] = -1; at[s] = 0;
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = (int) (latDeg / 90.0 * (ZC / 2));
                double lat = WorldContract.latOf(z);
                double sLit = 0, sSh = 0, sW = 0, sD = 0; int n = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    double mm = PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    double q = PrecipField.DIAG.get()[5];
                    double pSh = PrecipField.DIAG.get()[10] * MMD;
                    double tSfc = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                    double e = ParcelLift.vaporPressure(q, PrecipField.P_SURF);
                    double tD = ParcelLift.dewpoint(e);
                    double tLcl = ParcelLift.lclTempDaviesJones(tSfc, tD);
                    double pLcl = PrecipField.P_SURF * Math.pow(tLcl / tSfc, ParcelLift.CP_D / ParcelLift.R_D);
                    double qLcl = ParcelLift.qs(tLcl, pLcl);
                    double zLcl = ParcelLift.zOfP(pLcl, tSfc, Atmosphere.GAMMA);
                    double pCt = ParcelLift.pOfZ(Atmosphere.H_BL, tSfc, Atmosphere.GAMMA);
                    double tCt = tLcl - ParcelLift.gammaMoist(tLcl, pLcl, qLcl) * (Atmosphere.H_BL - zLcl);
                    double dqs = qLcl - ParcelLift.qs(tCt, pCt);
                    if (dqs <= 0) nNegQc++;
                    double w = PrecipField.wStarK(tSfc, PrecipField.qSat(tSfc), q, lat, k, vEff);
                    sLit += litRate(tSfc, q, lat, k, vEff) * MMD;
                    sSh += pSh; sW += w; sD += dqs; n++; nTot++;
                }
                if (n == 0) continue;
                double aLit = sLit / n;
                if (aLit > best[s]) { best[s] = aLit; at[s] = latDeg; }
                say(rep, String.format(LF, "  %-7.1f %6d %11.3f %11.3f %11.3f %11.6f", latDeg, n, aLit, sSh / n, sW / n, sD / n));
            }
            say(rep, String.format(LF, "  ARGMAX:  P_lit = %.3f mm/day @ %.1f N", best[s], at[s]));
        }
        say(rep, "");
        double seasonModel = best[0] / Math.max(1e-12, best[1]);
        double rMag = best[0] / 7.754, rSea = seasonModel / (7.754 / 4.805);
        say(rep, String.format(LF, "  GPCP anchor:  JJA 7.754 @8.75   DJF 4.805 @3.75   seasonal ratio 1.614"));
        say(rep, String.format(LF, "  model      :  JJA %.3f @%.1f  DJF %.3f @%.1f   seasonal ratio %.3f", best[0], at[0], best[1], at[1], seasonModel));
        say(rep, String.format(LF, "  GATE_LIT_MAGNITUDE=%s   ratio JJA/JPCP = %.3f  (must be in [0.5, 2.0])",
            (rMag >= 0.5 && rMag <= 2.0) ? "PASS" : "FAIL", rMag));
        say(rep, String.format(LF, "  GATE_LIT_SEASONAL=%s    ratio of seasonal ratios = %.3f  (must be in [0.5, 2.0])",
            (rSea >= 0.5 && rSea <= 2.0) ? "PASS" : "FAIL", rSea));
        say(rep, String.format(LF, "  GATE_LIT_QC_POS=%s      dqs > 0 at %d/%d samples", nNegQc == 0 ? "PASS" : "FAIL", nTot - nNegQc, nTot));
        say(rep, "  GATE_LIT_ZEROCOST=REVIEW  code path uses only ParcelLift O(1) entries (no lift(), no moistAdiabatT)");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P974] " + s); rep.println("[P974] " + s); }
}