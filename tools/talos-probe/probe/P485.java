package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P485：**D70 的定量证据** —— 群系的「地貌变体」用的是**旧陆海场**的档案。
 *
 * <p>事实链（已逐行读过源码）：LandformField.solve 的唯一输入是 OrographyField.sample，
 * computeWeights(beltMask01, relief01, elevation01) 决定 low/hill/plat/mtn/peak 五档，
 * 而 LandformField 对 PlateField / SimTerrain 的引用数 = 0。
 * 而 V2BiomeSelect Tier-2 直接消费 LandformField 的 mtnAmt / plat / low / h0。
 *
 * <p>本探针把两个场并排量：按 (PlateField 是否陆) x (OrographyField 是否陆) 分四格，
 * 看 LandformField 的山地量跟谁走。
 */
public class P485 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P485] " + s); rep.flush(); System.out.println("[P485] " + s); System.out.flush(); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p485_report.txt"), "UTF-8");
        say("P485：D70 —— 群系地貌变体（山/高原/盆地）跟的是旧陆海场还是新陆海场？");
        say(String.format(LF, "  SEED=%d  PLATE_CELL=%d km  LandformField.CELL=%d m",
            SEED, CELL / 1000, LandformField.CELL));
        say("");

        int[] xs = {-9_000_000, -5_000_000, -1_000_000, 3_000_000, 7_000_000};
        int[] zs = {1_000_000, 5_000_000, 9_000_000, 13_000_000, 17_000_000};
        int n = 0;
        // 四格：[plateLand?1:0][oroLand?1:0]
        int[] cnt = new int[4];
        double[] sumMtn = new double[4], sumElev = new double[4], sumRelief = new double[4], sumBelt = new double[4], sumH0 = new double[4];
        double[] mv = new double[xs.length * zs.length];
        int nm = 0;
        say("A. 逐点读数（前 10 点）");
        say(String.format(LF, "   %-22s %5s %5s %9s %8s %8s %7s %7s %7s",
            "点", "新陆", "旧陆", "新高程m", "旧relief", "旧belt", "mtnAmt", "plat", "h0"));
        for (int z : zs) {
            for (int x : xs) {
                boolean pLand = PlateField.isLandWithCell(x, z, SD, CELL);
                double pElev = PlateField.elevationWithCell(x, z, SD, CELL);
                OrographyField.OroSample o = OrographyField.sample(x, z, SEED);
                LandformField.Sample lf = LandformField.sample(x, z, SEED);
                int c = (pLand ? 2 : 0) + (o.isLand ? 1 : 0);
                cnt[c]++;
                sumMtn[c] += lf.mtnAmt; sumElev[c] += pElev;
                sumRelief[c] += o.relief01; sumBelt[c] += o.beltMask01; sumH0[c] += lf.h0;
                mv[nm++] = lf.mtnAmt;
                n++;
                if (n <= 10) say(String.format(LF, "   (%9d,%9d) %5s %5s %9.1f %8.3f %8.3f %7.3f %7.3f %7.1f",
                    x, z, pLand ? "陆" : "海", o.isLand ? "陆" : "海", pElev, o.relief01, o.beltMask01,
                    lf.mtnAmt, lf.plat, lf.h0));
            }
        }
        say("");
        say("B. 四格统计（mtnAmt = 0 表示「没有山地变体」）");
        say(String.format(LF, "   %-14s %5s %10s %10s %10s %10s %10s",
            "新陆/旧陆", "n", "均mtnAmt", "均新高程m", "均旧relief", "均旧belt", "均h0"));
        String[] nm4 = {"海/海", "海/陆", "陆/海", "陆/陆"};
        for (int c = 0; c < 4; c++) {
            if (cnt[c] == 0) { say(String.format(LF, "   %-14s %5d  （空）", nm4[c], 0)); continue; }
            say(String.format(LF, "   %-14s %5d %10.3f %10.1f %10.3f %10.3f %10.1f", nm4[c], cnt[c],
                sumMtn[c] / cnt[c], sumElev[c] / cnt[c], sumRelief[c] / cnt[c], sumBelt[c] / cnt[c], sumH0[c] / cnt[c]));
        }
        say("");
        say("   ⇒ 判读：");
        say("     · 若「陆/海」（真陆、旧场说海）的均 mtnAmt 明显低于「陆/陆」 ⇒ 山地变体跟【旧场】走（D70 成立）");
        say("     · 若「海/陆」（真海、旧场说陆）的均 mtnAmt 明显高于「海/海」 ⇒ 海上挂了不存在的山地（D70 更严重）");
        say("");

        // 相关性
        double[] pe = new double[n], mr = new double[n], mm = new double[n];
        int k = 0;
        for (int z : zs) {
            for (int x : xs) {
                pe[k] = PlateField.elevationWithCell(x, z, SD, CELL);
                mr[k] = OrographyField.sample(x, z, SEED).relief01;
                mm[k] = LandformField.sample(x, z, SEED).mtnAmt;
                k++;
            }
        }
        say(String.format(LF, "C. 相关系数（n=%d）：corr(mtnAmt, 新高程) = %+.3f   corr(mtnAmt, 旧relief) = %+.3f",
            n, corr(mm, pe), corr(mm, mr)));
        say("   ⇒ 谁的相关性高，山地变体就跟谁走。（两者都高说明两场在这一带相关，样本不足；见 D）");
        say("");

        // 矛盾计数
        int c1 = 0, c2 = 0;
        for (int i = 0; i < n; i++) {
            if (pe[i] > 1500.0 && mm[i] < 0.2) c1++;      // 真山但没有山地变体
            if (pe[i] < 300.0 && mm[i] > 0.5) c2++;       // 平原却有强山地变体
        }
        say(String.format(LF, "D. 矛盾计数：新高程 >1500 m 但 mtnAmt <0.2 的点 = %d / %d；新高程 <300 m 但 mtnAmt >0.5 的点 = %d / %d",
            c1, n, c2, n));
        say("   （样本小 ⇒ 只作方向性证据；正式量化需要把网格加密，见记账）");
        say("");
        say("⚠ 记账：本探针只测量。样本 25 点（5x5，1M 间距 ⇒ 每点一个 LandformField 瓦片）——");
        say("   足以判断「跟谁走」，不足以给全局比例。全局比例要靠 V2BiomeField 的 LUT 普查。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static double corr(double[] a, double[] b) {
        int n = a.length;
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += a[i]; mb += b[i]; }
        ma /= n; mb /= n;
        double sab = 0, sa = 0, sb = 0;
        for (int i = 0; i < n; i++) {
            double da = a[i] - ma, db = b[i] - mb;
            sab += da * db; sa += da * da; sb += db * db;
        }
        return sab / Math.sqrt(Math.max(1e-30, sa * sb));
    }
}
