package probe;
import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.*;
public class P954 {
  public static void main(String[] a) throws Exception {
    StringBuilder sb=new StringBuilder();
    double g0=Atmosphere.gammaOf(0.0), g1=Atmosphere.gammaOf(1.0);
    double gO=Atmosphere.gammaOf(0.328);
    sb.append(String.format(Locale.ROOT,"P954: Ekman 分母的量级   GAMMA_OCN=%.4e  GAMMA_LND=%.4e%n",g0,g1));
    sb.append(String.format(Locale.ROOT,"  gammaOf(0)=%.4e  gammaOf(0.328)=%.4e  gammaOf(1)=%.4e%n%n",g0,gO,g1));
    sb.append(String.format(Locale.ROOT,"  OMEGA=%.6e  R_EFF=%.3e%n%n",WorldContract.OMEGA,WorldContract.R_EFF));
    sb.append("   lat |      f        | den_ocn=rho(g^2+f^2)  | f/gamma |  (f/gamma)^2 | 分母比(相对45度)\n");
    double denO45=0;
    double[] dens=new double[19];
    for(int i=0;i<=18;i++){
      double d=i*5.0, lat=Math.toRadians(d);
      double f=WorldContract.coriolis(lat);
      double den=1.225*(g0*g0+f*f);
      dens[i]=den;
      if(i==9) denO45=den;
    }
    for(int i=0;i<=18;i++){
      double d=i*5.0, lat=Math.toRadians(d);
      double f=WorldContract.coriolis(lat);
      double den=dens[i];
      sb.append(String.format(Locale.ROOT,"  %4.0f | %+.4e | %.4e | %8.3f | %11.2f | %10.3f%n",
          d, f, den, f/g0, (f/g0)*(f/g0), denO45/den));
    }
    sb.append("\n  列义: 「分母比」= den(45)/den(phi) = 同一个 p 梯度在 phi 处产生的 v 相对 45 度的放大倍数\n");
    sb.append("DONE\n");
    PrintStream rep=new PrintStream(new File("K:/moder/EyeOfHarmonyBuffer/build/eoh_probe/mtn/p954_report.txt"),"UTF-8");
    rep.print(sb); rep.flush(); System.out.print(sb); System.out.println("JAVA_EXIT=0");
  }
}
