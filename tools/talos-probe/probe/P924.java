package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P924 -- §592 修法 #1/#2 的【秒级可达性验证】。
//   纪律（§592 五）：打开开关后先读【目标量】；目标量不变 => 先查路径可达性，再谈物理。
//   本探针同时验证【默认下逐位中立】。
public class P924 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p924_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0);
        double dd = Math.toRadians(PrecipField.dphiDeg());
        sb.append("P924: §592 修法 #1/#2 的可达性验证  (theta=JJA)\n\n");

        // ---------- 修法 #1：CW_SMOOTH ----------
        boolean savedCW = PrecipField.CW_SMOOTH;
        sb.append("=== 修法 #1: CW_SMOOTH 是否接到 eddyScalar 上 ===\n");
        sb.append("   lat | columnWater   eddyScalar(off)  eddyScalar(on) | eddyMfc(off)   eddyMfc(on)\n");
        int cwSameOff = 0, cwDiffOn = 0, n = 0;
        for (double deg = 30; deg <= 85.0001; deg += 2.5) {
            double lr = Math.toRadians(deg);
            double cw = PrecipField.columnWater(lr, thS);
            PrecipField.CW_SMOOTH = false;
            double eOff = PrecipField.eddyScalar(lr, thS), mOff = PrecipField.eddyMfc(lr, thS);
            PrecipField.CW_SMOOTH = true;
            double eOn = PrecipField.eddyScalar(lr, thS), mOn = PrecipField.eddyMfc(lr, thS);
            if (eOff == cw) cwSameOff++;
            if (eOn != eOff) cwDiffOn++;
            n++;
            sb.append(String.format(LF, "  %5.1f | %11.5f %16.5f %14.5f | %12.5f %12.5f%n",
                deg, cw, eOff, eOn, mOff * 1e5, mOn * 1e5));
        }
        PrecipField.CW_SMOOTH = savedCW;
        sb.append(String.format(LF, "  检查1 eddyScalar(关) 逐位等于 columnWater : %d/%d  => %s%n", cwSameOff, n, cwSameOff == n ? "OK 默认中立" : "**FAIL**"));
        sb.append(String.format(LF, "  检查2 eddyScalar(开) 与(关) 不同           : %d/%d  => %s%n%n", cwDiffOn, n, cwDiffOn > 0 ? "OK 已接上" : "**FAIL 仍是空操作**"));

        // ---------- 修法 #2：EDDY_T_SMOOTH ----------
        boolean savedT = PrecipField.EDDY_T_SMOOTH;
        sb.append("=== 修法 #2: EDDY_T_SMOOTH 是否接到当前温度源(t850)上 ===\n");
        sb.append("   lat |   raw dTdy(1e-6)  t850Slope(on) | sigma(off)   sigma(on)  |   相对差\n");
        int tSameOff = 0, tDiffOn = 0, m2 = 0;
        double maxRel = 0;
        for (double deg = 30; deg <= 85.0001; deg += 2.5) {
            double lr = Math.toRadians(deg);
            double raw = (com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.t850Month(lr + dd, thS)
                        - com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.t850Month(lr - dd, thS))
                        / (2.0 * dd * WorldContract.R_EFF);
            PrecipField.EDDY_T_SMOOTH = false;
            double sOff = PrecipField.t850Slope(lr, thS), gOff = PrecipField.eadyGrowth(lr, thS);
            PrecipField.EDDY_T_SMOOTH = true;
            double sOn = PrecipField.t850Slope(lr, thS), gOn = PrecipField.eadyGrowth(lr, thS);
            if (sOff == raw) tSameOff++;
            if (sOn != sOff) tDiffOn++;
            double rel = (raw != 0.0) ? Math.abs(sOn - raw) / Math.abs(raw) : 0.0;
            if (rel > maxRel) maxRel = rel;
            m2++;
            if (deg >= 50 && deg <= 70)
                sb.append(String.format(LF, "  %5.1f | %14.5f %14.5f | %10.6f %10.6f | %8.3f%%%n",
                    deg, raw * 1e6, sOn * 1e6, gOff * 1e5, gOn * 1e5, 100.0 * rel));
        }
        PrecipField.EDDY_T_SMOOTH = savedT;
        sb.append(String.format(LF, "  检查3 t850Slope(关) 逐位等于 raw 中心差分    : %d/%d  => %s%n", tSameOff, m2, tSameOff == m2 ? "OK 默认中立" : "**FAIL**"));
        sb.append(String.format(LF, "  检查4 t850Slope(开) 与(关) 不同              : %d/%d  => %s%n", tDiffOn, m2, tDiffOn > 0 ? "OK 已接上" : "**FAIL 仍是空操作**"));
        sb.append(String.format(LF, "  最大相对差 = %.3f%%（50~70 度区间已打印）%n%n", 100.0 * maxRel));

        boolean pass = (cwSameOff == n) && (cwDiffOn > 0) && (tSameOff == m2) && (tDiffOn > 0);
        sb.append("VERDICT=").append(pass ? "BOTH_REWIRES_LIVE" : "**NOT_LIVE**").append("\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
