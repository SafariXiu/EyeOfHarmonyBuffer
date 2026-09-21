package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.*; import java.util.Locale;

// P917 -- §587：极地夏季降水暴增约 4~9 倍，成因链在哪一环？
//   全部只碰【标量函数】（SST / 季节振幅表 / omega 表 / 涡动表）+ 陆地占比，不调 Zonal.profile ⇒ 秒级。
public class P917 {
    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        OceanWiring.onWorld(SEED);
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p917_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        long sd = SimTerrain.seedOf(SEED); int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        sb.append("P917: 极地成因链（标量函数，秒级）\n");
        sb.append("  sd=").append(sd).append("  WORLD_IS_TALOS=").append(PlateField.WORLD_IS_TALOS).append("\n\n");
        sb.append("  纬度  陆地%    SST夏     SST冬    ΔSST | aSea  aLand | wZm夏   wZm冬 (1e-3) | eddy表夏 eddy表冬\n");
        double[] lats = {55,60,62.5,65,67.5,70,72.5,75,75.6,77.5,80,82.5,82.8,85,87.5,90,
                         -55,-62.5,-67.5,-70,-72.5,-75,-75.6,-80,-82.5,-85,-90};
        for (double d : lats) {
            double lr = Math.toRadians(d);
            double tS, tW;
            try { tS = Atmosphere.seaSurfaceTemp(lr, thS); } catch (Throwable t) { tS = Double.NaN; }
            try { tW = Atmosphere.seaSurfaceTemp(lr, thW); } catch (Throwable t) { tW = Double.NaN; }
            double al = Math.abs(d);
            int z = WorldContract.zOfLat(d);
            int nl = 0, n = 0;
            for (int i = 0; i < 60; i++) {
                int x = (int) Math.round((i+0.5) * 40_000_000.0 / 60);
                if (PlateField.isLandWithCell(x, z, sd, cell)) nl++;
                n++;
            }
            sb.append(String.format(LF, "  %6.1f %5.0f%% %8.2f %8.2f %7.2f | %5.2f %5.2f | %7.3f %7.3f | %8.4f %8.4f%n",
                d, 100.0*nl/n, tS-273.15, tW-273.15, (tS-tW),
                ZonalTables.aSea(al), ZonalTables.aLand(al),
                ZonalTables.wZmMonth(d, thS)*1e3, ZonalTables.wZmMonth(d, thW)*1e3,
                ZonalTables.eddyMfcObsMonth(lr, thS), ZonalTables.eddyMfcObsMonth(lr, thW)));
        }
        sb.append("\n  判读：若 SST冬 在极地仍远高于冰点 / ΔSST 异常 ⇒ 缺海冰（自述「本世界没有海冰」）是主因；\n");
        sb.append("        若 SST 正常而 wZm/eddy表 在极地夏季暴增 ⇒ 是环流表把极地夏季喂太饱。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
