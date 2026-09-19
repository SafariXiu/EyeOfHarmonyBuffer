package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P677 -- §470（选项甲 的 go/no-go）：用模型自己的两层 T、q 算毛湿稳定度 M，
//   判 ∇·v₁ = F_net/M 这条【局地 WTG 闭合】是否【条件数可接受】。
//   M = rho*H_EFF*(h_BL - h_FT)，h = cp*T + g*z + L*q（Neelin 的 MSE 口径，§453）。
public class P677 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P677] " + s); System.out.println("[P677] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double CP = 1004.0, LV = 2.45e6, G = 9.81;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    /** 盒均 {M, dh, F_net, w_prop, w_now, T_BL, q_BL} */
    static double[] box(long sd, int cell, double th, int b) {
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        double[] a = new double[7]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qb = PrecipField.DIAG.get()[5];
                double wNow = PrecipField.DIAG.get()[3];
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double tBl = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                double hBl = CP * tBl + LV * qb;
                double tFt = tBl - Atmosphere.GAMMA * 0.5 * Atmosphere.H_EFF;
                double qFt = qb * Math.exp(-(0.5 * Atmosphere.H_EFF) / PrecipField.H_MOIST);
                double hFt = CP * tFt + G * 0.5 * Atmosphere.H_EFF + LV * qFt;
                double dh = hBl - hFt;
                double M = Atmosphere.RHO_AIR * Atmosphere.H_EFF * dh;
                double wProp = (M == 0.0) ? Double.NaN : -Atmosphere.H_BL * (qn / M);
                a[0] += M; a[1] += dh; a[2] += qn; a[3] += wProp; a[4] += wNow;
                a[5] += tBl; a[6] += qb; n++;
            }
        for (int i = 0; i < 7; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p677_report.txt"), "UTF-8");
        say("P677: 选项甲 的 go/no-go —— 毛湿稳定度 M 的条件数（§470）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        say(String.format(LF, "     两层口径：T_FT = T_BL - GAMMA*H_EFF/2 = T_BL - %.1f K；",
            0.5 * Atmosphere.H_EFF * Atmosphere.GAMMA));
        say(String.format(LF, "               q_FT = q_BL*exp(-(H_EFF/2)/H_MOIST) = q_BL*%.4f（模型自己的损耗律）；z_FT = %.0f m",
                Math.exp(-0.5 * Atmosphere.H_EFF / PrecipField.H_MOIST), 0.5 * Atmosphere.H_EFF));
        say("");
        String[] cn = {"1 基线（全 OFF）", "2 §448 最佳", "3 §448 + S3 + V"};
        Object[][] cfg = {{false,false},{true,false},{true,true}};
        for (int k = 0; k < cn.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][0];
            SoilMoisture.ENABLED = (Boolean) cfg[k][1];
            Vegetation.ENABLED = (Boolean) cfg[k][1];
            Vegetation.RS_BARE = ((Boolean) cfg[k][1]) ? 150.0 : 0.0;
            Vegetation.RS_PASS = 2;
            StationaryWave.Q_NET_HEATING = (Boolean) cfg[k][1];
            StationaryWave.QRAD_ASR_MINUS_OLR = (Boolean) cfg[k][1];
            StationaryWave.ENABLED = false;
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            say("  --- " + cn[k] + " ---");
            say("     盒子 |  T_BL    q_BL     dh(J/kg)      M(J/m2)     F_net(W/m2)  w_甲(m/s)    w_现(m/s)");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| %6.2f %8.5f %+10.1f %+12.3e %+10.2f %+11.3e %+11.3e",
                    NM[b], a[5], a[6], a[1], a[0], a[2], a[3], a[4]));
            }
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SoilMoisture.ENABLED = false; Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0;
        StationaryWave.Q_NET_HEATING = false; StationaryWave.QRAD_ASR_MINUS_OLR = false;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        say("");
        say("判据：M 必须在两个盒子【同号且远离 0】；否则 F_net/M 病态（正负翻转 / 爆量）。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}