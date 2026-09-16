package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P294：**接线验收** —— 新模拟器的地形经 {@code SimTerrain} 走旧实现唯一入口
 * {@code V2TerrainGen.composeColumn} 之后，到底长什么样。
 */
public class P294 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P294] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p294_report.txt"), "UTF-8");
        int seaLevel = 63, maxY = 254;
        say("P294：接线验收（SimTerrain -> V2TerrainGen.composeColumn 唯一入口）");
        say(String.format(LF, "  世界种子=%d  seaLevel=%d  maxY=%d  PLATE_CELL=%d km",
            SEED, seaLevel, maxY, PlateField.PLATE_CELL / 1000));
        say(String.format(LF, "  映射（线性+软封顶）：陆地 h = seaLevel + %.4f*elev（软封顶 %.0f）；海洋 depth = %.4f*(-elev) + 深海起伏 %.0f",
            SimTerrain.LAND_GAIN, V2TerrainGen.SOFT_CAP_H, SimTerrain.OCEAN_GAIN, SimTerrain.SEABED_RELIEF));
        say("");

        // ---- 0) 关掉雪线：本探针 A/B/C 三段共 ~334 万次 composeColumn，只读 c.land 与 c.h ----
        // SimTerrain.compose 在陆地分支里**无条件**算雪线（warmestMonthTempK -> SimClimate），
        // 而散点会穿过上千个气候瓦片、每片要解 2~8 s ⇒ 本探针会从 ~4 s 变成数小时（实测卡死）。
        // SNOW_FROM_TEMP 只写 c.snow，**A/B/C 三段一个字段都不读它** ⇒ 关掉是**口径中性**的。
        final boolean snowSaved = SimTerrain.SNOW_FROM_TEMP;
        SimTerrain.SNOW_FROM_TEMP = false;
        say(String.format(LF, "  （本跑 SNOW_FROM_TEMP 临时置 false：A/B/C 只读 c.land/c.h，口径中性；D 段单独测冷启动）"));
        say("");

        // 1) 逐列对照：直接调 composeColumn（走分派）vs 直接查 PlateField
        say("A. 一致性：composeColumn 的 land 判定必须与 PlateField.isLandWithCell 逐位一致");
        int n = 0, mismatch = 0, nLand = 0;
        int[] hist = new int[16];
        double hMin = 1e9, hMax = -1e9;
        for (int i = 0; i < 4000; i++) {
            int x = (int) ((i * 37_777_777L) % 40_000_000L) - 20_000_000;
            int z = (int) ((i * 11_111_111L) % 20_000_000L);
            V2TerrainGen.Column c = V2TerrainGen.composeColumn(x, z, SEED, seaLevel, null, 0.5, 0.5, maxY);
            boolean simLand = PlateField.isLandWithCell(x, z, SimTerrain.seedOf(SEED), PlateField.PLATE_CELL);
            if (c.land != simLand) mismatch++;
            if (c.land) nLand++;
            if (c.h < hMin) hMin = c.h;
            if (c.h > hMax) hMax = c.h;
            hist[Math.min(15, (int) ((c.h - seaLevel + 40) / 24.0))]++;
            n++;
        }
        say(String.format(LF, "  采样 %d 列：陆地 %d (%.1f%%)   海陆判定不一致 %d %s",
            n, nLand, 100.0*nLand/n, mismatch, mismatch == 0 ? "★ 逐位一致 ✓" : "**不一致**"));
        say(String.format(LF, "  列顶方块高度 min %d / max %d（seaLevel=%d, maxY=%d）", (int)hMin, (int)hMax, seaLevel, maxY));
        say("");

        // 2) 高度分布（相对海平面）
        say("B. 列高分布（相对海平面，blocks）");
        int[] rel = new int[n];
        int k = 0;
        for (int i = 0; i < 4000; i++) {
            int x = (int) ((i * 37_777_777L) % 40_000_000L) - 20_000_000;
            int z = (int) ((i * 11_111_111L) % 20_000_000L);
            V2TerrainGen.Column c = V2TerrainGen.composeColumn(x, z, SEED, seaLevel, null, 0.5, 0.5, maxY);
            rel[k++] = c.h - seaLevel;
        }
        int[] rs = Arrays.copyOf(rel, k);
        Arrays.sort(rs);
        say(String.format(LF, "  p1 %d  p10 %d  p25 %d  p50 %d  p75 %d  p90 %d  p99 %d  min %d  max %d",
            rs[(int)(0.01*k)], rs[(int)(0.10*k)], rs[(int)(0.25*k)], rs[k/2], rs[(int)(0.75*k)], rs[(int)(0.90*k)], rs[(int)(0.99*k)], rs[0], rs[k-1]));
        say("");

        // 3) 连续性：沿 z 走一条经线，看相邻列的高度落差（硬切普查）
        say("C. 硬切普查（沿 16 条经线走 20,000 km，相邻列 dx=1 block ... 用 dx=1）");
        int worst = 0, nPair = 0, over3 = 0, over8 = 0, over20 = 0;
        for (int li = 0; li < 16; li++) {
            int x = -15_000_000 + li * 2_000_000;
            int prev = Integer.MIN_VALUE;
            for (int z = 0; z < 2_000_000; z += 97) {
                V2TerrainGen.Column c = V2TerrainGen.composeColumn(x, z, SEED, seaLevel, null, 0.5, 0.5, maxY);
                if (prev != Integer.MIN_VALUE) {
                    int d = Math.abs(c.h - prev);
                    if (d > worst) worst = d;
                    if (d > 3) over3++;
                    if (d > 8) over8++;
                    if (d > 20) over20++;
                    nPair++;
                }
                prev = c.h;
            }
        }
        say(String.format(LF, "  相邻列（dz=97 blocks）落差：最大 %d blocks   >3 占 %.2f%%   >8 占 %.3f%%   >20 占 %.4f%%",
            worst, 100.0*over3/nPair, 100.0*over8/nPair, 100.0*over20/nPair));
        say("  （注意 dz=97 是刻意的粗采样；真要查 1-block 硬切要用 P309 那种 2 km 网格普查）");
        say("");

        // 4) 性能
        say("D. 性能");
        // ⚠ 旧版这里计时 200,000 列、位置是 (i*101, i*57)，会穿过约 46,000 个气候瓦片 ⇒ **数十小时**。
        // 那是在 §98（雪线接气候）之前写的，读数已不代表任何东西。改成：
        //   D1 单瓦片内稳态成本（关闭雪线，测纯地形映射）
        //   D2 冷启动成本（开启雪线，第一次进新瓦片要解气候）
        int M = 200_000, acc = 0;
        for (int w = 0; w < 20_000; w++) V2TerrainGen.composeColumn(1_000 + (w % 600) * 101, 1_000 + (w % 500) * 57, SEED, seaLevel, null, 0.5, 0.5, maxY);
        long t0 = System.nanoTime();
        for (int i = 0; i < M; i++) acc += V2TerrainGen.composeColumn(1_000 + (i % 600) * 101, 1_000 + (i % 500) * 57, SEED, seaLevel, null, 0.5, 0.5, maxY).h;
        double ns = (System.nanoTime() - t0) / (double) M;
        say(String.format(LF, "  D1 单瓦片内稳态（雪线关）= %.0f ns/列  ⇒ 一个区块（256 列）%.2f ms", ns, ns * 256 / 1e6));
        say(String.format(LF, "  （旧实现的 composeColumn 生产实测约 3~5 us/列；校验和 %d）", acc));
        SimTerrain.SNOW_FROM_TEMP = true;
        com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.clearCache();
        for (int w = 0; w < 2000; w++) V2TerrainGen.composeColumn(w, w, SEED, seaLevel, null, 0.5, 0.5, maxY);
        com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.resetStats();
        long t1 = System.nanoTime();
        int nCold = 0;
        for (int i = 0; i < 40; i++) { V2TerrainGen.composeColumn(i * 100_000 + 5, i * 50_000 + 5, SEED, seaLevel, null, 0.5, 0.5, maxY); nCold++; }
        double msCold = (System.nanoTime() - t1) / 1e6 / nCold;
        say(String.format(LF, "  D2 冷启动：跨 %d 个新瓦片（雪线开）= 平均 %.1f ms/列，其中 SimClimate 解瓦片 %d 次 / %.1f s",
            nCold, msCold, com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.SOLVE_COUNT.get(),
            com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.SOLVE_NANOS.get() / 1e9));
        say("  ⇒ **地形生成现在按瓦片计费**：世界里每进入一个 100x50 km 新瓦片，第一块地形要等一次气候求解。");
        SimTerrain.SNOW_FROM_TEMP = false;
        say("");
        say("E. 开关的可逆性");
        SimTerrain.ENABLED = false;
        com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField.OroSample o =
            com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField.sample(123456, 654321, SEED);
        V2TerrainGen.Column cOld = V2TerrainGen.composeColumn(123456, 654321, SEED, seaLevel, o, 0.5, 0.5, maxY);
        say(String.format(LF, "  ENABLED=false 时 composeColumn(123456,654321) = land=%s h=%d（走旧实现，不抛异常即通过）",
            cOld.land, cOld.h));
        SimTerrain.ENABLED = true;
        SimTerrain.SNOW_FROM_TEMP = snowSaved;          // 恢复本探针开头动过的开关
        V2TerrainGen.Column cNew = V2TerrainGen.composeColumn(123456, 654321, SEED, seaLevel, null, 0.5, 0.5, maxY);
        say(String.format(LF, "  ENABLED=true  时同一点 = land=%s h=%d", cNew.land, cNew.h));
        rep.close();
    }

    static void say(String s) { System.out.println("[P294] " + s); rep.println("[P294] " + s); }
}
