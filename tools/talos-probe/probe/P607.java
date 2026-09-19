package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P607 -- 陆海热容对比：现有表给的是什么？平板模型会给什么？
public class P607 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P607] " + s); System.out.println("[P607] " + s); }

    static final double C_SEA = 1000.0 * 4000.0 * 50.0;
    static final double C_LAND = 1500.0 * 1000.0 * 1.0;

    static double omega() { return 2.0 * Math.PI / WorldContract.DAYS_PER_YEAR; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p607_report.txt"), "UTF-8");
        say("P607: 陆海热容对比（平板模型 vs 现有表）");
        say(String.format(LF, "  DAYS_PER_YEAR=%.0f   C_SEA=%.3e   C_LAND=%.3e  （比值 %.0f）",
            WorldContract.DAYS_PER_YEAR, C_SEA, C_LAND, C_SEA / C_LAND));
        say(String.format(LF, "  EPS=%.4f   长波反馈 4*EPS*sigma*T^3 @288K = %.2f W/(m^2 K)",
            Radiation.EPS, 4 * Radiation.EPS * Radiation.SIGMA * Math.pow(288.0, 3)));

        say("");
        say("  [A] 现有表给的季节振幅与相位（北半球）");
        say("      纬度    aSea(K)  aLand(K)  比值   psiSea(度) psiLand(度)");
        for (double latd = 5; latd <= 65; latd += 10) {
            double as = ZonalTables.aSea(latd), al = ZonalTables.aLand(latd);
            say(String.format(LF, "      %4.0f    %6.2f   %6.2f   %5.2f", latd, as, al, al / as));
        }

        say("");
        say("  [B] 平板模型给的振幅与滞后（lambda 全部用模型自己的量）");
        say("      lambda = 4*EPS*sigma*T^3 + beta*chv*LV*dqsat/dT");
        say("      纬度   T(K)   lam_long  lam_evap   lam_tot  tau_sea(d) tau_land(d)  A_sea  A_land  比");
        double chvRef = 1.2 * 1.3e-3 * 6.0;   // rho * C_D * |V|
        for (double latd = 5; latd <= 65; latd += 10) {
            double lat = Math.toRadians(latd);
            double t = Atmosphere.annualSeaLevelTemp(lat, 0.5, 0.0);
            double lamLong = 4.0 * Radiation.EPS * Radiation.SIGMA * t * t * t;
            double dq = (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.qSat(t + 1.0)
                       - com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.qSat(t - 1.0)) / 2.0;
            double lamEvap = chvRef * Radiation.LV * dq;
            // 海洋：beta=1；陆地：beta=0.3（§387 的干沙值）
            double lamSea = lamLong + lamEvap;
            double lamLand = lamLong + 0.3 * lamEvap;
            double w = omega();
            double tauSea = C_SEA / lamSea, tauLand = C_LAND / lamLand;
            // F0：吸收太阳的季节振幅
            double f0 = (Radiation.insolation(lat, Math.toRadians(23.44))
                       - Radiation.insolation(lat, -Math.toRadians(23.44))) / 2.0;
            double aSea = (f0 / lamSea) / Math.sqrt(1 + Math.pow(w * tauSea, 2));
            double aLand = (f0 / lamLand) / Math.sqrt(1 + Math.pow(w * tauLand, 2));
            say(String.format(LF, "      %4.0f  %6.1f  %7.2f  %8.2f  %8.2f   %8.1f    %8.1f   %5.1f  %5.1f  %5.2f",
                latd, t, lamLong, lamEvap, lamSea, tauSea / 86400.0, tauLand / 86400.0, aSea, aLand, aLand / aSea));
        }

        say("");
        say("  [C] 滞后相位 phi = atan(omega*tau)（度）");
        say("      纬度   phi_sea  phi_land  差");
        double chv2 = chvRef;
        for (double latd = 5; latd <= 65; latd += 10) {
            double lat = Math.toRadians(latd);
            double t = Atmosphere.annualSeaLevelTemp(lat, 0.5, 0.0);
            double lamLong = 4.0 * Radiation.EPS * Radiation.SIGMA * t * t * t;
            double dq = (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.qSat(t + 1.0)
                       - com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.qSat(t - 1.0)) / 2.0;
            double lamEvap = chv2 * Radiation.LV * dq;
            double w = omega();
            double pSea = Math.toDegrees(Math.atan(w * C_SEA / (lamLong + lamEvap)));
            double pLand = Math.toDegrees(Math.atan(w * C_LAND / (lamLong + 0.3 * lamEvap)));
            say(String.format(LF, "      %4.0f   %6.1f   %7.1f   %5.1f", latd, pSea, pLand, pSea - pLand));
        }
        say("");
        say(String.format(LF, "  现有表的滞后：PSI_SEA_DAYS=%.0f  PSI_LAND_DAYS=%.0f 天",
            Atmosphere.PSI_SEA_DAYS, Atmosphere.PSI_LAND_DAYS));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}