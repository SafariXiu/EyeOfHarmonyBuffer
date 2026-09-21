package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P938 -- (a) 相位：把观测表 T_ZM_SL_MONTH 的首谐波相位拟出来，并把【约定】钉死。
//   theta=0 是北半球夏至；表是月优先（m=0 是 1 月）。两者的换算必须由数据定，不许猜。
public class P938 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p938_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("P938: 观测表首谐波相位（T_ZM_SL_MONTH）\n");
        sb.append("  SEASON_SHAPE_PHI0 = ").append(ZonalTables.SEASON_SHAPE_PHI0).append("\n");
        sb.append("  DAYS_PER_YEAR = ").append(WorldContract.DAYS_PER_YEAR).append("\n\n");
        sb.append("   lat |  amp(K) | 峰月(0=Jan) | phi_month(度) | 换算到 theta 原点(度) | 换算到滞后天数\n");
        for (int j = 0; j <= 18; j += 2) {
            double sc = 0, ss = 0, mean = 0;
            for (int m = 0; m < 12; m++) {
                double v = ZonalTables.T_ZM_SL_MONTH[m * 19 + j];
                double aa = 2.0 * Math.PI * m / 12.0;
                sc += v * Math.cos(aa); ss += v * Math.sin(aa); mean += v;
            }
            mean /= 12.0;
            double amp = 2.0 * Math.hypot(sc, ss) / 12.0;
            double phiMonth = Math.atan2(ss, sc);                 // 峰在 m = phiMonth/(2pi)*12
            double peakMonth = phiMonth / (2.0 * Math.PI) * 12.0;
            // 约定：theta = 2*pi*day/DPY，day=0 是夏至；表里 m 对应 theta = 2*pi*(m/12 - PHI0)
            // => 表的相位 phiMonth 对应的 theta 相位 = phiMonth - 2*pi*PHI0 ? 两种可能，两个都打
            double thetaA = Math.toDegrees(phiMonth) - 360.0 * ZonalTables.SEASON_SHAPE_PHI0;
            double thetaB = Math.toDegrees(phiMonth) + 360.0 * ZonalTables.SEASON_SHAPE_PHI0;
            double lagA = thetaA / 360.0 * WorldContract.DAYS_PER_YEAR;
            double lagB = thetaB / 360.0 * WorldContract.DAYS_PER_YEAR;
            sb.append(String.format(LF, "  %5.0f | %7.3f | %11.2f | %13.2f | %10.2f / %10.2f | %8.1f / %8.1f%n",
                j * 5.0, amp, peakMonth, Math.toDegrees(phiMonth), thetaA, thetaB, lagA, lagB));
        }
        sb.append("\n  判读：夏至在 6 月（m=5.5 左右）。若某列的【峰月】落在 5~7 月，那列的约定就是对的。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}