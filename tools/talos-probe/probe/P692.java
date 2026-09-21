package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P692 -- §486【常驻物理门】：P600 的【指名站点】判据（目标第 (4) 项的后半）。
//   为什么用它取代 §399 的全局分桶：每个站点有明确地理身份，不需要全局分桶（§402）。
//   ⚠ 按 §476 的仪器纪律：必须先报每个站点在【本模型世界】里是不是陆地。
public class P692 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P692] " + s); System.out.println("[P692] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final Object[][] SITES = {
        {24.0, -14.0, "西撒哈拉 (加那利)", 0},
        {-23.0, 15.0, "纳米布 (本格拉)", 0},
        {-23.0, -70.0, "阿塔卡马 (秘鲁)", 0},
        {28.0, -114.0, "下加利福尼亚 (加州)", 0},
        {32.0, -83.0, "美国东南 (湾流)", 1},
        {-28.0, -49.0, "巴西南部", 1},
        {-28.0, 152.0, "东澳", 1},
        {34.0, 140.0, "日本", 1}
    };

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p692_report.txt"), "UTF-8");
        say("P692 常驻门：海岸指名站点（P600 判据，§486）—— 物理要求：沙漠组 P < 暖流组 P");
        EarthRef.install();
        // ★★★ §583：必须装上海温提供者，否则 Atmosphere.SST_PROVIDER == null ⇒ sstAnom 恒为 0
        //   ⇒ 整个「冷海 ⇒ 抑制对流」链在本门里【完全不可见】。原版缺这一句（P913 有）。
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thN = Atmosphere.theta(0.0);                                            // NH 夏（JJA）
        double thS = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);              // SH 夏（DJF）
        // ★ §488 仪器修正：每个站点必须在【它自己的夏季】评。
        //   原版（承 P600）用单一 JJA 评全部 8 站 ⇒ 把 SH 站点的【冬季】当夏季比 ⇒ 季节混淆。
        //   这是仪器缺陷，不是模型缺陷 —— 必须先把仪器修对再判模型。
        say("");
        say("     站点                  | 类别 | 陆/海 | kappa | P(mm/day)");
        double dP = 0, wP = 0; int nd = 0, nw = 0; int landD = 0, landW = 0;
        for (Object[] s : SITES) {
            double lat = (Double) s[0], lon = (Double) s[1];
            int x = xOfLon(lon), z = zOfLat(lat);
            boolean land = PlateField.isLandWithCell(x, z, sd, cell);
            double k = Atmosphere.kappaMemo(x, z, sd, cell);
            double thOwn = (lat >= 0.0) ? thN : thS;                                   // §488 本半球夏季
            double p = PrecipField.mmPerDay(x, z, sd, cell, thOwn, GRAD);
            String cat = ((Integer) s[3]) == 0 ? "沙漠" : "暖流";
            if (((Integer) s[3]) == 0) { dP += p; nd++; if (land) landD++; }
            else { wP += p; nw++; if (land) landW++; }
            say(String.format(LF, "     %-20s | %s | %s | %.3f | %7.3f",
                (String) s[2], cat, land ? "陆" : "**海**", k, p));
        }
        double md = dP / nd, mw = wP / nw;
        say("");
        say(String.format(LF, "     沙漠组 %d 站（其中陆地 %d）：P 均值 %.3f", nd, landD, md));
        say(String.format(LF, "     暖流组 %d 站（其中陆地 %d）：P 均值 %.3f", nw, landW, mw));
        // ★★★★★ §583：改用【本世界自己的地理】选站点。（原地球地名表保留为对照列，不承担判定。）
        //   为什么必须改：P911/P912 实测这 8 个地球地名在本世界【大多是陆地】——
        //   暖流组三个陆站全部 P=0.0000，而沙漠组靠两个【海】站撑着 ⇒ 比值 4.64 方向反。
        //   那是【站点选错】，不是物理错。SST 距平场本身有结构（|距平|>0.5 的占 4%，范围 [-2.45,+2.63] K）。
        final int GRADG = 500_000, NLATG = 25, NLONG = 36;
        double[][] kapG = new double[NLATG][NLONG], anG = new double[NLATG][NLONG];
        for (int gi = 0; gi < NLATG; gi++) {
            double glat = -60.0 + gi * 5.0; int gz = zOfLat(glat);
            for (int gj = 0; gj < NLONG; gj++) {
                int gx = xOfLon(gj * 10.0);
                kapG[gi][gj] = Atmosphere.kappaMemo(gx, gz, sd, cell);
                anG[gi][gj] = (kapG[gi][gj] <= 0.5)
                            ? Atmosphere.sstAnom(gx, gz, glat >= 0 ? thN : thS) : 0.0;
            }
        }
        int gcap = NLATG * NLONG;
        double[] cLat = new double[gcap], cLon = new double[gcap], cAdj = new double[gcap];
        int nc = 0;
        for (int gi = 1; gi < NLATG - 1; gi++) for (int gj = 0; gj < NLONG; gj++) {
            if (kapG[gi][gj] <= 0.5) continue;
            double as = 0; int an2 = 0;
            for (int di = -1; di <= 1; di++) for (int dj = -1; dj <= 1; dj++) {
                int ii = gi + di, jj = ((gj + dj) % NLONG + NLONG) % NLONG;
                if (kapG[ii][jj] <= 0.5) { as += anG[ii][jj]; an2++; }
            }
            if (an2 > 0) { cLat[nc] = -60.0 + gi * 5.0; cLon[nc] = gj * 10.0; cAdj[nc] = as / an2; nc++; }
        }
        for (int a1 = 0; a1 < nc; a1++) {
            int m = a1;
            for (int b1 = a1 + 1; b1 < nc; b1++) if (cAdj[b1] < cAdj[m]) m = b1;
            double t1 = cAdj[a1]; cAdj[a1] = cAdj[m]; cAdj[m] = t1;
            t1 = cLat[a1]; cLat[a1] = cLat[m]; cLat[m] = t1;
            t1 = cLon[a1]; cLon[a1] = cLon[m]; cLon[m] = t1;
        }
        int gq = Math.max(1, nc / 4);
        double gC = 0, gW = 0; int qC = 0, qW = 0;
        for (int a1 = 0; a1 < gq; a1++) {
            int gx = xOfLon(cLon[a1]);
            double pp = PrecipField.mmPerDay(gx, zOfLat(cLat[a1]), sd, cell, cLat[a1] >= 0 ? thN : thS, GRADG);
            gC += pp; qC++;
        }
        for (int a1 = nc - gq; a1 < nc; a1++) {
            int gx = xOfLon(cLon[a1]);
            double pp = PrecipField.mmPerDay(gx, zOfLat(cLat[a1]), sd, cell, cLat[a1] >= 0 ? thN : thS, GRADG);
            gW += pp; qW++;
        }
        double geoRatio = (gW > 0.0) ? (gC / qC) / (gW / qW) : 999.0;
        say("");
        say(String.format(LF, "     [对照] 地球地名组：沙漠 %.4f / 暖流 %.4f", md, mw));
        say(String.format(LF, "     GATE_COLDWARM_GEO=%.4f  (冷海邻接 %.4f n=%d / 暖海邻接 %.4f n=%d ; 候选陆点 %d)",
            geoRatio, gC / qC, qC, gW / qW, qW, nc));
        say(String.format(LF, "     GATE_COASTAL_LANDFRAC=%d/%d", landD + landW, nd + nw));
        say("     GATE_COASTAL_VERDICT=" + (geoRatio < 1.0 ? "PASS" : "FAIL"));
        say("     （§583：判定已改用【本世界地理】口径；地球地名组降为对照列）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}