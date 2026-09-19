package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P627 -- 装上真正的 SST provider（我此前所有地球掩膜探针都漏了这一步）。
public class P627 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P627] " + s); System.out.println("[P627] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p627_report.txt"), "UTF-8");
        say("P627: 装上 SST provider 之后的 sstAnom 量级");
        EarthRef.install();
        int wi = (int) SimTerrain.seedOf(SEED);
        OceanField.install(wi);                    // <- 我此前漏掉的一步
        SimClimate.clearCache();
        say(String.format(LF, "  OceanField.ENABLED=%s  worldSeedInt=%d", OceanField.ENABLED, wi));
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("");
        say("  纬度  点(仅海洋)  均值K    RMS K    最小K    最大K    solveCount");
        long t0 = System.nanoTime();
        for (double latd = -20; latd <= 20; latd += 5) {
            int z = zOfLat(latd);
            double s = 0, s2 = 0, mn = 1e30, mx = -1e30; long n = 0;
            for (int c = 0; c < 72; c++) {
                int x = (int) Math.round((c + 0.5) * CIRC / 72);
                if (Atmosphere.kappaMemo(x, z, sd, cell) > 0.2) continue;
                double a = Atmosphere.sstAnom(x, z);
                if (Double.isNaN(a)) continue;
                s += a; s2 += a * a; mn = Math.min(mn, a); mx = Math.max(mx, a); n++;
            }
            if (n == 0) { say(String.format(LF, "  %4.0f  (无海洋点)", latd)); continue; }
            double m = s / n;
            say(String.format(LF, "  %4.0f  %6d   %+7.2f  %7.2f  %+7.2f  %+7.2f   %d",
                latd, n, m, Math.sqrt(s2 / n - m * m), mn, mx, OceanField.solveCount));
        }
        say(String.format(LF, "  耗时 %d ms", (System.nanoTime() - t0) / 1_000_000));
        say("");
        say("  参考：热带太平洋 SST 距平观测幅度约 ±1~3 K。");
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
