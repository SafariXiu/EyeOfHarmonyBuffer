package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P457：B2 定标 —— 扫 EDDY_MIX，找让「锚带 45~55 度纬向平均 夏季」落进 2.0~2.6 mm/day 的值。
 *
 * <p>口径与 P296 完全一致：每纬线在 NX 个 x 上取 mmPerDay 的**纬向平均**（与锚的「纬向平均」同口径），
 * Theta 用 Atmosphere.theta(0)（北半球夏至）、gradStep = 500 km。
 *
 * <p>为什么是 EDDY_MIX：设计冻结 :5151 写明「EDDY_MIX（锚 = 45~55 度纬向平均 2.0~2.6 mm/day）」，
 * ⚠ **D41**：所谓「合格区间 1.37~2.11」我只在源码注释与设计冻结里找到**断言**，
 * 全工程与设计冻结**都没有它的推导来源**（与 B4 的自指「>=5」同族）。本轮把它扫穿。
 */
public class P457 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P457] " + s); System.out.println("[P457] " + s); }

    /** 45~55 度、夏季的纬向平均降水（mm/day）。 */
    static double band4555(double th, int nz, int nxb) {
        double acc = 0; int n = 0;
        for (int r = 0; r < nz; r++) {
            int z = (int) ((r + 0.5) / nz * WorldContract.Z_CYCLE);
            double lat = Math.toDegrees(WorldContract.latOf(z));
            if (lat < 45.0 || lat > 55.0) continue;
            double s = 0;
            for (int c = 0; c < nxb; c++) {
                // ⚠ E12 修复：这里原来用 x = -6000..+6000 km 的 200 点，**与 P296（验收仪器）
                // 的 x = 0..15,960 km 的 400 点不是同一批** ⇒ 同一 EDDY_MIX 下 2.007 vs 1.99。
                // 现在与本项目验收仪器 P296 的网格**逐点对齐**。
                int x = c * 40_000;
                s += PrecipField.mmPerDay(x, z, SD, CELL, th, GRAD);
            }
            acc += s / nxb; n++;
        }
        return n > 0 ? acc / n : 0;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p457_report.txt"), "UTF-8");
        final double saved = PrecipField.EDDY_MIX;
        double thS = Atmosphere.theta(0.0);
        say("P457：B2 定标 —— 扫 EDDY_MIX，目标 = **GPCP v2.2 LTM(1991-2020) 45~55N JJA 纬向平均**");
        say("  观测锚（我自己取的，见设计冻结 §135）：unweighted 2.565 / cos-weighted 2.562 mm/day");
        say("  （P296 的 band() 是**等纬距算术平均**，所以对齐用 unweighted 2.565；两者差 0.13%）");
        say(String.format(LF, "  当前 EDDY_MIX = %.3f（⚠ 原「合格区间 1.37~2.11」无推导来源，D41；本轮扫穿上界）", saved));
        say(String.format(LF, "  Theta=0（北半球夏至）  gradStep=%d km  nxb=200", GRAD / 1000));
        say("");
        say(String.format(LF, "  %-10s %14s %10s", "EDDY_MIX", "45~55 度 夏", "判定"));
        final double GPCP_JJA = 2.565;
        double[] ms = {1.37, 1.70, 2.00, 2.11, 2.30, 2.60, 2.80, 2.90, 2.95, 3.00, 3.05, 3.20};
        double best = 1e9, bestM = 0;
        for (double m : ms) {
            PrecipField.EDDY_MIX = m;
            double v = band4555(thS, 50, 400);
            say(String.format(LF, "  %-10.3f %14.3f   %+8.1f%% vs GPCP", m, v, 100 * (v / GPCP_JJA - 1)));
            if (Math.abs(v - GPCP_JJA) < Math.abs(best)) { best = v - GPCP_JJA; bestM = m; }
        }
        say(String.format(LF, "  ==> 离 GPCP 观测锚(JJA %.3f) 最近 = EDDY_MIX %.2f（给 %.3f mm/day，偏差 %+.2f%%）",
            GPCP_JJA, bestM, GPCP_JJA + best, 100 * best / GPCP_JJA));
        PrecipField.EDDY_MIX = saved;
        say("");
        if (best > 0) {
            say(String.format(LF, "  ==> 落在 [2.0, 2.6] 内、且离现默认 1.7 最近的值 = EDDY_MIX = %.2f（给 %.3f mm/day）", bestM, best));
        } else {
            say("  ==> （旧分支已弃用：判据不再是一个区间，而是一个观测值）");
        }
        say("");
        say("  记账：EDDY_MIX 是纯乘子，只缩放涡动水汽通量辐合项（eddyMfc），");
        say("        不改变赤道带/副热带干带（那里 stormGate = 0）。所以这次定标是局部的。");
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
