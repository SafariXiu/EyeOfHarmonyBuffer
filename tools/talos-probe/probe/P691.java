package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P691 -- §485/§599 复核版：涡动符号到底跟不跟随 curv(W)？
//   ⚠ 原版（§485 用）的三处问题，本版逐一记账：
//     ① :9 的前提「eddyMfc 符号 = curv(W) 符号」只在【乘积形式 K*curv*gate】下成立，
//        而生产走【散度形式】d(gate*K*X')/dy = K'X' + K*X'' + gate'KX'  ⇒ 前提陈旧。
//     ② :45 打印的 gate 用 stormGate（mode 0 遗留）⇒ 应调分派器 gateOf。
//     ③ :17 用 EDDY_DPHI_DEG（final 5.0）而非 dphiDeg()（可被 EDDY_DPHI_DEG_V 覆盖）。
//   本版把三条都修正，并把【乘积形式】与【散度形式】并排，看哪一个才等于 eddyMfc。
public class P691 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static double D, DY;
    static void say(String s) { rep.println("[P691] " + s); System.out.println("[P691] " + s); }

    static double curvW(double latd, double theta) {
        double c = Math.toRadians(latd);
        double w0 = PrecipField.columnWater(c, theta);
        double wp = PrecipField.columnWater(c + D, theta);
        double wm = PrecipField.columnWater(c - D, theta);
        return (wp - 2.0 * w0 + wm) / (DY * DY);
    }
    static double Kof(double y, double th) {
        double ld = PrecipField.deformRadius(y, th), sg = PrecipField.eadyGrowth(y, th);
        return (PrecipField.EDDY_CLOSURE == 0) ? PrecipField.EDDY_MIX * sg * ld * ld
             : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
    }
    /** 乘积形式（旧 modelShape 的口径）：K*curv*gate。 */
    static double productForm(double y, double th) {
        return Kof(y, th) * curvW(Math.toDegrees(y), th) * PrecipField.gateOf(y, th);
    }
    /** 散度形式（生产）：d(gate*K*X')/dy。 */
    static double divergenceForm(double y, double th) {
        return (F(y + D, th) - F(y - D, th)) / (2.0 * DY);
    }
    static double F(double y, double th) {
        if (PrecipField.eddyScalar(y, th) <= 0.0) return 0.0;
        return PrecipField.gateOf(y, th) * Kof(y, th) * PrecipField.eddyDXdy(y, th, D);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p691_report.txt"), "UTF-8");
        D = Math.toRadians(PrecipField.dphiDeg()); DY = D * WorldContract.R_EFF;
        say("P691（§599 复核版）: 路径 EDDY_GATE_MODE=" + PrecipField.EDDY_GATE_MODE
            + " EDDY_VAR=" + PrecipField.EDDY_VAR + " EDDY_GRAD=" + PrecipField.EDDY_GRAD
            + " EDDY_CLOSURE=" + PrecipField.EDDY_CLOSURE + " dphi=" + PrecipField.dphiDeg());
        say("");
        double thD = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double thJ = Atmosphere.theta(0.0);
        String[] sn = {"JJA", "DJF"};
        double[] ss = {thJ, thD};
        for (int s = 0; s < 2; s++) {
            say("  --- " + sn[s] + " ---");
            say("     纬度 |  curv(W)(1e-9) | gateOf | eddyMfc(1e-5) | 乘积形式(1e-5) | 散度形式(1e-5) | 符号==curv? | 观测反号?");
            for (double latd = 35.0; latd <= 65.0; latd += 2.5) {
                double lr = Math.toRadians(latd);
                double cv = curvW(latd, ss[s]);
                double g  = PrecipField.gateOf(lr, ss[s]);
                double m  = PrecipField.eddyMfc(lr, ss[s]);
                double pr = productForm(lr, ss[s]);
                double dv = divergenceForm(lr, ss[s]);
                double o  = ZonalTables.eddyMfcObsMonth(lr, ss[s]);
                boolean signCurv = ((m > 0) == (cv > 0));
                boolean obsRev = !((m > 0) == (o > 0));
                say(String.format(LF, "     %5.1f | %14.5f | %6.3f | %13.5f | %14.5f | %14.5f | %10s | %s",
                    latd, cv * 1e9, g, m * 1e5, pr * 1e5, dv * 1e5,
                    signCurv ? "是" : "**否**", obsRev ? "**反号**" : "OK"));
            }
            say("");
        }
        say("判读：");
        say("  · 「散度形式」列必须逐点等于 eddyMfc（对账）；「乘积形式」列若不等 => §485 的旧前提作废。");
        say("  · 「符号==curv?」列若出现「否」=> eddyMfc 的符号【不】跟随 curv(W)。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
