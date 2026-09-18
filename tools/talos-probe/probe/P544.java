package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalClimate;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P544 —— 第 4 段适配器验证：`GlobalClimate.sample` 现在从**新链**取数。
 *
 * <p>为什么必须用探针验：`CommandTalosMap` 是**游戏内命令**，在本环境里跑不起来。
 * 但适配器的正确性除了「渲染那一层」之外全部可验 —— 本探针直接调 `sample`，
 * 检查四条**只有接到新链才会成立**的不变量。
 */
public class P544 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P544] " + s); System.out.println("[P544] " + s); }

    static final int SEED = 1022228679;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p544_report.txt"), "UTF-8");
        say("P544：第 4 段 —— GlobalClimate.sample 适配器验证（新链取数）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  MAX_D=" + WorldContract.MAX_D
            + "  R_EFF=" + String.format(LF, "%.1f", WorldContract.R_EFF / 1000.0) + " km");
        say("");

        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;

        int[][] pts = {
            {0, 0}, {0, 5_000_000}, {0, 10_000_000}, {0, 20_000_000},
            {0, 30_000_000}, {1_000_000, 3_000_000}, {-1_000_000, 7_000_000}, {123_456, 25_000_000}};

        say("=== A. 逐点采样（15 个字段全打印）===");
        say("        x            z    isLand  coastDist      bandD     windX     windZ      dry     rain     gyre  airMass     airT        q     curX      sst    speed");
        boolean inv = true;
        double tMax = 0;
        for (int[] p : pts) {
            long t0 = System.nanoTime();
            ClimateSample s = GlobalClimate.sample(p[0], p[1], SEED);
            double ms = (System.nanoTime() - t0) / 1e6;
            if (ms > tMax) tMax = ms;
            say(String.format(LF, "  %9d %12d    %5s %10.1f %10.6f %9.3f %9.3f %8.3f %8.3f %8.3f  %7s %8.3f %8.3f %8.4f %8.3f %8.4f",
                p[0], p[1], s.isLand ? "LAND" : "sea", s.coastDist, s.bandD, s.windX, s.windZ,
                s.pressureDry, s.rainfallBase, s.gyreWarmth, String.valueOf(s.airType),
                s.airTemperature, s.airHumidity, s.currentX, s.seaTemperature, s.currentSpeed));

            // ---- 不变量 ----
            boolean i1 = (s.isLand == PlateField.isLandWithCell(p[0], p[1], sd, cell));
            boolean i2 = (Math.abs(s.bandD - WorldContract.bandD(p[1])) < 1e-12);
            boolean i3 = s.isLand ? Double.isNaN(s.seaTemperature) : !Double.isNaN(s.seaTemperature);
            boolean i4 = (Math.abs(s.currentSpeed - Math.abs(s.currentX)) < 1e-12);
            if (!(i1 && i2 && i3 && i4)) {
                inv = false;
                say("      ** 不变量失败: isLand=" + i1 + " bandD=" + i2 + " sstNaN=" + i3 + " speed=" + i4 + " **");
            }
        }
        say("");
        say("=== B. 四条不变量（只有接到新链才会成立）===");
        say("  i1  isLand 与 PlateField.isLandWithCell 逐点一致");
        say("  i2  bandD  与 WorldContract.bandD 逐位一致（旧栈会是 1M 周期的另一个值）");
        say("  i3  海上 seaTemperature 非 NaN、陆上为 NaN");
        say("  i4  currentSpeed == |currentX|（bandMeansAt 口径，currentZ 恒 0）");
        say("  ⇒ 总判定 = " + (inv ? "全部通过 OK" : "**有不变量失败**"));
        say("");
        say(String.format(LF, "=== C. 单点最慢耗时 = %.1f ms（首查可能触发 OceanField 解一行）===", tMax));
        say("  ⚠ 记账：旧实现是 O(1) 查表；现在海上每点要碰 OceanField（行缓存命中后 O(1)）。");
        say("     /talosmap 出整张图时若发现过慢，应在 CommandTalosMap 侧加开关而不是回退旧栈。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
