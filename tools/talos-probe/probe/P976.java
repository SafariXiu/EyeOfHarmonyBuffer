package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P976 (SS670 item 5): is `kappa` a valid ocean mask?  SS647's 'the model ocean is too cold'
 * claim was based on P966 sampling with kappa<=0.5 and NO isLandWithCell check.
 * If those samples were actually LAND, then SS647 is my own caliber error, not a model defect.
 */
public class P976 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p976_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(rep, "P976: does kappa agree with PlateField.isLandWithCell?  (theta = January)");
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %6s %6s %9s %9s %11s %11s %11s %11s",
            "latN", "nLand", "nOcean", "k<0.02@Land", "k>0.98@Ocn", "T_land K", "T_ocean K", "T(k<.02) K", "T(k>.98) K"));
        for (int latDeg = 5; latDeg <= 65; latDeg += 5) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            int nL = 0, nO = 0, kLowOnLand = 0, kHighOnOcean = 0;
            double sTL = 0, sTO = 0; int cTL = 0, cTO = 0;
            double sKLow = 0, sKHigh = 0; int cKLow = 0, cKHigh = 0;
            for (int x = -23_000_000; x <= 23_000_000; x += 1_000_000) {
                boolean land = PlateField.isLandWithCell(x, z, SD, cell);
                double k = Atmosphere.kappaMemo(x, z, SD, cell);
                double T = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                if (land) { nL++; sTL += T; cTL++; if (k < 0.02) kLowOnLand++; }
                else { nO++; sTO += T; cTO++; if (k > 0.98) kHighOnOcean++; }
                if (k < 0.02) { sKLow += T; cKLow++; }
                if (k > 0.98) { sKHigh += T; cKHigh++; }
            }
            say(rep, String.format(LF, "  %-6d %6d %6d %9d %9d %11.2f %11.2f %11.2f %11.2f",
                latDeg, nL, nO, kLowOnLand, kHighOnOcean,
                cTL > 0 ? sTL / cTL : Double.NaN, cTO > 0 ? sTO / cTO : Double.NaN,
                cKLow > 0 ? sKLow / cKLow : Double.NaN, cKHigh > 0 ? sKHigh / cKHigh : Double.NaN));
        }
        say(rep, "");
        say(rep, "  Reading: if 'k<0.02@Land' is large, then kappa is NOT an ocean mask and any");
        say(rep, "  earlier 'ocean' sample that used kappa alone may have mixed in land.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P976] " + s); rep.println("[P976] " + s); }
}