package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P706 -- §509 在【等温帽已生效】的柱子上重新把 K_M 钉到 olrClear，再重测 (c)/(e)
public class P706 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P706] "+s); System.out.println("[P706] "+s); rep.flush(); }
    static final double TTOP=200.0, MU=0.6, DT=120.0;
    static VerticalColumn run(double ts,double q,double tol,int maxn){
        VerticalColumn c=new VerticalColumn(); c.init(ts,q,1.0,6.0);
        for(int n=1;n<=maxn;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<tol) break; }
        return c;
    }
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p706_report.txt"),"UTF-8");
        say("P706: §509 K_M 重标定（等温帽已生效）  K_W=OLR_K="+String.format(LF,"%.4f",StationaryWave.OLR_K));
        double tsc=300.0, qc=0.98*ParcelLift.qs(tsc,PrecipField.P_SURF);
        double lo=0.0, hi=6.0e-4, best=0.0;
        say("");
        say("=== A 二分 K_M（tol=2.0 求快）===");
        for(int i=0;i<8;i++){
            double m=0.5*(lo+hi); VerticalColumn.K_M=m;
            VerticalColumn c=run(tsc,qc,2.0,30000);
            double tgt=StationaryWave.olrClear(tsc,c.cwv()); best=m;
            say(String.format(LF,"  it%d K_M=%.4e  SCM OLR=%.2f  olrClear=%.2f  diff=%+.2f  tau=%.3f",i,m,c.olr,tgt,c.olr-tgt,c.lastTauTot));
            if(c.olr>tgt) lo=m; else hi=m;
        }
        VerticalColumn.K_M=best;
        say(String.format(LF,"  => K_M = %.4e  (旧值 1.4453e-04)",best));
        say("");
        say("=== B 用新 K_M 重测 (c)/(e)/收支门 ===");
        double[] ts={288.0,300.0,312.0};
        double[] olr=new double[3], gmax=new double[3]; double worst=0.0;
        say("   Ts |   CWV   |  tau   | SCM OLR | olrClear |  diff  | Gmax  | 稳定? |");
        for(int i=0;i<3;i++){
            double q=0.98*ParcelLift.qs(ts[i],PrecipField.P_SURF);
            VerticalColumn c=new VerticalColumn(); c.init(ts[i],q,1.0,6.0);
            for(int n=1;n<=40000;n++){
                c.step(DT,MU,TTOP);
                double r=Math.abs(c.budgetResidual());
                double sc=Math.abs(c.ra)+Math.abs(c.sh)+Math.abs(c.lh)+Radiation.LV*(c.precipConv+c.precipLs)/c.lastDt+1.0;   // §531: precipConv/precipLs 是【每步质量】，必须除 lastDt 才能与 W/m2 的 ra/sh/lh 同量纲
                if(n>5&&r/sc>worst) worst=r/sc;
                if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break;
            }
            olr[i]=c.olr;
            double gm=-1e9; for(int k=0;k+1<VerticalColumn.N;k++){ double dz=c.z[k]-c.z[k+1]; if(dz<=0) continue;
                double g=-(c.t[k]-c.t[k+1])/dz; if(g>gm) gm=g; }
            gmax[i]=gm*1000.0;
            say(String.format(LF,"  %4.0f | %7.2f | %6.3f | %7.2f | %8.2f | %+6.2f | %5.2f | %s",
                ts[i],c.cwv(),c.lastTauTot,olr[i],StationaryWave.olrClear(ts[i],c.cwv()),olr[i]-StationaryWave.olrClear(ts[i],c.cwv()),
                gmax[i],gmax[i]<ParcelLift.GAMMA_D*1000.0?"yes":"NO"));
        }
        int n=3; double sx=0,sy=0,sxy=0,sxx=0;
        for(int i=0;i<n;i++){ sx+=ts[i]; sy+=olr[i]; sxy+=ts[i]*olr[i]; sxx+=ts[i]*ts[i]; }
        double slope=(n*sxy-sx*sy)/(n*sxx-sx*sx);
        int flips=0; for(int i=2;i<n;i++) if((olr[i]-olr[i-1])*(olr[i-1]-olr[i-2])<0) flips++;
        say("");
        say(String.format(LF,"  (c) dOLR/dTs = %.3f   band 2~4 => %s",slope,(slope>2.0&&slope<4.0)?"IN BAND":"OUT OF BAND"));
        say(String.format(LF,"      OLR = %.2f %.2f %.2f  flips=%d %s",olr[0],olr[1],olr[2],flips,flips==0?"(monotone)":"(NON-monotone)"));
        say(String.format(LF,"  (e) Gmax = %.2f %.2f %.2f vs %.3f => %s",gmax[0],gmax[1],gmax[2],ParcelLift.GAMMA_D*1000.0,
            (gmax[0]<ParcelLift.GAMMA_D*1000.0&&gmax[1]<ParcelLift.GAMMA_D*1000.0&&gmax[2]<ParcelLift.GAMMA_D*1000.0)?"ALL PASS":"STILL FAILS"));
        say(String.format(LF,"  budget gate = %.3e => %s",worst,worst<1e-6?"PASSES":"BROKEN"));
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
