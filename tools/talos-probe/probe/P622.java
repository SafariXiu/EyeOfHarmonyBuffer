package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.*;
import java.util.Locale;

// P622 -- 目标第 5 项续：地表温度/SST 锚（ERA5）+ omega500 锚（NCEP）。
public class P622 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File REF = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
    static PrintStream rep;
    static void say(String s) { rep.println("[P622] " + s); System.out.println("[P622] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }

    /** 月份 m(1..12) -> theta。day=0 是北半球夏至（= 六月中）。 */
    static double thetaOfMonth(int m) {
        double day = (m - 6.5) * 30.4375;
        return 2.0 * Math.PI * day / WorldContract.DAYS_PER_YEAR;
    }

    /** 模型：某纬度带的地表温度（陆地/海洋分开，cos-lat 加权，年平或指定 theta）。 */
    static double[] modelTemp(double latc, long sd, int cell, boolean annual) {
        double[] ths = annual ? new double[]{0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2}
                              : new double[]{Atmosphere.theta(0.0)};
        double sL = 0, sS = 0, wL = 0, wS = 0;
        for (double th : ths) {
            for (int latOff = -1; latOff <= 1; latOff++) {
                // ★ 纬度必须 clamp 到 [-90,90]：latc=90 时 latOff=+1 会给 90.8 度
                //   => cos(90.8 度) = -0.014（【负权重】）且 z 超出 MAX_D
                //   => 加权分母近零 => 温度爆炸成 6.5e11 K 或 NaN。
                //   第一版就是这个 bug（P624 已证：80~91N 全经度 4 季节的非有限值 = 0，模型本身干净）。
                double latd = Math.max(-90.0, Math.min(90.0, latc + latOff * 0.8));
                int z = Math.max(0, Math.min(WorldContract.MAX_D, zOfLat(latd)));
                double w = Math.cos(Math.toRadians(latd));
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    int x = xOfLon(lon);
                    double k = Atmosphere.kappaMemo(x, z, sd, cell);
                    double t = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    if (k > 0.5) { sL += t * w; wL += w; } else { sS += t * w; wS += w; }
                }
            }
        }
        // ★ 注意：wL/wS 已经按【每个季节】各累加一次 ⇒ sL/wL 本身就是跨季节平均，
        //   再除 ths.length 就多除了一次（第一版因此给出 74 K 这种荒谬值）。
        return new double[]{wL > 0 ? sL / wL : Double.NaN, wS > 0 ? sS / wS : Double.NaN};
    }

    /** 模型：某纬度带的纬向平均 wEff（cos-lat 加权）。 */
    static double modelW(double latc, long sd, int cell, double th) {
        double s = 0, wsum = 0;
        for (int latOff = -1; latOff <= 1; latOff++) {
            double latd = Math.max(-90.0, Math.min(90.0, latc + latOff * 0.8));
            int z = Math.max(0, Math.min(WorldContract.MAX_D, zOfLat(latd)));
            double w = Math.cos(Math.toRadians(latd));
            for (int c = 0; c < 72; c++) {
                PrecipField.mmPerDay(xOfLon((c + 0.5) * 5.0), z, sd, cell, th, GRAD);
                s += PrecipField.DIAG.get()[14] * w; wsum += w;
            }
        }
        return s / wsum;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p622_report.txt"), "UTF-8");
        say("P622: 地表温度/SST 锚（ERA5）+ omega500 锚（NCEP）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;

        say("");
        say("  [A] 年平均地表温度（K）：模型 vs ERA5（ls_anchor_table.txt）");
        say("      lat   T_LAND 模/观       T_OCEAN 模/观");
        double bL = 0, bS = 0; int n = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File(REF, "ls_anchor_table.txt")), "UTF-8"))) {
            String s;
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] t = s.split("\\s+");
                if (t.length < 4) continue;
                double lat = Double.parseDouble(t[0]);
                double tzm = Double.parseDouble(t[1]);
                double tsea = Double.parseDouble(t[2]);
                double tland = Double.parseDouble(t[3]);
                double[] m = modelTemp(lat, sd, cell, true);
                say(String.format(LF, "      %+5.0f  %7.2f %7.2f (%+.2f)   %7.2f %7.2f (%+.2f)",
                    lat, m[0], tland, m[0] - tland, m[1], tsea, m[1] - tsea));
                bL += m[0] - tland; bS += m[1] - tsea; n++;
            }
        } catch (IOException e) { say("      读失败: " + e.getMessage()); }
        say(String.format(LF, "      => 平均偏差：陆地 %+.2f K   海洋 %+.2f K  (n=%d)", bL / n, bS / n, n));

        say("");
        say("  [B] 纬向平均 w（m/s）：模型 wDiag（由散度直接算）vs NCEP omega500 换算");
        say("      文件首行给纬度表，逐月一行。只打 1/4/7/10 月以控篇幅。");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File(REF, "omega500_month.txt")), "UTF-8"))) {
            String s; double[] lats = null; int mi = 0;
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.isEmpty()) continue;
                if (s.startsWith("#")) {
                    if (s.contains("lat 0 ")) {
                        String[] t = s.substring(s.indexOf("lat")).split("\\s+");
                        lats = new double[t.length - 1];
                        for (int i = 1; i < t.length; i++) lats[i - 1] = Double.parseDouble(t[i]);
                    }
                    continue;
                }
                mi++;
                if (lats == null) continue;
                String[] t = s.split("\\s+");
                if (mi != 1 && mi != 4 && mi != 7 && mi != 10) continue;
                double th = thetaOfMonth(mi);
                say(String.format(LF, "      月 %2d (theta=%.4f)", mi, th));
                say("         lat  模型wEff      观测w      差");
                double bias = 0; int c2 = 0;
                for (int i = 0; i < lats.length && i + 1 < t.length; i++) {
                    double ob = Double.parseDouble(t[i + 1]);
                    if (Math.abs(lats[i]) > 65) continue;
                    double mw = modelW(lats[i], sd, cell, th);
                    if (i % 2 == 0)
                        say(String.format(LF, "        %4.0f  %+10.3e  %+10.3e  %+10.3e", lats[i], mw, ob, mw - ob));
                    bias += mw - ob; c2++;
                }
                say(String.format(LF, "        => 平均偏差 %+.3e m/s (n=%d)", bias / c2, c2));
            }
        } catch (IOException e) { say("      读失败: " + e.getMessage()); }

        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
