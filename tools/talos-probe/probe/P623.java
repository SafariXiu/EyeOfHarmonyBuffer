package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P623 -- 极区诊断：surfaceTemp 在 85N/90N 出现 NaN 与 6.5e11 K。
public class P623 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P623] " + s); System.out.println("[P623] " + s); }
    static final int SEED = 1022228679;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p623_report.txt"), "UTF-8");
        say("P623: 极区诊断");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say("");
        say("  [A] zOfLat / latOf 在极点的往返");
        for (double latd : new double[]{80, 84, 86, 88, 89, 89.5, 89.9, 90}) {
            int z = WorldContract.zOfLat(latd);
            double back = Math.toDegrees(WorldContract.latOf(z));
            say(String.format(LF, "      lat=%5.1f  z=%9d  latOf(z)=%9.4f 度  (z/MAX_D=%.6f)", latd, z, back, z / (double) WorldContract.MAX_D));
        }
        say("");
        say("  [B] 90N 附近逐点分解（经度 0/90/180/270）");
        say("      纬度  经度   kappa     elev(m)     海平面T(K)   季节(K)     surfaceTemp(K)");
        for (double latd : new double[]{85, 88, 89, 89.9, 90}) {
            int z = WorldContract.zOfLat(latd);
            for (int lon : new int[]{0, 90, 180, 270}) {
                int x = (int) Math.round(lon / 360.0 * 40_000_000.0);
                double k = Atmosphere.kappaMemo(x, z, sd, cell);
                double e = PlateField.elevationWithCell(x, z, sd, cell);
                double lat = WorldContract.latOf(z);
                double sl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z));
                double sa = Atmosphere.seasonalAnomaly(lat, k, th);
                double st = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                say(String.format(LF, "      %5.1f  %4d  %.3f  %10.1f  %12.4g  %12.4g  %12.4g", latd, lon, k, e, sl, sa, st));
            }
        }
        say("");
        say("  [C] kappaMemo / elevation 在极点的返回");
        for (double latd : new double[]{85, 89, 90}) {
            int z = WorldContract.zOfLat(latd);
            int x = 0;
            double kk = Atmosphere.kappaMemo(x, z, sd, cell);
            double ev = PlateField.elevationWithCell(x, z, sd, cell);
            say(String.format(LF, "      lat=%4.1f  kappa=%.6g  elev=%.6g", latd, kk, ev));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
