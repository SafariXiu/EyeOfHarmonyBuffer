package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P672 -- §463: 层 3 的【预算判据】。一次完整桶自旋的世界-点代价是多少？
public class P672 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P672] " + s); System.out.println("[P672] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p672_report.txt"), "UTF-8");
        say("P672: 层 3 的预算判据（一次桶自旋的世界-点代价）");
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
        say("     采样陆地点数 = " + n);
        // A) 单次 betaAt（冷）—— 它就是一次完整桶自旋
        SoilMoisture.invalidate();
        SoilMoisture.spinupCount = 0; SoilMoisture.SPINUP_NANOS = 0;
        for (int i = 0; i < n; i++) SoilMoisture.betaAt(xs[i], zs[i], sd, cell, thS, GRAD);
        double spMs = SoilMoisture.SPINUP_NANOS / 1.0e6;
        say(String.format(LF, "     A) betaAt 冷启动：自旋次数=%d  自旋累计=%.1f ms  单次自旋=%.3f ms",
            SoilMoisture.spinupCount, spMs, spMs / Math.max(1, SoilMoisture.spinupCount)));
        // B) 单次 mmPerDay（冷，S3 打开 ⇒ 内部含一次自旋）
        SoilMoisture.invalidate(); SimClimate.clearCache();
        long t0 = System.nanoTime();
        double s = 0;
        for (int i = 0; i < n; i++) s += PrecipField.mmPerDay(xs[i], zs[i], sd, cell, thS, GRAD);
        double mmMs = (System.nanoTime() - t0) / 1.0e6;
        say(String.format(LF, "     B) mmPerDay 冷启动：%d 点共 %.1f ms  单点=%.3f ms  （盒均 P=%.3f）",
            n, mmMs, mmMs / n, s / n));
        double one = spMs / Math.max(1, SoilMoisture.spinupCount);
        say("");
        say(String.format(LF, "     判据：层 3 若用【不动点迭代】VEG_ITER=3 ⇒ 每陆地点多 3 次自旋 = +%.1f ms/点 = 单点 mmPerDay 的 %.0f%%",
            3 * one, 100.0 * 3 * one / (mmMs / n)));
        say(String.format(LF, "           层 3 若用【准静态一次前向代入】 ⇒ +1 次自旋 = +%.1f ms/点 = 单点 mmPerDay 的 %.0f%%",
            one, 100.0 * one / (mmMs / n)));
        SoilMoisture.ENABLED = false;
        SimClimate.clearCache(); SoilMoisture.invalidate();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}