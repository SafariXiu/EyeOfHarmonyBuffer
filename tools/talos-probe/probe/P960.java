package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P960 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  static double qsat(double t){ double tc=t-273.15; double es=611.2*Math.exp(17.67*tc/(tc+243.5)); return 0.622*es/(101325.0-0.378*es); }
  /** 一次采集：返回 {q, tQ, wE, divU, kPb, Cb, chv} 的均值 */
  static double[] scan(long seed,double theta,double latLo,double latHi){
    double sq=0,st=0,sw=0,sd=0,sk=0,sc=0,sv=0; int n=0;
    double qftF=Math.exp(-(0.5*Atmosphere.H_EFF)/PrecipField.H_MOIST);
    for(double latDeg=latLo; latDeg<=latHi+1e-9; latDeg+=5.0){
      int z=(int)(latDeg/90.0*WorldContract.MAX_D);
      for(int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
        for(int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
          double kap=Atmosphere.kappaMemo(x,z,seed,CELL); if(kap>0.5) continue;
          PrecipField.mmPerDay(x,z,seed,CELL,theta,GRAD);
          double[] d=PrecipField.DIAG.get();
          double q=d[5], tQ=d[6], wE=d[3], divU=d[2];
          if(!(q>0)||!(tQ>200)) continue;
          double chv=Radiation.bulkCoeff(0.0,Math.hypot(0,0)+0.0);
          double kPb=(wE>0.0)?(1.0-PrecipField.BETA_DOWNDRAFT)*PrecipField.EPS_C*PrecipField.RHO_AIR*wE/PrecipField.RHO_WATER*86400.0*1000.0:0.0;
          double Cb=PrecipField.Q_BLBUDGET_SUB_ONLY
                   ? 86400.0*PrecipField.RHO_AIR*Math.max(0.0,Atmosphere.H_BL*divU)
                   : 86400.0*PrecipField.RHO_AIR*Math.abs(-Atmosphere.H_BL*divU);
          sq+=q; st+=tQ; sw+=wE; sd+=divU; sk+=kPb; sc+=Cb*(1.0-qftF); sv+=chv; n++;
        } }
    }
    return new double[]{n, sq/Math.max(1,n), st/Math.max(1,n), sw/Math.max(1,n), sd/Math.max(1,n), sk/Math.max(1,n), sc/Math.max(1,n), sv/Math.max(1,n)};
  }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double thW=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    double qftF=Math.exp(-(0.5*Atmosphere.H_EFF)/PrecipField.H_MOIST);
    sb.append("P960: 闭包在热带的 denB 分解  (同一探针/同批格点/同相位 => 同口径)\n");
    sb.append(String.format(LF,"  qftF=%.4f  H_BL=%.1f  H_EFF=%.1f  H_MOIST=%.1f  EPS_C=%.3f  BETA_DOWNDRAFT=%.2f  SUB_ONLY=%s%n%n",
        qftF, Atmosphere.H_BL, Atmosphere.H_EFF, PrecipField.H_MOIST, PrecipField.EPS_C, PrecipField.BETA_DOWNDRAFT, PrecipField.Q_BLBUDGET_SUB_ONLY));
    double[][] r=new double[2][];
    boolean[] cfg={false,true};
    for(int c=0;c<2;c++){ PrecipField.Q_FROM_BLBUDGET=cfg[c]; SimClimate.clearCache();
      r[c]=scan(seed,thW,5.0,15.0);
      sb.append(String.format(LF,"  [%s] n=%d  q=%.6f  tQ=%.2f  wE=%+.3e  divU=%+.3e  kPb=%.3f  C(1-qftF)=%.3f  chv=%.3e%n",
          cfg[c]?"ON ":"OFF", (int)r[c][0], r[c][1], r[c][2], r[c][3], r[c][4], r[c][5], r[c][6], r[c][7])); }
    PrecipField.Q_FROM_BLBUDGET=false; SimClimate.clearCache();
    sb.append("\n  ---- denB 三项的对比 ----\n");
    double d0=86400.0*r[0][7], d1=86400.0*r[1][7];
    sb.append(String.format(LF,"  项            OFF        ON         变化%n"));
    sb.append(String.format(LF,"  86400*chv   %9.3f %9.3f   %+.1f%%%n", d0, d1, 100*(d1-d0)/Math.max(1e-9,d0)));
    sb.append(String.format(LF,"  k_P         %9.3f %9.3f   %+.1f%%%n", r[0][5], r[1][5], 100*(r[1][5]-r[0][5])/Math.max(1e-9,Math.abs(r[0][5]))));
    sb.append(String.format(LF,"  C(1-qftF)   %9.3f %9.3f   %+.1f%%%n", r[0][6], r[1][6], 100*(r[1][6]-r[0][6])/Math.max(1e-9,Math.abs(r[0][6]))));
    sb.append(String.format(LF,"  denB        %9.3f %9.3f   %+.1f%%%n", d0+r[0][5]+r[0][6], d1+r[1][5]+r[1][6],
        100*((d1+r[1][5]+r[1][6])-(d0+r[0][5]+r[0][6]))/Math.max(1e-9,(d0+r[0][5]+r[0][6]))));
    sb.append(String.format(LF,"  q           %9.6f %9.6f   %+.1f%%  <= 被抽干的程度%n", r[0][1], r[1][1], 100*(r[1][1]-r[0][1])/r[0][1]));
    sb.append("\n  判据(预先写死): q 的下降须与某一项的变化【同向且量级相当】才算定位\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p960_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
