package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P705 -- §507 扫描 K_M：它能否修掉 (e)？（同时 (c) 须仍在带内、收支门须仍 <1e-6）
public class P705 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P705] "+s); System.out.println("[P705] "+s); }
    static final double TTOP=200.0, MU=0.6, DT=120.0;
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p705_report.txt"),"UTF-8");
        say("P705: §507 K_M 扫描（Ts=300 固定）—— 检验「加辐射能否压住 2 小时的对流调整」");
        double[] km={1.4453e-4, 2.0e-4, 3.0e-4, 4.5e-4, 7.0e-4};
        say("");
        say("  K_M      | tau_tot |  OLR   | Gmax K/km | Gmax@z(m) | 稳定? | 收支残差 | 冷点T(K)@z(m)");
        for(int i=0;i<km.length;i++){
            VerticalColumn.K_M=km[i];
            double q=0.98*ParcelLift.qs(300.0,PrecipField.P_SURF);
            VerticalColumn c=new VerticalColumn(); c.init(300.0,q,1.0,6.0);
            double worst=0.0;
            for(int n=1;n<=40000;n++){
                c.step(DT,MU,TTOP);
                double r=Math.abs(c.budgetResidual());
                double sc=Math.abs(c.ra)+Math.abs(c.sh)+Math.abs(c.lh)+Radiation.LV*(c.precipConv+c.precipLs)/c.lastDt+1.0;   // §531: precipConv/precipLs 是【每步质量】，必须除 lastDt 才能与 W/m2 的 ra/sh/lh 同量纲
                if(n>5&&r/sc>worst) worst=r/sc;
                if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break;
            }
            double gm=-1e9; int kg=0;
            for(int k=0;k+1<VerticalColumn.N;k++){
                double dz=c.z[k]-c.z[k+1]; if(dz<=0) continue;
                double g=-(c.t[k]-c.t[k+1])/dz; if(g>gm){gm=g;kg=k;}
            }
            int kmin=0; for(int k=1;k<VerticalColumn.N;k++) if(c.t[k]<c.t[kmin]) kmin=k;
            say(String.format(LF,"  %.4e | %7.3f | %6.2f | %9.2f | %9.0f | %-5s | %.2e | %.1f @ %.0f",
                km[i],c.lastTauTot,c.olr,gm*1000.0,c.z[kg],
                (gm*1000.0)<ParcelLift.GAMMA_D*1000.0?"yes":"NO",worst,c.t[kmin],c.z[kmin]));
        }
        say("");
        say("=== 参考：K_M 只改辐射，改不了 tRef。逐层 Γ 剖面（K_M=7.0e-4）===");
        VerticalColumn.K_M=7.0e-4;
        double q=0.98*ParcelLift.qs(300.0,PrecipField.P_SURF);
        VerticalColumn c=new VerticalColumn(); c.init(300.0,q,1.0,6.0);
        for(int n=1;n<=40000;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break; }
        say("      z(m) |   p(hPa) |   T(K)  |  Gamma(K/km) |  Gamma_d=9.77");
        for(int k=VerticalColumn.N-1;k>=0;k-=4){
            if(k+1>=VerticalColumn.N) continue;
            double dz=c.z[k]-c.z[k+1];
            double g=dz>0?-(c.t[k]-c.t[k+1])/dz*1000.0:0.0;
            say(String.format(LF,"  %8.0f | %8.1f | %7.2f | %12.2f |",c.z[k],VerticalColumn.PC[k]/100.0,c.t[k],g));
        }
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
}
