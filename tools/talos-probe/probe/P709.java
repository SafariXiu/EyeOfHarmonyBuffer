package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;
// P709 -- §513 反问题：beta 能不能让两盒分开？（并检验：柱内 q 是不是被 qRef 规定死的）
public class P709 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P709] "+s); System.out.println("[P709] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double TTOP=200.0, DT=120.0, MU=372.0/1361.0;
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p709_report.txt"),"UTF-8");
        say("P709: §513 反问题 —— beta 扫描（看柱内 q 是否被 qRef 规定死）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL;
        double th=Atmosphere.theta(0.0);
        double[][] pts={{95,23,0},{15,28,1}};   // 亚洲点 / 撒哈拉点
        String[] nm={"ASIA 95E/23N","SAHARA 15E/28N"};
        double[] betas={1.00,0.70,0.40,0.20,0.05};
        for(int ip=0;ip<2;ip++){
            int x=xOfLon(pts[ip][0]), z=zOfLat(pts[ip][1]);
            PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            double[] d=PrecipField.DIAG.get();
            double tS=Atmosphere.surfaceTemp(x,z,sd,cell,th);
            double q0=d[5];
            say("");
            say(String.format(LF,"=== %s   模型给的 tS=%.2f K  q=%.5f  beta_模型=%.3f ===",nm[ip],tS,q0,d[13]));
            say("   beta  |  diag Ts | q_bot    | qsS      | qsS*beta | LH      | M(J/kg)  | P(mm/d)");
            for(int ib=0;ib<betas.length;ib++){
                VerticalColumn c=new VerticalColumn(); c.init(tS,q0,betas[ib],6.0);
                for(int n=1;n<=18000;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<3.0) break; }
                int kb=VerticalColumn.N-1;
                double qref=c.qRef(kb);
                double qb=c.q[VerticalColumn.N-1];
                ParcelLift.Result pr=ParcelLift.lift(c.tSfc,qb);
                double M=Double.NaN, zl=Double.NaN;
                if(!Double.isNaN(pr.pLnb)){
                    int k=0; double bd=1e18;
                    for(int j=0;j<VerticalColumn.N;j++){ double dd=Math.abs(VerticalColumn.PC[j]-pr.pLnb); if(dd<bd){bd=dd;k=j;} }
                    // §514 修正：补上 g*z 位势项（原式漏了）
                    M=(Radiation.CP*c.t[kb]+PrecipField.G_ACC*c.z[kb]+Radiation.LV*c.q[kb])
                     -(Radiation.CP*c.t[k]+PrecipField.G_ACC*c.z[k]+Radiation.LV*c.q[k]);
                    zl=c.z[k];
                }
                double pp=(c.precipConv+c.precipLs)/c.lastDt*86400.0*1000.0;
                double qsS=ParcelLift.qs(c.tSfc,c.pSfc);
                double lh=c.chv*Radiation.LV*c.windSpeed*(qsS*betas[ib]-qb);
                say(String.format(LF,"  %6.2f | %8.2f | %8.5f | %8.5f | %8.5f | %+7.2f | %+9.1f | %8.3f",
                    betas[ib],c.tSfc,qb,qsS,qsS*betas[ib],lh,M,pp));
                say(String.format(LF,"         ^ qsS*beta %s q_bot => LH %s => beta %s",
                    qsS*betas[ib]<qb?"< 【小于】":" 大于", lh<=0.0?"【被夹到 0，beta 失效】":"有效",
                    lh<=0.0?"【不影响结果】":"有效"));
            }
        }
        say("");
        say("判读：若 q_bot 与 qRef_bot 【几乎相等】且不随 beta 变 ⇒ 柱内湿度是被 qRef 规定死的");
        say("      ⇒ 单柱【造不出沙漠】(真实沙漠的干来自大尺度下沉，不是地表)");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
