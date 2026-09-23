package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P984 (SS676/SS685 follow-up): is the magnitude gate ROBUST to z_ct?
 *  z_ct cannot come from the model (GAMMA=6.5 K/km exceeds the moist adiabat => the parcel is
 *  buoyant to the top; the model has no trade-wind inversion).  So the honest test is whether
 *  the gate outcome is insensitive across the literature range of shallow-cumulus cloud tops. */
public class P984 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final double E_P = 0.24, SIGMA_UP = 0.30;
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
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p984_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double[] zcts = {1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 4000.0};
        say(rep, "P984: robustness of the magnitude gate to z_ct (ocean 0..25N, production inputs)");
        say(rep, "  anchor(OCEAN): JJA 7.958  DJF 4.956  ratio 1.606   gate window [0.5, 2.0]");
        say(rep, "");
        say(rep, String.format(LF, "  %-8s %10s %10s %10s %10s %10s %10s", "z_ct", "JJA argmax", "DJF argmax", "ratio_JJA", "season", "MAG", "SEA"));
        for (double zCt : zcts) {
            double[] peak = new double[2];
            for (int s = 0; s < 2; s++) {
                double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
                peak[s] = -1;
                for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                    int z = WorldContract.zOfLat(latDeg);
                    double lat = WorldContract.latOf(z);
                    double sum = 0; int n = 0;
                    for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                        double k = Atmosphere.kappaMemo(x, z, SD, cell);
                        if (k >= 0.02) continue;
                        PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                        double q = PrecipField.DIAG.get()[5];
                        double tSfc = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                        double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                        double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                        sum += litRate(tSfc, q, lat, k, vEff, zCt) * MMD; n++;
                    }
                    if (n > 0 && sum / n > peak[s]) peak[s] = sum / n;
                }
            }
            double rj = peak[0] / 7.958;
            double rs = (peak[0] / Math.max(1e-12, peak[1])) / 1.606;
            say(rep, String.format(LF, "  %-8.0f %10.3f %10.3f %10.3f %10.3f %10s %10s",
                zCt, peak[0], peak[1], rj, peak[0] / Math.max(1e-12, peak[1]),
                (rj >= 0.5 && rj <= 2.0) ? "PASS" : "FAIL", (rs >= 0.5 && rs <= 2.0) ? "PASS" : "FAIL"));
        }
        say(rep, "");
        say(rep, "  Reading: if MAG is PASS across the literature cloud-top range (1500..2500 m), the free");
        say(rep, "  parameter is DEMONSTRABLY immaterial to the gate and the item can be closed honestly.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P984] " + s); rep.println("[P984] " + s); }
}