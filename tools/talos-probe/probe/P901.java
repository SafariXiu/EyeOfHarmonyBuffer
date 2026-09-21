package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P901 -- §568 BLQ_GATE 受控 A/B：翻它，量 P692/P683/P712 三个门关心的量。
//   口径逐字照抄 P712（同一 SEED / cell / GRAD / 采样格 / Boxes 定义）。
//   BLQ_GATE=false 是当前生产态；true 是 §496 那条「边界层准平衡对流判据」。
public class P901 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P901] "+s); System.out.println("[P901] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    // [0]=ASIA 70-120E/15-35N   [1]=SAHARA 0-30E/20-35N
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};

    /** 返回 {Q_ASIA, Q_SAHARA, P_ASIA, P_SAHARA, n_ASIA, n_SAHARA} */
    static double[] measure(long sd, int cell, double th){
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
        return new double[]{sQ[0]/n[0], sQ[1]/n[1], sP[0]/n[0], sP[1]/n[1], n[0], n[1]};
    }

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p901_report.txt"),"UTF-8");
        say("P901: §568 BLQ_GATE 受控 A/B（口径照抄 P712）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        double thJJA=Atmosphere.theta(0.0);
        double thDJF=Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5);
        say(String.format(LF,"  sd=%d  cell=%d  GRAD=%d", sd, cell, GRAD));
        say("");
        say("  算例          | ASIA  Qdiv(W/m2)   P(mm/d) | SAHARA Qdiv        P      | blocked/calls  meanDh");
        double[] off=null, on=null;
        for(int k=0;k<2;k++){
            boolean gate = (k==1);
            PrecipField.BLQ_GATE = gate;
            SimClimate.clearCache();
            for(int s=0;s<2;s++){
                double th = (s==0)? thJJA : thDJF;
                String tag = (s==0)?"JJA":"DJF";
                PrecipField.blqBlocked=0; PrecipField.blqCalls=0;
                double[] r = measure(sd, cell, th);
                double ratio = (PrecipField.blqCalls>0)? (100.0*PrecipField.blqBlocked/PrecipField.blqCalls) : 0.0;
                say(String.format(LF,"  BLQ=%-5s %s | %+9.3f  %7.3f | %+9.3f  %7.3f | %6.1f%%  %+.1f",
                    gate?"true":"false", tag, r[0], r[2], r[1], r[3], ratio, PrecipField.blqLastDh));
                if(s==0){ if(gate) on=r; else off=r; }
            }
        }
        say("");
        say("  === JJA 变化量（true - false） ===");
        say(String.format(LF,"  ASIA   Qdiv %+.3f -> %+.3f  (Δ %+.3f)   P %.3f -> %.3f  (Δ %+.3f, %+.1f%%)",
            off[0], on[0], on[0]-off[0], off[2], on[2], on[2]-off[2], 100.0*(on[2]-off[2])/off[2]));
        say(String.format(LF,"  SAHARA Qdiv %+.3f -> %+.3f  (Δ %+.3f)   P %.3f -> %.3f  (Δ %+.3f, %+.1f%%)",
            off[1], on[1], on[1]-off[1], off[3], on[3], on[3]-off[3], 100.0*(on[3]-off[3])/off[3]));
        say("");
        say(String.format(LF,"  P712 判据 [3] 沙漠 P < 季风 P ： false=%s (%.3f vs %.3f)   true=%s (%.3f vs %.3f)",
            (off[3]<off[2])?"PASS":"FAIL", off[3], off[2], (on[3]<on[2])?"PASS":"FAIL", on[3], on[2]));
        say(String.format(LF,"  P712 判据 [1] 季风 Qdiv>0      ： false=%s   true=%s", (off[0]>0)?"PASS":"FAIL", (on[0]>0)?"PASS":"FAIL"));
        say(String.format(LF,"  P712 判据 [2] 沙漠 Qdiv<0      ： false=%s   true=%s", (off[1]<0)?"PASS":"FAIL", (on[1]<0)?"PASS":"FAIL"));
        PrecipField.BLQ_GATE = false;
        say("");
        say("  说明：BLQ_GATE 已复位为 false；本探针只读生产路径，未改任何 src。");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
