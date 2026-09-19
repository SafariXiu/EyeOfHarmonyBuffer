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

// P594（修正版）-- 【与地球数值无关的物理判据】
// 用户原则：要保证的是物理正确，不是地球正确。所以判据必须是【任何自转行星都成立的关系】。
//
// [A] 质量守恒：胞内 Integral[w dphi] = 0。Held-Hou 解析上恒为 0；
//     现有 W_ZM 表是 0~18.75 度全正的【降水指标】，不是质量流函数。
//     ★ 上一版我把积分截断在 latd=18.0（phi_H=18.64），漏掉负尾，得到假的 +0.0227。
//       正确做法是在 X = lat/phi_H 空间积到 1。
// [B] 副热带大陆西岸必须比东岸【干】（信风离岸 + 上升流 + 大尺度下沉）。
//     地球上是撒哈拉/加那利、阿塔卡马、纳米布；任何有海洋的自转行星都该有。
// [C] 中纬反过来：西岸必须比东岸【湿】（西风带迎风）。
//     ★ 上一版我把相关系数的判据写反了（eastness=+1 是【东】岸）。
//       且 eastness 是连续量，桶均值与相关系数会打架 => 以【桶均值】为准，相关只作诊断。
public class P594 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P594] " + s); System.out.println("[P594] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    /** 返回 {西岸P, 西岸n, 东岸P, 东岸n, corr(诊断)}。 */
    static double[] coastTest(long sd, int cell, double th, double latLo, double latHi) {
        long nE = 0, nW = 0; double sE = 0, sW = 0;
        double se = 0, sp = 0, sep = 0, se2 = 0, sp2 = 0; long n = 0;
        for (double latd = latLo; latd <= latHi; latd += 2.5)
            for (int hemi = -1; hemi <= 1; hemi += 2) {
                int z = WorldContract.zOfLat((int) Math.round(latd * hemi));
                for (int c = 0; c < 144; c++) {
                    int x = (int) Math.round((c + 0.5) * CIRC / 144);
                    if (Atmosphere.kappaMemo(x, z, sd, cell) < 0.7) continue;   // 只看陆地
                    double e = Atmosphere.eastness(x, z, sd, cell);
                    if (Math.abs(e) < 0.3) continue;                            // 只看明确岸线
                    double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    if (e > 0) { sE += p; nE++; } else { sW += p; nW++; }
                    se += e; sp += p; sep += e * p; se2 += e * e; sp2 += p * p; n++;
                }
            }
        double me = se / n, mp = sp / n;
        double cov = sep / n - me * mp;
        double ve = se2 / n - me * me, vp = sp2 / n - mp * mp;
        return new double[]{nW == 0 ? Double.NaN : sW / nW, nW,
                            nE == 0 ? Double.NaN : sE / nE, nE,
                            (ve > 0 && vp > 0) ? cov / Math.sqrt(ve * vp) : 0};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p594_report.txt"), "UTF-8");
        say("P594（修正版）: 与地球数值无关的物理判据");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);

        say("");
        say("  [A] 质量守恒 Integral[w dphi] / Integral|w| dphi（要求 = 0）");
        // 现有表：0~90 度
        double sT = 0, aT = 0;
        for (int i = 0; i < 1800; i++) {
            double la = (i + 0.5) * 0.05;
            double w = ZonalTables.wZm(la);
            sT += w * 0.05; aT += Math.abs(w) * 0.05;
        }
        // Held-Hou：在 X 空间积到 1（解析上恒为 0）
        double sH = 0, aH = 0;
        int NX = 200000;
        for (int i = 0; i < NX; i++) {
            double X = (i + 0.5) / NX;
            double w = HadleyCell.wShape(X);
            sH += w / NX; aH += Math.abs(w) / NX;
        }
        say(String.format(LF, "      现有 W_ZM 表  : 归一化收支 = %+.4f   %s", sT / aT,
            Math.abs(sT / aT) < 1e-3 ? "通过" : "★ 不等于 0 ★"));
        say(String.format(LF, "      求解 Held-Hou : 归一化收支 = %+.3e   %s", sH / aH,
            Math.abs(sH / aH) < 1e-9 ? "通过 ✓（解析上恒为 0）" : "★ 不等于 0 ★"));
        say("      => 含义：现有表【不是质量流函数】，是一个被标定过的降水指标。");
        say("         把它换成真正的动力学 w、却不同时补上水汽物理 => 必然塌（§398 的机制）。");

        say("");
        say("  [B] 副热带 15~35 度：要求 西岸 P < 东岸 P（物理：信风离岸 + 上升流 + 下沉）");
        double[] b = coastTest(sd, cell, thS, 15, 35);
        say(String.format(LF, "      西岸(陆地) P=%.3f mm/day  n=%d", b[0], (long) b[1]));
        say(String.format(LF, "      东岸(陆地) P=%.3f mm/day  n=%d", b[2], (long) b[3]));
        say(String.format(LF, "      corr(eastness,P)=%+.4f（仅诊断：eastness 连续，可与桶均值不一致）", b[4]));
        boolean p2 = b[0] < b[2];
        say(String.format(LF, "      实测 西/东 = %.3f  => %s", b[0] / b[2], p2 ? "通过 ✓" : "★ 违反物理 ★"));

        say("");
        say("  [C] 中纬 40~60 度：要求 西岸 P > 东岸 P（物理：西风带迎风）");
        double[] cc = coastTest(sd, cell, thS, 40, 60);
        say(String.format(LF, "      西岸(陆地) P=%.3f mm/day  n=%d", cc[0], (long) cc[1]));
        say(String.format(LF, "      东岸(陆地) P=%.3f mm/day  n=%d", cc[2], (long) cc[3]));
        say(String.format(LF, "      corr(eastness,P)=%+.4f（仅诊断）", cc[4]));
        boolean p3 = cc[0] > cc[2];
        say(String.format(LF, "      实测 西/东 = %.3f  => %s", cc[0] / cc[2], p3 ? "通过 ✓" : "★ 违反物理 ★"));

        say("");
        say("  ★ 总判据（三项都要过才算「物理正确」，全程不引用地球任何数值）：");
        say(String.format(LF, "      [A] 质量守恒（现有表）  %s", Math.abs(sT / aT) < 1e-3 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      [A'] 质量守恒（求解值） %s", Math.abs(sH / aH) < 1e-9 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      [B] 副热带西岸干        %s", p2 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      [C] 中纬西岸湿          %s", p3 ? "通过 ✓" : "不通过 ✗"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
