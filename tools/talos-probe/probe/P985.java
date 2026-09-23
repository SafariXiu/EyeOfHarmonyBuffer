package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P985: a FULLY-CITED parameter set.  E_P = 0.24 (Liu et al. 2024 Fig.3D);
 *  sigma_up = 0.065 (Pergaud et al. 2009, LES area fraction);
 *  z_ct = 2500 m (Squires 1958 / Byers & Hall 1955 via Rauber et al. BAMS 88(12) 1913:
 *  'maritime clouds with tops greater than 2500 m usually rain within half an hour'). */
public class P985 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final double E_P = 0.24;
    static final double MMD = 86400.0 * 1000.0;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    static double litRate(double tSfc, double q, double lat, double k, double vEff, double zCt, double sig) {
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
        return E_P * sig * Atmosphere.RHO_AIR * w * dqs / PrecipField.RHO_WATER;
    }

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p985_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double[] sigs = {0.065, 0.30};
        double[] zcts = {2000.0, 2500.0, 3000.0, 3500.0};
        say(rep, "P985: fully-cited parameter sets  (E_P=0.24, z_ct from Squires 1958, sigma_up from Pergaud 2009)");
        say(rep, "  anchor(OCEAN): JJA 7.958  DJF 4.956  ratio 1.606   window [0.5,2.0]");
        say(rep, "");
        say(rep, String.format(LF, "  %-8s %-8s %10s %10s %9s %9s %6s %6s", "sigma_up", "z_ct", "JJA peak", "DJF peak", "rJJA", "rSEA", "MAG", "SEA"));
        for (double sig : sigs) {
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
                            sum += litRate(tSfc, q, lat, k, vEff, zCt, sig) * MMD; n++;
                        }
                        if (n > 0 && sum / n > peak[s]) peak[s] = sum / n;
                    }
                }
                double rj = peak[0] / 7.958;
                double rs = (peak[0] / Math.max(1e-12, peak[1])) / 1.606;
                say(rep, String.format(LF, "  %-8.3f %-8.0f %10.3f %10.3f %9.3f %9.3f %6s %6s",
                    sig, zCt, peak[0], peak[1], rj, rs,
                    (rj >= 0.5 && rj <= 2.0) ? "PASS" : "FAIL", (rs >= 0.5 && rs <= 2.0) ? "PASS" : "FAIL"));
            }
            say(rep, "");
        }
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P985] " + s); rep.println("[P985] " + s); }
}