package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P961 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double thW=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    double qftF=Math.exp(-(0.5*Atmosphere.H_EFF)/PrecipField.H_MOIST);
    PrecipField.Q_FROM_BLBUDGET=true; SimClimate.clearCache();
    sb.append("P961: 复算闭包后的 q  (探针内复算, 与 mmPerDay 同式)\n");
    sb.append(String.format(LF,"  qftF=%.4f  Q_FROM_BLBUDGET=%s  SUB_ONLY=%s  BETA=%.2f%n%n",
        qftF, PrecipField.Q_FROM_BLBUDGET, PrecipField.Q_BLBUDGET_SUB_ONLY, PrecipField.BETA_DOWNDRAFT));
    sb.append("  纬度 |  n  |  q_pre     q_post    q_post/q_pre | RH_pre RH_post |  86400chv    kPb    Cb(1-qftF) |  denB\n");
    int H=WorldContract.MAX_D;
    for(int i=1;i<=6;i++){
      double latDeg=i*5.0; int z=(int)(latDeg/90.0*H);
      double sqp=0,sqn=0,sdv=0,sk=0,sc=0,ss=0,sbta=0; int n=0;
      for(int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
        for(int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
          double kap=Atmosphere.kappaMemo(x,z,seed,CELL); if(kap>0.5) continue;
          double[] uv=Atmosphere.windAt(x,z,seed,CELL,thW,GRAD);
          double sp=Math.hypot(uv[0],uv[1]);
          PrecipField.mmPerDay(x,z,seed,CELL,thW,GRAD);
          double[] d=PrecipField.DIAG.get();
          double qp=d[5], tQ=d[6], wE=d[3], divU=d[2], beta=d[13], depl=d[11];
          if(!(qp>0)||!(tQ>200)) continue;
          double chv=Radiation.bulkCoeff(0.0,sp);
          double kPb=(wE>0.0)?(1.0-PrecipField.BETA_DOWNDRAFT)*PrecipField.EPS_C*PrecipField.RHO_AIR*wE/PrecipField.RHO_WATER*86400.0*1000.0:0.0;
          double Cb=PrecipField.Q_BLBUDGET_SUB_ONLY
                   ? 86400.0*PrecipField.RHO_AIR*Math.max(0.0,Atmosphere.H_BL*divU)
                   : 86400.0*PrecipField.RHO_AIR*Math.abs(-Atmosphere.H_BL*divU);
          double A=86400.0*chv, Cd=Cb*(1.0-qftF), denB=A+kPb+Cd;
          double qn=(denB>1e-12)? A*PrecipField.qSat(tQ)*beta*depl/denB : qp;
          sqp+=qp; sqn+=qn; sdv+=denB; sk+=kPb; sc+=Cd; ss+=A; sbta+=beta; n++;
        } }
      double nn=Math.max(1,n);
      sb.append(String.format(LF,"  %4.0f | %3d | %.6f  %.6f  %8.4f    | %6.3f %7.3f | %9.1f %7.1f %11.1f | %7.1f%n",
          latDeg, n, sqp/nn, sqn/nn, sqn/Math.max(1e-12,sqp), sqp/nn/PrecipField.qSat(299.0), sqn/nn/PrecipField.qSat(299.0),
          ss/nn, sk/nn, sc/nn, sdv/nn));
    }
    PrecipField.Q_FROM_BLBUDGET=false; SimClimate.clearCache();
    sb.append("\n  列义: q_pre=DIAG[5](闭包前)  q_post=探针复算(闭包后)  RH 用 qSat(299K) 近似, 仅作相对比较\n");
    sb.append("  判据(预先写死): 若 q_post/q_pre > 1 则闭包【增湿】, 反噬不在 q 上; 若 < 1 则看是哪一项\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p961_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
