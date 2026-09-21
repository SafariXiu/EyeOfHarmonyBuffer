package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P909 -- §579 判决：SST 距平到底装没装上、在暖流处是正还是负。
public class P909 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P909] "+s); System.out.println("[P909] "+s); rep.flush(); }
    static final int SEED=1022228679;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final Object[][] PT={
        {"GulfStream 32N/83W (暖)",32.0,-83.0},{"Brazil 28S/49W (暖)",-28.0,-49.0},
        {"E.Australia 28S/152E (暖)",-28.0,152.0},{"Kuroshio 34N/140E (暖)",34.0,140.0},
        {"California 28N/114W (冷)",28.0,-114.0},{"Canary 24N/14W (冷)",24.0,-14.0},
        {"Peru 23S/70W (冷)",-23.0,-70.0},{"Benguela 23S/15E (冷)",-23.0,15.0}};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p909_report.txt"),"UTF-8");
        say("P909: §579 SST 距平判决（provider 有没有装 + 暖流处符号）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        boolean installed = (Atmosphere.SST_PROVIDER != null);
        say("  Atmosphere.SST_PROVIDER 非空 = " + installed);
        double thN=Atmosphere.theta(0.0), thS=Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
        say("");
        say("  站点                      | 半球 | T_zm(纬向平均) C | SST距平 K | 实际 SST C | 类型");
        for(int i=0;i<PT.length;i++){
            double latd=(Double)PT[i][1], lon=(Double)PT[i][2];
            int x=xOfLon(lon), z=zOfLat(latd);
            double th=(latd>=0)?thN:thS;
            double tzm=Atmosphere.seaSurfaceTemp(WorldContract.latOf(z), th);
            double anom=Atmosphere.sstAnom(x,z,th);
            say(String.format(LF,"  %-24s | %s | %14.2f | %+8.2f | %9.2f | %s",
                PT[i][0], (latd>=0?"JJA":"DJF"), tzm-273.15, anom, tzm+anom-273.15, (i<4?"应暖":"应冷")));
        }
        say("");
        say("  判读：前 4 个是【西边界暖流】⇒ 距平应为【正】；后 4 个是【东边界寒流】⇒ 应为【负】。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
