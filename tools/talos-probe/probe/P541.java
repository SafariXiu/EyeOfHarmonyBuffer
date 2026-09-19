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
 * P541：**系综锚点测量** —— 把「真实地形代价」与「采样噪声」分开。
 *
 * <p>动机：P463/P296 的 band() 口径是 NX=400 × 40 km = **16,000 km**，而 V8 地形最大
 * 特征尺度是 PHI 9,000 km / Bn 12,000 km ⇒ 一条纬线上只有约 1.3 个独立样本，
 * 却拿去和 GPCP 的「地球整圈 40,000 km 纬向平均」比。
 * 换地形 = 换实现 ⇒ 「独立 err 从 0.225 涨到 0.415」里有多少是真物理、多少是采样，
 * 在旧口径下**无法分辨**。
 *
 * <p>本探针把两件事分开：
 * <ul>
 *   <li>A. 同一世界、跨 16,000 / 40,000 / 80,000 km ⇒ 看口径收敛；</li>
 *   <li>B. 16 个生产世界（seedOf(1..16)）在 40,000 km 口径下的**系综均值** ⇒ 看系统性偏差。</li>
 * </ul>
 * 判据口径与 P463 逐点一致（indepErr 的 6 个 GPCP 分母逐字照抄）。
 */
public class P541 {

    static final int SEED0 = 1022228679;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000, NZ = 50, NX = 1000;   // NX=1000 @ 40km = 40,000 km
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P541] " + s); System.out.println("[P541] " + s); }

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

    /** 6 个锚的**平均绝对相对偏差**（与 P463.indepErr 逐字同口径）。 */
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

    static double[] field(long sd, double theta, double xspan) {
        double[] p = new double[NZ];
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double a = 0;
            for (int c = 0; c < NX; c++) a += PrecipField.mmPerDay((int) Math.round(c * xspan / NX), z, sd, CELL, theta, GRAD);
            p[r] = a / NX;
        }
        return p;
    }

    static double landFrac(long sd, double lo, double hi, double xspan) {
        int nl = 0, n = 0;
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
            if (lat < lo || lat > hi) continue;
            for (int c = 0; c < NX; c++) {
                if (PlateField.isLandWithCell((int) Math.round(c * xspan / NX), z, sd, CELL)) nl++;
                n++;
            }
        }
        return n > 0 ? 100.0 * nl / n : 0;
    }

    static void row(String tag, double[] pS, double[] pW) {
        say(String.format(LF, "  %-14s %7.3f %7.3f %7.3f %7.3f %7.3f %7.3f   %6.4f",
            tag, band(pS, 47.5, 62.5), band(pW, 47.5, 62.5), band(pS, 2.5, 12.5),
            band(pW, 2.5, 12.5), band(pS, 27.5, 37.5), band(pW, 27.5, 37.5), indepErr(pS, pW)));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p541_report.txt"), "UTF-8");
        say("P541：系综锚点测量（把真实地形代价与采样噪声分开）");
        say("  锚（GPCP v2.3，全部为观测值）：47-62夏 2.534 / 47-62冬 2.432 / 赤道夏 6.585 / 赤道冬 3.580 / 副热夏 2.301 / 副热冬 2.391");
        say("  NX=" + NX + " 步长 40 km ⇒ 跨度 40,000 km；NZ=" + NZ + "；gain=" + PrecipField.EDDY_PHYS_GAIN
            + " closure=" + PrecipField.EDDY_CLOSURE);
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        // §567：原先这里对 terr ∈ {1,0} 循环，把同一套锚点在【新/旧两种地形】上各测一遍。
        // 旧地形与它的开关已整支删除 ⇒ 只剩 TalosField 一臂。下面的循环体**原样保留**
        // （所以缩进仍是一层深），只是去掉了这一层循环与其自证行。
        say("");
        say("========== 地形 = TalosField（唯一地形路；§567 已删除旧 PlateField 地形与开关）==========");
        say("A. 采样跨度效应（单世界 1022228679）");
            say("  跨度              47-62夏  47-62冬   赤道夏   赤道冬   副热夏   副热冬    独立err");
            long sd0 = SimTerrain.seedOf(SEED0);
            for (double xs : new double[]{16_000_000.0, 40_000_000.0, 80_000_000.0}) {
                row(String.format(LF, "%,.0f km", xs / 1000.0), field(sd0, thS, xs), field(sd0, thW, xs));
            }
            say("B. 系综（16 个生产世界 seedOf(1..16)，跨度 40,000 km）");
            say("  世界              47-62夏  47-62冬   赤道夏   赤道冬   副热夏   副热冬    独立err");
            double[] mS = new double[6], mW = new double[6];
            double errSum = 0; int nw = 0;
            double[] sumS = new double[NZ], sumW = new double[NZ];
            for (int w = 1; w <= 16; w++) {
                long sd = SimTerrain.seedOf(w);
                double[] pS = field(sd, thS, 40_000_000.0), pW = field(sd, thW, 40_000_000.0);
                row(String.format(LF, "world %d", w), pS, pW);
                errSum += indepErr(pS, pW); nw++;
                for (int r = 0; r < NZ; r++) { sumS[r] += pS[r]; sumW[r] += pW[r]; }
            }
            for (int r = 0; r < NZ; r++) { sumS[r] /= nw; sumW[r] /= nw; }
            row("系综均值", sumS, sumW);
            say(String.format(LF, "  ⇒ 系综均值 独立 err = %.4f   逐世界 err 均值 = %.4f", indepErr(sumS, sumW), errSum / nw));
            say("C. 锚带陆地占比（系综均值，16 世界）");
            say("  跨度          47-62带   2.5-12.5带   27.5-37.5带");
            for (double xs : new double[]{16_000_000.0, 40_000_000.0}) {
                double a1 = 0, a2 = 0, a3 = 0;
                for (int w = 1; w <= 16; w++) {
                    long sd = SimTerrain.seedOf(w);
                    a1 += landFrac(sd, 47.5, 62.5, xs); a2 += landFrac(sd, 2.5, 12.5, xs); a3 += landFrac(sd, 27.5, 37.5, xs);
                }
                say(String.format(LF, "  %,.0f km      %6.1f%%     %6.1f%%       %6.1f%%",
                    xs / 1000.0, a1 / 16, a2 / 16, a3 / 16));
            }
            say("  地球实测陆地占比（ETOPO1，同纬带）：47.5-62.5 ≈ 55%   2.5-12.5 ≈ 24%   27.5-37.5 ≈ 45%");
        rep.close();
    }
}
