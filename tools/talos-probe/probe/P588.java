package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P588 -- S1 核心：Held-Hou 胞边界由【求解】得到，取代查表 + 硬平移。
public class P588 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P588] " + s); System.out.println("[P588] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p588_report.txt"), "UTF-8");
        say("P588: S1 Held-Hou 胞边界求解（取代查表 + 硬平移）");

        double sc = HadleyCell.selfCheck();
        say("");
        say(String.format(LF, "  [A] selfCheck() = %.3e   %s", sc, sc < 1e-4 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      （含：小角极限、w 的两个零点、w 与 dPsi/dX 的比例、Psi 极值 0.2862）"));

        say("");
        say("  [B] 复现研究给出的 phi_H(DeltaT) 表（H=10 km, T0=300 K）");
        say("      DeltaT   R        phi_H精确   phi_H小角    phi_0=phi_H/sqrt5   phi_0/phi_H   二分步数  残差");
        double[] dts = {25, 50, 75, 100, 125, 150};
        for (double dt : dts) {
            double R = HadleyCell.rFromDeltaT(dt);
            double y = HadleyCell.solveSinPhiH(R);
            double ph = Math.toDegrees(Math.asin(y));
            double phSA = Math.toDegrees(Math.asin(HadleyCell.smallAngleSinPhiH(R)));
            say(String.format(LF, "      %5.0f K  %.4f   %7.2f°     %7.2f°      %7.2f°          %.4f       %3d    %.1e",
                dt, R, ph, phSA, ph / Math.sqrt(5.0), 1.0 / Math.sqrt(5.0),
                HadleyCell.lastBisectIters, Math.abs(HadleyCell.lastResidual)));
        }
        say("      研究给出的参考值：25K->14.1°/6.3°,  50K->19.5°/8.7°,  100K->26.6°/11.8°,  150K->31.4°/14.0°");

        say("");
        say("  [C] 模型自己的 T_E(phi)：赤道-极温差 => R => phi_H");
        double tEq = Atmosphere.zonalMeanSeaLevelK(0.0);
        double tPole = Atmosphere.zonalMeanSeaLevelK(Math.toRadians(88.0));
        double dT = tEq - tPole;
        double R = HadleyCell.rFromDeltaT(dT);
        double ph = Math.toDegrees(Math.asin(HadleyCell.solveSinPhiH(R)));
        double p0 = ph / Math.sqrt(5.0);
        say(String.format(LF, "      T_E(0)=%.2f K   T_E(88N)=%.2f K   DeltaT=%.2f K", tEq, tPole, dT));
        say(String.format(LF, "      => dH=%.4f  R=%.5f  phi_H=%.2f°  phi_0=%.2f°", dT / HadleyCell.T0, R, ph, p0));

        say("");
        say("  [D] 与现值对照：现有 W_ZM 的升降边界 vs 求解边界");
        say("      现有：ZonalTables.wZm 表的升降交界在 |lat|=18.75°，再被 ITCZ_MIGRATION 平移 +10° => 28.75°N");
        say(String.format(LF, "      求解：phi_0=%.2f° => 该纬度以北是【下沉】；28.75°N %s",
            p0, 28.75 > p0 ? "落【下沉】区 ✓（现有把它当上升，正是撒哈拉问题的根）" : "落上升区"));
        say("");
        say("      形状对照 w(X)，X=|lat|/phi_H（采样）：");
        say("        lat    现有表 wZm     求解 wShape(X)");
        for (double latd = 0; latd <= 35; latd += 2.5) {
            double now = ZonalTables.wZm(latd);
            double X = latd / ph;
            say(String.format(LF, "       %5.1f    %+9.5f      %+9.5f", latd, now, HadleyCell.wShape(X)));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
