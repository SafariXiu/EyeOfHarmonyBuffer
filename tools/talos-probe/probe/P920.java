package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P920 -- §587 最后一块：模型的 eddyMfc 与【观测表】并排扫到 90 度。
//   纯标量调用（几百次）⇒ 秒级。用来判定「模型是否只是把观测表的形状直接搬出来」。
public class P920 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p920_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        sb.append("P920: 模型 eddyMfc  vs  观测表 EDDY_MFC_OBS_MONTH   vs  GPCP（本地复算，NH）\n\n");
        sb.append("   lat  | model夏   table夏  GPCP夏 | model冬   table冬  GPCP冬\n");
        // GPCP v2.3 LTM 纬向平均（mm/day），NH，2.5 度网格中心（refs/split_gpcp.py 输出）
        double[][] gp = {{36.25,2.25,3.07},{38.75,2.34,3.27},{41.25,2.42,3.22},{43.75,2.41,3.07},
                         {46.25,2.47,2.77},{48.75,2.53,2.63},{51.25,2.66,2.54},{53.75,2.60,2.49},
                         {56.25,2.62,2.48},{58.75,2.51,2.39},{61.25,2.29,2.05},{63.75,2.11,1.84},
                         {66.25,1.83,1.59},{68.75,1.57,1.41},{71.25,1.22,1.14},{73.75,1.06,0.94}};
        for (int i = 0; i < 21; i++) {
            double d = 35.0 + i * 2.5;                       // 35.0 .. 85.0
            double lr = Math.toRadians(d);
            double mS, mW, tS, tW;
            try { mS = PrecipField.eddyMfc(lr, thS); } catch (Throwable t) { mS = Double.NaN; }
            try { mW = PrecipField.eddyMfc(lr, thW); } catch (Throwable t) { mW = Double.NaN; }
            tS = ZonalTables.eddyMfcObsMonth(lr, thS);
            tW = ZonalTables.eddyMfcObsMonth(lr, thW);
            String g = "--";
            for (double[] r : gp) { if (Math.abs(r[0] - d) < 1.3) g = String.format(LF, "%.2f/%.2f", r[1], r[2]); }
            String gs = "--", gw = "--";
            for (double[] r : gp) { if (Math.abs(r[0] - d) < 1.3) { gs = String.format(LF, "%.2f", r[1]); gw = String.format(LF, "%.2f", r[2]); } }
            sb.append(String.format(LF, "  %5.1f | %8.4f %8.4f %7s | %8.4f %8.4f %7s%n", d, mS, tS, gs, mW, tW, gw));
        }
        sb.append("\n  判读：若 model 与 table 形状逐点一致 ⇒ 中高纬降水就是这张表的直接搬用（无独立物理）。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
