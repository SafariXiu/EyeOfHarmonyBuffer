package probe;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P916 -- §586 仪器判决：Zonal.profile 的纬度分辨率是否就是那个「7.2 度」？
//   零成本：只碰纯函数 latOfRow / WorldContract.latOf，不调 Zonal.profile。
public class P916 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p916_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("P916: Zonal.profile 的纬度网格\n");
        sb.append("  NZ=").append(Zonal.NZ).append("  NX=").append(Zonal.NX)
          .append("  XSPAN=").append(Zonal.XSPAN).append("\n");
        sb.append("  Z_CYCLE=").append(WorldContract.Z_CYCLE).append("\n\n");
        sb.append("  行号 r   声明纬度 latOfRow(r)   相邻行间距(度)\n");
        double prev = Double.NaN; int above = 0;
        for (int r = 0; r < Zonal.NZ; r++) {
            double lat = Math.toDegrees(Zonal.latOfRow(r));
            double d = Double.isNaN(prev) ? 0.0 : Math.abs(lat - prev);
            sb.append(String.format(LF, "  %5d %14.2f %16.2f%n", r, lat, d));
            prev = lat;
        }
        sb.append("\n  === 与 P296 实测对照 ===\n");
        sb.append("  P296 夏峰 @46.8  -> latOfRow(6) = ").append(String.format(LF,"%.2f", Math.toDegrees(Zonal.latOfRow(6)))).append("\n");
        sb.append("  P296 冬峰 @39.6  -> latOfRow(5) = ").append(String.format(LF,"%.2f", Math.toDegrees(Zonal.latOfRow(5)))).append("\n");
        sb.append("  P296 ITCZ 夏 @10.8 -> latOfRow(1) = ").append(String.format(LF,"%.2f", Math.toDegrees(Zonal.latOfRow(1)))).append("\n");
        sb.append("  P296 ITCZ 冬 @ 3.6 -> latOfRow(0) = ").append(String.format(LF,"%.2f", Math.toDegrees(Zonal.latOfRow(0)))).append("\n");
        sb.append("\n  判读：若这些全部逐位吻合 ⇒ 「7.2 度」= 恰好一格 ⇒ 判据要求 >=10 度在构造上不可达。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
