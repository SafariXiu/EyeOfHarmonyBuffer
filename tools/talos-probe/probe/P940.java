package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;

// P940 -- 冬季 2 倍过湿：先量 zonalSlTemp 在冬/夏 vs 观测表。
public class P940 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p940_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thJ = Atmosphere.theta(0.0);
        double thD = Atmosphere.theta(365.25 / 2.0);
        sb.append("P940: zonalSlTemp 冬/夏 vs 观测  PHASE_FROM_OBS=").append(Atmosphere.PHASE_FROM_OBS)
          .append("  ZONAL_SL_FROM_TABLE=").append(PrecipField.ZONAL_SL_FROM_TABLE).append("\n\n");
        sb.append("   lat | 模型JJA  观测JJA   差 | 模型DJF  观测DJF   差 | 模型冬-夏  观测冬-夏\n");
        for (double dg = 25; dg <= 85.0001; dg += 5) {
            double lr = Math.toRadians(dg);
            double mj = PrecipField.zonalSlTemp(lr, thJ), oj = ZonalTables.tZmSlMonth(lr, thJ);
            double md = PrecipField.zonalSlTemp(lr, thD), od = ZonalTables.tZmSlMonth(lr, thD);
            sb.append(String.format(LF, "  %5.0f | %7.2f %8.2f %+6.2f | %7.2f %8.2f %+6.2f | %+9.2f %+9.2f%n",
                dg, mj, oj, mj - oj, md, od, md - od, md - mj, od - oj));
        }
        sb.append("\n  判读：看【模型冬-夏】与【观测冬-夏】两列的差；再单看 DJF 那一列的偏差符号与量级。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}