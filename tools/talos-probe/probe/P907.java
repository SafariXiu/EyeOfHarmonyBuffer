package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P907 -- §577 BLQ_THETA_E (Folkins & Braun 2003 湿熵阈值) 受控 A/B。
public class P907 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P907] "+s); System.out.println("[P907] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};
    static final String[] NM={"ASIA 70-120E/15-35N","SAHARA 0-30E/20-35N"};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p907_report.txt"),"UTF-8");
        say("P907: §577 θ_e 阈值判据 A/B（口径照抄 P712/P901）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        say("  观测锚：亚洲季风 JJA 7.667 ；撒哈拉 JJA 0.105 ；比值 0.0137");
        say("");
        say("  算例                |  ASIA P  | SAHARA P |  比值  | blocked | mean dh(K)");
        boolean[] cases={false,true};
        String[] cn={"BLQ_THETA_E=false","BLQ_THETA_E=true "};
        for(int c=0;c<2;c++){
            PrecipField.BLQ_THETA_E=cases[c];
            SimClimate.clearCache();
            PrecipField.blqBlocked=0; PrecipField.blqCalls=0;
            double[] sP=new double[2]; int[] n=new int[2];
            for(int b=0;b<2;b++)
                for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
                for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                    int x=xOfLon(lon), z=zOfLat(latd);
                    n[b]++; sP[b]+=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
                }
            double rA=sP[0]/n[0], rS=sP[1]/n[1];
            double pct=(PrecipField.blqCalls>0)?100.0*PrecipField.blqBlocked/PrecipField.blqCalls:0.0;
            say(String.format(LF,"  %s | %8.3f | %8.3f | %6.3f | %6.1f%% | %+8.2f",
                cn[c], rA, rS, rS/rA, pct, PrecipField.blqLastDh));
        }
        PrecipField.BLQ_THETA_E=false; SimClimate.clearCache();
        say("");
        say("  参考：θ_e,conv 端点 334 K(≤25C) / 342 K(≥28C)");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
