package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P555 -- 冷瓦片成本的分解：那 ~2 s 到底是【气候瓦片】还是【海洋行】？
 *
 * 用户裁决(B)：把 SimClimate 冷瓦片解算变便宜。动手之前必须先量，
 * 因为 solveNode 里有一句 Atmosphere.sstAnom(x,z) --
 * 它在 SST provider 已装时会惰性解一条海洋行（文档化 ~11.6 s/行、最坏 34.8 s）。
 * 若 2 s 主要来自那里，优化 solveNode 的代数就是白干。
 */
public class P555 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P555] " + s); System.out.println("[P555] " + s); }
    static final int SEED = 1022228679;

    static double ms(long ns) { return ns / 1.0e6; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p555_report.txt"), "UTF-8");
        say("P555: 冷瓦片成本分解（src 零改动）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;

        // ---- A) 冷海洋行 ----
        OceanField.install(SEED);
        say("  接线: installedSeed=" + OceanField.installedSeed()
            + "  SST_PROVIDER=" + (Atmosphere.SST_PROVIDER != null));
        int NROW = 8;
        long t0 = System.nanoTime();
        for (int i = 0; i < NROW; i++) {
            int z = 200_000 + i * 4_000_000;
            Atmosphere.sstAnom(500_000, z);
        }
        double tRow = ms(System.nanoTime() - t0) / NROW;
        say(String.format(LF, "A) 冷海洋行: %d 行共 %.1f ms  =>  %.1f ms/行", NROW, tRow * NROW, tRow));

        // ---- B) 纯气候瓦片（海洋行此时已暖）----
        SimClimate.clearCache(); SimClimate.resetStats();
        int NT = 8;
        for (int i = 0; i < NT; i++) {
            int x = i * SimClimate.TILE_X + SimClimate.TILE_X / 2;
            int z = i * 500_000 + SimClimate.TILE_Z / 2;
            SimClimate.sample(x, z, SEED, null);
        }
        long sc = SimClimate.SOLVE_COUNT.get(), sn = SimClimate.SOLVE_NANOS.get(), nd = SimClimate.NODE_COUNT.get();
        say(String.format(LF, "B) 冷气候瓦片: solves=%d  nodes=%d  =>  %.1f ms/瓦片   %.2f ms/节点",
            sc, nd, sc > 0 ? ms(sn) / sc : -1, sc > 0 ? ms(sn) / nd : -1));

        // ---- C) 组件热点（全部在已暖的点上）----
        final int x = SimClimate.TILE_X / 2, z = SimClimate.TILE_Z / 2;
        final double th = Atmosphere.theta(0.0);
        say("C) 组件单测（已暖，单位 us/次）:");
        say(String.format(LF, "     kappaAt              %8.2f", bench(new F() { public double get() { return Atmosphere.kappaAt(x, z, sd, cell); } })));
        say(String.format(LF, "     elevationWithCell    %8.2f", bench(new F() { public double get() { return PlateField.elevationWithCell(x, z, sd, cell); } })));
        say(String.format(LF, "     sstAnom(已暖)        %8.2f", bench(new F() { public double get() { return Atmosphere.sstAnom(x, z); } })));
        say(String.format(LF, "     surfaceTemp          %8.2f", bench(new F() { public double get() { return Atmosphere.surfaceTemp(x, z, sd, cell, th); } })));
        say(String.format(LF, "     windAt               %8.2f", bench(new F() { public double get() { return Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP)[0]; } })));
        say(String.format(LF, "     coastDistanceNew(F)  %8.2f", bench(new F() { public double get() { return PlateField.coastDistanceNew(x, z, sd, cell, SimClimate.COAST_FINE); } })));
        say(String.format(LF, "     coastDistanceNew(C)  %8.2f", bench(new F() { public double get() { return PlateField.coastDistanceNew(x, z, sd, cell, SimClimate.COAST_FAR); } })));
        say(String.format(LF, "     upwindElev           %8.2f", bench(new F() { public double get() { return PrecipField.upwindElev(x, z, sd, cell, 3.0, 1.0); } })));
        say(String.format(LF, "     mmPerDay(已暖)       %8.2f", bench(new F() { public double get() { return PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP); } })));

        // ---- D) memo 的效果 ----
        say("D) memo 的效果（windAt 内部已走 kappaMemo；memo 关时每次重算 193 点环）:");
        final double k1 = bench(new F() { public double get() { return Atmosphere.kappaAt(x, z, sd, cell); } });
        boolean mc = Atmosphere.beginMemo();
        final double k2 = bench(new F() { public double get() { return Atmosphere.kappaMemo(x, z, sd, cell); } });
        Atmosphere.endMemo(mc);
        say(String.format(LF, "     kappaAt %.2f us   vs   kappaMemo(memo 开) %.2f us   => 省 %.2f us/次", k1, k2, k1 - k2));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    interface F { double get(); }
    static double bench(F f) {
        for (int i = 0; i < 2000; i++) f.get();
        int N = 20000;
        long t = System.nanoTime();
        for (int i = 0; i < N; i++) f.get();
        return (System.nanoTime() - t) / 1000.0 / N;
    }
}
