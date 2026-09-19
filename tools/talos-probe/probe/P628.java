package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P628 -- 赤道 divU 大 13.6 倍的因果链闭合：
//   aLand/aSea 的差 -> seasonalAnomaly 跨海岸线跳变 -> p' 梯度 -> f=0 处无科氏约束 -> 风大 -> 散度大
public class P628 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P628] " + s); System.out.println("[P628] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p628_report.txt"), "UTF-8");
        say("P628: 赤道 divU 的因果链");
        say(String.format(LF, "  K_P*CHI = %.1f Pa/K（代码注释给 276）  CELL_TROPIC_GATE_DEG=%.1f",
            Atmosphere.K_P * Atmosphere.CHI, Atmosphere.CELL_TROPIC_GATE_DEG));
        say("");
        say("  [A] 赤道附近的 陆/海 季节振幅与相位（表 aLand/aSea + PSI_*_DAYS）");
        say("      纬度  aSea(K)  aLand(K)  比   psiSea(度)  psiLand(度)");
        for (double latd = 0; latd <= 20; latd += 5) {
            double as = ZonalTables.aSea(latd), al = ZonalTables.aLand(latd);
            double pS = Atmosphere.PSI_SEA_DAYS * 360.0 / WorldContract.DAYS_PER_YEAR;
            double pL = Atmosphere.PSI_LAND_DAYS * 360.0 / WorldContract.DAYS_PER_YEAR;
            say(String.format(LF, "      %4.0f  %6.2f   %6.2f  %5.2f   %8.1f   %8.1f", latd, as, al, al / as, pS, pL));
        }
        say("");
        say("  [B] 赤道剖面上跨海岸线的 seasonalAnomaly 与 p'（JJA），找一个真实的岸线");
        EarthRef.install();
        OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        int z = 0;   // 赤道
        say("      经度   kappa   季节(K)      p'(Pa)     divU(1/s)");
        double prevK = -1, prevP = 0; int shown = 0;
        for (int c = 0; c < 360 && shown < 40; c += 2) {
            int x = (int) Math.round(c * CIRC / 360.0);
            double k = Atmosphere.kappaMemo(x, z, sd, cell);
            double lat = WorldContract.latOf(z);
            double sa = Atmosphere.seasonalAnomaly(lat, k, th);
            double pp = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            if (prevK >= 0 && Math.abs(k - prevK) > 0.15) {   // 只在跨海岸线处打
                PrecipField.mmPerDay(x, z, sd, cell, th, 500_000);
                say(String.format(LF, "      %5.0f  %.3f  %+8.3f  %+10.1f  %+11.4e",
                    (double) c, k, sa, pp, PrecipField.DIAG.get()[0]));
                shown++;
            }
            prevK = k; prevP = pp;
        }
        say("");
        say("  [C] 若赤道陆海季节振幅差是 0（aSea == aLand），divU 会变成多少？");
        say("      （不能改表 —— 只做解析估计：dT 从 0.9 K 降到 0 时 p' 梯度按比例降）");
        double as0 = ZonalTables.aSea(0.0), al0 = ZonalTables.aLand(0.0);
        say(String.format(LF, "      aSea(0)=%.3f  aLand(0)=%.3f  => 跨岸跳变上界约 %.3f K", as0, al0, al0 - as0));
        say(String.format(LF, "      K_P*CHI*(aLand-aSea) = %.1f Pa（跨岸压力跳变上界）", Atmosphere.K_P * Atmosphere.CHI * (al0 - as0)));
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
