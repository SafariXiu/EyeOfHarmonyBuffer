package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;
// P710 -- §515 M 的四种候选口径一起量：让数据决定用哪一个（Wiley/AMS/MIT 三个源全 403/405）
public class P710 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P710] "+s); System.out.println("[P710] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0, RD=287.05;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};
    static final String[] BN={"ASIA","SAHARA"};
    static final double TTOP=200.0, DT=120.0, MU=372.0/1361.0;
    // 湿静力能 h = cp*T + g*z + L*q
    static double h(VerticalColumn c,int k){ return Radiation.CP*c.t[k]+PrecipField.G_ACC*c.z[k]+Radiation.LV*c.q[k]; }
    // 饱和湿静力能
    static double hs(VerticalColumn c,int k){ return Radiation.CP*c.t[k]+PrecipField.G_ACC*c.z[k]
        +Radiation.LV*ParcelLift.qs(c.t[k],VerticalColumn.PC[k]); }
    // 湿熵 s = cp*lnT - Rd*lnp + L*q/T
    static double s(VerticalColumn c,int k,boolean sat){
        double q=sat?ParcelLift.qs(c.t[k],VerticalColumn.PC[k]):c.q[k];
        return Radiation.CP*Math.log(c.t[k])-RD*Math.log(VerticalColumn.PC[k])+Radiation.LV*q/c.t[k]; }
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p710_report.txt"),"UTF-8");
        say("P710: §515 M 四种口径一起量（FIXED_TS=true，地面状态取自模型自己）");
        VerticalColumn.FIXED_TS=true;
        EarthRef.install(); com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   ⚠ 本探针自身的循环起点仍是旧式 lo+half（见下方 for 行）—— 属 P1-6 待统一项；
        //     此处声明的陆占比用 Boxes 的【格心口径】，步长与本探针一致（dLon=10 dLat=5）。
        Boxes.declare(rep, "P710 ASIA   70-120E/15-35N", sd, cell, 70,120,15,35, 10, 5);
        Boxes.declare(rep, "P710 SAHARA 0-30E /20-35N",  sd, cell,  0, 30,20,35, 10, 5);
        double[][] acc=new double[2][4]; int[] n=new int[2];
        double[] sum = new double[4];
        for(int b=0;b<2;b++){
            for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
            for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
                int x=xOfLon(lon), z=zOfLat(latd);
                PrecipField.mmPerDay(x,z,sd,cell,th,GRAD); double[] d=PrecipField.DIAG.get();
                double tS=Atmosphere.surfaceTemp(x,z,sd,cell,th); double q0=d[5];
                ParcelLift.Result pr=ParcelLift.lift(tS,q0);
                if(Double.isNaN(pr.pLnb)) continue;
                VerticalColumn c=new VerticalColumn(); c.init(tS,q0,d[13],6.0);
                for(int m=1;m<=16000;m++){ c.step(DT,MU,TTOP); if(m>200&&Math.abs(c.dHdtMeasured)<3.0) break; }
                int kb=VerticalColumn.N-1;
                int kL=0; double bd=1e18;
                for(int j=0;j<VerticalColumn.N;j++){ double dd=Math.abs(VerticalColumn.PC[j]-pr.pLnb); if(dd<bd){bd=dd;kL=j;} }
                int kT=0; bd=1e18;   // throttling layer: z ~ H_BL = 1000 m
                for(int j=0;j<VerticalColumn.N;j++){ double dd=Math.abs(c.z[j]-1000.0); if(dd<bd){bd=dd;kT=j;} }
                double M1=h(c,kb)-h(c,kL);
                double M2=h(c,kb)-hs(c,kL);
                double M3=s(c,kb,false)-s(c,kT,true);
                double M4=h(c,kb)-h(c,kT);
                acc[b][0]+=M1; acc[b][1]+=M2; acc[b][2]+=M3; acc[b][3]+=M4; n[b]++;
            }
        }
        say("");
        String[] nm={"M1 h_BL-h_env(LNB)","M2 h_BL-h*_env(LNB)","M3 s_bl-s*_th(1km)","M4 h_BL-h_env(1km)"};
        say(String.format(LF,"  %-22s | %14s | %14s | %10s | %s","口径","ASIA","SAHARA","contrast","方向"));
        for(int j=0;j<4;j++){
            double A=acc[0][j]/n[0], B=acc[1][j]/n[1];
            double cc=(A-B)/(Math.abs(A)+Math.abs(B));
            say(String.format(LF,"  %-22s | %+14.4e | %+14.4e | %+10.4f | %s",nm[j],A,B,cc,cc>0?"对":"【反】"));
        }
        say("");
        say(String.format(LF,"  样本 n = %d / %d",n[0],n[1]));
        say("  判据：方向须为【对】(亚洲更不稳)，且量级须与文献的 GMS 量级相符");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
