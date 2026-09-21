package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.*; import java.util.Locale;

// P919 -- §587：把极地夏季那 4.16 mm/day 拆成【涡动前 / 涡动后】，并dump q / tSl / k / wE / depl / beta。
//   20 次 mmPerDay 调用 ⇒ 秒级（Zonal.profile 要 25000 次、960 秒）。
public class P919 {
    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        OceanWiring.onWorld(SEED);
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p919_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        long sd = SimTerrain.seedOf(SEED); int cell = PlateField.PLATE_CELL, GR = Zonal.GRAD, NX = Zonal.NX;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        sb.append("P919: 极地降水的分量拆解（mm/day = DIAG * 86400 * 1000）\n");
        sb.append("  列：k=kappa(0=海) tSl=陆面温(K) q=比湿(g/kg) beta=土壤湿度因子 wE=有效上升(m/s) depl=水汽深度(m)\n");
        sb.append("      pPre=涡动【前】 pPost=涡动【后】 eddyShare=涡动贡献占比\n\n");
        int[] rows = {0, 1, 2, 3, 4, 25, 30, 35, 40};   // 3.6/10.8/18.0/25.2/32.4 N 与 3.6/39.6/75.6/90.0 S
        String[] tag = {"r0   +3.6N", "r1  +10.8N", "r2  +18.0N", "r3  +25.2N", "r4  +32.4N", "r25  -3.6S", "r30 -39.6S", "r35 -75.6S", "r40 -68.4S"};
        double[] ths = {thS, thW};
        String[] ts = {"JJA(theta=0)", "DJF(theta=pi)"};
        for (int ti = 0; ti < 2; ti++) {
            for (int ri = 0; ri < rows.length; ri++) {
                int r = rows[ri];
                int z = (int) ((r + 0.5) / Zonal.NZ * WorldContract.Z_CYCLE);
                double lat = Zonal.latOfRow(r);
                sb.append(String.format(LF, "--- %s  %s  lat=%.2f  z=%d ---%n", ts[ti], tag[ri], lat, z));
                double sPre = 0, sPost = 0;
                for (int c = 0; c < NX; c += 250) {
                    int x = (int) Math.round((c + 0.5) * Zonal.XSPAN / NX);
                    PrecipField.mmPerDay(x, z, sd, cell, ths[ti], GR, true);
                    double[] d = PrecipField.DIAG.get();
                    double pPre = d[8] * 86400.0 * 1000.0, pPost = d[9] * 86400.0 * 1000.0;
                    sPre += pPre; sPost += pPost;
                    sb.append(String.format(LF, "    x=%9d  k=%.3f tSl=%7.2f q=%6.3f beta=%4.2f wE=%9.3e depl=%8.3f  pPre=%7.3f pPost=%7.3f  share=%6.1f%%%n",
                        x, d[7], d[6], d[5] * 1000.0, d[13], d[3], d[11], pPre, pPost,
                        pPost > 1e-9 ? 100.0 * (pPost - pPre) / pPost : 0.0));
                }
                int n = (NX + 249) / 250;
                double mPre = sPre / n, mPost = sPost / n;
                sb.append(String.format(LF, "    ⇒ 均值 pPre=%.3f  pPost=%.3f  eddy贡献=%.3f (%.1f%%)%n%n",
                    mPre, mPost, mPost - mPre, mPost > 1e-9 ? 100.0 * (mPost - mPre) / mPost : 0.0));
            }
        }
        sb.append("  判读：若 pPre 已很大 ⇒ 是 wE/q 那条大尺度链；若 eddy贡献 占绝大部分 ⇒ 是 eddyMfc 的换算。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}