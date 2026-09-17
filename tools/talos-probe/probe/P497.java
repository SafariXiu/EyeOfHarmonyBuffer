package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P497：**季风缺口的量化** —— 模型到底有没有「陆地夏季多雨、冬季少雨」这件事？
 *
 * <p>为什么必须先量这个：用户要的是「自己长出季风」。季风 = **风向反转 + 干湿季反转**。
 * B3（P284）已经证明**风向/气压反转是有的**（内陆 77% 反相、p 反号 100%）；</br>
 * 那么缺的到底是**雨的那一半**，还是别的？本探针按「陆地 vs 海洋 x 两个至日」把降水拆开，
 * 给出模型自己的季风指数，作为与文献对照的靶子。
 *
 * <p>口径（写死）：
 * <ul>
 *   <li>陆地 = {@code kappaAt > 0.8}；海洋 = {@code kappaAt < 0.2}；两者之间不计（避免海岸混合）</li>
 *   <li>每个纬度在 x 上取 N 个点（跨越多个板块格），对同类点取算术平均</li>
 *   <li>降水用**生产函数** {@code PrecipField.mmPerDay}，GRAD_STEP 用生产值</li>
 * </ul>
 */
public class P497 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P497] " + s); rep.flush(); System.out.println("[P497] " + s); System.out.flush(); }

    static final int NX = 120;
    static final int XSPAN = 60_000_000;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p497_report.txt"), "UTF-8");
        say("P497：季风缺口的量化 —— 陆地 vs 海洋的降水，两个至日");
        say(String.format(LF, "  EDDY_PLACEMENT_FROM_OBS=%s  EDDY_MFC_REF=%.3e  LANDS_ANNUAL_IN_PRESSURE=%s",
                PrecipField.EDDY_PLACEMENT_FROM_OBS, PrecipField.EDDY_MFC_REF, Atmosphere.LANDS_ANNUAL_IN_PRESSURE));
        say("");
        say(String.format(LF, "  %5s %10s %10s %10s %10s %12s", "lat", "陆_夏", "陆_冬", "海_夏", "海_冬", "陆(夏-冬)"));
        double[] mi = new double[2];
        for (int la = 5; la <= 45; la += 5) {
            int z = (int) Math.round(la / 90.0 * WorldContract.MAX_D);
            double[] sum = new double[4]; int[] cnt = new int[4];
            double[] sumW = new double[2]; int[] cntW = new int[2];
            for (int i = 0; i < NX; i++) {
                int x = (int) ((long) i * XSPAN / NX);
                double k = Atmosphere.kappaAt(x, z, SD, CELL);
                boolean land = k > 0.8, sea = k < 0.2;
                if (!land && !sea) continue;
                int base = land ? 0 : 2;
                double pS = PrecipField.mmPerDay(x, z, SD, CELL, 0.0, 500_000);
                double pW = PrecipField.mmPerDay(x, z, SD, CELL, Math.PI, 500_000);
                sum[base] += pS; sum[base + 1] += pW; cnt[base]++; cnt[base + 1]++;
            }
            double ls = cnt[0] > 0 ? sum[0] / cnt[0] : Double.NaN;
            double lw = cnt[1] > 0 ? sum[1] / cnt[1] : Double.NaN;
            double ss = cnt[2] > 0 ? sum[2] / cnt[2] : Double.NaN;
            double sw = cnt[3] > 0 ? sum[3] / cnt[3] : Double.NaN;
            say(String.format(LF, "  %5d %10.2f %10.2f %10.2f %10.2f %12.2f   (n=%d/%d/%d/%d)",
                    la, ls, lw, ss, sw, ls - lw, cnt[0], cnt[1], cnt[2], cnt[3]));
            if (la >= 15 && la <= 30) { mi[0] += (ls - lw); mi[1]++; }
        }
        say("");
        if (mi[1] > 0) say(String.format(LF, "  ⇒ **15~30 度的陆地季风指数（夏 − 冬）= %+.2f mm/day**", mi[0] / mi[1]));
        say("  对照（真实地球，陆地站点月降水）：印度/萨赫勒 20 度带 夏 6~8 mm/day、冬 0~0.5 mm/day");
        say("  ⇒ 对照值是 **+6 ~ +8 mm/day**；若模型只有 0~1 ⇒ **雨的季风反转基本不存在**。");
        say("");
        say("★ 判读（写在这里，跑之前定）：");
        say("   · 若 15~30 度的「陆地夏 − 陆地冬」 < 1 mm/day  ⇒ 缺的是**降水对热力反转的响应**；");
        say("   · 若 ≥ 3 mm/day 但符号/位置不对 ⇒ 缺的是**季节相位或纬度放置**。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
