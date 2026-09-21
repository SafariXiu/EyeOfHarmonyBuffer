package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P932 -- B 的第 2 步：σ 在夏季向极【升】是物理还是缺陷？
//   σ = EADY_COEF * |f| * |dU/dz| / N，dU/dz 由热成风从 t850 的经向梯度给出。
//   若 t850 的经向梯度在夏季极地【不弱】，那是温度表的形状问题；若梯度正常而 σ 仍升，那是公式链的问题。
public class P932 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p932_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        double d = Math.toRadians(5.0);
        sb.append("P932: σ 的因子链（夏季极地）\n");
        sb.append("  EADY_COEF=").append(PrecipField.EADY_COEF).append("  dphi=").append(PrecipField.dphiDeg()).append("\n\n");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            sb.append(s == 0 ? "=== JJA ===\n" : "=== DJF ===\n");
            sb.append("   lat | t850(K)  | dT/dy(1e-6 K/m) |  |f|(1e-5)  | N(1e-3) | sigma(1e-5) | L_d(km) |   K(1e7)\n");
            for (double dg = 30; dg <= 85.0001; dg += 2.5) {
                double lr = Math.toRadians(dg);
                double t = ZonalTables.t850Month(lr, th);
                double dTdy = (ZonalTables.t850Month(lr + d, th) - ZonalTables.t850Month(lr - d, th)) / (2.0 * d * WorldContract.R_EFF);
                double f = Math.abs(WorldContract.coriolis(lr));
                double N = PrecipField.staticN(lr, th);
                double sg = PrecipField.eadyGrowth(lr, th);
                double ld = PrecipField.deformRadius(lr, th);
                double K = PrecipField.EDDY_MIX * sg * ld * ld;
                sb.append(String.format(LF, "  %5.1f | %9.3f | %15.4f | %11.4f | %7.4f | %11.5f | %7.1f | %8.4f%n",
                    dg, t, dTdy * 1e6, f * 1e5, N * 1e3, sg * 1e5, ld / 1000.0, K / 1e7));
            }
            sb.append("\n");
        }
        sb.append("  判读：\n");
        sb.append("   · 在夏季，极地的 dT/dy 应当【弱】=> |dT/dy| 向极递减。\n");
        sb.append("   · 若 sigma 向极【升】，看是 dT/dy 反了、还是 |f|（必然升）与 N 的组合把 sigma 抬起来。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
