package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P629 -- 把赤道的 divU 拆成 du/dx 与 dv/dz，定位它到底来自哪。
public class P629 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P629] " + s); System.out.println("[P629] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p629_report.txt"), "UTF-8");
        say("P629: 赤道 divU 的分量分解");
        EarthRef.install();
        OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(String.format(LF, "  U_MAX=%.1f  H_BL=%.1f  U0_STORM=%.1f", Atmosphere.U_MAX, Atmosphere.H_BL, com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.U0_STORM));
        say("");
        say("  纬度   u(m/s)    v(m/s)    du/dx(1/s)   dv/dz(1/s)   divU(1/s)   pzRef(Pa/m)   uZm(m/s)");
        for (double latd = 0; latd <= 40; latd += 5) {
            int z = WorldContract.zOfLat(latd);
            int x0 = (int) Math.round(180.0 / 360.0 * CIRC);   // 经度 180
            double[] u0 = Atmosphere.windAt(x0, z, sd, cell, th, GRAD);
            double[] ux = Atmosphere.windAt(x0 + GRAD, z, sd, cell, th, GRAD);
            double[] uw = Atmosphere.windAt(x0 - GRAD, z, sd, cell, th, GRAD);
            double[] un = Atmosphere.windAt(x0, z + GRAD, sd, cell, th, GRAD);
            double[] us = Atmosphere.windAt(x0, z - GRAD, sd, cell, th, GRAD);
            double dudx = (ux[0] - uw[0]) / (2.0 * GRAD);
            double dvdz = (un[1] - us[1]) / (2.0 * GRAD);
            double latRad = WorldContract.latOf(z);
            double pzRef = ZonalTables.pRefSlopePerRad(Math.toDegrees(latRad)) / WorldContract.R_EFF;
            double uzm = ZonalTables.uZmBlend(Math.toDegrees(latRad), th, Atmosphere.kappaMemo(x0, z, sd, cell));
            say(String.format(LF, "  %4.0f  %+7.3f  %+7.3f  %+11.4e  %+11.4e  %+11.4e  %+11.4e  %+7.3f",
                latd, u0[0], u0[1], dudx, dvdz, dudx + dvdz, pzRef, uzm));
        }
        say("");
        say("  [B] 赤道（lat=0）沿经度：uZmBlend 是否随经度变？（它只依赖纬度+kappa）");
        int z0 = 0;
        for (int c : new int[]{0, 90, 180, 270}) {
            int x = (int) Math.round(c * CIRC / 360.0);
            double[] u0 = Atmosphere.windAt(x, z0, sd, cell, th, GRAD);
            double k = Atmosphere.kappaMemo(x, z0, sd, cell);
            say(String.format(LF, "      lon=%3d  kappa=%.3f  uZmBlend=%.4f  u=%.4f  v=%.4f",
                c, k, ZonalTables.uZmBlend(0.0, th, k), u0[0], u0[1]));
        }
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
