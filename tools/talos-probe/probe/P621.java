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

// P621 -- 目标第 5 项：多元锚 + 留出集。
//   (a) 纬向平均降水（按 陆地/海洋 x JJA/DJF），锚 = obs_lat_profile.txt（GPCP v2.3 LTM）
//   (b) 留出集盒子（南美/澳洲/非洲南部/北美季风），锚 = holdout_boxes.tsv
// 全部数据在 tools/talos-probe + build/eoh_probe/refs，src 零地球知识。
public class P621 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File REF = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
    static PrintStream rep;
    static void say(String s) { rep.println("[P621] " + s); System.out.println("[P621] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round((lon / 360.0) * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    /** obs_lat_profile.txt 的一行。 */
    static double[][] OBS = new double[0][];

    static double[][] readProfile() throws IOException {
        List<double[]> L = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File(REF, "obs_lat_profile.txt")), "UTF-8"))) {
            String s;
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.isEmpty() || s.startsWith("GPCP") || s.startsWith("latN")) continue;
                String[] t = s.split("\\s+");
                if (t.length < 6) continue;
                L.add(new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1]),
                        Double.parseDouble(t[2]), Double.parseDouble(t[3]),
                        Double.parseDouble(t[4]), Double.parseDouble(t[5])});
            }
        }
        return L.toArray(new double[0][]);
    }

    /** 模型在同一纬带上的 陆地/海洋 纬向平均（cos-lat 加权）。返回 {landJ, landD, seaJ, seaD, nL, nS} */
    static double[] modelBand(double latc, long sd, int cell) {
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double w = Math.cos(Math.toRadians(latc));
        double sLJ = 0, sLD = 0, sSJ = 0, sSD = 0, wL = 0, wS = 0;
        int nL = 0, nS = 0;
        for (int latOff = -1; latOff <= 1; latOff++) {
            double latd = latc + latOff * 0.8;
            int z = zOfLat(latd);
            for (int c = 0; c < 144; c++) {
                int x = (int) Math.round((c + 0.5) * 2.5 / 360.0 * CIRC);
                double k = Atmosphere.kappaMemo(x, z, sd, cell);
                boolean land = k > 0.5;
                double pj = PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                double pd = PrecipField.mmPerDay(x, z, sd, cell, thW, GRAD);
                if (land) { sLJ += pj * w; sLD += pd * w; wL += w; nL++; }
                else { sSJ += pj * w; sSD += pd * w; wS += w; nS++; }
            }
        }
        return new double[]{wL > 0 ? sLJ / wL : Double.NaN, wL > 0 ? sLD / wL : Double.NaN,
                            wS > 0 ? sSJ / wS : Double.NaN, wS > 0 ? sSD / wS : Double.NaN, nL, nS};
    }

    /** 盒子均值（plain）。 */
    static double[] modelBox(double lonLo, double lonHi, double latLo, double latHi, long sd, int cell) {
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double sj = 0, sw = 0; long n = 0;
        for (double latd = latLo; latd <= latHi; latd += 2.5)
            for (int c = 0; c < 144; c++) {
                double lon = (c + 0.5) * 2.5;
                boolean in = (lonLo <= lonHi) ? (lon >= lonLo && lon <= lonHi)
                                              : (lon >= lonLo || lon <= lonHi);
                if (!in) continue;
                int x = (int) Math.round(lon / 360.0 * CIRC), z = zOfLat(latd);
                sj += PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                sw += PrecipField.mmPerDay(x, z, sd, cell, thW, GRAD);
                n++;
            }
        return new double[]{sj / n, sw / n, n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p621_report.txt"), "UTF-8");
        say("P621: 多元锚（纬向平均降水）+ 留出集");
        EarthRef.install();
        // ★★★ 必须装 SST provider：`Atmosphere.sstAnom` 在 SST_PROVIDER==null 时恒返回 0，
        //   而 `p'_thermal` 里 `(1-kappa)*sstAnom` 是真实的一项。
        //   （我此前所有地球掩膜探针都漏了这一步 ⇒ 那些读数是在「SST 距平关闭」下得的。）
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        say(String.format(LF, "  OceanField.ENABLED=%s（SST provider 已装）", com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED));
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OBS = readProfile();
        say(String.format(LF, "  锚：obs_lat_profile.txt  %d 个纬带（GPCP v2.3 LTM 1991-2020 + ETOPO1）", OBS.length));

        say("");
        say("  [A] 纬向平均降水：模型 vs 观测（mm/day，cos-lat 加权，按 陆地/海洋 分开）");
        say("      latN |  陆JJA 模/观   陆DJF 模/观  |  海JJA 模/观   海DJF 模/观");
        double sumAbs = 0, sumObs = 0; int cnt = 0;
        double biasLJ = 0, biasLD = 0, biasSJ = 0, biasSD = 0; int nL = 0, nS = 0;
        for (double[] o : OBS) {
            double[] m = modelBand(o[0], sd, cell);
            say(String.format(LF, "      %5.2f | %6.2f %6.2f   %6.2f %6.2f  | %6.2f %6.2f   %6.2f %6.2f",
                o[0], m[0], o[1], m[1], o[2], m[2], o[3], m[3], o[4]));
            if (!Double.isNaN(m[0])) { biasLJ += m[0] - o[1]; nL++; sumAbs += Math.abs(m[0] - o[1]); sumObs += o[1]; cnt++; }
            if (!Double.isNaN(m[1])) biasLD += m[1] - o[2];
            if (!Double.isNaN(m[2])) { biasSJ += m[2] - o[3]; nS++; }
            if (!Double.isNaN(m[3])) biasSD += m[3] - o[4];
        }
        say("");
        say(String.format(LF, "      平均偏差：陆地 JJA %+.3f  陆地 DJF %+.3f  |  海洋 JJA %+.3f  海洋 DJF %+.3f",
            biasLJ / nL, biasLD / nL, biasSJ / nS, biasSD / nS));
        say(String.format(LF, "      陆地 JJA 平均绝对偏差 %.3f（观测均值 %.3f）", sumAbs / cnt, sumObs / cnt));

        say("");
        say("  [B] 留出集盒子（**不参与任何标定**）");
        say("      盒子               模型JJA  观测JJA   模型DJF  观测DJF   判定");
        double ho = 0, hn = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File(REF, "holdout_boxes.tsv")), "UTF-8"))) {
            String s;
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] t = s.split("\t");
                if (t.length < 8) continue;
                String nm = t[0];
                // ★ tsv 里的经度可能是负的（Python 的 %d 保留了原值）；本类的采样在 [0,360)
                double lo = ((Double.parseDouble(t[1]) % 360.0) + 360.0) % 360.0;
                double hi = ((Double.parseDouble(t[2]) % 360.0) + 360.0) % 360.0;
                double la0 = Double.parseDouble(t[3]), la1 = Double.parseDouble(t[4]);
                double oj = Double.parseDouble(t[5]), od = Double.parseDouble(t[6]);
                boolean hold = t[7].trim().equals("1");
                double[] m = modelBox(lo, hi, la0, la1, sd, cell);
                double rj = m[0] / oj, rd = m[1] / od;
                boolean ok = (rj > 0.4 && rj < 2.5) && (rd > 0.4 && rd < 2.5);
                say(String.format(LF, "      %-18s %6.2f  %6.2f   %6.2f  %6.2f   %s%s",
                    nm, m[0], oj, m[1], od, hold ? "[留出] " : "", ok ? "量级内 ✓" : "★ 超出 ±2.5x ★"));
                if (hold) { ho += (ok ? 1 : 0); hn++; }
            }
        } catch (IOException e) { say("      读 holdout_boxes.tsv 失败: " + e.getMessage()); }
        say(String.format(LF, "      => 留出集：%.0f/%.0f 在 ±2.5x 内", ho, hn));

        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
