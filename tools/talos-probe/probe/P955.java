package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.*;
public class P955 {
  static final Locale LF=Locale.ROOT;
  static double qs(double tK){ return PrecipField.qSat(tK); }
  /** 解析 dq_sat/dT（Bolton 式，与 PrecipField.qSat 逐字一致） */
  static double dqs(double tK){
    double tc=tK-273.15, den=tc+243.5;
    double es=611.2*Math.exp(17.67*tc/den);
    double des=es*17.67*243.5/(den*den);
    double P=101325.0, a=P-0.378*es;
    return 0.622*P*des/(a*a);
  }
  /** 甲：定点迭代 N 次 */
  static double twIter(double T,double q,int n){
    double tw=T;
    for(int i=0;i<n;i++) tw = T - (Radiation.LV/Radiation.CP)*(qs(tw)-q);
    return tw;
  }
  /** 乙：一次线性化（闭式） */
  static double twLin(double T,double q){
    return T - Radiation.LV*(qs(T)-q)/(Radiation.CP + Radiation.LV*dqs(T));
  }
  /** 基准：二分 60 次 */
  static double twRef(double T,double q){
    double lo=T-60.0, hi=T;
    for(int i=0;i<60;i++){ double m=0.5*(lo+hi);
      double f=Radiation.CP*(T-m)-Radiation.LV*(qs(m)-q);
      if(f>0) lo=m; else hi=m; }   // f 随 m 递减
    return 0.5*(lo+hi);
  }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    sb.append(String.format(LF,"P955: 湿球温度 T_w 两种解法 vs 二分基准   CP=%.2f  LV=%.3e%n%n",Radiation.CP,Radiation.LV));
    // 样本：T 270..305 K, RH 0.40..0.95（海面边界层现实区间）
    int NT=36, NR=12; double[] Ts=new double[NT*NR], Qs=new double[NT*NR]; int n=0;
    for(int i=0;i<NT;i++){ double T=270.0+35.0*i/(NT-1);
      for(int j=0;j<NR;j++){ double rh=0.40+0.55*j/(NR-1);
        Ts[n]=T; Qs[n]=rh*qs(T); n++; } }
    double[] ref=new double[n];
    for(int i=0;i<n;i++) ref[i]=twRef(Ts[i],Qs[i]);
    sb.append(String.format(LF,"  样本 n=%d   T=270~305 K   RH=0.40~0.95   T_w 范围 %.3f ~ %.3f K%n%n",
        n, min(ref), max(ref)));
    sb.append("  解法               RMS误差(K)   最大误差(K)   误差>0.1K占比   ns/次\n");
    for(int it=1; it<=4; it++){
      double se=0,mx=0; int bad=0;
      long t0=System.nanoTime(); double sink=0;
      for(int rep=0; rep<2000; rep++) for(int i=0;i<n;i++) sink+=twIter(Ts[i],Qs[i],it);
      double ns=(System.nanoTime()-t0)/(double)(2000L*n);
      for(int i=0;i<n;i++){ double e=twIter(Ts[i],Qs[i],it)-ref[i]; se+=e*e; if(Math.abs(e)>mx)mx=Math.abs(e); if(Math.abs(e)>0.1)bad++; }
      sb.append(String.format(LF,"  甲 定点迭代 %d 次   %10.4f %12.4f %13.1f%% %10.1f%n", it, Math.sqrt(se/n), mx, 100.0*bad/n, ns));
    }
    { double se=0,mx=0; int bad=0;
      long t0=System.nanoTime(); double sink=0;
      for(int rep=0; rep<2000; rep++) for(int i=0;i<n;i++) sink+=twLin(Ts[i],Qs[i]);
      double ns=(System.nanoTime()-t0)/(double)(2000L*n);
      for(int i=0;i<n;i++){ double e=twLin(Ts[i],Qs[i])-ref[i]; se+=e*e; if(Math.abs(e)>mx)mx=Math.abs(e); if(Math.abs(e)>0.1)bad++; }
      sb.append(String.format(LF,"  乙 一次线性化        %10.4f %12.4f %13.1f%% %10.1f%n", Math.sqrt(se/n), mx, 100.0*bad/n, ns)); }
    { long t0=System.nanoTime(); double sink=0;
      for(int rep=0; rep<200; rep++) for(int i=0;i<n;i++) sink+=twRef(Ts[i],Qs[i]);
      double ns=(System.nanoTime()-t0)/(double)(200L*n);
      sb.append(String.format(LF,"  基准 二分 60 次      %10s %12s %13s %10.1f%n","0(基准)","-","-", ns)); }
    sb.append("\n  代表点明细 (T, RH, 基准 T_w, 甲3次误差, 乙误差):\n");
    for(int k=0;k<12;k++){ int i=(int)((long)k*(n-1)/11); if(i>=n) continue;
      sb.append(String.format(LF,"    T=%.1fK RH=%.2f  ref=%.3f  甲3=%.4f  乙=%.4f%n",
          Ts[i], Qs[i]/qs(Ts[i]), ref[i], twIter(Ts[i],Qs[i],3)-ref[i], twLin(Ts[i],Qs[i])-ref[i])); }
    sb.append("\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p955_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
  static double min(double[] v){ double m=Double.MAX_VALUE; for(double x:v) m=Math.min(m,x); return m; }
  static double max(double[] v){ double m=-Double.MAX_VALUE; for(double x:v) m=Math.max(m,x); return m; }
}
