package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P952 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    long seed=SimTerrain.seedOf(WORLD);
    double thW=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    sb.append("P952: 风是否被 U_MAX 截断?  U_MAX="+Atmosphere.U_MAX+"  PZREF_VZ_MODE="+Atmosphere.PZREF_VZ_MODE+"\n\n");
    sb.append("  lat |  sp_mean  sp_max  sp_p95 | frac(sp>=0.999UMAX) | nSat/nSea\n");
    int H=WorldContract.MAX_D;
    for (int i=1;i<=17;i++) {
      double latDeg=i*5.0; int z=(int)(latDeg/90.0*H);
      double sm=0,mx=0,n=0,nSat=0,ntot=0; double[] all=new double[NW*NPW]; int na=0;
      for (int w=0;w<NW;w++){ int x0=-(NW/2)*SPAN+w*SPAN;
        for (int k=0;k<NPW;k++){ int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
          double kap=Atmosphere.kappaMemo(x,z,seed,CELL);
          if (kap>0.5) continue;
          double[] uv=Atmosphere.windAt(x,z,seed,CELL,thW,GRAD);
          double sp=Math.hypot(uv[0],uv[1]);
          sm+=sp; if(sp>mx)mx=sp; n++; all[na++]=sp; ntot++;
          if (sp>=0.999*Atmosphere.U_MAX) nSat++;
        } }
      double[] s=Arrays.copyOf(all,na); Arrays.sort(s);
      sb.append(String.format(LF,"  %3.0f | %8.3f %7.3f %7.3f | %18.1f%% | %.0f/%.0f%n",
          latDeg, sm/Math.max(1,n), mx, s[(int)(0.95*(na-1))], 100.0*nSat/Math.max(1,ntot), nSat, ntot));
    }
    sb.append("\nDONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p952_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
