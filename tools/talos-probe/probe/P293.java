package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P293：赤道连续性验收 —— 换 A(phi) 表之后不许再出现硬跳变（B2 子代理的 P295 回归）。 */
public class P293 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p293_report.txt"), "UTF-8");
        say(rep, "P293：赤道连续性验收（A(phi) 观测表）");
        say(rep, "");
        double eps = Math.toRadians(0.0001);
        double[] ks = {0.0, 0.15, 0.5, 1.0};
        double worst = 0;
        say(rep, String.format(LF, "  %-8s %-8s %14s %14s %12s", "Theta", "kappa", "Tano(-eps)", "Tano(+eps)", "跳变 K"));
        for (int si = 0; si < 8; si++) {
            double th = si * Math.PI / 4.0;
            for (double k : ks) {
                double a = Atmosphere.seasonalAnomaly(-eps, k, th);
                double b = Atmosphere.seasonalAnomaly(+eps, k, th);
                double jump = Math.abs(a - b);
                if (jump > worst) worst = jump;
                say(rep, String.format(LF, "  %-8.3f %-8.2f %14.6f %14.6f %12.6f", th, k, a, b, jump));
            }
        }
        say(rep, "");
        say(rep, String.format(LF, "  最大跳变 = %.8f K   %s", worst, worst < 1e-9 ? "★ 逐位连续 ✓" : "**仍有跳变**"));
        say(rep, "  参考：B2 子代理 P295 在旧表（0 度 = 0.5/0.3）下实测 0.3814 / 0.5387 / 0.8698 K");
        // 顺带：A(phi) 表本身
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %10s %10s", "纬度", "A_land K", "A_sea K"));
        for (int lat = 0; lat <= 90; lat += 10) {
            say(rep, String.format(LF, "  %-6d %10.2f %10.2f", lat,
                com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.aLand(lat),
                com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.aSea(lat)));
        }
        // 经向导数连续性（数值）
        say(rep, "");
        double dz = 1000.0;
        double th0 = 0.0;
        double dN = (Atmosphere.seasonalAnomaly(Math.toRadians(0.01) , 0.5, th0) - Atmosphere.seasonalAnomaly(0.0, 0.5, th0)) / (0.01/90.0*WorldContract.MAX_D);
        double dS = (Atmosphere.seasonalAnomaly(0.0, 0.5, th0) - Atmosphere.seasonalAnomaly(-Math.toRadians(0.01), 0.5, th0)) / (0.01/90.0*WorldContract.MAX_D);
        say(rep, String.format(LF, "  赤道处 dT'/dz：北侧 %+.4e  南侧 %+.4e K/m  （应当接近）", dN, dS));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P293] " + s); rep.println("[P293] " + s); }
}
