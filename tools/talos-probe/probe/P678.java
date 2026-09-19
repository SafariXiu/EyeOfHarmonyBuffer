package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P678 -- §471（选项乙 的 go/no-go）：用【稳态边界层水汽收支】定 q_BL。
//   E = P + V,  V = 86400*rho*|w_BL|*(q - q_FT)   （与自由对流层的交换）
//   E = 86400*chv*(qSat(Ts)*beta - q) ;  P = k_P*q （模型自己的 P 对 q 是线性的）
//   => q* = (86400*chv*qSat(Ts)*beta + C*q_FT) / (86400*chv + k_P + C),  C = 86400*rho*|w_BL|
//   q_FT = q*exp(-(H_EFF/2)/H_MOIST)  （模型自己的损耗律）
public class P678 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P678] " + s); System.out.println("[P678] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double QFT_F = Math.exp(-0.5 * Atmosphere.H_EFF / PrecipField.H_MOIST);
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    /** 盒均 {q_now, q_budget, qRatio, M_now, M_budget, beta, wbl, kP, chv} */
    static double[] box(long sd, int cell, double th, int b) {
        double[] a = new double[9]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qn = PrecipField.DIAG.get()[5];
                double divU = PrecipField.DIAG.get()[0];
                double wE = PrecipField.DIAG.get()[3];
                double kk = Atmosphere.kappaMemo(x, z, sd, cell);
                double tS = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                double[] u = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                double chv = Radiation.bulkCoeff(kk, Math.hypot(u[0], u[1]));
                double beta = SoilMoisture.ENABLED ? SoilMoisture.betaAt(x, z, sd, cell, th, GRAD) : 1.0;
                double qsTs = PrecipField.qSat(tS);
                double kP = (qn > 1.0e-9) ? pm / qn : 0.0;          // 模型 P 对 q 的线性系数
                double wbl = Math.abs(-Atmosphere.H_BL * divU);
                double C = 86400.0 * Atmosphere.RHO_AIR * wbl;
                double den = 86400.0 * chv + kP + C;
                double qb = (den > 0) ? (86400.0 * chv * qsTs * beta + C * QFT_F * 0.0) / den : qn;
                // q_FT 与 q 成正比 => 代回：q = (A0 + C*QFT_F*q)/den  => q = A0/(den - C*QFT_F)
                double qb2 = (den - C * QFT_F > 0) ? (86400.0 * chv * qsTs * beta) / (den - C * QFT_F) : Double.NaN;
                double dh = 1004.0 * (tS - (tS - Atmosphere.GAMMA * 0.5 * Atmosphere.H_EFF))
                          - 9.81 * 0.5 * Atmosphere.H_EFF
                          + 2.45e6 * (qn - qn * QFT_F);
                double dhB = 1004.0 * (Atmosphere.GAMMA * 0.5 * Atmosphere.H_EFF)
                           - 9.81 * 0.5 * Atmosphere.H_EFF
                           + 2.45e6 * (qb2 - qb2 * QFT_F);
                a[0] += qn; a[1] += qb2; a[2] += beta; a[3] += wbl; a[4] += kP; a[5] += chv;
                a[6] += Atmosphere.RHO_AIR * Atmosphere.H_EFF * dh;
                a[7] += Atmosphere.RHO_AIR * Atmosphere.H_EFF * dhB;
                a[8] += pm; n++;
            }
        for (int i = 0; i < 9; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p678_report.txt"), "UTF-8");
        say("P678: 选项乙 的 go/no-go —— 稳态边界层水汽收支定 q_BL（§471）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SoilMoisture.ENABLED = true; Vegetation.ENABLED = true;
        Vegetation.RS_BARE = 150.0; Vegetation.RS_PASS = 2;
        SimClimate.clearCache(); SoilMoisture.invalidate(); Vegetation.invalidate();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        say(String.format(LF, "     q_FT/q = exp(-(H_EFF/2)/H_MOIST) = %.4f（模型自己的损耗律）", QFT_F));
        say("");
        String[] cn = {"1 现状（RH_SEA 口径）", "2 现状 + 水汽源（§448）"};
        Object[][] cfg = {{false},{true}};
        for (int k = 0; k < cn.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][0];
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            say("  --- " + cn[k] + " ---");
            say("     盒子 | q_现     q_收支   比 | M_现(J/m2)  M_收支(J/m2) | beta   |w_BL|(m/s)  k_P   chv   P");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| %.5f %.5f %5.2f | %+10.3e %+10.3e | %.3f %.3e %6.1f %.4f %.3f",
                    NM[b], a[0], a[1], a[1] / a[0], a[6], a[7], a[2], a[3], a[4], a[5], a[8]));
            }
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SoilMoisture.ENABLED = false; Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        say("");
        say("判据：① 收支给出的 q 与现状同量级（验证）；② M 在两个盒子都 > 0（可用）。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}