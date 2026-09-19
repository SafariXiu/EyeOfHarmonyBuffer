package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*; import java.util.Locale;

// P639 -- §428 的收口判据：涡动门/闭合换配置后，【中纬海洋 DJF 降水】对 GPCP 锚的比值。
// 复用 P621 的仪器（EarthRef + OceanField.install + SimClimate.clearCache，§420 的纪律）。
public class P639 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File REF = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
    static PrintStream rep;
    static void say(String s) { rep.println("[P639] " + s); System.out.println("[P639] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static double[] modelBand(double latc, long sd, int cell) {
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double w = Math.cos(Math.toRadians(latc));
        double sLJ = 0, sLD = 0, sSJ = 0, sSD = 0, wL = 0, wS = 0;
        for (int latOff = -1; latOff <= 1; latOff++) {
            int z = zOfLat(latc + latOff * 0.8);
            for (int c = 0; c < 144; c++) {
                int x = (int) Math.round((c + 0.5) * 2.5 / 360.0 * CIRC);
                boolean land = Atmosphere.kappaMemo(x, z, sd, cell) > 0.5;
                double pj = PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                double pd = PrecipField.mmPerDay(x, z, sd, cell, thW, GRAD);
                if (land) { sLJ += pj * w; sLD += pd * w; wL += w; }
                else { sSJ += pj * w; sSD += pd * w; wS += w; }
            }
        }
        return new double[]{sLJ / wL, sLD / wL, sSJ / wS, sSD / wS};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p639_report.txt"), "UTF-8");
        say("P639: 中纬海洋/陆地 DJF+JJA 降水 vs GPCP 锚（涡动门 3 模式 x 闭合 2 种）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        List<double[]> obs = new ArrayList<double[]>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
            new File(REF, "obs_lat_profile.txt")), "UTF-8"));
        String s;
        while ((s = br.readLine()) != null) {
            s = s.trim();
            if (s.isEmpty() || s.startsWith("GPCP") || s.startsWith("latN")) continue;
            String[] t = s.split("\\s+");
            if (t.length < 6) continue;
            obs.add(new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]),
                Double.parseDouble(t[3]), Double.parseDouble(t[4])});
        }
        br.close();
        say(String.format(LF, "  锚 %d 个纬带（GPCP v2.3 LTM）", obs.size()));

        int[] gms = {0, 2, 1, 0, 2, 2};
        int[] cls = {1, 0, 0, 0, 1, 0};
        int[] t8s = {0, 0, 0, 0, 0, 1};
        String[] nm = {"A 旧生产 门0+sig2", "B 门2+sig1", "C 门1+sig1", "D 门0+sig1", "E 门2+sig2", "F 门2+sig1+850hPa"};
        say("");
        // ⚠ 列标签修正（第一版把 modelBand 的返回顺序 {landJ,landD,seaJ,seaD} 与 obs 列序 {landJJA,landDJF,seaJJA,seaDJF} 配错了）：
        //   m[0]=陆JJA m[1]=陆DJF m[2]=海JJA m[3]=海DJF；o[1]=陆JJA o[2]=陆DJF o[3]=海JJA o[4]=海DJF
        say("      配置           纬带 |  海JJA 模/观 (比值)  |  陆DJF 模/观 (比值)  |  陆JJA 比值  海DJF 比值");
        for (int cfg = 0; cfg < nm.length; cfg++) {
            PrecipField.EDDY_GATE_MODE = gms[cfg];
            PrecipField.EDDY_CLOSURE = cls[cfg];
            PrecipField.EDDY_SIGMA_T850 = (t8s[cfg] == 1);
            SimClimate.clearCache();
            double rS = 0, rL = 0, rSJ = 0, rLJ = 0; int n = 0;
            say(String.format(LF, "   --- %s ---", nm[cfg]));
            for (double[] o : obs) {
                if (o[0] < 25.0 || o[0] > 60.0) continue;
                double[] m = modelBand(o[0], sd, cell);
                double a = m[2] / o[3];   // 海JJA
                double b = m[1] / o[2];   // 陆DJF
                double c2 = m[0] / o[1];  // 陆JJA
                double d = m[3] / o[4];   // 海DJF
                say(String.format(LF, "      %5.1fN | %6.2f/%6.2f = %5.2f | %6.2f/%6.2f = %5.2f | %5.2f  %5.2f",
                    o[0], m[2], o[3], a, m[1], o[2], b, c2, d));
                rS += a; rL += b; rSJ += c2; rLJ += d; n++;
            }
            say(String.format(LF, "      ==> 25~60N 平均比值：海JJA %.2f   陆DJF %.2f   陆JJA %.2f   海DJF %.2f  (n=%d)",
                rS / n, rL / n, rSJ / n, rLJ / n, n));
        }
        PrecipField.EDDY_GATE_MODE = 2; PrecipField.EDDY_CLOSURE = 0; PrecipField.EDDY_SIGMA_T850 = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
