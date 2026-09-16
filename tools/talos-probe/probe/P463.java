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
 * P463：**涡动闭合大改的标定扫描**（设计冻结 §146）。
 *
 * <p>口径**逐点对齐验收仪器 P296**（NX=400 个 x 点、x = c*40km、NZ=50），
 * 以免重犯 E12（两支探针各用一套采样）。
 *
 * <p>标定目标：45~55N JJA = GPCP v2.2 LTM(1991-2020) **2.565** mm/day。
 * 同时报**三条独立 GPCP 验收**（没被拟合过的量）：
 * 中纬 47.5~62.5、赤道 2.5~12.5、副热带 27.5~37.5。
 */
public class P463 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000, NX = 400, NZ = 50;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P463] " + s); System.out.println("[P463] " + s); }

    static double band(double[] p, double lo, double hi) {
        double s = 0; int n = 0;
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
            if (lat < lo || lat > hi) continue;
            s += p[r]; n++;
        }
        return n > 0 ? s / n : 0;
    }

    static double[] field(double theta) {
        double[] p = new double[NZ];
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double a = 0;
            for (int c = 0; c < NX; c++) a += PrecipField.mmPerDay(c * 40_000, z, SD, CELL, theta, GRAD);
            p[r] = a / NX;
        }
        return p;
    }

    /** 三条**独立** GPCP 量的偏差绝对值之和（越小越好）。 */
    static double indepErr(double[] pS, double[] pW) {
        double e = 0;
        e += Math.abs(band(pS, 47.5, 62.5) / 2.534 - 1);
        e += Math.abs(band(pW, 47.5, 62.5) / 2.432 - 1);
        e += Math.abs(band(pS, 2.5, 12.5) / 6.585 - 1);
        e += Math.abs(band(pW, 2.5, 12.5) / 3.580 - 1);
        e += Math.abs(band(pS, 27.5, 37.5) / 2.301 - 1);
        e += Math.abs(band(pW, 27.5, 37.5) / 2.391 - 1);
        return e / 6.0;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p463_report.txt"), "UTF-8");
        say("P463：涡动闭合大改的标定扫描（口径 = P296，NX=400 x NZ=50）");
        say(String.format(LF, "  现行 EDDY_MIX=%.3f   EDDY_TAU=%.0f s (%.2f 天)   标定目标 GPCP 45~55 JJA = 2.565",
            PrecipField.EDDY_MIX, PrecipField.EDDY_TAU, PrecipField.EDDY_TAU / 86400.0));
        say("");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        final int savedC = PrecipField.EDDY_CLOSURE;
        final double savedG = PrecipField.EDDY_PHYS_GAIN;
        final double savedMix = PrecipField.EDDY_MIX;
        say(String.format(LF, "  %-8s %-10s %9s %9s %9s %9s %9s %9s %9s", "closure", "gain", "45-55夏", "47-62夏", "47-62冬", "赤道夏", "赤道冬", "副热夏", "独立 err"));
        // ⚠ D48 修完之后**两个闭合都要重新标定**才能公平比较：
        //   closure 0 是纯乘子 ⇒ 扫 EDDY_MIX；closure 1 ⇒ 扫 EDDY_PHYS_GAIN。
        // D47（deformRadius 换 beta 平面）之后重扫：L_d 在 45~55N 降 11.4% ⇒ κ 降 1.238 倍
        double[] gains = {5.45};
        double[] eddyMixSweep = {2.8, 3.0, 3.2};
        double bestErr = 1e9, bestGain = 0;
        for (int mode = 0; mode <= 1; mode++) {
            double[] gs = (mode == 0) ? eddyMixSweep : gains;
            for (double g : gs) {
                PrecipField.EDDY_CLOSURE = mode;
                if (mode == 0) { PrecipField.EDDY_MIX = g; PrecipField.EDDY_PHYS_GAIN = 1.0; }
                else { PrecipField.EDDY_MIX = savedMix; PrecipField.EDDY_PHYS_GAIN = g; }
                long t0 = System.nanoTime();
                double[] pS = field(thS), pW = field(thW);
                double e = indepErr(pS, pW);
                say(String.format(LF, "  %-8d %-10.3f %9.3f %9.3f %9.3f %9.3f %9.3f %9.3f %9.4f   (%.0f s)",
                    mode, g, band(pS, 45, 55), band(pS, 47.5, 62.5), band(pW, 47.5, 62.5),
                    band(pS, 2.5, 12.5), band(pW, 2.5, 12.5), band(pS, 27.5, 37.5), e, (System.nanoTime() - t0) / 1e9));
                if (Math.abs(band(pS, 45, 55) / 2.565 - 1) < 0.02) say(String.format(LF, "        ^^ 这一行**命中标定目标**（45~55 夏 %+.2f%%），独立 err = %.4f", 100*(band(pS,45,55)/2.565-1), e));
                if (e < bestErr) { bestErr = e; bestGain = g; }
            }
        }
        PrecipField.EDDY_CLOSURE = savedC; PrecipField.EDDY_PHYS_GAIN = savedG; PrecipField.EDDY_MIX = savedMix;
        say("");
        say(String.format(LF, "  ⇒ 物理化闭合的最优 gain = %.2f（独立 err 均值 %.4f）", bestGain, bestErr));
        say("  ⚠ 判定（§146.3 先写死）：gain 标定后 45~55 夏须差 <2%；**独立 err 必须小于现行式**；B2.a/B2.b 不得跌破。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
