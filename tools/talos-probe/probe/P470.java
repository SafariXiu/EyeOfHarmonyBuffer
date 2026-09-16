package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * P470：**接线本身的验收**（P467 测的是「场」，本探针测的是「接线」）。
 *
 * <h3>为什么必须有这个探针（E30）</h3>
 * P467 v3 直接调 {@link OceanField#anomalyAt}，那时 {@code Atmosphere.SST_PROVIDER == null}
 * ⇒ 解行内部的 {@code windStress -> pressureAnomaly -> sstAnom} 恒返回 0 ⇒
 * **那条递归支路根本没被执行**。装上提供者之后才暴露：
 * {@code solveRow} 里的 {@code tauS} 循环没被抑制 ⇒ 无限重入（jstack 实证：一行跑 10 分钟没完）。
 * **⇒ 「没装提供者的验收」证明不了「装了提供者的生产」能跑。**
 *
 * <p>本探针做四件事：
 * <ol>
 *   <li>装线后走 {@link Atmosphere#sstAnom} 取值，断言 {@code OceanField.reentryBlocked == 0}；</li>
 *   <li>把它与直接调 {@code OceanField.anomalyAt} 的值**逐位比对**（必须完全相同）；</li>
 *   <li>用**接线口径**重算 P467 v3 在 +30/-30 的海盆剖面，与 v3 的表逐字对照；</li>
 *   <li>实测全 64 行预热墙钟（P469 外推是 1016 s，这里给真值）。</li>
 * </ol>
 */
public class P470 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int MAXD = WorldContract.MAX_D;
    static final int ROWS = 64, SCAN = 500_000;
    static final int N = 64, WEST_HI = 16, MID_LO = 24, MID_HI = 40;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P470] " + s); rep.flush(); System.out.println("[P470] " + s); System.out.flush(); }

    /** 走**接线口径**取 T'。 */
    static double viaProvider(int x, int z) { return Atmosphere.sstAnom(x, z); }

    static double median(double[] v, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

    static List<int[]> basinsAt(int z) {
        LinkedHashMap<Long, int[]> m = new LinkedHashMap<>();
        for (int x = -MAXD; x <= MAXD; x += SCAN) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return new ArrayList<>(m.values());
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p470_report.txt"), "UTF-8");
        say("P470：接线验收（走 Atmosphere.sstAnom，而不是直接调 OceanField）");
        say(String.format(LF, "  ENABLED=%s  ROWS=%d  SURF_FACTOR=%.2f",
            OceanField.ENABLED, OceanField.ROWS, com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp.SURF_FACTOR));
        say(String.format(LF, "  A. 装线前：SST_PROVIDER=%s  installedSeed=%d  reentryBlocked=%d",
            Atmosphere.SST_PROVIDER == null ? "null" : "非 null", OceanField.installedSeed(), OceanField.reentryBlocked));
        say("");

        // ---------- B. 装线 ----------
        long t0 = System.nanoTime();
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "B. OceanWiring.onWorld(%d) 已调用（%.3f s 返回；预热在后台）", SEED, (System.nanoTime() - t0) / 1e9));
        say(String.format(LF, "   SST_PROVIDER=%s  installedSeed=%d",
            Atmosphere.SST_PROVIDER == null ? "**null（接线失败）**" : "已装", OceanField.installedSeed()));

        int z30 = (int) (30.0 / 90.0 * (ZC / 2));
        List<int[]> b30 = basinsAt(z30);
        say(String.format(LF, "   +30 行海盆 %d 个", b30.size()));
        if (b30.isEmpty()) { say("   **没有海盆，后面全部无法进行**"); rep.flush(); System.out.println("JAVA_EXIT=2"); return; }
        int wx = b30.get(0)[0], ex = b30.get(0)[1];
        int probeX = wx + 30_000;
        long t1 = System.nanoTime();
        double vp = viaProvider(probeX, z30);
        double tp = (System.nanoTime() - t1) / 1e9;
        say(String.format(LF, "   首次经 Atmosphere.sstAnom(%d, %d) 取值 = %+.4f K，耗时 %.2f s", probeX, z30, vp, tp));
        say(String.format(LF, "   **E30 回归断言**：reentryBlocked = %d  ⇒ %s",
            OceanField.reentryBlocked, OceanField.reentryBlocked == 0 ? "通过（无重入）" : "**失败：发生了重入**"));
        say(String.format(LF, "   同一线程抑制状态：sstSuppressed=%s（解行已结束，应为 false）", Atmosphere.sstSuppressed()));
        say("");

        // ---------- C. 提供者 == 直接调用（逐位） ----------
        say("C. 逐位比对：Atmosphere.sstAnom(x,z) 必须 == OceanField.anomalyAt(x,z,SEED)");
        int bad = 0, cnt = 0; double maxDiff = 0;
        for (int[] b : b30) {
            for (int i = 0; i <= 8; i++) {
                int x = b[0] + (int) ((b[1] - b[0]) * i / 8.0);
                double a = Atmosphere.sstAnom(x, z30);
                double c = OceanField.anomalyAt(x, z30, SEED);
                double d = Math.abs(a - c);
                if (d > maxDiff) maxDiff = d;
                if (Double.doubleToLongBits(a) != Double.doubleToLongBits(c)) bad++;
                cnt++;
            }
        }
        say(String.format(LF, "   %d 个点：逐位不等 %d 个，最大差 %.3e", cnt, bad, maxDiff));
        say("");

        // ---------- D. 接线口径重算 P467 v3 的剖面 ----------
        say("D. 用**接线口径**重算 P467 v3 的 +30 / -30 剖面（直接与 v3 的表对照）");
        // ⚠ E31：表头 9 列、格式串只有 10 个占位符、实参 11 个 ⇒ 从「@东端」开始整列错位。
        //    现在表头与格式串都是 11 列，一一对应：lat 盆西 西端 1/4 1/2 3/4 东端 西带均 东带均 中位比 西带峰。
        say(String.format(LF, "   %-6s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-8s",
            "lat", "盆西km", "@西端", "@1/4", "@1/2", "@3/4", "@东端", "西带均", "东带均", "西|中位比", "西带峰"));
        for (int latDeg : new int[]{30, -30}) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            for (int[] b : basinsAt(z)) {
                int westX = b[0], eastX = b[1];
                double[] v = new double[5];
                for (int i = 0; i < 5; i++) v[i] = viaProvider(westX + (int) ((eastX - westX) * i / 4.0), z);
                double[] wv = new double[WEST_HI + 1], mv = new double[MID_HI - MID_LO + 1];
                double w = 0, e = 0; int nw = 0, ne = 0, im = 0; double wPeak = -1e9;
                for (int i = 0; i <= N; i++) {
                    int x = westX + (int) ((eastX - westX) * i / (double) N);
                    double a = viaProvider(x, z);
                    if (i <= 8) { w += a; nw++; }
                    if (i >= N - 8) { e += a; ne++; }
                    if (i <= WEST_HI) { wv[i] = Math.abs(a); if (a > wPeak) wPeak = a; }
                    else if (i >= MID_LO && i <= MID_HI) mv[im++] = Math.abs(a);
                }
                say(String.format(LF, "   %-6d %-9d %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %+.2f",
                    latDeg, westX / 1000, v[0], v[1], v[2], v[3], v[4],
                    w / Math.max(1, nw), e / Math.max(1, ne),
                    median(wv, WEST_HI + 1) / Math.max(1e-9, median(mv, im)), wPeak));
            }
        }
        say(String.format(LF, "   ⇒ 至此 reentryBlocked = %d（必须仍为 0）", OceanField.reentryBlocked));
        say("");

        // ---------- E. 预热真值 ----------
        say("E. 实测全 64 行预热的墙钟（P469 外推值 = 1016 s）");
        long t2 = System.nanoTime();
        int last = -1;
        int guard = 0;
        while (!OceanWiring.isWarm()) {
            Thread.sleep(1000);
            int r = OceanWiring.warmRows();
            if (r != last) {
                say(String.format(LF, "   %7.1f s   warmRows=%d/%d   solveCount=%d   累计解行 %.1f s",
                    (System.nanoTime() - t0) / 1e9, r, OceanField.ROWS, OceanField.solveCount, OceanField.solveNanos / 1e9));
                last = r;
            }
            if (++guard > 7200) { say("   **超过 2 小时仍未预热完，放弃等待**"); break; }
        }
        say(String.format(LF, "   ⇒ 预热从 onWorld 到 isWarm() 共 %.1f s = %.2f 分钟",
            (System.nanoTime() - t0) / 1e9, (System.nanoTime() - t0) / 6e10));
        say(String.format(LF, "   ⇒ 解行累计 %.1f s，solveCount=%d", OceanField.solveNanos / 1e9, OceanField.solveCount));
        say(String.format(LF, "   ⇒ 最终 reentryBlocked = %d  ⇒ %s",
            OceanField.reentryBlocked, OceanField.reentryBlocked == 0 ? "通过" : "**失败**"));
        say("");
        say("⚠ 记账：本探针调用生产接线入口，未改任何物理参数。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
