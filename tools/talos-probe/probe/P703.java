package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P703 -- §504 标定 K_M：让本柱的发射层与模型【自己】的 olrClear 一致（零地球数据）
public class P703 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P703] "+s); System.out.println("[P703] "+s); }
    static final double TTOP=200.0, MU=0.6, DT=120.0;
    static VerticalColumn run(double tSfcK,double qSfc){
        VerticalColumn c=new VerticalColumn(); c.init(tSfcK,qSfc,1.0,6.0);
        for(int n=1;n<=45000;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break; }
        return c;
    }
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p703_report.txt"),"UTF-8");
        say("P703: §504 K_M 标定 —— 目标：本柱 OLR 匹配模型自己的 olrClear，且【零地球数据】");
        say(String.format(LF,"  K_W = OLR_K = %.4f m2/kg  （复用 StationaryWave.OLR_K）",StationaryWave.OLR_K));
        say("");
        double ts0=300.0, q0=0.98*ParcelLift.qs(ts0,PrecipField.P_SURF);
        double lo=0.0, hi=1.0e-3;
        say("=== A 二分 K_M ===");
        say(String.format(LF,"  %-6s | %10s | %10s | %10s | %10s","iter","K_M","SCM OLR","olrClear","tau_tot"));
        double best=0;
        for(int i=0;i<8;i++){
            double m=0.5*(lo+hi); VerticalColumn.K_M=m;
            VerticalColumn c=run(ts0,q0);
            double tgt=StationaryWave.olrClear(ts0,c.cwv());
            best=m;
            if(i<4||i>9) say(String.format(LF,"  %-6d | %10.3e | %10.3f | %10.3f | %10.3f  diff=%+.2f",i,m,c.olr,tgt,c.lastTauTot,c.olr-tgt));
            if(c.olr>tgt) lo=m; else hi=m;      // K_M 越大 ⇒ OLR 越小
        }
        VerticalColumn.K_M=best;
        say(String.format(LF,"  ⇒ 标定结果 K_M = %.4e m2/kg",best));
        double mTot=(PrecipField.P_SURF-VerticalColumn.P_TOP)/PrecipField.G_ACC;
        say(String.format(LF,"     等价的「充分混合气体总光程」= K_M*MASS_total = %.4f （对比水汽项 OLR_K*CWV ≈ %.2f）",
            best*mTot, StationaryWave.OLR_K*60.0));
        say("");
        say("=== B 标定后重测 (c) ∂OLR/∂Ts ===");
        double[] ts={288.0,300.0,312.0};
        double[] olr=new double[ts.length], tgt=new double[ts.length], cwv=new double[ts.length];
        for(int i=0;i<ts.length;i++){
            double q=0.98*ParcelLift.qs(ts[i],PrecipField.P_SURF);
            VerticalColumn c=run(ts[i],q);
            olr[i]=c.olr; cwv[i]=c.cwv(); tgt[i]=StationaryWave.olrClear(ts[i],cwv[i]);
            say(String.format(LF,"  Ts=%.0f  CWV=%7.2f kg/m2  tau=%7.3f  SCM OLR=%7.2f  olrClear=%7.2f  diff=%+7.2f",
                ts[i],cwv[i],c.lastTauTot,olr[i],tgt[i],olr[i]-tgt[i]));
        }
        int n=ts.length; double sx=0,sy=0,sxy=0,sxx=0;
        for(int i=0;i<n;i++){ sx+=ts[i]; sy+=olr[i]; sxy+=ts[i]*olr[i]; sxx+=ts[i]*ts[i]; }
        double slope=(n*sxy-sx*sy)/(n*sxx-sx*sx);
        int flips=0; for(int i=2;i<n;i++) if((olr[i]-olr[i-1])*(olr[i-1]-olr[i-2])<0) flips++;
        say(String.format(LF,"  ∂OLR/∂Ts = %.3f W/m2/K   判据带 2~4 ⇒ %s",slope,
            (slope>2.0&&slope<4.0)?"【在带内】":"【在带外】"));
        say(String.format(LF,"  OLR 序列 = %.2f %.2f %.2f %.2f %.2f  变号 %d 次 %s",olr[0],olr[1],olr[2],olr[3],olr[4],flips,
            flips==0?"(单调)":"【非单调】"));
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
}
