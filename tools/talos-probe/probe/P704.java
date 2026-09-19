package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P704 -- §505 标定后的 (c) dOLR/dTs + (e) 稳定度 + P701 收支门复验
public class P704 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P704] "+s); System.out.println("[P704] "+s); }
    // §511 ★ 日照量改用【模型自己的 absSolar】量级（§454 实测 372~404 W/m²）。
    //   原来 MU=0.6 ⇒ S0*MU = 816 W/m²，是真实全球平均地表短波(~240)的 3.4 倍。
    //   那个不相容【一直存在】，但 Ts 是给定输入 ⇒ 表面收支从未闭合 ⇒ 掩盖了它。
    //   表面能量平衡解出来之后它立刻暴露：解出 Ts ≈ 375 K、CWV 爆炸。
    static final double ABS_SOLAR = 372.0;                 // 模型自己的量级
    static final double MU = ABS_SOLAR / 1361.0;           // = 0.2733
    static final double TTOP=200.0, DT=120.0;
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p704_report.txt"),"UTF-8");
        VerticalColumn.K_M = 1.4453e-4;
        say("P704: §505 标定后复测  K_W=OLR_K="+String.format(LF,"%.4f",StationaryWave.OLR_K)
            +"  K_M="+String.format(LF,"%.4e",VerticalColumn.K_M));
        double[] ts={288.0,300.0,312.0};
        double[] olr=new double[3], cwv=new double[3], tgt=new double[3];
        double[] gmax=new double[3]; double worst=0.0; double sfcResid=0.0;
        double[] tsfc=new double[3];   // §511：Ts 现在是【诊断量】，横轴必须用它
        say("");
        say("  Ts(K) |   CWV   |  tau  | SCM OLR | olrClear |  diff  | Gmax K/km | Gmax@z(m) | stable?");
        for(int i=0;i<3;i++){
            double q=0.98*ParcelLift.qs(ts[i],PrecipField.P_SURF);
            VerticalColumn c=new VerticalColumn(); c.init(ts[i],q,1.0,6.0);
            for(int n=1;n<=40000;n++){
                c.step(DT,MU,TTOP);
                double r=Math.abs(c.budgetResidual());
                double sc=Math.abs(c.ra)+Math.abs(c.sh)+Math.abs(c.lh)+Radiation.LV*(c.precipConv+c.precipLs)/c.lastDt+1.0;   // §531: precipConv/precipLs 是【每步质量】，必须除 lastDt 才能与 W/m2 的 ra/sh/lh 同量纲
                if(r/sc>worst && n>5) worst=r/sc;
                if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break;
            }
            olr[i]=c.olr; cwv[i]=c.cwv(); tgt[i]=StationaryWave.olrClear(ts[i],cwv[i]);
            if(Math.abs(c.lastSfcResid)>Math.abs(sfcResid)) sfcResid=c.lastSfcResid;
            tsfc[i]=c.tSfc;
            double gm=-1e9; int kg=0;
            for(int k=0;k+1<VerticalColumn.N;k++){
                double dz=c.z[k]-c.z[k+1]; if(dz<=0) continue;
                double g=-(c.t[k]-c.t[k+1])/dz; if(g>gm){gm=g;kg=k;}
            }
            gmax[i]=gm*1000.0;
            say(String.format(LF,"  %6.0f | %7.2f | %5.3f | %7.2f | %8.2f | %+6.2f | %9.2f | %9.0f | %s",
                ts[i],cwv[i],c.lastTauTot,olr[i],tgt[i],olr[i]-tgt[i],gmax[i],c.z[kg],
                gmax[i]<ParcelLift.GAMMA_D*1000.0?"yes":"NO"));
        }
        int n=3; double sx=0,sy=0,sxy=0,sxx=0;
        for(int i=0;i<n;i++){ sx+=tsfc[i]; sy+=olr[i]; sxy+=tsfc[i]*olr[i]; sxx+=tsfc[i]*tsfc[i]; }
        double slope=(n*sxy-sx*sy)/(n*sxx-sx*sx);
        int flips=0; for(int i=2;i<n;i++) if((olr[i]-olr[i-1])*(olr[i-1]-olr[i-2])<0) flips++;
        say("");
        say(String.format(LF,"  DIAGNOSED Ts = %.2f %.2f %.2f K  (initial was %.0f %.0f %.0f)",tsfc[0],tsfc[1],tsfc[2],ts[0],ts[1],ts[2]));
        say(String.format(LF,"  (c) dOLR/dTs = %.3f W/m2/K   band 2~4 => %s",slope,(slope>2.0&&slope<4.0)?"IN BAND":"OUT OF BAND"));
        say(String.format(LF,"      OLR = %.2f %.2f %.2f   flips=%d %s",olr[0],olr[1],olr[2],flips,flips==0?"(monotone)":"(NON-monotone)"));
        say(String.format(LF,"  (e) Gmax = %.2f %.2f %.2f  vs Gamma_d = %.3f => %s",gmax[0],gmax[1],gmax[2],
            ParcelLift.GAMMA_D*1000.0,(gmax[0]<ParcelLift.GAMMA_D*1000.0&&gmax[1]<ParcelLift.GAMMA_D*1000.0&&gmax[2]<ParcelLift.GAMMA_D*1000.0)?"ALL PASS":"STILL FAILS"));
        say(String.format(LF,"  budget gate (P701) worst relative residual = %.3e => %s",worst,worst<1e-6?"STILL PASSES":"BROKEN"));
        say(String.format(LF,"  (d') INDEPENDENT: surface energy balance residual = %.3e W/m2 (binary-search closure)",sfcResid));
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
}
