package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P963 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  /** 返回 {pSh(地板), pMm(最终), q(闭包前), pPostEddy} 的均值 */
  static double[] scan(long seed,double th,double latDeg){
    int z=(int)(latDeg/90.0*WorldContract.MAX_D);
    double a=0,b=0,c=0,e=0; int n=0;
    for(int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
      for(int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
        double kap=Atmosphere.kappaMemo(x,z,seed,CELL); if(kap>0.5) continue;
        double p=PrecipField.mmPerDay(x,z,seed,CELL,th,GRAD);
        double[] d=PrecipField.DIAG.get();
        a+=d[10]*86400.0*1000.0; b+=p; c+=d[5]; e+=d[9]*86400.0*1000.0; n++;
      } }
    double nn=Math.max(1,n); return new double[]{a/nn,b/nn,c/nn,e/nn,n};
  }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double thW=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    sb.append("P963: 验证「地板随 q 上升而下降」 (同批海洋格点/同相位/开与关)\n");
    sb.append(String.format(LF,"  SHALLOW_FLOOR=%s  ALPHA_SH=%.3f%n%n", PrecipField.SHALLOW_FLOOR, PrecipField.ALPHA_SH));
    double[][] off=new double[6][], on=new double[6][];
    double[] lats={5,10,15,20,25,30};
    for(int c=0;c<2;c++){ PrecipField.Q_FROM_BLBUDGET=(c==1); SimClimate.clearCache();
      for(int i=0;i<6;i++){ double[] r=scan(seed,thW,lats[i]); if(c==0) off[i]=r; else on[i]=r; } }
    PrecipField.Q_FROM_BLBUDGET=false; SimClimate.clearCache();
    sb.append("  纬度 |  地板pSh OFF   地板pSh ON   比 |  最终P OFF   最终P ON   比 | q(闭包前,应相同)\n");
    for(int i=0;i<6;i++){
      sb.append(String.format(LF,"  %4.0f | %11.4f %11.4f %6.3f | %10.4f %10.4f %6.3f | %.6f / %.6f%n",
          lats[i], off[i][0], on[i][0], on[i][0]/Math.max(1e-12,off[i][0]),
          off[i][1], on[i][1], on[i][1]/Math.max(1e-12,off[i][1]), off[i][2], on[i][2])); }
    sb.append("\n  判据(预先写死): 若 pSh_ON/pSh_OFF < 1 且与「q 上升」同向, 则机制确认\n");
    sb.append("  (q 那一列两次应相同 —— 它是闭包【之前】的值; 地板是闭包【之后】算的)\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p963_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
