package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P953 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  static void row(StringBuilder sb, String tag, long seed, double th){
    int H=WorldContract.MAX_D;
    sb.append("  ").append(tag).append("\n");
    sb.append("   lat |  d(ux)/dx(海)  d(vz)/dz(海)   和 | d(ux)/dx(陆)  d(vz)/dz(陆)   和\n");
    for (int i=1;i<=17;i++){
      double latDeg=i*5.0; int z=(int)(latDeg/90.0*H);
      double a1=0,a2=0,b1=0,b2=0; int ns=0,nl=0;
      for (int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
        for (int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
          double kap=Atmosphere.kappaMemo(x,z,seed,CELL);
          double[] ux=Atmosphere.windAt(x+GRAD,z,seed,CELL,th,GRAD);
          double[] uw=Atmosphere.windAt(x-GRAD,z,seed,CELL,th,GRAD);
          double[] un=Atmosphere.windAt(x,z+GRAD,seed,CELL,th,GRAD);
          double[] us=Atmosphere.windAt(x,z-GRAD,seed,CELL,th,GRAD);
          double d1=(ux[0]-uw[0])/(2.0*GRAD), d2=(un[1]-us[1])/(2.0*GRAD);
          if (kap<=0.5){ a1+=d1; a2+=d2; ns++; } else { b1+=d1; b2+=d2; nl++; }
        } }
      double n1=Math.max(1,ns), n2=Math.max(1,nl);
      sb.append(String.format(LF,"   %3.0f | %+12.3e %+12.3e %+9.2e | %+12.3e %+12.3e %+9.2e%n",
          latDeg, a1/n1, a2/n1, (a1+a2)/n1, b1/n2, b2/n2, (b1+b2)/n2));
    }
  }
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double th=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    sb.append("P953: divU = d(ux)/dx + d(vz)/dz 两项分解  GRAD="+GRAD+"  SEA_ONLY_UZM="+ZonalTables.SEA_ONLY_UZM+"\n\n");
    row(sb,"=== 基线 ===",seed,th);
    sb.append("\n");
    ZonalTables.SEA_ONLY_UZM=false; SimClimate.clearCache();
    row(sb,"=== SEA_ONLY_UZM=false (u_zm 不再按 kappa 混合) ===",seed,th);
    ZonalTables.SEA_ONLY_UZM=true; SimClimate.clearCache();
    sb.append("\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p953_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
