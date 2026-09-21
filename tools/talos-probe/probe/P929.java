package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P929 -- B 的第一步：把 stormGateCrit 的【几何量】直接测出来，不再靠读代码推。
//   gate = S((|phi|-ac)/w) * (1 - S((|phi|-|pp|)/w))，S = smoothstep，clamp 到 [0,1]
public class P929 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p929_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0)};
        String[] tn = {"JJA (theta=0)", "DJF (theta=pi)"};
        sb.append("P929: stormGateCrit 的几何量（EDDY_GATE_MODE=").append(PrecipField.EDDY_GATE_MODE)
          .append("，U0_STORM=").append(PrecipField.U0_STORM).append("）\n\n");
        for (int s = 0; s < 2; s++) {
            double th = ths[s];
            sb.append("=== ").append(tn[s]).append(" ===\n");
            for (int hi = 0; hi < 2; hi++) {
                boolean north = (hi == 0);
                double pc = PrecipField.criticalLatRad(th, north);
                double pp = PrecipField.polarEdgeLatRad(th, north);
                double w = PrecipField.rhinesWidthRad(pc, th);
                for (int k = 0; k < 2; k++) w = PrecipField.rhinesWidthRad(pc + 0.5 * w * (north ? 1.0 : -1.0), th);
                double wc = Math.max(Math.toRadians(2.0), Math.min(Math.toRadians(20.0), w));
                sb.append(String.format(LF, "  %s : 临界纬度 ac = %s 度   极侧边缘 pp = %s 度   Rhines w = %.3f 度 (clamp 后 %.3f 度)%n",
                    north ? "NH" : "SH",
                    Double.isNaN(pc) ? "NaN" : String.format(LF, "%.3f", Math.toDegrees(Math.abs(pc))),
                    Double.isNaN(pp) ? "NaN" : String.format(LF, "%.3f", Math.toDegrees(Math.abs(pp))),
                    Math.toDegrees(w), Math.toDegrees(wc)));
            }
            sb.append("   lat |  uZm(lat)  |  gate  | S((a-ac)/w)  1-S((a-pp)/w)\n");
            for (double d = 0; d <= 90.0001; d += 5) {
                double lr = Math.toRadians(d);
                double u = ZonalTables.uZm(d, th);
                double g = PrecipField.gateOf(lr, th);   // §598 修正：分派器（原为遗留的 stormGate）
                double pc = PrecipField.criticalLatRad(th, true);
                double pp = PrecipField.polarEdgeLatRad(th, true);
                double w = PrecipField.rhinesWidthRad(pc, th);
                for (int k = 0; k < 2; k++) w = PrecipField.rhinesWidthRad(pc + 0.5 * w, th);
                double wc = Math.max(Math.toRadians(2.0), Math.min(Math.toRadians(20.0), w));
                double lo = smooth((lr - pc) / wc), hiFac = Double.isNaN(pp) ? 1.0 : (1.0 - smooth((lr - Math.abs(pp)) / wc));
                sb.append(String.format(LF, "  %4.0f | %10.4f | %6.4f | %12.5f %13.5f%n", d, u, g, lo, hiFac));
            }
            sb.append("\n");
        }
        sb.append("  判读：gate 的两因子分别看；哪一因子把 50~70 度压掉，就是 B 的着力点。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static double smooth(double x) { x = x < 0 ? 0 : (x > 1 ? 1 : x); return x * x * (3 - 2 * x); }
}
