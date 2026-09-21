package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P906 -- §576 BLQ_SMOOTH_K 扫描：找「季风活得下来、沙漠活不下来」的那一档。
//   口径照抄 P712/P901。BLQ_GATE=true 固定，SoilMoisture 走生产默认(true)。
public class P906 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P906] "+s); System.out.println("[P906] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};

    static double[] box(long sd,int cell,double th){
        double[] sP=new double[2]; int[] n=new int[2];
        for(int b=0;b<2;b++)
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                n[b]++; sP[b]+=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            }
        return new double[]{sP[0]/n[0], sP[1]/n[1]};
    }

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p906_report.txt"),"UTF-8");
        say("P906: §576 BLQ_SMOOTH_K 扫描（BLQ_GATE=true 固定，SoilMoisture=生产默认 true）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        double thJJA=Atmosphere.theta(0.0);
        say(String.format(LF,"  sd=%d cell=%d  theta=JJA", sd, cell));
        say("  观测锚：亚洲季风 JJA 7.667 mm/d ；撒哈拉 JJA 0.105 mm/d ；比值 0.0137");
        say("");
        say("  BLQ  SMOOTH_K |  ASIA P  | SAHARA P |  比值  | ASIA 相对观测 | 判定");
        double[] ks={0.25,0.5,1.0,2.0,4.0,8.0};
        for(int i=0;i<ks.length;i++){
            PrecipField.BLQ_GATE = true;
            PrecipField.BLQ_SMOOTH_K = ks[i];
            SimClimate.clearCache();
            double[] r=box(sd,cell,thJJA);
            double ratio=r[1]/r[0];
            say(String.format(LF,"  true %8.2f | %8.3f | %8.3f | %6.3f | %12.1f%% | %s",
                ks[i], r[0], r[1], ratio, 100.0*r[0]/7.667, (ratio<0.5?"方向对":"")));
        }
        PrecipField.BLQ_GATE=false; PrecipField.BLQ_SMOOTH_K=1.0; SimClimate.clearCache();
        double[] base=box(sd,cell,thJJA);
        say("");
        say(String.format(LF,"  对照 BLQ=false | %8.3f | %8.3f | %6.3f |", base[0], base[1], base[1]/base[0]));
        say("");
        say("  判读：找「ASIA 接近 7.667 而 SAHARA 接近 0.105」的那一档 SMOOTH_K。");
        say("  注意 BLQ_SMOOTH_K 是【数值光滑宽度】不是物理常数 ⇒ 它不能承载物理结论，只能决定「阈值附近的软硬」。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
