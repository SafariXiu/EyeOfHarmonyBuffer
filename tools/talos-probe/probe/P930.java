package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P930 -- B 的正确起点：用【分派器】gateOf 而不是遗留的 stormGate 重测门的剖面。
//   ⚠ 记账：P923 与 P929 第一段都误用了 public 的 stormGate（= mode 0 遗留），
//   而实际生效的是 gateOf() -> stormGateCrit()。本探针两个都打，便于看出差别。
public class P930 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p930_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        sb.append("P930: 门的正确剖面（EDDY_GATE_MODE=").append(PrecipField.EDDY_GATE_MODE).append("）\n");
        sb.append("   gateOf  = 分派器（实际生效）    stormGate = mode0 遗留（P923 误用）\n\n");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            sb.append(s == 0 ? "=== JJA (theta=0) ===\n" : "=== DJF (theta=pi) ===\n");
            sb.append("   lat |  uZm(N)  | gateOf(N)  stormGate(N) |  uZm(S)  | gateOf(S)  stormGate(S)\n");
            for (double d = 0; d <= 90.0001; d += 2.5) {
                double lr = Math.toRadians(d);
                sb.append(String.format(LF, "  %5.1f | %8.4f | %10.5f %12.5f | %8.4f | %9.5f %12.5f%n",
                    d, ZonalTables.uZm(d, th), PrecipField.gateOf(lr, th), PrecipField.stormGate(lr, th),
                    ZonalTables.uZm(-d, th), PrecipField.gateOf(-lr, th), PrecipField.stormGate(-lr, th)));
            }
            sb.append("\n");
        }
        sb.append("  判读：用 gateOf 那一列看 50~70 度是否真的塌陷；stormGate 那一列只是对照。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
