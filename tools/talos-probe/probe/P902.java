package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P902 -- §569 开关组合受控 A/B：SoilMoisture / Vegetation / BLQ_GATE。
//   口径逐字照抄 P712（同 SEED / cell / GRAD / 采样格 / Boxes）。
//   目的：P901 测出 BLQ 单独无效（对比 1.14 -> 1.47）。本探针测「先让边界层变干」(P2) 是否打破那个循环。
public class P902 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P902] "+s); System.out.println("[P902] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};   // ASIA, SAHARA
    static final double[][] CST={{-70.4,-23.0},{-22.6,14.5},{28.0,-114.0}}; // Atacama, Namib, Baja(西岸)

    static double[] box(long sd,int cell,double th){
        double[] sQ=new double[2], sP=new double[2]; int[] n=new int[2];
        for(int b=0;b<2;b++)
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                double pm=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
                double[] h=new double[6];
                StationaryWave.DIAG_HEAT=new ThreadLocal<double[]>(){protected double[] initialValue(){return h;}};
                double qd=StationaryWave.netColumnHeating(x,z,sd,cell,th,GRAD,pm);
                StationaryWave.DIAG_HEAT=null;
                n[b]++; sQ[b]+=qd; sP[b]+=pm;
            }
        return new double[]{sQ[0]/n[0], sQ[1]/n[1], sP[0]/n[0], sP[1]/n[1]};
    }
    /** 三个海岸沙漠站（本半球夏季）的 P */
    static double[] coasts(long sd,int cell,double th){
        double[] r=new double[CST.length];
        for(int i=0;i<CST.length;i++) r[i]=PrecipField.mmPerDay(xOfLon(CST[i][1]), zOfLat(CST[i][0]), sd, cell, th, GRAD);
        return r;
    }

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p902_report.txt"),"UTF-8");
        say("P902: §569 开关组合 A/B（口径照抄 P712）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        double thJJA=Atmosphere.theta(0.0), thDJF=Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
        say(String.format(LF,"  sd=%d cell=%d GRAD=%d", sd, cell, GRAD));
        say("");
        say("  #  SOIL VEG  BLQ | ASIA Qdiv    P     | SAHARA Qdiv    P    | P_SAH/P_ASI | 三海岸 P(mm/d) Atacama/Namib/Baja | blocked");
        String[] names={"A 生产态","B +SOIL","C +SOIL+VEG","D +SOIL+VEG+BLQ"};
        Object[][] combos={{false,false,false},{true,false,false},{true,true,false},{true,true,true}};
        double[][] res=new double[4][];
        double[][] cres=new double[4][];
        for(int k=0;k<4;k++){
            SoilMoisture.ENABLED=(Boolean)combos[k][0];
            Vegetation.ENABLED=(Boolean)combos[k][1];
            PrecipField.BLQ_GATE=(Boolean)combos[k][2];
            SimClimate.clearCache();
            PrecipField.blqBlocked=0; PrecipField.blqCalls=0;
            double[] r=box(sd,cell,thJJA);
            double[] c=coasts(sd,cell,thJJA);
            res[k]=r; cres[k]=c;
            double ratio=(PrecipField.blqCalls>0)?100.0*PrecipField.blqBlocked/PrecipField.blqCalls:0.0;
            say(String.format(LF,"  %s | %+9.3f %7.3f | %+9.3f %7.3f | %10.3f | %7.3f %7.3f %7.3f | %5.1f%%",
                names[k], r[0], r[2], r[1], r[3], r[3]/r[2], c[0], c[1], c[2], ratio));
        }
        say("");
        say("  === 与生产态对比（JJA） ===");
        for(int k=1;k<4;k++){
            say(String.format(LF,"  %-14s ASIA P %+.1f%%  SAHARA P %+.1f%%  沙漠/季风比 %.3f -> %.3f   阿塔卡马 %.3f -> %.3f",
                names[k], 100.0*(res[k][2]-res[0][2])/res[0][2], 100.0*(res[k][3]-res[0][3])/res[0][3],
                res[0][3]/res[0][2], res[k][3]/res[k][2], cres[0][0], cres[k][0]));
        }
        say("");
        say("  P712 判据 [3] 沙漠 P < 季风 P ：");
        for(int k=0;k<4;k++) say(String.format(LF,"    %-14s %s  (%.3f vs %.3f)", names[k], (res[k][3]<res[k][2])?"PASS":"FAIL", res[k][3], res[k][2]));
        say("");
        say("  === DJF（只测 D 组合，看季节对比）===");
        SoilMoisture.ENABLED=true; Vegetation.ENABLED=true; PrecipField.BLQ_GATE=true; SimClimate.clearCache();
        double[] d=box(sd,cell,thDJF);
        say(String.format(LF,"  D 组合 DJF：ASIA Qdiv %+.3f P %.3f | SAHARA Qdiv %+.3f P %.3f", d[0],d[2],d[1],d[3]));
        say(String.format(LF,"  生产态 DJF 基线：ASIA -85.349 P 0.555 | SAHARA -91.610 P 0.342"));
        say("");
        say("  === 复位 ===");
        SoilMoisture.ENABLED=false; Vegetation.ENABLED=false; PrecipField.BLQ_GATE=false; SimClimate.clearCache();
        say("  三个开关已全部复位为 false；未改任何 src。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
