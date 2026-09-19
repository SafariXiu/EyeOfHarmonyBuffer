package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;
// P702 -- §502 完整垂直维度：5 条【物理】判据（命门已在 P701 通过）
public class P702 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s){ rep.println("[P702] "+s); System.out.println("[P702] "+s); }
    static final double TTOP = 200.0, MU = 0.6, DT = 120.0;

    static VerticalColumn run(double tSfcK, double qSfc){
        VerticalColumn c = new VerticalColumn();
        c.init(tSfcK, qSfc, 1.0, 6.0);
        for(int n=1;n<=90000;n++){
            c.step(DT, MU, TTOP);
            if(n>200 && Math.abs(c.dHdtMeasured)<0.1) break;
        }
        return c;
    }
    static double rhAt(VerticalColumn c, double pTarget){
        int best=0; double bd=1e18;
        for(int k=0;k<VerticalColumn.N;k++){ double d=Math.abs(VerticalColumn.PC[k]-pTarget); if(d<bd){bd=d;best=k;} }
        double qs = ParcelLift.qs(c.t[best], VerticalColumn.PC[best]);
        return qs>0 ? c.q[best]/qs : 0.0;
    }
    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p702_report.txt"),"UTF-8");
        say("P702: §502 —— 5 条物理判据（判据以【物理正确】为准，非地球正确）");
        double[] ts = {288.0, 294.0, 300.0, 306.0, 312.0};
        VerticalColumn[] cs = new VerticalColumn[ts.length];
        say("");
        say("=== (a) 对流层顶存在性 + 高度随地面温度【升高】 ===");
        say(String.format(LF,"  %8s | %8s | %10s | %12s | %12s | %10s","Ts(K)","OLR","冷点 z(m)","冷点 T(K)","Γmin(K/km)@z","Γ_sfc(K/km)"));
        double[] zcp = new double[ts.length], olrs = new double[ts.length];
        for(int i=0;i<ts.length;i++){
            double q = 0.98*ParcelLift.qs(ts[i], PrecipField.P_SURF);
            cs[i] = run(ts[i], q);
            olrs[i] = cs[i].olr;
            // 冷点：从柱顶往下找最低温
            int kmin=0; for(int k=1;k<VerticalColumn.N;k++) if(cs[i].t[k]<cs[i].t[kmin]) kmin=k;
            zcp[i]=cs[i].z[kmin];
            // Γmin 与其所在高度；以及近地面 Γ
            double gmin=1e9; int kg=0;
            for(int k=0;k+1<VerticalColumn.N;k++){
                double dz=cs[i].z[k]-cs[i].z[k+1]; if(dz<=0) continue;
                double g=-(cs[i].t[k]-cs[i].t[k+1])/dz;
                if(g<gmin){ gmin=g; kg=k; }
            }
            double dzs=cs[i].z[VerticalColumn.N-2]-cs[i].z[VerticalColumn.N-1];
            double gs = dzs>0 ? -(cs[i].t[VerticalColumn.N-2]-cs[i].t[VerticalColumn.N-1])/dzs : 0;
            say(String.format(LF,"  %8.1f | %8.2f | %10.1f | %12.2f | %12.2f | %10.2f | tau=%.3f",
                ts[i], olrs[i], zcp[i], cs[i].t[kmin], gmin*1000.0, gs*1000.0, cs[i].lastTauTot));
            say(String.format(LF,"           CWV=%.2f kg/m2  tau=%.3f  模型自己的 olrClear=%.2f W/m2  差=%+.2f",
                cs[i].cwv(), cs[i].lastTauTot, StationaryWave.olrClear(ts[i], cs[i].cwv()),
                cs[i].olr - StationaryWave.olrClear(ts[i], cs[i].cwv())));
            say(String.format(LF,"           kg=%d 应看：冷点高度应【单调上升】; Γmin 应远小于 Γ_d=9.77 K/km", kg));
        }
        boolean rise=true; for(int i=1;i<ts.length;i++) if(zcp[i]<=zcp[i-1]) rise=false;
        say(String.format(LF,"  ⇒ 冷点高度单调上升 = %s   (%.0f → %.0f m)", rise?"【是】":"【否】", zcp[0], zcp[ts.length-1]));
        double dzdT=(zcp[ts.length-1]-zcp[0])/(ts[ts.length-1]-ts[0]);
        say(String.format(LF,"  ⇒ dz_cp/dTs = %.0f m/K   (Held/Thuburn-Craig：应显著为正)", dzdT));

        say("");
        say("=== (c) ∂OLR/∂Ts ===");
        double sx=0,sy=0,sxy=0,sxx=0; int n=ts.length;
        for(int i=0;i<n;i++){ sx+=ts[i]; sy+=olrs[i]; sxy+=ts[i]*olrs[i]; sxx+=ts[i]*ts[i]; }
        double slope=(n*sxy-sx*sy)/(n*sxx-sx*sx);
        double teff=Math.pow(olrs[2]/5.670374419e-8,0.25);
        say(String.format(LF,"  ∂OLR/∂Ts = %.3f W/m²/K   (n=%d, Ts %.0f~%.0f K)", slope, n, ts[0], ts[n-1]));
        say(String.format(LF,"  Planck 参照 4*OLR/Teff = %.3f W/m²/K  (Teff=%.1f K)", 4*olrs[2]/teff, teff));
        int flips=0; for(int i=2;i<n;i++) if((olrs[i]-olrs[i-1])*(olrs[i-1]-olrs[i-2])<0) flips++;
        say(String.format(LF,"  OLR 序列 = %.2f %.2f %.2f %.2f %.2f   单调性: 变号 %d 次 %s",
            olrs[0],olrs[1],olrs[2],olrs[3],olrs[4], flips, flips==0?"(单调)":"【非单调 ⇒ 线性斜率不是有效概括】"));
        say(String.format(LF,"  判据带 = 2~4 W/m²/K（本探针声明的带）⇒ %s",
            (slope>2.0 && slope<4.0) ? "【在带内】":"【在带外】"));
        say("  ⚠ 记账：首版判据式写成 slope>1.0&&slope<5.0，与本节声明的 2~4 不一致 ⇒ 1.184 被误报为「在量级内」。已修。");

        say("");
        say("=== (d) RH 廓线 与 (e) 静力稳定 ===");
        say(String.format(LF,"  %8s | %7s %7s %7s | %10s %10s | %s","Ts(K)","RH950","RH550","RH200","Γmax(K/km)","Γmax@z(m)","稳定?"));
        double gd = ParcelLift.GAMMA_D*1000.0;
        for(int i=0;i<ts.length;i++){
            double r950=rhAt(cs[i],95000.0), r550=rhAt(cs[i],55000.0), r200=rhAt(cs[i],20000.0);
            double gmax=-1e9; int kg=0;
            for(int k=0;k+1<VerticalColumn.N;k++){
                double dz=cs[i].z[k]-cs[i].z[k+1]; if(dz<=0) continue;
                double g=-(cs[i].t[k]-cs[i].t[k+1])/dz;
                if(g>gmax){ gmax=g; kg=k; }
            }
            say(String.format(LF,"  %8.1f | %7.3f %7.3f %7.3f | %10.2f %10.0f | %s",
                ts[i], r950, r550, r200, gmax*1000.0, cs[i].z[kg],
                (gmax*1000.0)<gd ? "是 (Γ<Γ_d)" : "【否】"));
        }
        say(String.format(LF,"  物理期望 RH：低层 0.8~0.95 / 中层 0.5~0.75 / 高层 0.3~0.6（Betts-Miller 取 90/70/50）"));

        say("");
        say("=== (b) 对流层是否落在湿绝热上 ===");
        for(int i=0;i<ts.length;i+=2){
            double q = 0.98*ParcelLift.qs(ts[i], PrecipField.P_SURF);
            ParcelLift.Result r = ParcelLift.lift(ts[i], q);
            double se=0; int cnt=0;
            for(int k=0;k<VerticalColumn.N;k++){
                double pk=VerticalColumn.PC[k];
                if(pk<20000.0||pk>cs[i].pSfc*0.9) continue;      // 只比 200~900 hPa
                double tma=ParcelLift.moistAdiabatT(r.tLcl,r.pLcl,pk,80);
                double d=cs[i].t[k]-tma; se+=d*d; cnt++;
            }
            say(String.format(LF,"  Ts=%.0f K: 环境 vs 湿绝热的 RMS 偏差 = %.2f K (n=%d, 200~900 hPa)",
                ts[i], Math.sqrt(se/cnt), cnt));
        }
        say("  物理期望：RCE 下热带自由对流层应接近湿绝热（RMS 几 K 以内）");
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
}
