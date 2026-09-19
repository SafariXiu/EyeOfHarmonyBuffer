package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P598 -- 地球锚重测：地球掩膜 + ETOPO1 地形（§398 的 0.725 是矛盾状态下得的，作废）。
// 首次在【有青藏高原】的条件下跑地球。
public class P598 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P598] " + s); System.out.println("[P598] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}, {300, 350, 25, 35}};
    static final String[] NM = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋", "北大西洋"};
    /** GPCP v2.3 LTM JJA（mm/day）—— 与 P578 同一批锚。 */
    static final double[] TRUTH = {7.775, 0.105, 3.595, 2.402, 0.677};

    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    /** 返回 {P均值, 陆占比}。 */
    static double[] box(long sd, int cell, double th, int b) {
        long n = 0, nL = 0; double s = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5) {
            int z = zOfLat(latd);
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon);
                s += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                if (Atmosphere.kappaMemo(x, z, sd, cell) > 0.5) nL++;
                n++;
            }
        }
        return new double[]{s / n, (double) nL / n};
    }

    /** §399 的海岸判据，但在地球底质上（与随机行星互为独立检验）。 */
    static double[] coast(long sd, int cell, double th, double lo, double hi) {
        long nE = 0, nW = 0; double sE = 0, sW = 0;
        for (double latd = lo; latd <= hi; latd += 2.5)
            for (int hemi = -1; hemi <= 1; hemi += 2) {
                int z = zOfLat(latd * hemi);
                for (int c = 0; c < 144; c++) {
                    int x = xOfLon((c + 0.5) * 2.5);
                    if (Atmosphere.kappaMemo(x, z, sd, cell) < 0.7) continue;
                    double e = Atmosphere.eastness(x, z, sd, cell);
                    if (Math.abs(e) < 0.3) continue;
                    double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    if (e > 0) { sE += p; nE++; } else { sW += p; nW++; }
                }
            }
        return new double[]{nE == 0 ? Double.NaN : sE / nE, nE, nW == 0 ? Double.NaN : sW / nW, nW};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p598_report.txt"), "UTF-8");
        say("P598: 地球锚重测（地球掩膜 + ETOPO1 地形）");
        // ★ 地球层必须在任何气候查询之前装上（进程内换层会留下旧缓存/旧海温行）。
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say(String.format(LF, "  地球层已装：nlat=%d nlon=%d  掩膜=%s  地形=%s",
            EarthRef.nlat, EarthRef.nlon, PlateField.MASK != null, PlateField.ELEV != null));
        double hTibet = PlateField.elevationWithCell(xOfLon(91.1), zOfLat(29.65), sd, cell);
        say(String.format(LF, "  青藏高原自证：拉萨一带海拔 = %.0f m（ETOPO1 口径；无地形时是 386 m）", hTibet));

        say("");
        say("  [A] 五个盒子：JJA 模型 vs GPCP v2.3 LTM");
        say("      盒子        模型JJA   观测JJA   比值(模型/观测)   陆占比");
        double[] modelJ = new double[BOX.length];
        for (int b = 0; b < BOX.length; b++) {
            double[] r = box(sd, cell, thS, b);
            modelJ[b] = r[0];
            say(String.format(LF, "      %-10s  %6.3f   %6.3f     %6.3f          %5.1f%%",
                NM[b], r[0], TRUTH[b], r[0] / TRUTH[b], r[1] * 100));
        }
        say("");
        say(String.format(LF, "      亚洲/撒哈拉：模型 %.3f   观测 %.1f", modelJ[0] / modelJ[1], TRUTH[0] / TRUTH[1]));
        say(String.format(LF, "      => 观测比 74.0，§347 门槛 10；§398 那个 0.725 是在【矛盾状态】下得的，已作废"));

        say("");
        say("  [B] ITCZ 季节迁移（亚洲框 JJA vs DJF）");
        double[] asiaW = box(sd, cell, thW, 0);
        double[] sahW = box(sd, cell, thW, 1);
        say(String.format(LF, "      亚洲：JJA %.3f  DJF %.3f   季节比 %.2f", modelJ[0], asiaW[0], modelJ[0] / asiaW[0]));
        say(String.format(LF, "      撒哈拉：JJA %.3f  DJF %.3f", modelJ[1], sahW[0]));

        say("");
        say("  [C] §399 的海岸判据，在地球底质上（与随机行星互为独立检验）");
        double[] b1 = coast(sd, cell, thS, 15, 35);
        say(String.format(LF, "      副热带 15~35：大陆西岸 %.3f (n=%d)  大陆东岸 %.3f (n=%d)  西/东 = %.3f  %s",
            b1[0], (long) b1[1], b1[2], (long) b1[3], b1[0] / b1[2],
            b1[0] < b1[2] ? "西岸干 ✓" : "★ 违反 ★"));
        double[] b2 = coast(sd, cell, thS, 40, 60);
        say(String.format(LF, "      中纬 40~60  ：大陆西岸 %.3f (n=%d)  大陆东岸 %.3f (n=%d)  西/东 = %.3f  %s",
            b2[0], (long) b2[1], b2[2], (long) b2[3], b2[0] / b2[2],
            b2[0] > b2[2] ? "西岸湿 ✓" : "★ 违反 ★"));

        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
