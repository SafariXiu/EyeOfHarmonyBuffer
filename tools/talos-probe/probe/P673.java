package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P673 -- §466 验收：pass 循环是否【零额外自旋】，以及 V 的残差与代价。
public class P673 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P673] " + s); System.out.println("[P673] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p673_report.txt"), "UTF-8");
        say("P673: §466 验收（零额外自旋？V 残差？代价？）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SoilMoisture.ENABLED = true;
        SimClimate.clearCache(); SoilMoisture.invalidate();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        final int N = 40;
        int[] xs = new int[N], zs = new int[N];
        int n = 0;
        for (double latd = 17.5; latd <= 32.5 && n < N; latd += 2.5)
            for (int c = 14; c < 24 && n < N; c++) {
                int x = xOfLon((c + 0.5) * 5.0), z = zOfLat(latd);
                if (PlateField.isLandWithCell(x, z, sd, cell)) { xs[n] = x; zs[n] = z; n++; }
            }
        String[] nm = {"A 基线（Vegetation 关）", "B Vegetation 开 + RS_BARE=150 + RS_PASS=1"};
        double[][] cfg = {{0.0, 0.0}, {1.0, 150.0}};
        for (int ci = 0; ci < 2; ci++) {
            Vegetation.ENABLED = cfg[ci][0] > 0.5;
            Vegetation.RS_BARE = cfg[ci][1];
            Vegetation.invalidate(); SoilMoisture.invalidate(); SimClimate.clearCache();
            SoilMoisture.spinupCount = 0; SoilMoisture.evalCount = 0; SoilMoisture.SPINUP_NANOS = 0;
            SoilMoisture.refreshUsed = 0;
            long t0 = System.nanoTime();
            double sV = 0, sVr = 0; int nv = 0;
            for (int i = 0; i < n; i++) {
                double b = SoilMoisture.betaAt(xs[i], zs[i], sd, cell, thS, GRAD);
                sV += SoilMoisture.lastV; sVr += SoilMoisture.lastVResid; nv++;
            }
            double ms = (System.nanoTime() - t0) / 1.0e6;
            say("");
            say("  --- " + nm[ci] + " ---");
            say(String.format(LF, "     spinupCount=%d  evalCount=%d (%.1f/点)  自旋累计=%.1f ms  墙钟=%.1f ms (%.3f ms/点)",
                SoilMoisture.spinupCount, SoilMoisture.evalCount, (double) SoilMoisture.evalCount / n,
                SoilMoisture.SPINUP_NANOS / 1.0e6, ms, ms / n));
            say(String.format(LF, "     V̄ 均值=%.4f  V 残差均值=%.4f  refreshUsed=%d",
                sV / nv, sVr / nv, SoilMoisture.refreshUsed));
        }
        Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0; Vegetation.invalidate();
        SoilMoisture.ENABLED = false; SoilMoisture.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}