package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P921 -- §587：把 eddyMfc 的【分支与量级】钉死。%.6e 精度 + 开关值。
public class P921 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p921_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        sb.append("P921: eddyMfc 的分支与量级\n");
        sb.append(String.format(LF, "  EDDY_PLACEMENT_FROM_OBS = %s%n", flag("EDDY_PLACEMENT_FROM_OBS")));
        sb.append(String.format(LF, "  EDDY_FULL_DIVERGENCE    = %s%n", flag("EDDY_FULL_DIVERGENCE")));
        sb.append(String.format(LF, "  EDDY_MFC_REF = ")).append(num("EDDY_MFC_REF")).append("\n");
        sb.append(String.format(LF, "  EDDY_VAR=")).append(num("EDDY_VAR")).append("  EDDY_GRAD=").append(num("EDDY_GRAD")).append("  EDDY_GATE_MODE=").append(num("EDDY_GATE_MODE")).append("\n\n");
        sb.append("   lat  |  model夏(1e-5)   table夏 |  model冬(1e-5)   table冬 | 比值夏   比值冬\n");
        for (int i = 0; i <= 20; i++) {
            double d = 35.0 + i * 2.5;
            double lr = Math.toRadians(d);
            double mS = PrecipField.eddyMfc(lr, thS), mW = PrecipField.eddyMfc(lr, thW);
            double tS = ZonalTables.eddyMfcObsMonth(lr, thS), tW = ZonalTables.eddyMfcObsMonth(lr, thW);
            sb.append(String.format(LF, "  %5.1f | %14.6f %9.4f | %14.6f %9.4f | %8.6f %8.6f%n",
                d, mS * 1e5, tS, mW * 1e5, tW,
                Math.abs(tS) > 1e-9 ? mS / tS : Double.NaN,
                Math.abs(tW) > 1e-9 ? mW / tW : Double.NaN));
        }
        sb.append("\n  判读：若 比值 逐点恒为同一常数 ⇒ eddyMfc 就是【观测表 × 常数】；\n");
        sb.append("        若比值乱跳 ⇒ 走的是 modelShape 分支（模型自算），与观测表无关。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static String flag(String n) {
        try { return String.valueOf(PrecipField.class.getField(n).get(null)); } catch (Throwable t) { return "N/A"; }
    }
    static String num(String n) {
        try { return String.valueOf(PrecipField.class.getField(n).get(null)); } catch (Throwable t) { return "N/A"; }
    }
}
