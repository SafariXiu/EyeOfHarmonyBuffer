package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Locale;

/**
 * P469：**第三步接线的代价（有界测量）**。
 *
 * <p>为什么要这个探针：P468 的预热跑了几十分钟，**一行打点都没出来** ——
 * 因为它的打点条件是 {@code r / 8 != last / 8} 而 {@code last} 初值是 -1，
 * Java 整数除法把 {@code -1 / 8} 截断成 {@code 0}，于是「前 8 行」永远不触发打印。
 * 仪器 bug（E29）⇒ 那一跑**没有给出任何代价信息**，不能拿它下结论。
 *
 * <p>本探针改成：**每一行都打点并 flush**，并且把代价拆成三层：
 * <ol>
 *   <li>单元代价：windStress / pressureAnomaly / kappaAt / isLand 各测一批（定热点）；</li>
 *   <li>逐行代价：只解**少数几行**，报海盆数、总盆宽、解行耗时；</li>
 *   <li>全局普查：64 行的海盆数与总盆宽，**用 isLand 连续段计数**（不解 GyreRow）；</li>
 *   <li>外推：用 (2) 的实测系数 × (3) 的总盆宽 ⇒ 全 64 行预热代价。</li>
 * </ol>
 */
public class P469 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int MAXD = WorldContract.MAX_D;
    static final int ROWS = 64, SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P469] " + s); System.out.println("[P469] " + s); rep.flush(); }

    static int rowZ(int zIdx) { return (int) ((zIdx + 0.5) / ROWS * ZC); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p469_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        double th = Atmosphere.theta(0.0);
        say("P469：第三步接线的代价（有界测量）");
        say(String.format(LF, "  SEED=%d cell=%d ROW_H=%.0f GRAD=%d km ROWS=%d", SEED, cell, OceanField.ROW_H, OceanField.GRAD / 1000, ROWS));
        say("");

        // ---------- A. 单元代价 ----------
        say("A. 单元代价（每个数量 20,000 次，warmup 2,000 次不计时）");
        for (int i = 0; i < 2000; i++) { Atmosphere.windStress(0, 0, sd, cell, th, 500_000); }
        long t = System.nanoTime();
        for (int i = 0; i < 20000; i++) { Atmosphere.windStress(i * 1000, 0, sd, cell, th, 500_000); }
        double usWind = (System.nanoTime() - t) / 1e3 / 20000.0;
        t = System.nanoTime();
        for (int i = 0; i < 20000; i++) { Atmosphere.pressureAnomaly(i * 1000, 0, sd, cell, th); }
        double usPres = (System.nanoTime() - t) / 1e3 / 20000.0;
        t = System.nanoTime();
        for (int i = 0; i < 20000; i++) { Atmosphere.kappaAt(i * 1000, 0, sd, cell); }
        double usKap = (System.nanoTime() - t) / 1e3 / 20000.0;
        t = System.nanoTime();
        for (int i = 0; i < 20000; i++) { PlateField.isLandWithCell(i * 1000, 0, sd, cell); }
        double usLand = (System.nanoTime() - t) / 1e3 / 20000.0;
        say(String.format(LF, "   windStress %.1f us   pressureAnomaly %.1f us   kappaAt %.1f us   isLandWithCell %.2f us",
            usWind, usPres, usKap, usLand));
        say(String.format(LF, "   ⇒ 一个网格点的 curl（4 相位 x 4 次 windStress）= %.2f ms", 16 * usWind / 1000.0));
        say(String.format(LF, "   ⇒ 一个 5,000 km 宽的盆（n=1000）= %.1f s", 16 * usWind / 1000.0 * 1000 / 1000.0));
        say("");

        // ---------- B. 逐行代价（只解 4 行） ----------
        int[] rows = {0, 16, 32, 48};
        say("B. 逐行代价（4 行，每行都打点）");
        say(String.format(LF, "   %-6s %-9s %-8s %-12s %-12s %-12s %-10s", "zIdx", "行纬度", "海盆数", "总盆宽km", "solveNanos s", "墙钟 s", "solveCount"));
        double totSpan = 0, totSolve = 0, totWall = 0;
        for (int zIdx : rows) {
            int z = rowZ(zIdx);
            double lat = Math.toDegrees(WorldContract.latOf(z, ZC));
            long s0 = OceanField.solveNanos, c0 = OceanField.solveCount, w0 = System.nanoTime();
            HashSet<Long> seen = new HashSet<>();
            double spanKm = 0;
            for (int x = -MAXD; x <= MAXD; x += SCAN) {
                int[] sp = OceanField.spanOf(x, z, SEED);
                if (sp == null) continue;
                if (seen.add(((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL))) spanKm += (sp[2] - sp[1]) / 1000.0;
            }
            long dw = System.nanoTime() - w0;
            double sn = (OceanField.solveNanos - s0) / 1e9;
            say(String.format(LF, "   %-6d %-9.2f %-8d %-12.0f %-12.2f %-12.2f %-10d",
                zIdx, lat, seen.size(), spanKm, sn, dw / 1e9, OceanField.solveCount - c0));
            totSpan += spanKm; totSolve += sn; totWall += dw / 1e9;
        }
        say(String.format(LF, "   ⇒ 4 行合计：盆宽 %.0f km，纯解行 %.1f s，墙钟 %.1f s", totSpan, totSolve, totWall));
        double sPerKm = totSolve / Math.max(1.0, totSpan);
        say(String.format(LF, "   ⇒ 实测系数 = %.4f s 每 1000 km 盆宽（%.2f s/盆，含扫描与陆地点空跑墙钟 %.4f s/km）",
            sPerKm * 1000.0, totSolve / Math.max(1, 4), totWall / Math.max(1.0, totSpan) * 1000.0));
        say("");

        // ---------- C. 全局普查（不解行） ----------
        say("C. 64 行的海盆普查（isLand 连续段计数，500 km 步长；不触发任何解行）");
        long c0 = OceanField.solveCount;
        double allSpan = 0; int allBasins = 0;
        int[] perRow = new int[ROWS];
        double[] perRowKm = new double[ROWS];
        long tc = System.nanoTime();
        for (int zIdx = 0; zIdx < ROWS; zIdx++) {
            int z = rowZ(zIdx);
            boolean inSea = false; int startX = 0; int nb = 0; double km = 0;
            for (int x = -MAXD; x <= MAXD + SCAN; x += SCAN) {
                boolean sea = x <= MAXD && !PlateField.isLandWithCell(x, z, sd, cell);
                if (sea && !inSea) { inSea = true; startX = x; }
                else if (!sea && inSea) { inSea = false; if (x - startX >= SCAN) { nb++; km += (x - startX) / 1000.0; } }
            }
            perRow[zIdx] = nb; perRowKm[zIdx] = km;
            allBasins += nb; allSpan += km;
        }
        double tcSec = (System.nanoTime() - tc) / 1e9;
        say(String.format(LF, "   普查耗时 %.1f s；触发解行 %d 次（必须为 0）", tcSec, OceanField.solveCount - c0));
        say(String.format(LF, "   ⇒ 全 64 行：海盆合计 %d 个，总盆宽 %.0f km", allBasins, allSpan));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROWS; i++) { sb.append(String.format(LF, "%d:%d/", i, perRow[i])); if ((i + 1) % 16 == 0) { say("   " + sb); sb.setLength(0); } }
        say(String.format(LF, "   （格式 zIdx:海盆数/） 总计 %d / %.0f km", allBasins, allSpan));
        say("");

        // ---------- D. 外推 ----------
        say("D. 外推全 64 行预热代价");
        say(String.format(LF, "   用实测系数 %.4f s/1000km x %.0f km = **%.0f s = %.1f 分钟 = %.2f 小时**",
            sPerKm * 1000.0, allSpan, sPerKm * allSpan, sPerKm * allSpan / 60.0, sPerKm * allSpan / 3600.0));
        say(String.format(LF, "   上界（用墙钟系数 %.4f s/1000km）= %.0f s = %.2f 小时",
            totWall / Math.max(1.0, totSpan) * 1000.0, totWall / Math.max(1.0, totSpan) * allSpan,
            totWall / Math.max(1.0, totSpan) * allSpan / 3600.0));
        say("");
        say("⚠ 记账：本探针只测量，**未改任何生产代码**。它调用 OceanField.spanOf 会真实解行，");
        say("   这些行会留在 OceanField 的缓存里，但探针退出即释放。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
