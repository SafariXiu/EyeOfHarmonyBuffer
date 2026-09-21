package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P936 -- §604：把 EDDY_MASK_OUTSIDE 接进 Var 路径之后，30~42 度的「门梯度泄漏」消掉了吗？
//   对比 mask=false（新接线，等价原路）与 mask=true（掩码在外）的 MFC 剖面。
public class P936 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p936_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        boolean saved = PrecipField.EDDY_MASK_OUTSIDE;
        sb.append("P936: 掩码在外 vs 在内（EDDY_GATE_MODE=").append(PrecipField.EDDY_GATE_MODE)
          .append("  EDDY_VAR=").append(PrecipField.EDDY_VAR).append("）\n\n");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            sb.append(s == 0 ? "=== JJA ===\n" : "=== DJF ===\n");
            sb.append("   lat | gateOf |  MFC(掩码在内)(1e-5)  MFC(掩码在外)(1e-5) |  差值    | 负瓣?\n");
            int negIn = 0, negOut = 0;
            StringBuilder rows = new StringBuilder();
            for (double dg = 28; dg <= 86.0001; dg += 2) {
                PrecipField.EDDY_MASK_OUTSIDE = false;
                double mIn = PrecipField.eddyMfc(Math.toRadians(dg), th);
                PrecipField.EDDY_MASK_OUTSIDE = true;
                double mOut = PrecipField.eddyMfc(Math.toRadians(dg), th);
                double g = PrecipField.gateOf(Math.toRadians(dg), th);
                if (dg >= 50 && mIn < 0) negIn++;
                if (dg >= 50 && mOut < 0) negOut++;
                rows.append(String.format(LF, "  %5.1f | %6.3f | %20.5f %21.5f | %+9.5f | %s%n",
                    dg, g, mIn * 1e5, mOut * 1e5, (mOut - mIn) * 1e5, mOut < 0 ? "负" : ""));
            }
            sb.append(rows);
            sb.append(String.format(LF, "  ⇒ 50 度以上的负瓣数： 掩码在内 %d 个，掩码在外 %d 个%n%n", negIn, negOut));
        }
        PrecipField.EDDY_MASK_OUTSIDE = saved;
        sb.append("  判读：掩码在外 => gate\' 项恒为 0 => 30~42 度的泄漏应当消失（那里 |差值| 最大）。\n");
        sb.append("        同时看 50 度以上的负瓣（= §601 的「洞」）有没有减少。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
