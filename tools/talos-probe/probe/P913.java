package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;

// P913 -- §583 P692 全量版：143 个候选陆点全测 + 分位数分组 + 原地球地名作对照列。
public class P913 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P913] "+s); System.out.println("[P913] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(((lon%360.0)+360.0)%360.0/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static double thOf(double latd){ return (latd>=0)?Atmosphere.theta(0.0):Atmosphere.theta(WorldContract.DAYS_PER_YEAR*0.5); }
    static final Object[][] EARTH={
        {24.0,-14.0,"W.Sahara"},{ -23.0,15.0,"Namib"},{ -23.0,-70.0,"Atacama"},{28.0,-114.0,"Baja"},
        {32.0,-83.0,"US-SE"},{ -28.0,-49.0,"Brazil-S"},{ -28.0,152.0,"E.Australia"},{34.0,140.0,"Japan"}};

    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p913_report.txt"),"UTF-8");
        say("P913: §583 P692 全量版（本世界地理选点 + 分位数分组 + 地球地名对照）");
        EarthRef.install(); OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        int NLAT=25, NLON=36;
        double[][] kap=new double[NLAT][NLON], an=new double[NLAT][NLON];
        for(int i=0;i<NLAT;i++){ double latd=-60.0+i*5.0; int z=zOfLat(latd);
            for(int j=0;j<NLON;j++){ int x=xOfLon(j*10.0);
                kap[i][j]=Atmosphere.kappaMemo(x,z,sd,cell);
                an[i][j]=(kap[i][j]<=0.5)? Atmosphere.sstAnom(x,z,thOf(latd)) : 0.0; } }
        List<double[]> cand=new ArrayList<>();
        for(int i=1;i<NLAT-1;i++) for(int j=0;j<NLON;j++){
            if(kap[i][j]<=0.5) continue;
            double s=0; int n=0;
            for(int di=-1;di<=1;di++) for(int dj=-1;dj<=1;dj++){
                int ii=i+di, jj=((j+dj)%NLON+NLON)%NLON;
                if(kap[ii][jj]<=0.5){ s+=an[ii][jj]; n++; } }
            if(n>0) cand.add(new double[]{-60.0+i*5.0, j*10.0, s/n});
        }
        say("  候选陆点 = " + cand.size());
        cand.sort((p,q)->Double.compare(p[2],q[2]));
        int Q=Math.max(1,cand.size()/4);
        double sC=0,sW=0; int nC=0,nW=0;
        say("");
        say("  分位数分组：最冷邻接 25% vs 最暖邻接 25%");
        say("  冷组（前 " + Q + " 个）：");
        for(int i=0;i<Q;i++){ double[] p=cand.get(i); int x=xOfLon(p[1]), z=zOfLat(p[0]);
            double pp=PrecipField.mmPerDay(x,z,sd,cell,thOf(p[0]),GRAD); sC+=pp; nC++;
            if(i<5||i>=Q-2) say(String.format(LF,"    lat=%+6.1f lon=%5.1f 邻接 %+6.2f K  P=%.4f", p[0],p[1],p[2],pp)); }
        say("  暖组（后 " + Q + " 个）：");
        for(int i=cand.size()-Q;i<cand.size();i++){ double[] p=cand.get(i); int x=xOfLon(p[1]), z=zOfLat(p[0]);
            double pp=PrecipField.mmPerDay(x,z,sd,cell,thOf(p[0]),GRAD); sW+=pp; nW++;
            if(i<cand.size()-Q+5) say(String.format(LF,"    lat=%+6.1f lon=%5.1f 邻接 %+6.2f K  P=%.4f", p[0],p[1],p[2],pp)); }
        double rC=sC/nC, rW=sW/nW;
        say("");
        say(String.format(LF,"  冷海邻接组 = %.4f (n=%d)   暖海邻接组 = %.4f (n=%d)   比值 = %.4f", rC,nC,rW,nW,rC/rW));
        say(String.format(LF,"  GATE_COLDWARM_GEO = %s    （判据 < 1；文献/GPCP 锚 0.022）", (rC<rW)?"PASS":"FAIL"));
        say("");
        say("  对照列（地球地名，不作判据）：");
        double eD=0,eW=0; int eDn=0,eWn=0;
        for(int i=0;i<EARTH.length;i++){ double latd=(Double)EARTH[i][0], lon=(Double)EARTH[i][1];
            int x=xOfLon(lon), z=zOfLat(latd);
            double k=Atmosphere.kappaMemo(x,z,sd,cell);
            double pp=PrecipField.mmPerDay(x,z,sd,cell,thOf(latd),GRAD);
            say(String.format(LF,"    %-13s kappa %.3f (%s)  P=%.4f", EARTH[i][2], k, (k>0.5?"陆":"海"), pp));
            if(i<4){ eD+=pp; eDn++; } else { eW+=pp; eWn++; } }
        say(String.format(LF,"    地球地名组 沙漠 %.4f vs 暖流 %.4f ⇒ 比值 %.4f", eD/eDn, eW/eWn, (eD/eDn)/(eW/eWn)));
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
