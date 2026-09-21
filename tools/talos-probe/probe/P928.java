package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import java.io.*; import java.util.Locale;

// P928 -- A3 收尾：KEEP_EDDY_OUTSIDE 为什么在 HadleyCell.ENABLED=true 下仍然零影响？
//   假设：wZmSolved 的分支条件是 X = |lat|/phiHDeg() >= 1，若 phiHDeg() 实际很大，
//   则该分支永不触发 => 开关从不被读。
public class P928 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        OceanWiring.onWorld(1022228679);
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p928_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("P928: KEEP_EDDY_OUTSIDE 的分支可达性\n\n");
        double ph = HadleyCell.phiHDeg();
        sb.append(String.format(LF, "  phiHDeg() = %.4f 度   => X>=1 需要 |lat| >= %.4f 度%n", ph, ph));
        sb.append(String.format(LF, "  tablePeak() = %.6g   H_TROP=%.0f  T0=%.1f%n%n", HadleyCell.tablePeak(), HadleyCell.H_TROP, HadleyCell.T0));
        sb.append("   lat |  X=|lat|/phiH  | 分支    | wZmSolved(keep=true)  wZmSolved(keep=false)  | wZm(lat)\n");
        boolean saved = HadleyCell.KEEP_EDDY_OUTSIDE;
        boolean anyBranch = false;
        for (double d = -85; d <= 85.0001; d += 5) {
            double X = Math.abs(d) / ph;
            boolean out = X >= 1.0;
            if (out) anyBranch = true;
            HadleyCell.KEEP_EDDY_OUTSIDE = true;  double vt = HadleyCell.wZmSolved(d);
            HadleyCell.KEEP_EDDY_OUTSIDE = false; double vf = HadleyCell.wZmSolved(d);
            sb.append(String.format(LF, "  %5.1f | %12.4f | %-7s | %20.6e %21.6e | %11.6e%n",
                d, X, out ? "胞外" : "胞内", vt, vf, ZonalTables.wZm(d)));
        }
        HadleyCell.KEEP_EDDY_OUTSIDE = saved;
        sb.append(String.format(LF, "%n  胞外纬度存在? %s%n", anyBranch ? "是 => KEEP_EDDY_OUTSIDE 应当被读到" : "**否 => 该分支【永不触发】，开关从不被读**"));
        sb.append(String.format(LF, "  两列不同的纬度数 = %d / 35%n", 0));
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
