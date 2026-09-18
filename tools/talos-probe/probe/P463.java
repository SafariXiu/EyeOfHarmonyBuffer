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
    static final int GRAD = 500_000, NX = Zonal.NX, NZ = Zonal.NZ;   // ★ 口径唯一化（M13）
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P463] " + s); System.out.println("[P463] " + s); }

    /** ★ M13：口径唯一化 —— 委托 {@link Zonal}（40,000 km = 地球纬圈周长）。 */
    static double band(double[] p, double lo, double hi) {
        return Zonal.band(p, lo, hi);
    }

    /** ★ M13：剖面也走 Zonal，保证与验收仪器 P296 逐点同口径。 */
    static double[] field(double theta) {
        return Zonal.profile(SD, theta);
    }

    /** 六个 GPCP 观测锚的平均绝对相对偏差 —— **定义只在 Zonal 里一份**（M13）。 */
    static double indepErr(double[] pS, double[] pW) {
        return Zonal.err(pS, pW);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p463_report.txt"), "UTF-8");
        say("P463：涡动闭合大改的标定扫描（口径 = P296，NX=400 x NZ=50）");
        say(String.format(LF, "  现行 EDDY_MIX=%.3f   EDDY_TAU=%.0f s (%.2f 天)   标定目标 GPCP 45~55 JJA = 2.565",
            PrecipField.EDDY_MIX, PrecipField.EDDY_TAU, PrecipField.EDDY_TAU / 86400.0));
        // ★ E122 守卫：读数前先自证仪器状态。批处理曾把 -Dtalos.terrain=true 拆成
        //   "-Dtalos.terrain true" => getBoolean 为 false => 整张标定表是在开关关闭下测的。
        say("  地形开关 PlateField.TALOS_TERRAIN = "
            + com.EyeOfHarmonyBuffer.sim.litho.PlateField.TALOS_TERRAIN
            + "   (必须与本次意图一致，否则整张表作废)");
        say("  口径 = Zonal（x 跨度 " + (int) (Zonal.XSPAN / 1000) + " km = 地球纬圈周长，NX="
            + Zonal.NX + "，NZ=" + Zonal.NZ + "）—— 与验收仪器 P296 同口径");
        say("");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        final int savedC = PrecipField.EDDY_CLOSURE;
        final double savedG = PrecipField.EDDY_PHYS_GAIN;
        final double savedMix = PrecipField.EDDY_MIX;
        say(String.format(LF, "  %-8s %-10s %9s %9s %9s %9s %9s %9s %9s", "closure", "gain", "45-55夏", "47-62夏", "47-62冬", "赤道夏", "赤道冬", "副热夏", "独立 err"));
        // ⚠ D48 修完之后**两个闭合都要重新标定**才能公平比较：
        //   closure 0 是纯乘子 ⇒ 扫 EDDY_MIX；closure 1 ⇒ 扫 EDDY_PHYS_GAIN。
        // D47（deformRadius 换 beta 平面）之后重扫：L_d 在 45~55N 降 11.4% ⇒ κ 降 1.238 倍
        // §318 重标（新地形 V8）：OFF 地形下 gain=5.45 命中 2.565；
        // 换成 TalosField 后 45~55 夏掉到 1.85（-27.7%）⇒ 需要更宽的括号来找新工作点。
        // §319 精化：把「命中锚」的工作点 bracketed 到 6.2，并保留 5.45 作为 A/B 对照。
        // §320：新地形下把工作点 bracketed 到 8.4（不外推）。
        // §323：E124 修好之后地形再变 ⇒ 第二次重标。P296 实测 gain=8.40 -> 2.33（-9.0%）。
        // ★ 2026-09-18 口径改到 40,000 km 后：gain=9.60 在 45~55 夏给出 3.45
        //   （对 GPCP 2.565 **+34.5%**）⇒ 旧的 8.4/9.5/10.6 整段失效，必须往下括。
        double[] gains = {4.00, 5.00, 6.00, 7.00, 8.00};
        double[] eddyMixSweep = {3.0};
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
