package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.*;
public class P950 {
  public static void main(String[] a) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("P950: cellPressure 分解  = CELL_GAIN * carrier(phi-mig) * tropicGate(phi) * (KAPPA_MEAN - kappa)\n\n");
    sb.append("  phi |  carrier(phi) | tropicGate | carrier*gate | *CELL_GAIN | 归一(45度)\n");
    double g45 = Atmosphere.tropicGate(Math.toRadians(45.0));
    double c45 = ZonalTables.carrier(45.0) * g45;
    for (int i = 0; i <= 18; i++) {
      double d = i * 5.0;
      double c = ZonalTables.carrier(d);
      double g = Atmosphere.tropicGate(Math.toRadians(d));
      sb.append(String.format(Locale.ROOT, "  %3.0f | %+13.4f | %10.6f | %+12.4f | %+10.2f | %9.4f%n",
          d, c, g, c * g, c * g * Atmosphere.CELL_GAIN, (c * g) / c45));
    }
    sb.append(String.format(Locale.ROOT, "%n  CELL_GAIN=%.2f  CELL_MIGRATION=8deg  CELL_LAG=30d  KAPPA_MEAN=%.4f%n",
        Atmosphere.CELL_GAIN, Atmosphere.KAPPA_MEAN));
    sb.append(String.format(Locale.ROOT, "  carrier(5)/carrier(45) = %.3f   gate(5)=%.4f%n",
        ZonalTables.carrier(5.0) / ZonalTables.carrier(45.0), Atmosphere.tropicGate(Math.toRadians(5.0))));
    sb.append("DONE\n");
    PrintStream rep = new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p950_report.txt"), "UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb);
    System.out.println("JAVA_EXIT=0");
  }
}
