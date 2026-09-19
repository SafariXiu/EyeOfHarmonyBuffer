package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P661 -- §447 季风源区海温：逐相位海洋 SST 的 A/B、代价、自洽性、季节振幅。
//   自洽性判据（必须逐位为 0）：mean_theta(annual path) 与 sstAnom(x,z,theta) 的 12 月均值
//   在**数学上恒等**（4 点等间距上环形线性插值的整数倍采样，均值 = 4 样本均值）
//   ⇒ 它同时验「年平支路没改坏」和「季节支路与年平口径一致」。
public class P661 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P661] " + s); System.out.println("[P661] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[] PH = {0.0, Math.PI/2, Math.PI, 3*Math.PI/2};
    static final String[] PHN = {"0.00pi(夏至/NH JJA)", "0.50pi(秋分)", "1.00pi(冬至/NH DJF)", "1.50pi(春分)"};

    static final double[][] R = {{88.5,19.3},{62.0,15.0},{115.0,15.0},{52.0,5.0},{-20.0,26.0},{-40.0,15.0}};
    static final String[] RN = {"孟加拉湾","阿拉伯海","南海","索马里外海","西撒外海","中西大西洋"};

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p661_report.txt"), "UTF-8");
        say("P661: 季风源区的海温季节循环（逐相位海洋 SST，§447）");
        EarthRef.install();
        OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;

        // ---------- A 段：开关 OFF ----------
        OceanField.PHASE_SEASONAL = false;
        SimClimate.clearCache();
        long c0 = OceanField.solveCount;
        say("");
        say("A 段 PHASE_SEASONAL=false —— 所有 theta 读数必须与年平**逐位相同**（自证 A/B 干净）");
        say("     区域        | 年平 SST'(K) | max|theta-年平| (K)");
        double[] annA = new double[R.length];
        for (int r = 0; r < R.length; r++) {
            int x = xOfLon(R[r][0]), z = zOfLat(R[r][1]);
            double a = Atmosphere.sstAnom(x, z);
            annA[r] = a;
            double mx = 0;
            for (int q = 0; q < 4; q++) mx = Math.max(mx, Math.abs(Atmosphere.sstAnom(x, z, PH[q]) - a));
            say(String.format(LF, "     %-11s |  %+10.6f  |  %.3e%s", RN[r], a, mx,
                (mx == 0.0 ? "   OK 逐位" : "   ** 非逐位 **")));
        }
        say("     A 段解行数 = " + (OceanField.solveCount - c0));

        // ---------- B 段：开关 ON ----------
        OceanField.PHASE_SEASONAL = true;
        SimClimate.clearCache();
        long c1 = OceanField.solveCount;
        say("");
        say("B 段 PHASE_SEASONAL=true —— 4 相位采样 + theta 上环形线性插值");
        say("     区域        | 相位                  SST'(K)   T_src(K)   zonal季节项(K)");
        double[] amp = new double[R.length], jja = new double[R.length], djf = new double[R.length];
        for (int r = 0; r < R.length; r++) {
            int x = xOfLon(R[r][0]), z = zOfLat(R[r][1]);
            boolean land = PlateField.isLandWithCell(x, z, sd, cell);
            int[] sp = OceanField.spanOf(x, z, SEED);
            double latRad = WorldContract.latOf(z);
            double mn = 1e9, mx2 = -1e9;
            say(String.format(LF, "     %-11s | 海盆 [%d, %d] km %s", RN[r],
                sp == null ? 0 : sp[1]/1000, sp == null ? 0 : sp[2]/1000,
                (land ? " **(陆地点!)**" : (sp == null ? " **(无海盆)**" : ""))));
            for (int q = 0; q < 4; q++) {
                double v = Atmosphere.sstAnom(x, z, PH[q]);
                double t = Atmosphere.annualSeaLevelTemp(latRad, 0.0, v);
                double zo = Atmosphere.seasonalAnomaly(latRad, 0.0, PH[q]);
                mn = Math.min(mn, v); mx2 = Math.max(mx2, v);
                if (q == 0) jja[r] = v;
                if (q == 2) djf[r] = v;
                say(String.format(LF, "     %-11s | %-20s %+8.4f  %8.3f   %+8.4f", RN[r], PHN[q], v, t, zo));
            }
            double a2 = Atmosphere.sstAnom(x, z);
            double s12 = 0;
            for (int m = 0; m < 12; m++) s12 += Atmosphere.sstAnom(x, z, 2.0*Math.PI*(m+0.5)/12.0);
            amp[r] = mx2 - mn;
            say(String.format(LF, "     %-11s | 年平(4 相位均) %+8.4f | JJA-DJF %+8.4f | 峰谷 %6.3f | 12 月均值 %+8.4f  自洽残差 %.3e K",
                RN[r], a2, jja[r]-djf[r], amp[r], s12/12.0, Math.abs(a2 - s12/12.0)));
        }
        say("     B 段解行数 = " + (OceanField.solveCount - c1));

        // ---------- C 段：年平读数有没有被改坏 ----------
        say("");
        say("C 段 年平读数 A/B 差（**这一项必须非零** —— 年平口径从「对年平 curl 解一次」");
        say("     换成「4 个相位解的平均」，两者因 hc/jetPeak/tanh 的**非线性**而不相等）");
        say("     区域        | A 年平(K)   |  B 年平(K)   |  差(K)");
        OceanField.PHASE_SEASONAL = true;
        for (int r = 0; r < R.length; r++) {
            int x = xOfLon(R[r][0]), z = zOfLat(R[r][1]);
            double b = Atmosphere.sstAnom(x, z);
            say(String.format(LF, "     %-11s | %+10.6f | %+10.6f | %+9.6f", RN[r], annA[r], b, b - annA[r]));
        }
        say("");
        say("NONLINEARITY_NOTE: A/B 之差 = 季节循环经非线性传递后的净效应；它**不是**误差。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
