package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;

import java.io.*;
import java.util.Locale;

public class P592 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P592] " + s); System.out.println("[P592] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p592_report.txt"), "UTF-8");
        say("P592: StationaryWave.signSelfCheck()（修订版：分母用 Q-Qbar）");
        double[] r = StationaryWave.signSelfCheck();
        say("");
        say(String.format(LF, "  Q(中心)      = %.6f", r[2]));
        say(String.format(LF, "  Q_有效(去平均) = %.6f", r[5]));
        say(String.format(LF, "  div(中心)    = %+.6e   判据 div<0（热源=>上升=>低层辐合）  %s", r[0], r[0] < 0 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "  p(中心)      = %+.6e   判据 p<0（热源=>低层低压）            %s", r[1], r[1] < 0 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "  |div|*c^2/Q_eff = %.4f   文献区间 [0.90, 1.05]  %s", r[3], (r[3] > 0.90 && r[3] < 1.05) ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "  逐点收支 c^2*div + eps*p + Q_eff = %.3e   （必须 ~0）  %s", r[6], Math.abs(r[6]) < 1e-9 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "  文献参考：β平面独立数值解 0.9906；σ=250/500/1000/2000 km 时 0.997/0.991/0.969/0.910"));
        say("");
        say(String.format(LF, "  ★ 总判据：%s", r[4] > 0.5 ? "全部通过 ✓" : "有不通过项 ✗"));
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
