package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.*;
public class P957 {
  static final Locale LF=Locale.ROOT;
  static final double RD=287.04, P0=100000.0, T0=273.15;
  /** 气压感知的饱和比湿（与 PrecipField.qSat 同一 Bolton 式，但 p 可变） */
  static double qsat(double tK,double p){
    double tc=tK-273.15; double es=611.2*Math.exp(17.67*tc/(tc+243.5));
    return 0.622*es/(p-0.378*es); }
  /** 湿熵（近似式，单位 J/kg/K）：s = Cp ln(T/T0) - Rd ln(p/p0) + L q/T */
  static double ent(double tK,double p,double q){
    return Radiation.CP*Math.log(tK/T0) - RD*Math.log(p/P0) + Radiation.LV*q/tK; }
  /** 湿球（二分，60 次） */
  static double twet(double T,double q,double p){
    double lo=T-70.0, hi=T;
    for(int i=0;i<60;i++){ double m=0.5*(lo+hi);
      double f=Radiation.CP*(T-m)-Radiation.LV*(qsat(m,p)-q);
      if(f>0) lo=m; else hi=m; }
    return 0.5*(lo+hi); }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    sb.append(String.format(LF,"P957: 修正版 —— 下气流用【中层/LFS】温压（不是边界层）%n%n"));
    double ps=101325.0;
    // 边界层：热带海洋实测 RH 0.76(赤道)~0.61(副热带)
    double[] blT={301.0, 299.0, 297.0};
    double[] blRH={0.76, 0.70, 0.61};
    // 中层（LFS 附近）：600 hPa, T 265~275 K, 饱和下降
    double pm=60000.0;
    sb.append("  [1] q_sat 的气压依赖核对 (T=270K):\n");
    sb.append(String.format(LF,"      p=1013hPa: q_sat=%.5f   p=600hPa: q_sat=%.5f   比=%.3f%n",
        qsat(270,ps), qsat(270,pm), qsat(270,pm)/qsat(270,ps)));
    sb.append("\n  [2] 下气流 vs 边界层（中层湿球饱和）:\n");
    sb.append("      blT  blRH |  bl_q   |  Td   q_d   | q_d-q_bl (g/kg) | s_bl    s_d     ds_d   | 符号\n");
    for(int i=0;i<3;i++){
      double Tb=blT[i], rh=blRH[i], qb=rh*qsat(Tb,ps);
      for(double Td=265.0; Td<=275.0; Td+=5.0){
        double qd=qsat(Td,pm), sbl=ent(Tb,ps,qb), sd=ent(Td,pm,qd), dsd=sbl-sd;
        sb.append(String.format(LF,"      %.1f %.2f | %.5f | %.1f %.5f | %+15.3f | %7.1f %7.1f %7.1f | %s%n",
            Tb, rh, qb, Td, qd, 1000*(qd-qb), sbl, sd, dsd, (qd-qb)>0?"源":"【汇】"));
      }
    }
    sb.append("\n  [3] alpha 约掉的代数核对:  M_d = alpha*M_u, M_u = F_s/(alpha*ds_d) => M_d = F_s/ds_d\n");
    double Fs=1.0e-3, dsd=50.0;
    for(double al : new double[]{0.1,0.3,0.5,1.0,2.0}){
      double Mu=Fs/(al*dsd), Md=al*Mu;
      sb.append(String.format(LF,"      alpha=%.1f : M_u=%.6e  M_d=%.6e   M_d*ds_d=%.6e (=F_s? %s)%n",
          al, Mu, Md, Md*dsd, Math.abs(Md*dsd-Fs)<1e-18?"是":"否")); }
    sb.append("\n  [4] 湿熵式的自检: s 应随 T 升、随 p 升而降、随 q 升\n");
    sb.append(String.format(LF,"      s(300K,1013hPa,0.018)=%.1f   s(280K,1013hPa,0.018)=%.1f   (T 升 => s 升? %s)%n",
        ent(300,ps,0.018), ent(280,ps,0.018), ent(300,ps,0.018)>ent(280,ps,0.018)?"是":"否"));
    sb.append(String.format(LF,"      s(280K,1013hPa,0.006)=%.1f  s(280K,600hPa,0.006)=%.1f  (p 降 => s 升? %s)%n",
        ent(280,ps,0.006), ent(280,pm,0.006), ent(280,pm,0.006)>ent(280,ps,0.006)?"是":"否"));
    sb.append(String.format(LF,"      s(280K,1013hPa,0.010)=%.1f  s(280K,1013hPa,0.002)=%.1f  (q 升 => s 升? %s)%n",
        ent(280,ps,0.010), ent(280,ps,0.002), ent(280,ps,0.010)>ent(280,ps,0.002)?"是":"否"));
    sb.append("\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p957_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
