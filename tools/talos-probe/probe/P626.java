package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P626 -- sstAnom 的量级：热带 SST 距平的观测幅度是 ±1~3 K，任何 ±10 K 都是错的。
public class P626 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P626] " + s); System.out.println("[P626] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p626_report.txt"), "UTF-8");
        say("P626: sstAnom 的量级（地球掩膜 + ETOPO1）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("");
        say("  纬度  点(仅海洋)  均值K    RMS K    最小K    最大K");
        for (double latd = -20; latd <= 20; latd += 5) {
            int z = zOfLat(latd);
            double s = 0, s2 = 0, mn = 1e30, mx = -1e30; long n = 0;
            for (int c = 0; c < 72; c++) {
                int x = (int) Math.round((c + 0.5) * CIRC / 72);
                if (Atmosphere.kappaMemo(x, z, sd, cell) > 0.2) continue;   // 只取海洋
                double a = Atmosphere.sstAnom(x, z);
                if (Double.isNaN(a)) continue;
                s += a; s2 += a * a; mn = Math.min(mn, a); mx = Math.max(mx, a); n++;
            }
            if (n == 0) { say(String.format(LF, "  %4.0f  (无海洋点)", latd)); continue; }
            double m = s / n;
            say(String.format(LF, "  %4.0f  %6d   %+7.2f  %7.2f  %+7.2f  %+7.2f", latd, n, m, Math.sqrt(s2 / n - m * m), mn, mx));
        }
        say("");
        say("  参考：热带太平洋 SST 距平的观测幅度约 ±1~3 K（暖池/冷舌偶极）");
        say("        ⇒ 若模型给 ±10 K 量级，则赤道辐合强 13.6 倍就有了直接来源。");
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
