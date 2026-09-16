package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * P467：**OceanField 的验收探针** —— 海温异常场是否给出「西暖东冷」？
 *
 * <p>只调用生产类 {@link OceanField}（ENABLED=false 不影响直接调用）。
 *
 * <h3>v1 -> v2 改了什么（判据）</h3>
 * v1 的判据① 是「所有海盆西带均值 &gt; 0」——**过严**：并非每个海盆纬度上都有副热带西边界流
 * （副极地海盆的西边界流是冷的，南极绕极流根本没有西边界）。真实世界里「西暖东冷」
 * 是**副热带海盆**的特征。v2 改成**结构判据 + 量级判据**（见文件尾「判定标准」）。
 *
 * <h3>v2 -> v3 改了什么（采样）</h3>
 * v2 每个纬度只查了 {@code spanOf(0, z)} —— 那只能拿到**包含 x=0 的那一个海盆**。
 * 南半球 4 个纬度全部报「无合格海盆」，不是南半球没有海，而是 x=0 在那里是陆地。
 * v3 因此**扫全经度枚举该纬线上的所有海盆**，一个都不漏。判据不变（还是 v2 那套）。
 */
public class P467 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int MAXD = WorldContract.MAX_D;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    /** 采样条数（0..N，含两端）。西带 = 前 25%，内区 = 37.5%~62.5%。 */
    static final int N = 64;
    static final int WEST_HI = 16;
    static final int MID_LO = 24, MID_HI = 40;
    /** 枚举海盆时的经度扫描步长（block）。海盆宽 ~10,000 km，500 km 步长不会漏。 */
    static final int SCAN = 500_000;

    static void say(String s) { rep.println("[P467] " + s); System.out.println("[P467] " + s); }

    static double median(double[] v, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n);
        Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

    /** 扫全经度枚举该纬线上的所有合格海盆，返回 {westX, eastX} 列表（去重）。 */
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

    static final class Stat {
        int lat; int westX, eastX;
        double ratio, wPeak, satFrac, meanAbs, wMean, eMean, widthKm;
        final double[] v = new double[5];
        boolean sh;   // 南半球
    }

    static Stat eval(int latDeg, int z, int westX, int eastX) {
        Stat s = new Stat();
        s.lat = latDeg; s.westX = westX; s.eastX = eastX;
        s.widthKm = (eastX - westX) / 1000.0;
        s.sh = latDeg < 0;
        for (int i = 0; i < 5; i++) s.v[i] = OceanField.anomalyAt(westX + (int) ((eastX - westX) * i / 4.0), z, SEED);
        double[] wv = new double[WEST_HI + 1], mv = new double[MID_HI - MID_LO + 1];
        double w = 0, e = 0; int nw = 0, ne = 0, im = 0;
        double wPeak = -1e9, sumAbs = 0; int nAll = 0, nSat = 0;
        for (int i = 0; i <= N; i++) {
            int x = westX + (int) ((eastX - westX) * i / (double) N);
            double a = OceanField.anomalyAt(x, z, SEED);
            if (i <= 8) { w += a; nw++; }
            if (i >= N - 8) { e += a; ne++; }
            if (i <= WEST_HI) { wv[i] = Math.abs(a); if (a > wPeak) wPeak = a; }
            else if (i >= MID_LO && i <= MID_HI) mv[im++] = Math.abs(a);
            sumAbs += Math.abs(a);
            if (Math.abs(a) >= 0.99 * SeaSurfaceTemp.ANOM_MAX) nSat++;
            nAll++;
        }
        s.wMean = w / Math.max(1, nw); s.eMean = e / Math.max(1, ne);
        s.wPeak = wPeak;
        s.meanAbs = sumAbs / Math.max(1, nAll);
        s.satFrac = 100.0 * nSat / Math.max(1, nAll);
        s.ratio = median(wv, WEST_HI + 1) / Math.max(1e-9, median(mv, im));
        return s;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p467_report.txt"), "UTF-8");
        say("P467 v3：OceanField 验收 —— 海温异常场是否「西暖东冷」（结构 + 量级）");
        say(String.format(LF, "  ROWS=%d  ROW_H=%.0f m  H_TOTAL=%.0f  A_H=%.1e  GRAD=%d km  JET_RANGE_RD=%.0f  ENABLED=%s",
            OceanField.ROWS, OceanField.ROW_H, OceanField.H_TOTAL, OceanField.A_H,
            OceanField.GRAD / 1000, OceanField.JET_RANGE_RD, OceanField.ENABLED));
        say(String.format(LF, "  SURF_FACTOR=%.2f  ANOM_MAX=%.2f K  LAMBDA=1/(%.0f d)  扫描步长 %d km",
            SeaSurfaceTemp.SURF_FACTOR, SeaSurfaceTemp.ANOM_MAX, 1.0 / SeaSurfaceTemp.LAMBDA / 86400.0, SCAN / 1000));
        say("");
        int[] lats = {15, 30, 45, 60, -15, -30, -45, -60};
        List<Stat> all = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int latDeg : lats) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            // rowIndexOf/rowZ 是包内可见的，探针在别的包 ⇒ 按 WorldContract 的同一公式复算
            int zIdx = (int) Math.round((double) z / ZC * OceanField.ROWS) % OceanField.ROWS;
            int zRow = (int) ((zIdx + 0.5) / OceanField.ROWS * ZC);
            double rowLat = WorldContract.latOf(zRow, ZC) * 180.0 / Math.PI;
            List<int[]> bs = basinsAt(z);
            say(String.format(LF, "== lat %+d（行 %d，实际行纬度 %+.2f）: 合格海盆 %d 个", latDeg, zIdx, rowLat, bs.size()));
            if (bs.isEmpty()) { say("     (无)"); continue; }
            say(String.format(LF, "     %-9s %-7s %-8s %-8s %-8s %-8s %-8s %-11s %-9s %-8s %-7s",
                "盆西km", "宽km", "T'@西端", "@1/4", "@1/2", "@3/4", "@东端", "西带均/东带均", "西|中位比", "西带峰", "饱和"));
            for (int[] b : bs) {
                Stat s = eval(latDeg, z, b[0], b[1]);
                all.add(s);
                say(String.format(LF, "     %-9d %-7.0f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %+.2f / %+.2f   %-9.2f %+.2f    %.0f%%",
                    b[0] / 1000, s.widthKm, s.v[0], s.v[1], s.v[2], s.v[3], s.v[4],
                    s.wMean, s.eMean, s.ratio, s.wPeak, s.satFrac));
            }
        }
        double dt = (System.nanoTime() - t0) / 1e9;
        say("");
        say(String.format(LF, "  ⇒ 解行 %d 次，累计 %.2f s，平均 %.2f s/行",
            OceanField.solveCount, OceanField.solveNanos / 1e9,
            OceanField.solveNanos / 1e9 / Math.max(1, OceanField.solveCount)));
        say(String.format(LF, "  ⇒ 探针总墙钟 %.1f s；合格海盆合计 %d 个（北半球 %d，南半球 %d）",
            dt, all.size(), all.size() - countSH(all), countSH(all)));
        say("");
        // ---- 判据聚合 ----
        int nSub = 0, nSubRatio = 0, nSubPeak = 0, nSubSat = 0;
        double bestPeak = Double.NEGATIVE_INFINITY; String bestWhere = "-";
        int nSatAny = 0;
        for (Stat s : all) {
            if (s.satFrac <= 20.0) nSatAny++;
            if (Math.abs(s.lat) >= 20 && Math.abs(s.lat) <= 50) {
                nSub++;
                if (s.ratio >= 3.0) nSubRatio++;
                if (s.wPeak >= 4.0 && s.wPeak <= 8.0) nSubPeak++;
                if (s.satFrac <= 20.0) nSubSat++;
                if (s.wPeak > bestPeak) { bestPeak = s.wPeak; bestWhere = String.format(LF, "%+d/%d..%d km", s.lat, s.westX / 1000, s.eastX / 1000); }
            }
        }
        say("  判定标准（本探针事先写死，跑之前就写在这里）：");
        say("   ① 结构：西带 |T'| 中位数 >= 3 x 内区 |T'| 中位数（西边界流把异常集中在西侧）；");
        say("   ② 量级上限：任何一条采样线上饱和点占比 <= 20%（ANOM_MAX 不应是主动限幅器）；");
        say("   ③ 量级锚：|lat| 在 20~50 的海盆，西带峰值必须落在 +4.0 ~ +8.0 K（湾流暖舌观测区间）；");
        say("   ③-b 至少有一个这样的海盆（不能全空 —— 否则模型根本没造出西边界流）；");
        say("   ④ 不判「东带必须为负」：东侧上升流冷舌是**另一层**（沿岸风驱动的 Ekman 抽吸），");
        say("      本层（地转流 + 平流）不负责它，硬判会把两件事混在一个数字里。东带值只作为观测记录。");
        say("");
        say(String.format(LF, "  ② 饱和：%d/%d 个海盆的饱和点占比 <= 20%%（上限 %.0f%%）", nSatAny, all.size(), maxSat(all)));
        say(String.format(LF, "  ① 结构：|lat| 20~50 的 %d 个海盆里，西|中位比 >= 3 的有 %d 个", nSub, nSubRatio));
        say(String.format(LF, "  ③ 量级：|lat| 20~50 内西带峰值落在 +4~+8 K 的有 %d 个（要求 >= 1）；最大者 = %s 的 %+.2f K",
            nSubPeak, bestWhere, bestPeak));
        say("");
        say(String.format(LF, "  ⇒ ③-b %s", nSubPeak >= 1 ? "通过" : "不通过"));
        say(String.format(LF, "  ⇒ ①  %s（%d/%d）", nSub > 0 && nSubRatio == nSub ? "全部通过" : "部分通过", nSubRatio, nSub));
        say(String.format(LF, "  ⇒ ②  %s（%d/%d）", nSatAny == all.size() ? "通过" : "不通过", nSatAny, all.size()));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static int countSH(List<Stat> a) { int n = 0; for (Stat s : a) if (s.sh) n++; return n; }
    static double maxSat(List<Stat> a) { double m = 0; for (Stat s : a) m = Math.max(m, s.satFrac); return m; }
}
