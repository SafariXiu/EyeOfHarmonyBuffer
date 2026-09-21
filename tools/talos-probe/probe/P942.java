package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;

// P942 -- X（模型柱水汽）vs W_COL_MONTH（观测柱水汽表），冬/夏分开。
public class P942 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p942_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thJ = Atmosphere.theta(0.0), thD = Atmosphere.theta(365.25 / 2.0);
        sb.append("P942: 模型的 X（柱水汽）vs 观测表 W_COL_MONTH\n\n");
        sb.append("   lat |  模型X夏  观测X夏  比 |  模型X冬  观测X冬  比 | 冬/夏(模型) 冬/夏(观测)\n");
        for (double dg = 25; dg <= 75.0001; dg += 5) {
            double lr = Math.toRadians(dg);
            double mj = PrecipField.eddyScalar(lr, thJ), oj = ZonalTables.wColMonth(lr, thJ);
            double md = PrecipField.eddyScalar(lr, thD), od = ZonalTables.wColMonth(lr, thD);
            sb.append(String.format(LF, "  %5.0f | %8.3f %8.3f %5.2f | %8.3f %8.3f %5.2f | %10.2f %12.2f%n",
                dg, mj, oj, mj / oj, md, od, md / od, md / mj, od / oj));
        }
        sb.append("\n  判读：看【模型X冬/观测X冬】的比值。>1 即冬季水汽偏多。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}