package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P707 -- §510 (e) 剩余 0.3%：扫描 W_STAB，并直接测 tRef 自己的递减率
public class P707 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P707] "+s); System.out.println("[P707] "+s); rep.flush(); }
    static final double TTOP=200.0, MU=0.6, DT=120.0;
    public static void main(String[] a) throws Exception {
        rep=new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p707_report.txt"),"UTF-8");
        say("P707: §510 扫描 W_STAB @ Ts=300   Gamma_d = "+String.format(LF,"%.3f",ParcelLift.GAMMA_D*1000.0)+" K/km");
        say("");
        say("  W_STAB | Gmax K/km | Gmax@z(m) | 稳定? | OLR    | 冷点T(K)@z(m)");
        double[] ws={0.85,0.90,0.95,1.00};
        for(int i=0;i<ws.length;i++){
            VerticalColumn.W_STAB=ws[i];
            double q=0.98*ParcelLift.qs(300.0,PrecipField.P_SURF);
            VerticalColumn c=new VerticalColumn(); c.init(300.0,q,1.0,6.0);
            for(int n=1;n<=40000;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break; }
            double gm=-1e9; int kg=0;
            for(int k=0;k+1<VerticalColumn.N;k++){ double dz=c.z[k]-c.z[k+1]; if(dz<=0) continue;
                double g=-(c.t[k]-c.t[k+1])/dz; if(g>gm){gm=g;kg=k;} }
            int kmin=0; for(int k=1;k<VerticalColumn.N;k++) if(c.t[k]<c.t[kmin]) kmin=k;
            say(String.format(LF,"  %6.2f | %9.2f | %9.0f | %-5s | %6.2f | %.1f @ %.0f",
                ws[i],gm*1000.0,c.z[kg],(gm*1000.0)<ParcelLift.GAMMA_D*1000.0?"yes":"NO",c.olr,c.t[kmin],c.z[kmin]));
        }
        say("");
        say("=== 直接测 tRef 自己的递减率（W_STAB=0.90，Ts=300 的收敛柱）===");
        VerticalColumn.W_STAB=0.90;
        double q=0.98*ParcelLift.qs(300.0,PrecipField.P_SURF);
        VerticalColumn c=new VerticalColumn(); c.init(300.0,q,1.0,6.0);
        for(int n=1;n<=40000;n++){ c.step(DT,MU,TTOP); if(n>200&&Math.abs(c.dHdtMeasured)<1.0) break; }
        double[] tRef=c.bettsMillerTRef(TTOP);
        say("      z(m) |  p(hPa) |  T_env  | G_env |  T_ref  | G_ref | G_ref>Gd ?");
        for(int k=VerticalColumn.N-1;k>=0;k--){
            if(k<VerticalColumn.N-16) break;
            if(k+1>=VerticalColumn.N) continue;
            double dz=c.z[k]-c.z[k+1]; if(dz<=0) continue;
            double ge=-(c.t[k]-c.t[k+1])/dz*1000.0;
            double gr=-(tRef[k]-tRef[k+1])/dz*1000.0;
            say(String.format(LF,"  %8.0f | %7.1f | %7.2f | %5.2f | %7.2f | %5.2f | %s",
                c.z[k],VerticalColumn.PC[k]/100.0,c.t[k],ge,tRef[k],gr,
                gr>ParcelLift.GAMMA_D*1000.0?"【是 - 超绝热!】":"no"));
        }
        say("DONE");
        System.out.println("JAVA_EXIT=0");
    }
}
