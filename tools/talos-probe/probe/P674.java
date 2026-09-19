package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P674 -- §467: RS_PASS 扫描。V̄ 序列【收敛】还是【振荡】？这是双稳 vs 唯一不动点的分水岭。
public class P674 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P674] " + s); System.out.println("[P674] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p674_report.txt"), "UTF-8");
        say("P674: RS_PASS 扫描（§467）—— V̄ 序列收敛还是振荡？");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SoilMoisture.ENABLED = true;
        Vegetation.ENABLED = true; Vegetation.RS_BARE = 150.0;
        SimClimate.clearCache(); SoilMoisture.invalidate(); Vegetation.invalidate();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        final int N = 24;
        int[] xs = new int[N], zs = new int[N];
        int n = 0;
        for (double latd = 20.0; latd <= 30.0 && n < N; latd += 2.5)
            for (int c = 14; c < 24 && n < N; c++) {
                int x = xOfLon((c + 0.5) * 5.0), z = zOfLat(latd);
                if (PlateField.isLandWithCell(x, z, sd, cell)) { xs[n] = x; zs[n] = z; n++; }
            }
        say("     采样陆地点 = " + n + "   RS_BARE = " + Vegetation.RS_BARE);
        say("");
        say("     RS_PASS | pass 数 | evalCount/点 | ms/点 | V̄ 序列（每个 pass 一个值）");
        for (int rp = 0; rp <= 3; rp++) {
            Vegetation.RS_PASS = rp;
            SoilMoisture.invalidate(); Vegetation.invalidate(); SimClimate.clearCache();
            SoilMoisture.spinupCount = 0; SoilMoisture.evalCount = 0; SoilMoisture.vHistN = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) SoilMoisture.betaAt(xs[i], zs[i], sd, cell, thS, GRAD);
            double ms = (System.nanoTime() - t0) / 1.0e6;
            StringBuilder sb = new StringBuilder();
            for (int q = 0; q < rp + 1 && q < 12; q++) sb.append(String.format(LF, "%.4f ", SoilMoisture.V_HIST[q]));
            say(String.format(LF, "     %7d | %7d | %11.1f | %5.3f | %s", rp, rp + 1,
                (double) SoilMoisture.evalCount / n, ms / n, sb.toString()));
        }
        say("");
        say("判读：序列若单调收敛 ⇒ 唯一不动点（准静态够用）；若两点间振荡 ⇒ 双稳。");
        Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0; Vegetation.RS_PASS = 1;
        Vegetation.invalidate(); SoilMoisture.ENABLED = false; SoilMoisture.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}