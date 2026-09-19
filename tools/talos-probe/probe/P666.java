package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P666 -- §454: netColumnHeating 的逐项分解。
//   疑点：netColumnHeating 第 190 行算了 absSolar（净短波），但【只喂给 skinTempLand】，
//         从未进入 qRad ⇒ 净柱辐射里【没有短波】。这是「写了但没接」缺陷类。
public class P666 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P666] " + s); System.out.println("[P666] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    /** 盒均 {Qnet, qLat, qSens, qRad, cwv, ts, ts-ta, ASR, insolation} */
    static double[] box(long sd, int cell, double th, int b) {
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        double[] a = new double[9]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double[] d = StationaryWave.DIAG_HEAT.get();
                double lat = WorldContract.latOf(z);
                double kk = Atmosphere.kappaMemo(x, z, sd, cell);
                double ins = Radiation.insolation(lat, Atmosphere.subsolarLat(th));
                double asr = ins * (1.0 - Radiation.albedo(kk > 0.5, d[4]));
                a[0] += qn; a[1] += d[0]; a[2] += d[1]; a[3] += d[2]; a[4] += d[3];
                a[5] += d[4]; a[6] += d[5]; a[7] += asr; a[8] += ins; n++;
            }
        for (int i = 0; i < 9; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p666_report.txt"), "UTF-8");
        say("P666: netColumnHeating 的逐项分解（§454）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double[] ks = {0.0, 0.02};
        for (int ki = 0; ki < ks.length; ki++) {
            StationaryWave.WVLW_K = ks[ki];
            SimClimate.clearCache(); SoilMoisture.invalidate(); StationaryWave.invalidate();
            say("");
            say(String.format(LF, "  --- WVLW_K = %.2f （Q_NET_HEATING 的支路） ---", ks[ki]));
            say("     盒子 |    Qnet   qLat    qSens    qRad  |  CWV    ts(K)  ts-ta |   ASR  insolation【未接入】");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| %+8.2f %+7.2f %+8.2f %+8.2f | %6.1f %7.2f %+6.2f | %+7.2f %+8.2f",
                    NM[b], a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]));
            }
            double[] A = box(sd, cell, thS, 0), B = box(sd, cell, thS, 1);
            say(String.format(LF, "     经向对比（亚-撒）: Qnet %+8.2f  qLat %+7.2f  qSens %+8.2f  qRad %+8.2f  ASR %+7.2f",
                A[0] - B[0], A[1] - B[1], A[2] - B[2], A[3] - B[3], A[7] - B[7]));
            say(String.format(LF, "     若把 ASR 接进 qRad：Qnet 变成 亚洲 %+8.2f  撒哈拉 %+8.2f  对比 %+8.2f",
                A[0] + A[7], B[0] + B[7], (A[0] + A[7]) - (B[0] + B[7])));
        }
        StationaryWave.WVLW_K = 0.0;
        SimClimate.clearCache(); SoilMoisture.invalidate(); StationaryWave.invalidate();
        say("");
        say("文献量级：热带柱净辐射加热 F_net 约 +100~+150 W/m2（海洋）/ 近 0（沙漠）；");
        say("          净柱长波冷却约 -100~-150 W/m2；水汽长波吸收的真实效应 O(10~50) W/m2。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}