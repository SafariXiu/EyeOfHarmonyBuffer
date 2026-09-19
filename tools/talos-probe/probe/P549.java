package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P549 —— **A3：term1 的偏差在哪个因子？**（不是「加一个标定系数」，而是先定位形状）
 *
 * <p>动机：副热带三个框都是 0.33~0.43x，但**赤道带（term1 占比 100%）已经是 0.96x**。
 * 如果 {@code EPS_C} / {@code RH_SEA} 整体偏低，赤道也该偏 —— 它不偏
 * ⇒ **偏差不是常数倍，而是形状**。本探针把 {@code term1 = EPS_C*rho*q*wEff/rho_w}
 * 逐因子拆开，并检验「若要补上缺口，所需的值是否在物理上限之内」。
 *
 * <p>复现口径：复用 {@link P546#decompose}（同包），所以与 P546/P548 逐位一致。
 * 驱动：地球掩膜。GPCP 锚值取自 {@link Zonal#ANCHOR}（由 verify_gpcp_anchors.py 复算）。
 */
public class P549 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P549] " + s); System.out.println("[P549] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000, NX = 72;
    static final int SEED = 1022228679;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p549_report.txt"), "UTF-8");
        say("P549：A3 —— term1 的偏差在哪个因子？（地球掩膜驱动）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  EPS_C=" + PrecipField.EPS_C
            + "  RH_SEA=" + PrecipField.RH_SEA + "  H_BL=" + Atmosphere.H_BL
            + "  W_LOC_MAX=" + PrecipField.W_LOC_MAX + "  H_MOIST=" + PrecipField.H_MOIST);
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        // 锚带（北半球两个分支），与 Zonal 同口径
        double[][] bands = {{2.5, 12.5}, {27.5, 37.5}, {47.5, 62.5}};
        String[] bn = {"赤道 2.5-12.5", "副热 27.5-37.5", "中纬 47.5-62.5"};
        double[] gS = {6.585, 2.301, 2.534};
        double[] gW = {3.580, 2.391, 2.432};

        say("");
        say("=== term1 的因子分解（term1 = EPS_C * rho * q * wEff / rho_w）===");
        say("  带 / 季          term1   term2   term3   final   GPCP   亏空    q        w_zm      wLoc      wEff     depl");
        double[][][] keep = new double[3][2][6];
        for (int si = 0; si < 2; si++) {
            double theta = (si == 0) ? thS : thW;
            for (int bi = 0; bi < 3; bi++) {
                double lo = bands[bi][0], hi = bands[bi][1];
                double s1 = 0, s2 = 0, s3 = 0, sf = 0, sq = 0, szm = 0, zl = 0, dp = 0, kk = 0;
                long n = 0;
                for (int xk = 0; xk < 2; xk++) {
                    for (double latd = lo + 1.0; latd < hi; latd += (hi - lo) / 4.0) {
                        int q3 = WorldContract.zOfLat(latd);
                        int z = (xk == 0) ? q3 : 2 * WorldContract.MAX_D - q3;
                        for (int c = 0; c < NX; c++) {
                            int x = (int) Math.round((c + 0.5) * CIRC / NX);
                            double[] dg = new double[8];
                            double[] r = P546.decompose(x, z, sd, cell, theta, dg);
                            s1 += r[0]; s2 += r[1]; s3 += r[2]; sf += r[4];
                            sq += dg[1]; szm += dg[3]; zl += dg[4]; dp += dg[6]; kk += dg[0];
                            n++;
                        }
                    }
                }
                double t1 = s1 / n, t2 = s2 / n, t3 = s3 / n, fin = sf / n;
                double q = sq / n, wzm = szm / n, wloc = zl / n, depl = dp / n;
                double g = (si == 0) ? gS[bi] : gW[bi];
                double deficit = t1 > 1e-9 ? g / t1 : 999;
                keep[bi][si] = new double[]{t1, t2, t3, fin, g, deficit, q, wzm, wloc, depl};
                say(String.format(LF, "  %-12s %s %7.3f %7.3f %7.3f %7.3f %6.3f %6.2fx %8.5f %+.3e %+.3e %+.3e %5.3f",
                    bn[bi], (si == 0 ? "JJA" : "DJF"), t1, t2, t3, fin, g, deficit, q, wzm, wloc, wzm + wloc, depl));
            }
        }
        say("");
        say("=== 归因：要补上亏空，哪个因子必须变？以及**是否在上限之内** ===");
        say("  （term2/term3 都算在 final 里；下表只针对 **term1** 的亏空，用 term1 单独比 —— 即假设其它项不变）");
        say("  带 / 季          亏空   ①只抬 wEff: 需要/上限(w_zm+W_LOC_MAX)  可达?   ②只抬 q: 需要/上限(q/depl)  可达?   ③只抬 EPS_C: 需要   ≤1?");
        for (int si = 0; si < 2; si++) {
            for (int bi = 0; bi < 3; bi++) {
                double[] k = keep[bi][si];
                double t1 = k[0], g = k[4], deficit = k[5];
                double q = k[6], wzm = k[7], wloc = k[8], depl = k[9];
                double wEff = wzm + wloc;
                double wEffNeed = wEff * deficit;
                double wEffCap = wzm + PrecipField.W_LOC_MAX;
                double qNeed = q * deficit;
                double qCap = depl > 1e-9 ? q / depl : q;      // depletion -> 1 时的 q
                double epsNeed = PrecipField.EPS_C * deficit;
                say(String.format(LF, "  %-12s %s %6.2fx   %8.3e /%8.3e  %5s   %8.5f /%8.5f  %5s   %6.3f  %5s",
                    bn[bi], (si == 0 ? "JJA" : "DJF"), deficit,
                    wEffNeed, wEffCap, (wEffNeed <= wEffCap ? "YES" : "NO"),
                    qNeed, qCap, (qNeed <= qCap ? "YES" : "NO"),
                    epsNeed, (epsNeed <= 1.0 ? "YES" : "NO")));
            }
        }
        say("");
        say("=== 对照：赤道带（term1 占比 ~100%，是「整体尺度」的标尺）===");
        say("  若赤道带的 final 已经接近 GPCP，说明 EPS_C/RH_SEA/q 的**整体尺度是对的**，");
        say("  副热带的亏空必须由**形状**（wEff 的经向分布 / q 的源距离）解释，而不是一个全局乘子。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
