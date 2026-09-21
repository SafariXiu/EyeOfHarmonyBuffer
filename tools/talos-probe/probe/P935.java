package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P935 -- (c) 的真门槛：把【源场】从观测表换成模型自己的场，形状会不会变单极型？
//   四组合 = (ZONAL_SL_FROM_TABLE) x (COL_WATER_FROM_TABLE)，各数 X'' 的变号次数。
//   期望：观测的涡动 MFC 是单极型 => X'' 变号 1 次。
public class P935 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static double D, DY;
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p935_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        D = Math.toRadians(PrecipField.dphiDeg()); DY = D * WorldContract.R_EFF;
        boolean sZ = PrecipField.ZONAL_SL_FROM_TABLE, sC = PrecipField.COL_WATER_FROM_TABLE;
        int sV = PrecipField.EDDY_VAR, sG = PrecipField.EDDY_GRAD;
        sb.append("P935: 源场外生 vs 涌现 —— X\'\' 的变号次数（期望 1 = 单极型）\n\n");
        sb.append("  ZONAL_SL_FROM_TABLE  COL_WATER_FROM_TABLE  EDDY_VAR | JJA变号 DJF变号 | JJA 的 MFC 是否仍有洞\n");
        for (int iz = 0; iz < 2; iz++) for (int ic = 0; ic < 2; ic++) for (int iv = 0; iv <= 2; iv += 2) {
            PrecipField.ZONAL_SL_FROM_TABLE = (iz == 1);
            PrecipField.COL_WATER_FROM_TABLE = (ic == 1);
            PrecipField.EDDY_VAR = iv;
            int fj = flips(thS), fd = flips(thW);
            boolean hole = hasHole(thS);
            sb.append(String.format(LF, "  %-20s %-21s %-8d | %6d %7d | %s%n",
                (iz == 1 ? "true(观测表)" : "false(涌现)"), (ic == 1 ? "true(观测表)" : "false(涌现)"), iv,
                fj, fd, hole ? "**是（有负瓣）**" : "否"));
        }
        PrecipField.ZONAL_SL_FROM_TABLE = sZ; PrecipField.COL_WATER_FROM_TABLE = sC;
        PrecipField.EDDY_VAR = sV; PrecipField.EDDY_GRAD = sG;
        sb.append("\n  判读：变号 1 次且无负瓣 = 单极型（与观测同形）。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static int flips(double th) {
        double prev = Double.NaN; int f = 0;
        for (double dg = 36; dg <= 88.0001; dg += 1.0) {
            double y = Math.toRadians(dg);
            double xpp = (PrecipField.eddyDXdy(y + D, th, D) - PrecipField.eddyDXdy(y - D, th, D)) / (2.0 * DY);
            if (!Double.isNaN(prev) && Math.abs(xpp) > 1e-13 && Math.abs(prev) > 1e-13 && ((xpp > 0) != (prev > 0))) f++;
            if (Math.abs(xpp) > 1e-13) prev = xpp;
        }
        return f;
    }
    static boolean hasHole(double th) {
        int neg = 0;
        for (double dg = 50; dg <= 70.0001; dg += 1.0) {
            if (PrecipField.eddyMfc(Math.toRadians(dg), th) < 0) neg++;
        }
        return neg >= 3;
    }
}
