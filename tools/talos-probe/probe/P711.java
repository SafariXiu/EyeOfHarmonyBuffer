package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;
// P711 -- §517 判别性测量：区域 MSE 平流收支 Qdiv 在两盒的【符号】
//   文献判据：季风【输出】MSE => Qdiv > 0 ；沙漠【输入】MSE => Qdiv < 0
public class P711 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P711] "+s); System.out.println("[P711] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};
    static final String[] BN={"ASIA  (应 Qdiv>0 输出MSE)","SAHARA(应 Qdiv<0 输入MSE)"};
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p711_report.txt"),"UTF-8");
        say("P711: §517 区域 MSE 平流收支 Qdiv = 潜热+感热+净辐射（模型自己的 netColumnHeating）");
        say("      文献判据：季风 Qdiv>0（输出 MSE）；沙漠 Qdiv<0（输入 MSE）");
        EarthRef.install(); com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   本探针此前完全未声明 ⇒ 读数可解释性被限定（不知道「亚洲盒」有多少是海）。
        Boxes.declare(rep, "P711 ASIA 70-120E/15-35N", sd, cell, 70,120,15,35, 10.0, 5.0);
        Boxes.declare(rep, "P711 SAHARA 0-30E/20-35N",  sd, cell,  0, 30,20,35, 10.0, 5.0);
        double[] sQ=new double[2], sP=new double[2], sG=new double[2];
        int[] n=new int[2], posQ=new int[2], posP=new int[2];
        say("");
        say("  box    | lon lat |  P(mm/d) |  Qdiv(W/m2) | qLat  | qSens |  qRad  | GMS=Qdiv/(L*P)");
        for(int b=0;b<2;b++){
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                double pm=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
                double[] h=new double[6]; StationaryWave.DIAG_HEAT=new ThreadLocal<double[]>(){protected double[] initialValue(){return h;}};
                double qd=StationaryWave.netColumnHeating(x,z,sd,cell,th,GRAD,pm);
                StationaryWave.DIAG_HEAT=null;
                double lp=Radiation.LV*pm/86400.0;
                double gms=Math.abs(lp)>1e-9?qd/lp:Double.NaN;
                say(String.format(LF,"  %-6s | %3.0f %3.0f | %8.3f | %+11.3f | %+6.2f| %+6.2f| %+6.2f | %s",
                    b==0?"ASIA":"SAHARA",lon,latd,pm,qd,h[0],h[1],h[2],
                    Double.isNaN(gms)?"  n/a":String.format(LF,"%+8.3f",gms)));
                n[b]++; sQ[b]+=qd; sP[b]+=pm;
                if(qd>0) posQ[b]++;
                if(pm>0.001) posP[b]++;
                if(!Double.isNaN(gms)) sG[b]+=gms;
            }
        }
        say("");
        say(String.format(LF,"  %-8s | %4s | %9s | %11s | %10s | %s","box","n","<P> mm/d","<Qdiv> W/m2","<GMS>","Qdiv>0 比例"));
        for(int b=0;b<2;b++)
            say(String.format(LF,"  %-8s | %4d | %9.3f | %+11.3f | %+10.3f | %d/%d",
                b==0?"ASIA":"SAHARA",n[b],sP[b]/n[b],sQ[b]/n[b],sG[b]/n[b],posQ[b],n[b]));
        say("");
        double cQ=(sQ[0]/n[0]-sQ[1]/n[1])/(Math.abs(sQ[0]/n[0])+Math.abs(sQ[1]/n[1]));
        double cP=(sP[0]/n[0]-sP[1]/n[1])/(Math.abs(sP[0]/n[0])+Math.abs(sP[1]/n[1]));
        say(String.format(LF,"  contrast Qdiv = %+.4f",cQ));
        say(String.format(LF,"  contrast P    = %+.4f",cP));
        say("");
        boolean a1=sQ[0]>0, a2=sQ[1]<0;
        say("  文献判据判定：");
        say(String.format(LF,"    亚洲 Qdiv>0 ? %s   (实测 %+.3f)",a1?"是":"【否】",sQ[0]/n[0]));
        say(String.format(LF,"    撒哈拉 Qdiv<0 ? %s   (实测 %+.3f)",a2?"是":"【否】",sQ[1]/n[1]));
        say(String.format(LF,"  ⇒ %s",(a1&&a2)?"【两盒符号已反号 ⇒ 丙′ 有据可续】":"【两盒未反号 ⇒ 当前架构下缺区域 MSE 平流对比 ⇒ 乙 有据】"));
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
