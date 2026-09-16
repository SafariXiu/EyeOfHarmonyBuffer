package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P468：**第三步接线的代价与口径 A/B**（设计冻结 §162）。
 *
 * <p>三问：
 * <ol>
 *   <li>接线真的装上去了吗？（{@code OceanWiring.onWorld} 是否异步、是否只服务一个种子）</li>
 *   <li>**预热 64 条纬度行要多久？** —— 这个数字决定接线能不能上生产。</li>
 *   <li>装上之后，降水场动了多少？（同网格、同相位、同 seed，唯一变量 = SST' 有无）</li>
 * </ol>
 *
 * <p>网格与相位**逐字沿用 P296**（B2 验收探针），保证 delta 是同一口径下的 delta：
 * {@code NZ=50, NX=400, x = c*40_000, gradStep=500_000, thS=theta(0), thW=theta(DAYS_PER_YEAR/2)}。
 */
public class P468 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final int NZ = 50, NX = 400;

    /** ⚠ 必须 flush：stdout/rep 重定向到文件时都是**带缓冲**的，不 flush 就等于「没输出」（E29）。 */
    static void say(String s) {
        rep.println("[P468] " + s); rep.flush();
        System.out.println("[P468] " + s); System.out.flush();
    }

    /**
     * 按纬度带取纬向平均 —— **逐字沿用 P296 的 {@code band()}**：
     * 用**带符号**纬度、判据是 {@code lat >= lo && lat < hi}。
     *
     * <p>⚠ 我第一版写成了 {@code Math.abs(lat)}（两个半球一起平均）⇒ 赤道带给出 4.14，
     * 而 P296 的验收读数是 6.56。**同一个名字、两套口径** —— 这正是本项目反复吃过的亏。
     * 现在改成逐字复制，并额外提供 {@link #bandAbs} 把「两半球」那个口径**显式命名**出来。
     */
    static double band(double[] p, double lo, double hi) {
        double s = 0; int n = 0;
        for (int r = 0; r < p.length; r++) {
            double lat = Math.toDegrees(WorldContract.latOf((int) ((r + 0.5) / p.length * WorldContract.Z_CYCLE), WorldContract.Z_CYCLE));
            if (lat >= lo && lat < hi) { s += p[r]; n++; }
        }
        return n > 0 ? s / n : 0;
    }

    /** 整个 50x400 网格的夏/冬纬向平均剖面。返回 double[2][NZ]。 */
    static double[][] grid(int cell, long sd, double thS, double thW) {
        double[][] out = new double[2][NZ];
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double a = 0, b = 0;
            for (int c = 0; c < NX; c++) {
                int x = c * 40_000;
                a += PrecipField.mmPerDay(x, z, sd, cell, thS, 500_000);
                b += PrecipField.mmPerDay(x, z, sd, cell, thW, 500_000);
            }
            out[0][r] = a / NX; out[1][r] = b / NX;
        }
        return out;
    }

    static void dumpBands(String tag, double[][] g) {
        say(String.format(LF, "  %s  中纬47.5~62.5 夏%.2f 冬%.2f | 赤道2.5~12.5 夏%.2f 冬%.2f | 副热27.5~37.5 夏%.2f 冬%.2f",
            tag, band(g[0], 47.5, 62.5), band(g[1], 47.5, 62.5),
            band(g[0], 2.5, 12.5), band(g[1], 2.5, 12.5),
            band(g[0], 27.5, 37.5), band(g[1], 27.5, 37.5)));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p468_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say("P468：第三步接线的代价与口径 A/B");
        say(String.format(LF, "  SEED=%d  cell=%d  NZ=%d NX=%d  gradStep=500000  ROWS=%d  ENABLED=%s",
            SEED, cell, NZ, NX, OceanField.ROWS, OceanField.ENABLED));
        say(String.format(LF, "  接线前：SST_PROVIDER=%s  installedSeed=%d  sstAnom(0,0)=%.4f",
            Atmosphere.SST_PROVIDER == null ? "null" : "非 null", OceanField.installedSeed(), Atmosphere.sstAnom(0, 0)));
        say("");

        // ---------- B. 基线（SST' == 0） ----------
        long t0 = System.nanoTime();
        double[][] g0 = grid(cell, sd, thS, thW);
        double tb = (System.nanoTime() - t0) / 1e9;
        say(String.format(LF, "B. 基线网格（SST' == 0）算完，%.1f s", tb));
        dumpBands("   基线", g0);
        say("");

        // ---------- C. 接线 ----------
        long t1 = System.nanoTime();
        OceanWiring.onWorld(SEED);
        boolean provided = Atmosphere.SST_PROVIDER != null;
        say(String.format(LF, "C. OceanWiring.onWorld(%d)：SST_PROVIDER=%s  installedSeed=%d  立刻 isWarm()=%s warmRows=%d",
            SEED, provided ? "已装" : "**仍是 null**", OceanField.installedSeed(),
            OceanWiring.isWarm() ? "true" : "false（＝预热确实在后台）", OceanWiring.warmRows()));
        say(String.format(LF, "   sstAnom(0,0)=%.4f", Atmosphere.sstAnom(0, 0)));
        say("");

        // ---------- D. 预热代价 ----------
        say("D. 预热 64 条纬度行的代价（逐 8 行打点）");
        // ⚠ 旧写法是 if (r / 8 != last / 8) 且 last 初值 -1：Java 整数除法向零截断，
        //    (-1)/8 == 0 == 0/8 ⇒ 「前 8 行」这一档**永远不触发**（E29）。
        //    现在改成「值一变就打点」，并且每次 flush。
        int last = -1;
        while (!OceanWiring.isWarm()) {
            Thread.sleep(1000);
            int r = OceanWiring.warmRows();
            if (r != last) {
                say(String.format(LF, "   %6.1f s  warmRows=%d/%d  solveCount=%d  累计解行 %.1f s",
                    (System.nanoTime() - t1) / 1e9, r, OceanField.ROWS, OceanField.solveCount, OceanField.solveNanos / 1e9));
                last = r;
            }
        }
        double tw = (System.nanoTime() - t1) / 1e9;
        say(String.format(LF, "   ⇒ 预热完成：%.1f s（%.1f 分钟），solveCount=%d，其中纯解行累计 %.1f s（%.0f%%）",
            tw, tw / 60.0, OceanField.solveCount, OceanField.solveNanos / 1e9, 100.0 * OceanField.solveNanos / 1e9 / tw));
        say(String.format(LF, "   ⇒ 平均每行 %.2f s（含 41 次经度扫描与陆地点空跑）", tw / OceanField.ROWS));
        say("");

        // ---------- E. 接线后同网格 ----------
        long t2 = System.nanoTime();
        double[][] g1 = grid(cell, sd, thS, thW);
        say(String.format(LF, "E. 接线后同网格算完，%.1f s", (System.nanoTime() - t2) / 1e9));
        dumpBands("   接线后", g1);
        say("");

        // ---------- F. delta ----------
        say("F. 逐点 delta（接线后 - 基线），单位 mm/day");
        double maxS = 0, maxW = 0; int rS = -1, rW = -1; double sumAbsS = 0, sumAbsW = 0;
        for (int r = 0; r < NZ; r++) {
            double ds = g1[0][r] - g0[0][r], dw = g1[1][r] - g0[1][r];
            sumAbsS += Math.abs(ds); sumAbsW += Math.abs(dw);
            if (Math.abs(ds) > maxS) { maxS = Math.abs(ds); rS = r; }
            if (Math.abs(dw) > maxW) { maxW = Math.abs(dw); rW = r; }
        }
        say(String.format(LF, "   纬向平均 |delta| 均值： 夏 %.3f   冬 %.3f mm/day", sumAbsS / NZ, sumAbsW / NZ));
        say(String.format(LF, "   最大 |delta|： 夏 %.3f @lat %.1f   冬 %.3f @lat %.1f",
            maxS, Math.toDegrees(WorldContract.latOf((int) ((rS + 0.5) / NZ * WorldContract.Z_CYCLE), WorldContract.Z_CYCLE)),
            maxW, Math.toDegrees(WorldContract.latOf((int) ((rW + 0.5) / NZ * WorldContract.Z_CYCLE), WorldContract.Z_CYCLE))));
        say("   三条验收带的 delta：");
        String[] nm = {"中纬 47.5~62.5", "赤道 2.5~12.5 ", "副热 27.5~37.5"};
        double[][] bb = {{47.5, 62.5}, {2.5, 12.5}, {27.5, 37.5}};
        for (int i = 0; i < 3; i++) {
            double a0s = band(g0[0], bb[i][0], bb[i][1]), a1s = band(g1[0], bb[i][0], bb[i][1]);
            double a0w = band(g0[1], bb[i][0], bb[i][1]), a1w = band(g1[1], bb[i][0], bb[i][1]);
            say(String.format(LF, "     %s  夏 %.3f -> %.3f (%+.3f, %+.1f%%)   冬 %.3f -> %.3f (%+.3f, %+.1f%%)",
                nm[i], a0s, a1s, a1s - a0s, 100.0 * (a1s - a0s) / Math.max(1e-9, a0s),
                a0w, a1w, a1w - a0w, 100.0 * (a1w - a0w) / Math.max(1e-9, a0w)));
        }
        say("");
        say("G. 完整纬向平均剖面（自足记录：以后换任何带定义都不用重跑）");
        say(String.format(LF, "  %-8s %10s %10s %10s %10s %10s", "纬度", "基线夏", "接线后夏", "d夏", "基线冬", "接线后冬"));
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
            say(String.format(LF, "  %-8.2f %10.4f %10.4f %+10.4f %10.4f %10.4f",
                lat, g0[0][r], g1[0][r], g1[0][r] - g0[0][r], g0[1][r], g1[1][r]));
        }
        say("");
        say(String.format(LF, "   ⇒ 探针总墙钟 %.1f s（基线 %.1f + 预热 %.1f + 接线后 %.1f）",
            (System.nanoTime() - t0) / 1e9, tb, tw, (System.nanoTime() - t2) / 1e9));
        say("⚠ 记账：本探针只测量 + 调用生产接线入口，**未改任何物理参数**。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
