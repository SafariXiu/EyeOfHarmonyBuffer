package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P910 -- §580 判决：SST 距平为 0 是「那里没有海区」还是「重入守卫」。
public class P910 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P910] "+s); System.out.println("[P910] "+s); rep.flush(); }
    static final int SEED=1022228679;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final Object[][] PT={
        {"GulfStream 32N/83W",32.0,-83.0},{"Brazil 28S/49W",-28.0,-49.0},
        {"E.Australia 28S/152E",-28.0,152.0},{"Kuroshio 34N/140E",34.0,140.0},
        {"California 28N/114W",28.0,-114.0},{"Canary 24N/14W",24.0,-14.0},
        {"Peru 23S/70W",-23.0,-70.0},{"Benguela 23S/15E",-23.0,15.0}};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p910_report.txt"),"UTF-8");
        say("P910: §580 距平为 0 的判决（spanOf 是否为 null）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        say("  OceanField.ENABLED=" + OceanField.ENABLED + "  installedSeed=" + OceanField.installedSeed());
        say("");
        say("  站点                    |  z     | spanOf 结果 | 距平 K");
        int nulls=0;
        for(int i=0;i<PT.length;i++){
            double latd=(Double)PT[i][1], lon=(Double)PT[i][2];
            int x=xOfLon(lon), z=zOfLat(latd);
            String sp;
            try {
                int[] r=OceanField.spanOf(x, z, SEED);
                if(r==null){ sp="NULL"; } else { sp="[" + r[0] + "," + r[1] + "] 宽=" + (r[1]-r[0])/1000 + "km"; }
            } catch(Throwable t){ sp="EXC:" + t.getClass().getSimpleName(); }
            if(sp.equals("NULL")) nulls++;
            say(String.format(LF,"  %-22s | %9d | %-28s | %+7.3f",
                PT[i][0], z, sp, OceanField.anomalyAt(x, z, SEED)));
        }
        say("");
        say("  spanOf 为 NULL 的站数 = " + nulls + " / " + PT.length);
        say("  判读：NULL ⇒ 本世界的 OceanField 在该点【没有解出海区】⇒ 距平恒 0（不是重入守卫）");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
