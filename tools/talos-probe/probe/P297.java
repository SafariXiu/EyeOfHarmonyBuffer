package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P297 v2：雪线从「按高度」改成「按地表温度」的验收（§98）。 */
public class P297 {

    static final int SEED = 1022228679;
    static final int SEA = 63, MAXY = 254;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int cell;
    static long sd;

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P297] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p297_report.txt"), "UTF-8");
        cell = PlateField.PLATE_CELL;
        sd = SimTerrain.seedOf(SEED);
        say("P297：雪线口径验收（温度 vs 旧的高度规则）");
        say(String.format(LF, "  SNOW_FROM_TEMP=%s  SNOW_T=%.2f K", SimTerrain.SNOW_FROM_TEMP, SimTerrain.SNOW_T));
        say("");
        say("B. 各纬度带：陆地点的雪占比，按 kappa 分层（kappa 越高 = 越内陆）");
        say("   （这是「雪线跟气候走」的直接证据：同纬度、同高度，内陆与沿海应当不同）");
        say(String.format(LF, "  %-8s %12s %12s %12s %12s   %s", "纬度", "kappa<0.5", "0.5~0.8", "0.8~0.95", ">0.95", "该带陆地点"));
        double[][] onset = new double[6][4];
        for (int li = 0; li < 6; li++) {
            int latDeg = 30 + li * 10;
            int z0 = WorldContract.zOfLat(latDeg);
            int[] num = new int[4], den = new int[4];
            double[] on = new double[4];
            for (int b = 0; b < 4; b++) on[b] = 1e9;
            for (int dz = 0; dz < 60; dz++) {
                int z = z0 + dz * 1000;
                for (int dx = 0; dx < 600; dx++) {
                    int x = dx * 1000;
                    V2TerrainGen.Column c = V2TerrainGen.composeColumn(x, z, SEED, SEA, null, 0.5, 0.5, MAXY);
                    if (!c.land) continue;
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    int bi = k < 0.5 ? 0 : (k < 0.8 ? 1 : (k < 0.95 ? 2 : 3));
                    den[bi]++; if (c.snow) num[bi]++;
                    if (c.snow) { double e = PlateField.elevationWithCell(x, z, sd, cell); if (e < on[bi]) on[bi] = e; }
                }
            }
            int tot = 0; for (int b = 0; b < 4; b++) tot += den[b];
            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < 4; b++) {
                sb.append(String.format(LF, "%12s", den[b] >= 20 ? String.format(LF, "%.0f%%", 100.0*num[b]/den[b]) : "-"));
                onset[li][b] = on[b];
            }
            say(String.format(LF, "  %-8s%s   %d", latDeg + " 度", sb.toString(), tot));
        }
        say("");
        say("   各层「首次出现雪的**最低海拔**」（m）—— 同纬度下内陆层应当显著更高才出现雪：");
        say(String.format(LF, "  %-8s %12s %12s %12s %12s", "纬度", "kappa<0.5", "0.5~0.8", "0.8~0.95", ">0.95"));
        for (int li = 0; li < 6; li++) {
            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < 4; b++) sb.append(String.format(LF, "%12s", onset[li][b] < 1e8 ? String.format(LF, "%.0f", onset[li][b]) : "-"));
            say(String.format(LF, "  %-8s%s", (30 + li * 10) + " 度", sb.toString()));
        }
        say("");

        // A：找一片**真的有陆地**的窗口，看雪线随纬度的抬升
        say("A. 雪线随纬度的抬升（在 x=0..600 km 这条带上逐纬度找最高点与是否下雪）");
        say(String.format(LF, "  %-8s %12s %12s %14s", "纬度", "带内最高 m", "是否下雪", "雪线起始 m"));
        for (int latDeg = 20; latDeg <= 80; latDeg += 10) {
            int z0 = WorldContract.zOfLat(latDeg);
            double hMax = -1e9, on = 1e9; int nSnow = 0, nLand = 0;
            for (int dz = 0; dz < 20; dz++) {
                int z = z0 + dz * 2000;
                for (int dx = 0; dx < 240; dx++) {
                    int x = dx * 2500;
                    V2TerrainGen.Column c = V2TerrainGen.composeColumn(x, z, SEED, SEA, null, 0.5, 0.5, MAXY);
                    if (!c.land) continue;
                    nLand++;
                    double e = PlateField.elevationWithCell(x, z, sd, cell);
                    if (e > hMax) hMax = e;
                    if (c.snow) { nSnow++; if (e < on) on = e; }
                }
            }
            say(String.format(LF, "  %-8s %12s %12s %14s", latDeg + " 度",
                nLand == 0 ? "-" : String.format(LF, "%.0f", hMax),
                nLand == 0 ? "-" : (nSnow > 0 ? "是" : "否"),
                nSnow > 0 ? String.format(LF, "%.0f", on) : "-"));
        }
        say("");

        // C：成本 —— **必须在一个瓦片内测**，否则量到的是瓦片求解
        SimClimate.surfaceTempK(1234, 1234, SEED);
        long t0 = System.nanoTime();
        int M = 200000; double acc = 0;
        for (int i = 0; i < M; i++) acc += SimClimate.surfaceTempK(1000 + (i % 2000), 1000 + (i % 500), SEED);
        double ns = (System.nanoTime() - t0) / (double) M;
        say(String.format(LF, "C. surfaceTempK（**单瓦片内**，纯缓存命中）= %.1f ns/次 ⇒ 一个区块（256 列）%.3f ms", ns, ns * 256 / 1e6));
        say("   ⚠ 上一版我扫过了多个瓦片，量到的是瓦片求解（312 us）—— 这是量法错误，不是接口慢。");
        say(String.format(LF, "   （校验和 %.6f）", acc));
        rep.close();
    }

    static void say(String s) { System.out.println("[P297] " + s); rep.println("[P297] " + s); }
}
