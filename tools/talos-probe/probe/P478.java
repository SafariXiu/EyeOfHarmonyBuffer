package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * P478：**A6「覆盖面」的第一个 tracked 读数** —— 带流的经向海岸比例。
 *
 * <p>A6 自 M0 起就标着「（未测过）」，到 2026-09-14 仍然没有探针。本探针把它变成可测的：
 *
 * <h3>定义（跑之前写死）</h3>
 * <ol>
 *   <li><b>海岸段</b>：每一条纬度行上、每个海盆的**西墙**与**东墙**各算一段海岸；</li>
 *   <li><b>经向</b>：该墙在**相邻两个纬度行**之间的 x 位移 &lt;= 100 km
 *       （行距 312.5 km ⇒ 坡度 &lt;= 0.32 ⇒ 与正北夹角 &lt;= 18 度）；
 *   <li><b>带流</b>：紧邻该墙的海洋里 <b>\|T'\| &gt;= 2.0 K</b>。
 *       用 T' 而不是速度，是因为 T' 就是边界流的**可观测签名**
 *       （西墙暖舌 / 东墙冷舌；见 {@code SeaSurfaceTemp} 的类注释），
 *       而生产里速度不对外暴露。</li>
 * </ol>
 *
 * <p>判据：**带流的经向海岸比例 &gt;= 50%**（沿用 A6 原目标值）。
 */
public class P478 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    /** 「经向」的判据：相邻两行之间墙的 x 位移上限（block）。 */
    static final int DRIFT_MAX = 100_000;
    /** 「带流」的判据：墙边海洋的 \|T'\| 下限（K）。 */
    static final double TONGUE_MIN = 2.0;

    static void say(String s) { rep.println("[P478] " + s); rep.flush(); System.out.println("[P478] " + s); System.out.flush(); }

    static List<int[]> basinsAt(int z) {
        LinkedHashMap<Long, int[]> m = new LinkedHashMap<>();
        for (int x = -WorldContract.MAX_D; x <= WorldContract.MAX_D; x += SCAN) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return new ArrayList<>(m.values());
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p478_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        say("P478：A6 覆盖面 —— 带流的经向海岸比例（第一个 tracked 读数）");
        say(String.format(LF, "  接线：installedSeed=%d  ROW_H=%.0f m", OceanField.installedSeed(), OceanField.ROW_H));
        say(String.format(LF, "  定义：经向 = 相邻两行墙位移 <= %d km；带流 = 墙边 |T'| >= %.1f K",
            DRIFT_MAX / 1000, TONGUE_MIN));
        say(String.format(LF, "  判据（事先写死）：带流的经向海岸比例 >= 50%%"));
        say("");
        int[] zIdxList = new int[OceanField.ROWS];
        for (int i = 0; i < OceanField.ROWS; i++) zIdxList[i] = i;
        // 每个 zIdx 记下它的墙：{x, side(+1 东墙 / -1 西墙), T'值}
        List<double[]> walls = new ArrayList<>();   // {zIdx, x, side, tAtWall}
        int nBasin = 0;
        long t0 = System.nanoTime();
        for (int zi : zIdxList) {
            int z = (int) ((zi + 0.5) / OceanField.ROWS * ZC);
            List<int[]> bs = basinsAt(z);
            nBasin += bs.size();
            for (int[] b : bs) {
                int wx = b[0], ex = b[1];
                if (ex - wx < 200_000) continue;
                double tw = OceanField.anomalyAt(wx + 10_000, z, SEED);
                double te = OceanField.anomalyAt(ex - 10_000, z, SEED);
                walls.add(new double[]{zi, wx, -1, tw});
                walls.add(new double[]{zi, ex, +1, te});
            }
        }
        say(String.format(LF, "  ⇒ 解 %d 行、%d 个海盆、%d 段海岸墙，耗时 %.1f s",
            OceanField.ROWS, nBasin, walls.size(), (System.nanoTime() - t0) / 1e9));
        say("");
        int nMerid = 0, nMeridWith = 0, nZonal = 0, nZonalWith = 0;
        int nW = 0, nWWith = 0, nE = 0, nEWith = 0;
        for (double[] a : walls) {
            for (double[] b : walls) {
                if ((int) b[0] != (int) a[0] + 1) continue;         // 只看相邻两行
                if (b[2] != a[2]) continue;                          // 同类墙（西对西、东对东）
                if (Math.abs(b[1] - a[1]) > DRIFT_MAX) continue;     // 这就是「经向」的定义
                nMerid++;
                boolean has = Math.abs(a[3]) >= TONGUE_MIN;
                if (has) nMeridWith++;
                if (a[2] < 0) { nW++; if (Math.abs(a[3]) >= TONGUE_MIN) nWWith++; }
                else          { nE++; if (Math.abs(a[3]) >= TONGUE_MIN) nEWith++; }
            }
        }
        // 全体的「带流」比例（不做经向筛选）作对照
        int nAll = 0, nAllWith = 0;
        for (double[] a : walls) { nAll++; if (Math.abs(a[3]) >= TONGUE_MIN) nAllWith++; }
        say(String.format(LF, "  经向海岸段（相邻行、位移 <= %d km）：%d 段", DRIFT_MAX / 1000, nMerid));
        say(String.format(LF, "     其中带流（墙边 |T'| >= %.1f K）：**%d 段 = %.1f%%**",
            TONGUE_MIN, nMeridWith, 100.0 * nMeridWith / Math.max(1, nMerid)));
        say(String.format(LF, "     西墙 %d 段，带流 %d = %.1f%%；东墙 %d 段，带流 %d = %.1f%%",
            nW, nWWith, 100.0 * nWWith / Math.max(1, nW), nE, nEWith, 100.0 * nEWith / Math.max(1, nE)));
        say(String.format(LF, "  （对照·不筛经向）全部 %d 段海岸墙里带流 %d = %.1f%%",
            nAll, nAllWith, 100.0 * nAllWith / Math.max(1, nAll)));
        say("");
        say(String.format(LF, "  判据：带流的经向海岸比例 %.1f%% >= 50%%  ⇒ %s",
            100.0 * nMeridWith / Math.max(1, nMerid),
            100.0 * nMeridWith / Math.max(1, nMerid) >= 50.0 ? "通过" : "**不通过**"));
        say(String.format(LF, "  reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只读取 OceanField 的公开取值接口 + PlateField 的公开判定，未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
