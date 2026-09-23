package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P962 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double thW=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    double qftF=Math.exp(-(0.5*Atmosphere.H_EFF)/PrecipField.H_MOIST);
    PrecipField.Q_FROM_BLBUDGET=true; SimClimate.clearCache();
    sb.append("P962: 闭包后的 q —— 【陆海分开】(P961 只测了海洋)\n");
    sb.append(String.format(LF,"  Q_FROM_BLBUDGET=%s SUB_ONLY=%s BETA=%.2f qftF=%.4f%n%n",
        PrecipField.Q_FROM_BLBUDGET, PrecipField.Q_BLBUDGET_SUB_ONLY, PrecipField.BETA_DOWNDRAFT, qftF));
    int H=WorldContract.MAX_D;
    for(int i=1;i<=4;i++){
      double latDeg=i*5.0; int z=(int)(latDeg/90.0*H);
      double[] oq={0,0}; double[] lq={0,0}; double[] ob={0,0,0}; double[] lb={0,0,0};
      int no=0,nl=0; double sbeta=0,sbetaN=0;
      for(int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
        for(int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
          double kap=Atmosphere.kappaMemo(x,z,seed,CELL);
          double[] uv=Atmosphere.windAt(x,z,seed,CELL,thW,GRAD);
          double sp=Math.hypot(uv[0],uv[1]);
          PrecipField.mmPerDay(x,z,seed,CELL,thW,GRAD);
          double[] d=PrecipField.DIAG.get();
          double qp=d[5], tQ=d[6], wE=d[3], divU=d[2], beta=d[13], depl=d[11];
          if(!(qp>0)||!(tQ>200)) continue;
          double chv=Radiation.bulkCoeff(kap,sp);
          double kPb=(wE>0.0)?(1.0-PrecipField.BETA_DOWNDRAFT)*PrecipField.EPS_C*PrecipField.RHO_AIR*wE/PrecipField.RHO_WATER*86400.0*1000.0:0.0;
          double Cb=PrecipField.Q_BLBUDGET_SUB_ONLY
                   ? 86400.0*PrecipField.RHO_AIR*Math.max(0.0,Atmosphere.H_BL*divU)
                   : 86400.0*PrecipField.RHO_AIR*Math.abs(-Atmosphere.H_BL*divU);
          double A=86400.0*chv, Cd=Cb*(1.0-qftF), denB=A+kPb+Cd;
          double qn=(denB>1e-12)? A*PrecipField.qSat(tQ)*beta*depl/denB : qp;
          if(kap<=0.5){ oq[0]+=qp; oq[1]+=qn; ob[0]+=A; ob[1]+=kPb; ob[2]+=Cd; no++; }
          else       { lq[0]+=qp; lq[1]+=qn; lb[0]+=A; lb[1]+=kPb; lb[2]+=Cd; nl++; sbeta+=beta; }
        } }
      sb.append(String.format(LF,"  ---- %3.0f 度   海洋 n=%d / 陆地 n=%d ----%n", latDeg, no, nl));
      if(no>0) sb.append(String.format(LF,"    海洋: q %.6f -> %.6f  比 %.4f   86400chv %.1f kPb %.1f Cb %.1f%n",
          oq[0]/no, oq[1]/no, oq[1]/Math.max(1e-12,oq[0]), ob[0]/no, ob[1]/no, ob[2]/no));
      if(nl>0) sb.append(String.format(LF,"    陆地: q %.6f -> %.6f  比 %.4f   86400chv %.1f kPb %.1f Cb %.1f   beta均 %.3f%n",
          lq[0]/nl, lq[1]/nl, lq[1]/Math.max(1e-12,lq[0]), lb[0]/nl, lb[1]/nl, lb[2]/nl, sbeta/nl));
    }
    PrecipField.Q_FROM_BLBUDGET=false; SimClimate.clearCache();
    sb.append("\n  判据: 若陆地 q 比 < 1 而海洋 > 1, 则 P296 赤道带的变差可由【陆地占比高】解释\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p962_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
