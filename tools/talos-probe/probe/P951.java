package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P951 {
  static final int WORLD=1022228679, CELL=PlateField.PLATE_CELL, GRAD=500_000;
  static final int NW=12, SPAN=40_000_000, NPW=25;
  static final Locale LF=Locale.ROOT;
  static PrintStream rep;
  static void say(String s){ rep.println("[P951] "+s); rep.flush(); System.out.println("[P951] "+s); System.out.flush(); }
  public static void main(String[] a) throws Exception {
    rep = new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p951_report.txt"),"UTF-8");
    long seed = SimTerrain.seedOf(WORLD);
    double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
    say("P951: divU 的两项分解 (therm vs cellTerm)  冬至相位");
    String[] tags = {"BOTH","NO_CELL","NO_THERMAL"};
    boolean[] nc = {false,true,false}, nt = {false,false,true};
    for (int c=0;c<3;c++) {
      Atmosphere.PA_NO_CELL = nc[c]; Atmosphere.PA_NO_THERMAL = nt[c];
      SimClimate.clearCache();
      say(""); say("  ==== " + tags[c] + "  (PA_NO_CELL=" + nc[c] + " PA_NO_THERMAL=" + nt[c] + ") ====");
      say("   lat | 海洋 divU0    wEff   | 陆地 divU0    wEff");
      int H = WorldContract.MAX_D;
      for (int i=1;i<=17;i++) {
        double latDeg=i*5.0; int z=(int)(latDeg/90.0*H);
        double so=0,sw=0,lo=0,lw=0; int ns=0,nl=0;
        for (int w=0;w<NW;w++) { int x0=-(NW/2)*SPAN+w*SPAN;
          for (int k=0;k<NPW;k++) { int x=x0+(int)((long)SPAN*(2*k+1)/(2*NPW));
            double kap=Atmosphere.kappaMemo(x,z,seed,CELL);
            PrecipField.mmPerDay(x,z,seed,CELL,thW,GRAD);
            double[] d=PrecipField.DIAG.get();
            if (kap<=0.5) { so+=d[0]; sw+=d[3]; ns++; } else { lo+=d[0]; lw+=d[3]; nl++; }
          } }
        double n1=Math.max(1,ns), n2=Math.max(1,nl);
        say(String.format(LF,"   %3.0f | %+10.3e %+9.2e | %+10.3e %+9.2e",latDeg,so/n1,sw/n1,lo/n2,lw/n2));
      }
    }
    Atmosphere.PA_NO_CELL=false; Atmosphere.PA_NO_THERMAL=false; SimClimate.clearCache();
    say(""); say("DONE");
    rep.flush(); System.out.println("JAVA_EXIT=0");
  }
}
