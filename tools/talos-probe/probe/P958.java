package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P958 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  static final double RD=287.04, P0=100000.0, T0=273.15, PS=101325.0, RHO=1.225;
  // E_obs (mm/day) from refs/e_obs_profile.npz, lats 0..90 step 5
  static final double[] EOBS={3.333,3.885,4.816,5.903,5.564,3.952,2.845,2.378,1.830,0.899,0.516,0.539,0.516,0.560,0.578,0.578,0.531,0.573,0.572};
  static double qsat(double tK,double p){ double tc=tK-273.15;
    double es=611.2*Math.exp(17.67*tc/(tc+243.5)); return 0.622*es/(p-0.378*es); }
  static double ent(double tK,double p,double q){
    return Radiation.CP*Math.log(tK/T0)-RD*Math.log(p/P0)+Radiation.LV*q/tK; }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double th=Atmosphere.theta(0.0);   // 夏至（与 e_obs 的年均对照只作量级与结构）
    sb.append("P958 (S1): 模型海洋格点上的 F_s 与等价体积蒸发, 逐纬对照 E_obs\n\n");
    sb.append("  lat |  Ts    qs(g/kg) q(g/kg)  RH   |  |V|  C_H*1e3 |  dq(g/kg) | E_bulk | E_obs | 比\n");
    int H=WorldContract.MAX_D;
    for(int i=0;i<=18;i++){
      double latDeg=i*5.0; int z=(int)(latDeg/90.0*H);
      double sTs=0,sqs=0,sq=0,sV=0,sCH=0,sdq=0; int n=0;
      for(int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
        for(int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
          double kap=Atmosphere.kappaMemo(x,z,seed,CELL); if(kap>0.5) continue;
          PrecipField.mmPerDay(x,z,seed,CELL,th,GRAD);
          double[] d=PrecipField.DIAG.get();
          double q=d[5], tQ=d[6]; if(!(q>0)||!(tQ>200)) continue;
          double[] uv=Atmosphere.windAt(x,z,seed,CELL,th,GRAD);
          double sp=Math.hypot(uv[0],uv[1]);
          double chv=Radiation.bulkCoeff(0.0,sp);
          // 海面温度：模型的海面支（海洋点 kappa=0）
          double Ts=Atmosphere.annualSeaLevelTemp(WorldContract.latOf(z),0.0,Atmosphere.sstAnom(x,z,th))
                  + Atmosphere.seasonalAnomaly(WorldContract.latOf(z),0.0,th);
          double qs=qsat(Ts,PS);
          sTs+=Ts; sqs+=qs; sq+=q; sV+=sp; sCH+=chv; sdq+=(qs-q); n++;
        } }
      if(n<5){ sb.append(String.format(LF,"  %3.0f | (海洋格点不足 n=%d)%n",latDeg,n)); continue; }
      double Ts=sTs/n, qs=sqs/n, q=sq/n, V=sV/n, CH=sCH/n, dq=sdq/n;
      double Eb=RHO*CH*V*dq*86400.0*1000.0;   // mm/day
      double eo=EOBS[i];
      sb.append(String.format(LF,"  %3.0f | %6.2f %8.2f %7.2f %5.2f | %5.2f %8.3f | %9.2f | %6.3f | %5.3f | %s%n",
          latDeg, Ts, qs*1000, q*1000, q/qs, V, CH*1e3, dq*1000, Eb, eo, eo>1e-9?String.format(LF,"%.3f",Eb/eo):"-"));
    }
    sb.append("\n  判据(§634 S1): ① 量级 ② 结构与 E_obs 同向(赤道相对极小/副热带极大) ③ 比值有限常数\n");
    sb.append("DONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p958_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
