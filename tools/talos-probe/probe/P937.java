package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P937 -- §609：季节振幅过冲出在哪一环？
//   当前 ZONAL_SL_FROM_TABLE=false（涌现）=> zonalSlTemp = 模型自己的基温 + seasonalAnomalyZonal(A_ZM_K)。
//   对照 ZonalTables.tZmSlMonth（观测月表）。逐纬度比【四季振幅】。
public class P937 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p937_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("P937: zonalSlTemp 的季节振幅（涌现）vs 观测月表  ZONAL_SL_FROM_TABLE=").append(PrecipField.ZONAL_SL_FROM_TABLE)
          .append("  COL_WATER_FROM_TABLE=").append(PrecipField.COL_WATER_FROM_TABLE).append("\n\n");
        // 四季：theta = 0(夏至) pi/2 pi(冬至) 3pi/2
        double[] ths = {0.0, Math.PI/2, Math.PI, 3*Math.PI/2};
        String[] tn = {"夏至", "秋分", "冬至", "春分"};
        sb.append("   lat |  模型四季(夏至/秋分/冬至/春分)          | 振幅  |  观测四季                                | 振幅  | 比值\n");
        for (double dg = 25; dg <= 85.0001; dg += 5) {
            double lr = Math.toRadians(dg);
            double mn = 1e9, mx = -1e9, mna = 1e9, mxa = -1e9;
            StringBuilder r1 = new StringBuilder(), r2 = new StringBuilder();
            for (int k = 0; k < 4; k++) {
                double v = PrecipField.zonalSlTemp(lr, ths[k]);
                double o = ZonalTables.tZmSlMonth(lr, ths[k]);
                if (v < mn) mn = v; if (v > mx) mx = v;
                if (o < mna) mna = o; if (o > mxa) mxa = o;
                r1.append(String.format(LF, "%7.2f", v));
                r2.append(String.format(LF, "%7.2f", o));
            }
            double ampM = 0.5 * (mx - mn), ampO = 0.5 * (mxa - mna);
            sb.append(String.format(LF, "  %5.0f | %s | %6.2f | %s | %6.2f | %5.2f%n",
                dg, r1.toString(), ampM, r2.toString(), ampO, ampO > 0.01 ? ampM / ampO : Double.NaN));
        }
        sb.append("\n  判读：比值 > 1 即模型振幅过大；看它随纬度怎么变。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
