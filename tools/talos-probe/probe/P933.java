package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P933 -- B：X（柱水汽）与它的两阶导在哪一段决定了洞与尖峰？
//   判据：若 columnWater 表本身在 60~70 度就有拐点（曲率变号），则问题在【表】；
//         若表平滑而 X'' 仍有双峰，则问题在【求导构造】。
public class P933 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static double D, DY;
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p933_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        D = Math.toRadians(PrecipField.dphiDeg()); DY = D * WorldContract.R_EFF;
        sb.append("P933: 柱水汽的两阶导（dphi=").append(PrecipField.dphiDeg())
          .append("  CW_SMOOTH=").append(PrecipField.CW_SMOOTH).append("）\n\n");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            sb.append(s == 0 ? "=== JJA ===\n" : "=== DJF ===\n");
            sb.append("   lat |   X(W)    X'(1e-3)   X''(1e-9) | 相邻 X 的斜率(1e-3/deg)  | GPCP夏 GPCP冬\n");
            for (double dg = 40; dg <= 86.0001; dg += 2) {
                double y = Math.toRadians(dg);
                double x  = PrecipField.eddyScalar(y, th);
                double xp = PrecipField.eddyDXdy(y, th, D);
                double xpp = (PrecipField.eddyDXdy(y + D, th, D) - PrecipField.eddyDXdy(y - D, th, D)) / (2.0 * DY);
                double slope = (PrecipField.eddyScalar(y + D, th) - PrecipField.eddyScalar(y - D, th))
                             / (2.0 * D * WorldContract.R_EFF) * 1e3 * Math.toDegrees(D * WorldContract.R_EFF) * 0 + 0;
                double sPerDeg = (PrecipField.eddyScalar(y + D, th) - PrecipField.eddyScalar(y - D, th)) / 10.0;
                sb.append(String.format(LF, "  %5.1f | %8.4f %10.5f %11.5f | %14.5f%n",
                    dg, x, xp * 1e3, xpp * 1e9, sPerDeg * 1e3));
            }
            sb.append("\n");
        }
        sb.append("  GPCP 参考（本地复算，mm/day）：夏 51.25->2.66 56.25->2.62 61.25->2.29 66.25->1.83 71.25->1.22\n");
        sb.append("                                冬 51.25->2.54 56.25->2.48 61.25->2.05 66.25->1.59 71.25->1.14\n");
        sb.append("  判读：看 X 的逐段斜率是否单调下降（平滑）。若斜率在某段反弹，那一段就是 X\'\' 变号的来源。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
