package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P959 {
  static final Locale LF=Locale.ROOT;
  // 观测年均 |V| (ERA5 u,v 1deg, 海洋) —— refs/e_obs_profile.npz 的 spd
  static final double[] VOBS={4.48,4.49,5.13,5.78,5.06,3.77,3.34,3.63,3.64,3.53,3.49,3.63,3.47,3.14,2.93,2.75,2.33,2.40,2.49};
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    double thS=Atmosphere.theta(0.0), thW=Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    sb.append("P959: |V| 的相位检查 —— P958 用的是夏至, 而 e_obs 是年均\n\n");
    sb.append("  lat | uZm(夏) uZm(冬) uZm(均) |  |V|(夏)  |V|(冬)  |V|(均) | V_obs | 均/观\n");
    for(int i=0;i<=18;i++){
      double d=i*5.0, lr=Math.toRadians(d);
      double us=ZonalTables.uZmBlend(d,thS,0.0), uw=ZonalTables.uZmBlend(d,thW,0.0);
      double[] vs=Atmosphere.wind(0,0,0.0,lr,thS), vw=Atmosphere.wind(0,0,0.0,lr,thW);
      double spS=Math.hypot(vs[0],vs[1]), spW=Math.hypot(vw[0],vw[1]);
      double spM=0.5*(spS+spW);
      sb.append(String.format(LF,"  %3.0f | %+7.2f %+7.2f %+7.2f | %7.2f %8.2f %8.2f | %5.2f | %s%n",
          d, us, uw, 0.5*(us+uw), spS, spW, spM, VOBS[i], VOBS[i]>1e-9?String.format(LF,"%.2f",spM/VOBS[i]):"-"));
    }
    sb.append("\n  注: Atmosphere.wind(px=0,pz=0,kappa=0,lat,theta) 只留纬向平均项 + vZmAt\n");
    sb.append("DONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p959_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
