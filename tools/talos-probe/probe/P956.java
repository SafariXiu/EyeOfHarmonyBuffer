package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.*;
public class P956 {
  static final Locale LF=Locale.ROOT;
  static double qs(double t){ return PrecipField.qSat(t); }
  static double twRef(double T,double q){
    double lo=T-60.0, hi=T;
    for(int i=0;i<60;i++){ double m=0.5*(lo+hi);
      double f=Radiation.CP*(T-m)-Radiation.LV*(qs(m)-q);
      if(f>0) lo=m; else hi=m; }
    return 0.5*(lo+hi); }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    sb.append(String.format(LF,"P956: 下气流对边界层是源还是汇?  q_d=q_sat(T_w) vs q_bl=RH*q_sat(T)%n%n"));
    sb.append("   RH  | 平均 T-T_w | 平均 (q_d-q_bl) | >0 占比 | 均值符号\n");
    for(int j=0;j<12;j++){
      double rh=0.40+0.55*j/11.0; double sd=0,sdt=0; int pos=0,n=0;
      for(int i=0;i<36;i++){ double T=270.0+35.0*i/35.0;
        double q=rh*qs(T), tw=twRef(T,q), d=qs(tw)-q;
        sd+=d; sdt+=(T-tw); if(d>0)pos++; n++; }
      sb.append(String.format(LF,"  %.3f | %9.3f K | %+12.4f g/kg | %6.1f%% | %s%n",
          rh, sdt/n, 1000.0*sd/n, 100.0*pos/n, sd>0?"【源】":"【汇】"));
    }
    // 找符号翻转的 RH 临界（在 T=300 K 处）
    double T=300.0, lo=0.50, hi=0.99;
    for(int i=0;i<60;i++){ double rh=0.5*(lo+hi); double q=rh*qs(T);
      double d=qs(twRef(T,q))-q; if(d>0) lo=rh; else hi=rh; }
    sb.append(String.format(LF,"%n  T=300K 处符号翻转的 RH 临界 = %.4f  (RH 低于它 => 下气流是源)%n", 0.5*(lo+hi)));
    double[] ts={280.0,290.0,300.0,305.0};
    sb.append("\n  各温度下的临界 RH: ");
    for(double t:ts){ double l=0.50,h=0.99;
      for(int i=0;i<60;i++){ double rh=0.5*(l+h); double q=rh*qs(t);
        double d=qs(twRef(t,q))-q; if(d>0) l=rh; else h=rh; }
      sb.append(String.format(LF,"T=%.0fK:%.4f  ",t,0.5*(l+h))); }
    sb.append("\n\n  热带海洋边界层实测 RH = 0.61(副热带) ~ 0.76(赤道)%n");
    sb.append("DONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p956_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
