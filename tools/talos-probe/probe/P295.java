package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P295：**用户裁决 6 的连带核查** —— `base` 的"次近站换人"跳变在世界里到底长什么样。
 *
 * <p>不修它，但必须知道它在**游戏可见单位**（blocks）上造成什么：
 * 相邻列落差多大、会不会翻转海陆判定、会不会在海里划出假海岸线。
 */
public class P295 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final int SEA = 63, MAXY = 254, STEP = 2000;

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P295] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p295_report.txt"), "UTF-8");
        say("P295：base 跳变的**游戏可见**连带核查（裁决 6）");
        say(String.format(LF, "  网格步长 %d blocks  垂直映射：陆地 %.4f blocks/m（seaLevel=%d）", STEP, SimTerrain.LAND_GAIN, SEA));
        say("");

        // ---- 关掉雪线：A 段共 3 x 2000 x 2000 = **1200 万次 composeColumn**，只读 c.h 与 c.land ----
        // SimTerrain.compose 在陆地分支无条件算雪线（-> SimClimate 瓦片求解），
        // 本段会穿过约 9600 个瓦片、每片 2~8 s ⇒ 本探针会从 ~10 s 变成数小时（实测卡死）。
        // SNOW_FROM_TEMP 只写 c.snow，A 段**不读它** ⇒ 关掉是口径中性的。
        final boolean snowSaved = SimTerrain.SNOW_FROM_TEMP;
        SimTerrain.SNOW_FROM_TEMP = false;
        say("  （本跑 SNOW_FROM_TEMP 临时置 false：A/B 段只读 c.h/c.land，口径中性）");
        say("");

        int[][] wins = {{0, 0}, {4_000_000, 8_000_000}, {12_000_000, 16_000_000}};
        int W = 2000;   // 2000 x 2000 列 @2 km = 4,000 x 4,000 km
        long pairs = 0, over20 = 0, over50 = 0, over100 = 0, over187 = 0;
        int worst = 0, worstX = 0, worstZ = 0;
        long flips = 0;
        for (int[] w : wins) {
            int[] prevRow = new int[W];
            boolean[] prevLand = new boolean[W];
            for (int j = 0; j < W; j++) {
                int z = w[1] + j * STEP;
                int prevH = Integer.MIN_VALUE; boolean prevL = false;
                for (int i = 0; i < W; i++) {
                    int x = w[0] + i * STEP;
                    V2TerrainGen.Column c = V2TerrainGen.composeColumn(x, z, SEED, SEA, null, 0.5, 0.5, MAXY);
                    if (prevH != Integer.MIN_VALUE) {
                        int d = Math.abs(c.h - prevH);
                        if (d > worst) { worst = d; worstX = x; worstZ = z; }
                        if (d > 20) over20++;
                        if (d > 50) over50++;
                        if (d > 100) over100++;
                        if (d > 187) over187++;
                        pairs++;
                        if (c.land != prevL) flips++;
                    }
                    if (j > 0) {
                        int d2 = Math.abs(c.h - prevRow[i]);
                        if (d2 > worst) { worst = d2; worstX = x; worstZ = z; }
                        if (d2 > 20) over20++;
                        if (d2 > 50) over50++;
                        if (d2 > 100) over100++;
                        if (d2 > 187) over187++;
                        pairs++;
                        if (c.land != prevLand[i]) flips++;
                    }
                    prevH = c.h; prevL = c.land;
                    prevRow[i] = c.h; prevLand[i] = c.land;
                }
            }
        }
        say(String.format(LF, "  3 个窗口（4,000x4,000 km @%d blocks）共 %d 对相邻列", STEP, pairs));
        say(String.format(LF, "  |dh| > 20 blocks : %d = %.4f %%", over20, 100.0*over20/pairs));
        say(String.format(LF, "  |dh| > 50 blocks : %d = %.4f %%", over50, 100.0*over50/pairs));
        say(String.format(LF, "  |dh| > 100 blocks: %d = %.5f %%", over100, 100.0*over100/pairs));
        say(String.format(LF, "  |dh| > 187 blocks（≈整面墙）: %d = %.6f %%", over187, 100.0*over187/pairs));
        say(String.format(LF, "  最大 |dh| = %d blocks @ (%d, %d)", worst, worstX, worstZ));
        say(String.format(LF, "  相邻列海陆判定翻转：%d 对 = %.3f %%  ← 这就是「假海岸线」的可见度", flips, 100.0*flips/pairs));
        say("  （翻转本身是正常的：海岸线本来就是海陆交界；这里看的是量级有没有异常变大）");
        say("");

        // 对照：旧实现的 base 跳变点在哪 —— 用 elevationFull 的 base 直接找"次近站换人"
        say("B. 直接定位 base 跳变（沿 8 条 x 扫描，看 1-block 步长下的高程最大跳变）");
        int worst1 = 0; long pairs1 = 0;
        for (int li = 0; li < 8; li++) {
            int z = li * 1_000_000;
            double prev = Double.NaN;
            for (int x = -2_000_000; x < 2_000_000; x += 1) {
                double e = PlateField.elevationWithCell(x, z, SimTerrain.seedOf(SEED), PlateField.PLATE_CELL);
                if (!Double.isNaN(prev)) {
                    int d = (int) Math.round(Math.abs(e - prev) * SimTerrain.LAND_GAIN);
                    if (d > worst1) worst1 = d;
                    pairs1++;
                }
                prev = e;
            }
        }
        SimTerrain.SNOW_FROM_TEMP = snowSaved;      // 恢复本探针开头动过的开关
        say(String.format(LF, "  1-block 步长下最大高程跳变 = **%d blocks**（%d 对）", worst1, pairs1));
        say("  ⇒ 这就是 §84.5 说的「整面墙」：base 在次近站换人处有 1,790 m 的跳变；");
        say("     实测算出来的值以本行为准（不要用推断的数）。");
        rep.close();
    }

    static void say(String s) { System.out.println("[P295] " + s); rep.println("[P295] " + s); }
}
