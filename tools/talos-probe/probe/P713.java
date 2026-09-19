package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;
// P713 -- §521 丙′ 前置检查：qRad = absSolar - olrClear(ts,cwv) 的两个杠杆各需要多大才翻负？
public class P713 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P713] "+s); System.out.println("[P713] "+s); rep.flush(); }
    static final int SEED=1022228679, GRAD=500_000;
    static final double CIRC=40_000_000.0;
    static int xOfLon(double lon){ return (int)Math.round(lon/360.0*CIRC); }
    static int zOfLat(double lat){ return WorldContract.zOfLat(lat); }
    static final double[][] BOX={{70,120,15,35},{0,30,20,35}};
    static final String[] BN={"ASIA  (应 qRad<0? 不，季风应>0)","SAHARA(应 qRad<0)"};
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p713_report.txt"),"UTF-8");
        say("P713: §521 qRad 分解 + 翻负阈值（忠实复刻 StationaryWave.netColumnHeating 254-314 行）");
        EarthRef.install(); com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED); SimClimate.clearCache();
        long sd=SimTerrain.seedOf(SEED); int cell=PlateField.PLATE_CELL; double th=Atmosphere.theta(0.0);
        // ★ §535（P1-5）：§476 要求【任何基于盒子的判据都必须先声明该盒在本世界的海陆构成】。
        //   本探针此前完全未声明 ⇒ 读数可解释性被限定（不知道「亚洲盒」有多少是海）。
        Boxes.declare(rep, "P713 ASIA 70-120E/15-35N", sd, cell, 70,120,15,35, 10.0, 5.0);
        Boxes.declare(rep, "P713 SAHARA 0-30E/20-35N",  sd, cell,  0, 30,20,35, 10.0, 5.0);
        say("");
        say("  box    | lon lat |  ta    |   qa     |  cwv  | alb   | absSolar |  ts    | olrClear |  qRad");
        double[] s=new double[2*8]; int[] n=new int[2];
        double[] cwvThr=new double[2], albThr=new double[2];
        for(int b=0;b<2;b++)
        for(double latd=BOX[b][2]+2.5; latd<=BOX[b][3]; latd+=5.0)
        for(double lon=BOX[b][0]+5.0; lon<=BOX[b][1]; lon+=10.0){
            int x=xOfLon(lon), z=zOfLat(latd);
            double pm=PrecipField.mmPerDay(x,z,sd,cell,th,GRAD);
            double lat=WorldContract.latOf(z);
            double k=Atmosphere.kappaMemo(x,z,sd,cell);
            double ta=Atmosphere.surfaceTemp(x,z,sd,cell,th);
            double[] u=Atmosphere.windAt(x,z,sd,cell,th,GRAD);
            double chv=Radiation.bulkCoeff(k,Math.hypot(u[0],u[1]));
            double dec=Atmosphere.subsolarLat(th);
            boolean isLand=k>0.5;
            final double pv=pm;
            double veg=isLand?Vegetation.vegAt(x,z,sd,cell,vv->pv):1.0;
            double alb=Radiation.albedo(isLand,ta)+(isLand?Vegetation.albedoAdd(veg):0.0);
            if(alb>0.95) alb=0.95;
            double ins=Radiation.insolation(lat,dec);
            double absS=ins*(1.0-alb);
            double qa=PrecipField.Q_FROM_SOURCE
                ?PrecipField.moistureFromSource(x,z,sd,cell,th,GRAD,ta,0.0,k,u[0],u[1])
                :PrecipField.moisture(ta,0.0,k);
            double ts=Radiation.skinTempLand(absS,ta,qa,chv,1.0);
            double cwv=qa*Atmosphere.RHO_AIR*PrecipField.H_MOIST;
            double olr=StationaryWave.olrClear(ts,cwv);
            double qrad=absS-olr;
            say(String.format(LF,"  %-6s | %3.0f %3.0f | %6.2f | %8.5f | %5.1f | %5.3f | %8.2f | %6.2f | %8.2f | %+7.2f",
                b==0?"ASIA":"SAHARA",lon,latd,ta,qa,cwv,alb,absS,ts,olr,qrad));
            n[b]++; s[b*8+0]+=ta; s[b*8+1]+=qa; s[b*8+2]+=cwv; s[b*8+3]+=alb;
            s[b*8+4]+=absS; s[b*8+5]+=ts; s[b*8+6]+=olr; s[b*8+7]+=qrad;
            // 阈值：固定 alb 解 cwv 使 qRad=0 ；固定 cwv 解 alb 使 qRad=0
            double lo=0.0,hi=200.0;
            for(int it=0;it<60;it++){ double m=0.5*(lo+hi);
                if(absS-StationaryWave.olrClear(ts,m)>0) lo=m; else hi=m; }
            cwvThr[b]+=0.5*(lo+hi);
            double l2=alb,h2=0.95;
            if(absS-StationaryWave.olrClear(ts,cwv)>0){
                for(int it=0;it<60;it++){ double m=0.5*(l2+h2);
                    if(ins*(1.0-m)-StationaryWave.olrClear(ts,cwv)>0) l2=m; else h2=m; }
                albThr[b]+=0.5*(l2+h2);
            } else albThr[b]+=alb;
        }
        say("");
        say(String.format(LF,"  %-8s | %4s | %7s | %8s | %6s | %6s | %8s | %6s | %8s | %7s",
            "box","n","<ta>","<qa>","<cwv>","<alb>","<absSol>","<ts>","<olrClr>","<qRad>"));
        for(int b=0;b<2;b++)
            say(String.format(LF,"  %-8s | %4d | %7.2f | %8.5f | %6.1f | %6.3f | %8.2f | %6.2f | %8.2f | %+7.2f",
                b==0?"ASIA":"SAHARA",n[b],s[b*8+0]/n[b],s[b*8+1]/n[b],s[b*8+2]/n[b],s[b*8+3]/n[b],
                s[b*8+4]/n[b],s[b*8+5]/n[b],s[b*8+6]/n[b],s[b*8+7]/n[b]));
        say("");
        say("  翻负阈值（各点平均）：");
        say(String.format(LF,"    ASIA  : 需 cwv < %.1f kg/m2（现 %.1f）   或 alb > %.3f（现 %.3f）",
            cwvThr[0]/n[0],s[2]/n[0],albThr[0]/n[0],s[3]/n[0]));
        say(String.format(LF,"    SAHARA: 需 cwv < %.1f kg/m2（现 %.1f）   或 alb > %.3f（现 %.3f）",
            cwvThr[1]/n[1],s[10]/n[1],albThr[1]/n[1],s[11]/n[1]));
        say("");
        say("  判读：阈值必须落在【物理可达范围】内，那条杠杆才有效。");
        say("        真实沙漠：cwv ~10-20 kg/m2，反照率 ~0.35-0.40");
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
