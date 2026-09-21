package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P934 -- (c) 的第一刀：EDDY_VAR 0/1/2 三选，哪一个给出【单极型】剖面？
//   §427 的物理论证：湿静能 h 的辐合自动给出单极型；柱水汽 W 的梯度峰在副热带 => 形状对不上。
//   判据（不看绝对量级，只看【形状】）：X'' 的变号次数。观测是单极型 => X'' 应当只变号一次。
public class P934 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static double D, DY;
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p934_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        D = Math.toRadians(PrecipField.dphiDeg()); DY = D * WorldContract.R_EFF;
        int saved = PrecipField.EDDY_VAR;
        sb.append("P934: EDDY_VAR 三选的形状对照（dphi=").append(PrecipField.dphiDeg())
          .append("  CW_SMOOTH=").append(PrecipField.CW_SMOOTH).append("）\n\n");
        for (int var = 0; var <= 2; var++) {
            PrecipField.EDDY_VAR = var;
            String nm = (var == 0) ? "柱水汽 W（现状）" : (var == 1 ? "近地面比湿 q" : "湿静能 h");
            sb.append("=== EDDY_VAR=").append(var).append("  ").append(nm).append(" ===\n");
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                sb.append(s == 0 ? "  -- JJA --\n" : "  -- DJF --\n");
                sb.append("     lat |     X      |   X\'(1e-3)  |  X\'\'(1e-9)  | MFC(1e-5) | X\'\'变号\n");
                double prevXpp = Double.NaN; int flips = 0;
                StringBuilder rows = new StringBuilder();
                for (double dg = 36; dg <= 88.0001; dg += 2) {
                    double y = Math.toRadians(dg);
                    double x  = PrecipField.eddyScalar(y, th);
                    double xp = PrecipField.eddyDXdy(y, th, D);
                    double xpp = (PrecipField.eddyDXdy(y + D, th, D) - PrecipField.eddyDXdy(y - D, th, D)) / (2.0 * DY);
                    double m  = PrecipField.eddyMfc(y, th);
                    boolean flip = (!Double.isNaN(prevXpp) && ((xpp > 0) != (prevXpp > 0)) && Math.abs(xpp) > 1e-13);
                    if (flip) flips++;
                    prevXpp = xpp;
                    rows.append(String.format(LF, "     %5.1f | %10.4f | %11.5f | %11.5f | %9.5f | %s%n",
                        dg, x, xp * 1e3, xpp * 1e9, m * 1e5, flip ? "<<< 变号" : ""));
                }
                sb.append(rows);
                sb.append("     ⇒ X\'\' 变号次数 = ").append(flips).append("（观测是单极型 => 期望 1 次）\n\n");
            }
        }
        PrecipField.EDDY_VAR = saved;
        sb.append("  判读：变号次数越少越接近单极型；再看 MFC 是否还出现「洞 + 尖峰」。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
