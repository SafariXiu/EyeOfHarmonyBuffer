package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P595 -- 【与地球数值无关的物理判据】定稿版。
// ★★ 命名陷阱说明（我在这里错了两次）：
//    Atmosphere.eastness = (东侧陆地 - 西侧陆地)/2。
//    eastness > 0  <=>  西侧是洋、东侧是陆  <=>  【海洋的东边界】 <=>  【大陆的西岸】
//    （秘鲁/加那利/本格拉/加利福尼亚 —— 四大上升流区都在这里）
//    所以下面一律用「洋东界(大陆西岸)」这种无歧义写法，不再用「东岸/西岸」。
public class P595 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P595] " + s); System.out.println("[P595] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    /** 返回 {洋东界P(=大陆西岸), n, 洋西界P(=大陆东岸), n, corr} */
    static double[] coast(long sd, int cell, double th, double latLo, double latHi) {
        long nE = 0, nW = 0; double sE = 0, sW = 0;
        double se = 0, sp = 0, sep = 0, se2 = 0, sp2 = 0; long n = 0;
        for (double latd = latLo; latd <= latHi; latd += 2.5)
            for (int hemi = -1; hemi <= 1; hemi += 2) {
                int z = WorldContract.zOfLat((int) Math.round(latd * hemi));
                for (int c = 0; c < 144; c++) {
                    int x = (int) Math.round((c + 0.5) * CIRC / 144);
                    if (Atmosphere.kappaMemo(x, z, sd, cell) < 0.7) continue;
                    double e = Atmosphere.eastness(x, z, sd, cell);
                    if (Math.abs(e) < 0.3) continue;
                    double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    if (e > 0) { sE += p; nE++; } else { sW += p; nW++; }
                    se += e; sp += p; sep += e * p; se2 += e * e; sp2 += p * p; n++;
                }
            }
        double me = se / n, mp = sp / n;
        double cov = sep / n - me * mp;
        double ve = se2 / n - me * me, vp = sp2 / n - mp * mp;
        return new double[]{nE == 0 ? Double.NaN : sE / nE, nE, nW == 0 ? Double.NaN : sW / nW, nW,
                            (ve > 0 && vp > 0) ? cov / Math.sqrt(ve * vp) : 0};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p595_report.txt"), "UTF-8");
        say("P595（定稿）: 与地球数值无关的物理判据");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);

        say("");
        say("  命名：eastness>0 = 西侧洋/东侧陆 = 【洋东界 = 大陆西岸】（上升流侧）");
        say("        eastness<0 = 西侧陆/东侧洋 = 【洋西界 = 大陆东岸】（暖流侧）");

        say("");
        say("  [A] 质量守恒 Integral[w dphi] / Integral|w| dphi（要求 = 0，与行星无关）");
        double sT = 0, aT = 0;
        for (int i = 0; i < 1800; i++) {
            double w = ZonalTables.wZm((i + 0.5) * 0.05);
            sT += w * 0.05; aT += Math.abs(w) * 0.05;
        }
        double sH = 0, aH = 0; int N = 200000;
        for (int i = 0; i < N; i++) {
            double w = HadleyCell.wShape((i + 0.5) / N);
            sH += w / N; aH += Math.abs(w) / N;
        }
        say(String.format(LF, "      现有 W_ZM 表  : %+.4f   %s", sT / aT, Math.abs(sT / aT) < 1e-3 ? "通过" : "★ 违反（它不是质量流函数，是降水指标）★"));
        say(String.format(LF, "      求解 Held-Hou : %+.3e   %s", sH / aH, Math.abs(sH / aH) < 1e-9 ? "通过 ✓" : "违反"));

        say("");
        say("  [B] 副热带 15~35 度：物理要求 大陆西岸 < 大陆东岸");
        double[] b = coast(sd, cell, thS, 15, 35);
        say(String.format(LF, "      大陆西岸(洋东界) P=%.3f mm/day  n=%d", b[0], (long) b[1]));
        say(String.format(LF, "      大陆东岸(洋西界) P=%.3f mm/day  n=%d", b[2], (long) b[3]));
        say(String.format(LF, "      corr(eastness,P)=%+.4f（诊断）", b[4]));
        boolean p2 = b[0] < b[2];
        say(String.format(LF, "      实测 西岸/东岸 = %.3f  => %s", b[0] / b[2], p2 ? "通过 ✓" : "★ 违反物理 ★"));

        say("");
        say("  [C] 中纬 40~60 度：物理要求 大陆西岸 > 大陆东岸（同一条物理，符号翻转）");
        double[] cc = coast(sd, cell, thS, 40, 60);
        say(String.format(LF, "      大陆西岸(洋东界) P=%.3f mm/day  n=%d", cc[0], (long) cc[1]));
        say(String.format(LF, "      大陆东岸(洋西界) P=%.3f mm/day  n=%d", cc[2], (long) cc[3]));
        say(String.format(LF, "      corr(eastness,P)=%+.4f（诊断）", cc[4]));
        boolean p3 = cc[0] > cc[2];
        say(String.format(LF, "      实测 西岸/东岸 = %.3f  => %s", cc[0] / cc[2], p3 ? "通过 ✓" : "★ 违反物理 ★"));

        say("");
        say("  ★ 总判据（全程不引用地球任何数值）：");
        say(String.format(LF, "      [A]  质量守恒（现有表）    %s", Math.abs(sT / aT) < 1e-3 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      [A'] 质量守恒（Held-Hou）  %s", Math.abs(sH / aH) < 1e-9 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      [B]  副热带大陆西岸干      %s", p2 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      [C]  中纬大陆西岸湿        %s", p3 ? "通过 ✓" : "不通过 ✗"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
