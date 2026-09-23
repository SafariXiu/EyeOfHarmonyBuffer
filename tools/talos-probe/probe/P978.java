package probe;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P978 (SS673 rule 1): nail the coordinate layer.  Round-trip lat <-> z, and show that the
 *  formula used by a whole family of earlier probes (lat/90*(Z_CYCLE/2)) is off by 2x. */
public class P978 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p978_report.txt"), "UTF-8");
        say(rep, "P978: coordinate contract self-check");
        say(rep, String.format(LF, "  MAX_D=%d   Z_CYCLE=%d   Z_CYCLE/2=%d   (Z_CYCLE/2 == 2*MAX_D ? %s)",
            WorldContract.MAX_D, WorldContract.Z_CYCLE, WorldContract.Z_CYCLE / 2,
            (WorldContract.Z_CYCLE / 2 == 2 * WorldContract.MAX_D) ? "YES" : "NO"));
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %10s %12s %12s %12s %12s", "latDeg", "zOfLat", "latOf(zOfLat)", "lat/90*MAXD", "latOf(that)", "lat/90*(ZC/2)"));
        int nOk = 0, nBad = 0, nWrong = 0;
        for (int latDeg = 5; latDeg <= 85; latDeg += 5) {
            int zGood = WorldContract.zOfLat(latDeg);
            int zMine = (int) ((double) latDeg / 90.0 * (WorldContract.Z_CYCLE / 2));
            int zDirect = (int) ((double) latDeg / 90.0 * WorldContract.MAX_D);
            double rtGood = Math.toDegrees(WorldContract.latOf(zGood));
            double rtDirect = Math.toDegrees(WorldContract.latOf(zDirect));
            double rtMine = Math.toDegrees(WorldContract.latOf(zMine));
            boolean ok = Math.abs(rtGood - latDeg) < 1e-6;
            if (ok) nOk++; else nBad++;
            if (Math.abs(rtMine - latDeg) > 1e-6) nWrong++;
            say(rep, String.format(LF, "  %-6d %10d %12.4f %12d %12.4f %12d  (latOf = %.2f)",
                latDeg, zGood, rtGood, zDirect, rtDirect, zMine, rtMine));
        }
        say(rep, "");
        say(rep, String.format(LF, "  latOf(zOfLat(lat)) == lat : %d/%d  (bad %d)", nOk, nOk + nBad, nBad));
        say(rep, String.format(LF, "  latOf(lat/90*(ZC/2)) == lat : %d/%d  (WRONG in %d cases)", (nOk + nBad) - nWrong, nOk + nBad, nWrong));
        say(rep, "");
        say(rep, String.format(LF, "  GATE_COORD_ROUNDTRIP=%s   (WorldContract.zOfLat is the correct converter)",
            (nBad == 0) ? "PASS" : "FAIL"));
        say(rep, String.format(LF, "  GATE_COORD_OLDWRONG=%s    (the ZC/2 formula is wrong in %d/%d cases)",
            (nWrong == nOk + nBad) ? "PASS" : "FAIL", nWrong, nOk + nBad));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P978] " + s); rep.println("[P978] " + s); }
}