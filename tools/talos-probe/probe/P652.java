package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P652 -- §439 选项 V 的 go/no-go，而且是【解析】的：
//   桶的稳态方程是   R(beta) = A*rh(beta) - beta*(e1 - e2*rh(beta)) = 0,  rh = RH_DRY + (RH_SEA-RH_DRY)*beta
//   数 R 在 [0,1] 上的【根个数】：1 个根 => 单稳（没有沙漠分支），3 个根 => 双稳（沙漠/草原）。
// A/e1/e2 的公式照抄 SoilMoisture.spinup（那里是私有），并用模型自己的 betaAt 做自检。
public class P652 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P652] " + s); System.out.println("[P652] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double D2R = Math.PI / 180.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    static double rh(double b) { return SoilMoisture.RH_DRY + (PrecipField.RH_SEA - SoilMoisture.RH_DRY) * b; }
    static double resid(double b, double A, double e1, double e2) { double r = rh(b); return A * r - b * (e1 - e2 * r); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p652_report.txt"), "UTF-8");
        say("P652: 桶稳态方程 R(beta)=0 的根个数（1 = 单稳 / 3 = 双稳）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("");
        say(String.format(LF, "  参数：RH_SEA=%.3f  RH_DRY=%.3f  W_FC=%.1f mm  WK_OVER_WFC=0.75(硬编码)",
            PrecipField.RH_SEA, SoilMoisture.RH_DRY, SoilMoisture.W_FC));
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0)};
        String[] tn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            say("");
            say(String.format(LF, "  === %s ===", tn[s]));
            say("     盒子   |  A(mm/day)   e1     e2   | R(0)     R(0.5)    R(1)   | 根个数 | 模型 betaAt  自检残差");
            for (int b = 0; b < 2; b++) {
                double th = ths[s];
                double sA = 0, se1 = 0, se2 = 0, n = 0;
                double s0 = 0, s5 = 0, s1 = 0, sroot = 0, sres = 0;
                for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                    for (int c = 0; c < 72; c++) {
                        double lon = (c + 0.5) * 5.0;
                        if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                        int x = xOfLon(lon), z = zOfLat(latd);
                        double lat = WorldContract.latOf(z);
                        double k = Atmosphere.kappaMemo(x, z, sd, cell);
                        PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                        double divU = PrecipField.DIAG.get()[2];
                        double[] u = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                        double sp = Math.hypot(u[0], u[1]);
                        double hUp = PrecipField.upwindElev(x, z, sd, cell, u[0], u[1]);
                        double depl = PrecipField.depletion(hUp, k);
                        double tSl = Atmosphere.zonalMeanSeaLevelK(lat)
                                   + Atmosphere.seasonalAnomalyZonal(lat, th);
                        double wE = PrecipField.wEff(lat, th, divU);
                        double qsTQ = PrecipField.qSat(tSl);
                        double chv = Radiation.bulkCoeff(k, sp);
                        double qa1 = PrecipField.RH_SEA * qsTQ * depl;
                        double absSolar = Radiation.insolation(lat, Atmosphere.subsolarLat(th))
                                        * (1.0 - Radiation.albedo(k > 0.5, tSl));
                        double tsPot = Radiation.skinTempLand(absSolar, tSl, qa1, chv, 1.0);
                        double qsTs = PrecipField.qSat(tsPot);
                        double A = (wE <= 0.0) ? 0.0 : PrecipField.EPS_C * Atmosphere.RHO_AIR * qsTQ * depl
                                  / PrecipField.RHO_WATER * wE * 86400.0 * 1000.0;
                        double e1 = chv * qsTs / PrecipField.RHO_WATER * 86400.0 * 1000.0;
                        double e2 = chv * qsTQ * depl / PrecipField.RHO_WATER * 86400.0 * 1000.0;
                        int roots = 0; double prev = resid(0.0, A, e1, e2);
                        for (int i = 1; i <= 200; i++) {
                            double bb = i / 200.0;
                            double r = resid(bb, A, e1, e2);
                            if (r == 0.0 || (prev > 0) != (r > 0)) roots++;
                            prev = r;
                        }
                        double bMod = SoilMoisture.betaAt(x, z, sd, cell, th, GRAD);
                        sA += A; se1 += e1; se2 += e2;
                        s0 += resid(0.0, A, e1, e2); s5 += resid(0.5, A, e1, e2); s1 += resid(1.0, A, e1, e2);
                        sroot += roots; sres += Math.abs(resid(bMod, A, e1, e2)); n++;
                    }
                say(String.format(LF, "     %s | %9.3f %7.2f %6.2f | %+8.3f %+8.3f %+8.3f | %6.2f | %.4f   %8.2e",
                    NM[b], sA / n, se1 / n, se2 / n, s0 / n, s5 / n, s1 / n, sroot / n,
                    SoilMoisture.betaAt(xOfLon((BOX[b][0] + BOX[b][1]) / 2), zOfLat(25.0), sd, cell, th, GRAD), sres / n));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
