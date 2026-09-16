package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P496：**中纬雨带的两项分解** —— 到底是「平均项」还是「涡动项」在决定雨带的位置？
 *
 * <p>为什么必须先量这个：步骤 1（给 W_ZM 加观测季节迁移）只动**平均项**。
 * 而 P296 测的是**两者之和**。如果雨带位置其实由**涡动项**钉住，那么步骤 1 再怎么做也修不好 B2.b ——
 * 这个判断必须在动生产代码之前拿到数。
 *
 * <p>分解口径（目标纪律：分解类仪器必须覆盖**求导之后的量**）：
 * <pre>
 *   平均项  w_z(phi,theta) = ZonalTables.wZm( 到直射点的平移 )
 *   涡动项  w_e(phi,theta) = PrecipField.eddyWEquivalent(phi,theta)   ← 它含 d2W/dphi2（二阶导）
 *   合计    w_z + w_e
 * </pre>
 * 同时打印**每一项自己的峰位**与**合计的峰位**，两季各一次。
 */
public class P496 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P496] " + s); rep.flush(); System.out.println("[P496] " + s); System.out.flush(); }

    static double meanTerm(double latDeg, double theta) {
        double shifted = latDeg - Math.toDegrees(PrecipField.precipSubsolarLat(theta));
        return ZonalTables.wZm(shifted);
    }
    static double eddyTerm(double latDeg, double theta) {
        return PrecipField.eddyWEquivalent(Math.toRadians(latDeg), theta);
    }

    /** 在 20~72 度里找 argmax（value > 0 才算；全负返回 NaN）。 */
    static double peak(double theta, int which) {
        double best = 0, bestLat = Double.NaN;
        for (double la = 20.0; la <= 72.001; la += 0.25) {
            double v = which == 0 ? meanTerm(la, theta)
                     : which == 1 ? eddyTerm(la, theta)
                     : meanTerm(la, theta) + eddyTerm(la, theta);
            if (v > best) { best = v; bestLat = la; }
        }
        return bestLat;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p496_report.txt"), "UTF-8");
        say("P496：中纬雨带的两项分解（平均项 vs 涡动项）");
        say(String.format(LF, "  ITCZ_MIGRATION=%.4f rad   U0_STORM=%.3f   EDDY_DPHI_DEG=%.1f",
                PrecipField.ITCZ_MIGRATION, PrecipField.U0_STORM, PrecipField.EDDY_DPHI_DEG));
        say(String.format(LF, "  precipSubsolarLat(0)=%.2f deg   precipSubsolarLat(pi)=%.2f deg",
                Math.toDegrees(PrecipField.precipSubsolarLat(0.0)), Math.toDegrees(PrecipField.precipSubsolarLat(Math.PI))));
        say("");
        double[][] pk = new double[2][3];
        String[] nm = {"平均项 w_z", "涡动项 w_e", "合计 w_z+w_e"};
        for (int s = 0; s < 2; s++) {
            double th = s == 0 ? 0.0 : Math.PI;
            say(String.format(LF, "--- theta = %.3f (%s) ---", th, s == 0 ? "北半球夏至" : "北半球冬至"));
            say(String.format(LF, "  %6s %12s %12s %12s", "lat", "w_z(1e-3)", "w_e(1e-3)", "sum(1e-3)"));
            for (double la = 24.0; la <= 72.001; la += 4.0) {
                say(String.format(LF, "  %6.1f %12.3f %12.3f %12.3f", la,
                        1e3 * meanTerm(la, th), 1e3 * eddyTerm(la, th),
                        1e3 * (meanTerm(la, th) + eddyTerm(la, th))));
            }
            for (int w = 0; w < 3; w++) {
                pk[s][w] = peak(th, w);
                say(String.format(LF, "  ⇒ %-12s 峰位 = %.2f 度", nm[w], pk[s][w]));
            }
            say("");
        }
        say("=============== 汇总：峰位与季节迁移（冬 − 夏）===============");
        say(String.format(LF, "  %-14s %8s %8s %10s", "项", "夏", "冬", "迁移"));
        for (int w = 0; w < 3; w++)
            say(String.format(LF, "  %-14s %8.2f %8.2f %+10.2f", nm[w], pk[0][w], pk[1][w], pk[1][w] - pk[0][w]));
        say("");
        say("★ 判读（写在这里，跑之前定）：");
        say("   · 若**涡动项**的峰位两季几乎不动（|迁移| < 3 度）而它又是合计峰位的决定者");
        say("     ⇒ **步骤 1（只改平均项）修不好 B2.b**，必须先做步骤 3（温度场季节形状）；");
        say("   · 若平均项主导且它已经会迁移 ⇒ 步骤 1 的方向对，继续。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
