package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P491：**⟨陆地占比⟩(φ) 的重新测量** —— P490 用的标准误是错的，结论必须重判。
 *
 * <p><b>为什么要重测</b>：P490 在每条纬线上只取 200 个 x（跨度 20,000 km），
 * 然后用**二项分布**的标准误 <code>sqrt(p(1-p)/N)</code> 判显著，得到「29 个纬度里 21 个超 2SE」。
 * 但 <code>PlateField</code> 的空间相关长度是 <code>PLATE_CELL = 2,400 km</code> ⇒
 * 20,000 km 的窗口里**独立样本只有约 8 个板块**，不是 200 个 ⇒
 * 真实 SE ≈ sqrt(0.33*0.67/8) = **16.6 pt**，而 P490 用的 2SE = **4.8 pt**，小了 7 倍。
 * 用错的 SE 去判显著，当然「到处都是显著」。
 *
 * <p><b>正确做法</b>：把采样分成 B 个**互不重叠、彼此远离**的窗口，
 * 用**窗口均值的离散度**估 SE（batch means / 块自助法）——
 * 它自动把窗口内的空间相关性算进去，不需要假设任何相关模型。
 *
 * <p><b>判读（跑之前写死）</b>：
 * <ul>
 *   <li>(i) 南北配对差 d = p(+φ) - p(-φ)，用 <code>se_d = sqrt(se_N^2 + se_S^2)</code>；
 *       若 <code>|d| &lt;= 2 se_d</code> 的纬度占 &gt;= 90%，且 <code>rms(d/se_d)</code> 与 1 同量级
 *       ⇒ **南北对称** ⇒ 纬向平均温度必须是 |lat| 的偶函数 ⇒ **④(b) 的奇次项不能加**；</li>
 *   <li>(ii) p(φ) 随纬度的变化若都在 2 SE 内 ⇒ ⟨κ⟩ 沿纬度是平的（与 P420 一致）。</li>
 * </ul>
 */
public class P491 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P491] " + s); rep.flush(); System.out.println("[P491] " + s); System.out.flush(); }

    // 12 个互不重叠的窗口，每个长 40,000 km，窗口间距 50,000 km ⇒ 窗口之间隔 10,000 km
    static final int NW = 12;
    static final long WIN_LEN = 40_000_000L;
    static final long WIN_STRIDE = 50_000_000L;
    static final int NP = 2000;          // 每窗口 2000 点，20 km 一个

    /** 返回 {p, se}：p = 12 个窗口均值的平均，se = 窗口均值的标准差 / sqrt(12)。 */
    static double[] landFrac(int z) {
        double[] pw = new double[NW];
        for (int w = 0; w < NW; w++) {
            long x0 = w * WIN_STRIDE;
            int cnt = 0;
            for (int k = 0; k < NP; k++) {
                int x = (int) (x0 + (long) k * WIN_LEN / NP);
                if (PlateField.isLandWithCell(x, z, SD, CELL)) cnt++;
            }
            pw[w] = cnt / (double) NP;
        }
        double m = 0; for (double v : pw) m += v; m /= NW;
        double ss = 0; for (double v : pw) ss += (v - m) * (v - m);
        double sd = Math.sqrt(ss / (NW - 1));
        return new double[]{m, sd / Math.sqrt(NW), sd};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p491_report.txt"), "UTF-8");
        say("P491：⟨陆地占比⟩(φ) 的重新测量（正确的、含空间相关的标准误）");
        say(String.format(LF, "  Z_CYCLE=%d  MAX_D=%d  PLATE_CELL=%d km", WorldContract.Z_CYCLE, WorldContract.MAX_D, CELL / 1000));
        say(String.format(LF, "  采样：%d 个互不重叠窗口 x 每个 %d km x 每窗口 %d 点（20 km 间距）",
                NW, WIN_LEN / 1000, NP));
        say(String.format(LF, "  ⇒ 每条纬线覆盖 x 跨度 %d km、共 %d 点；SE 由**窗口间离散度**给出（batch means）",
                (NW - 1) * WIN_STRIDE / 1000, NW * NP));
        say("");

        // ---- 0) 自检：mirror 的 |lat| 必须相等 ----
        double maxd = 0;
        for (int z = 0; z <= WorldContract.Z_CYCLE; z += 250_000) {
            double a = Math.abs(Math.toDegrees(WorldContract.latOf(z)));
            double b = Math.abs(Math.toDegrees(WorldContract.latOf(WorldContract.Z_CYCLE - z)));
            maxd = Math.max(maxd, Math.abs(a - b));
        }
        say(String.format(LF, "0. 自检 |lat(z)| vs |lat(Z_CYCLE-z)| 最大差 = %.3e 度", maxd));
        say("");

        say("1. 北半球逐纬度 p = 陆地占比（窗口级 SE；z = +|lat|/90*MAX_D）");
        say(String.format(LF, "   %6s %10s %10s %12s %12s %10s", "lat", "p", "SE_pt", "窗间sd_pt", "n_land/win", ""));
        double sumw = 0, sumwp = 0;
        double[] ps = new double[64], ses = new double[64];
        int np = 0;
        for (int i = -17; i <= 17; i++) {
            double lat = i * 5.0;
            int z = (int) Math.round(Math.abs(lat) / 90.0 * WorldContract.MAX_D);
            if (z <= 0 || z >= WorldContract.MAX_D) continue;
            double[] r = landFrac(z);
            double w = Math.cos(Math.toRadians(lat));
            sumw += w; sumwp += w * r[0];
            ps[np] = r[0]; ses[np] = r[1];
            np++;
            say(String.format(LF, "   %+6.1f %9.1f%% %10.2f %12.2f %12.1f",
                    lat, 100 * r[0], 100 * r[1], 100 * r[2], r[0] * NP));
        }
        say("");
        say(String.format(LF, "   ⇒ cos 加权全球 ⟨陆地占比⟩ = %.4f  (P420 测得 ⟨κ⟩ = 0.3280)", sumwp / sumw));
        say("");

        // ---- 2) 南北配对 ----
        say("2. 南北配对（z 与其镜像）：同一纬度、同一批 x");
        say(String.format(LF, "   %6s %10s %10s %10s %9s %9s %s", "lat", "p(北)", "p(南)", "差_pt", "SE差_pt", "d/SE", ""));
        int nSig = 0, nPairs = 0; double ssZ = 0, sumAbsD = 0;
        for (int i = 1; i <= 17; i++) {
            double lat = i * 5.0;
            int zN = (int) Math.round(lat / 90.0 * WorldContract.MAX_D);
            int zS = WorldContract.Z_CYCLE - zN;
            double[] rN = landFrac(zN), rS = landFrac(zS);
            double d = rN[0] - rS[0];
            double sed = Math.hypot(rN[1], rS[1]);
            double zz = d / sed;
            ssZ += zz * zz; sumAbsD += Math.abs(d); nPairs++;
            boolean sig = Math.abs(d) > 2.0 * sed;
            if (sig) nSig++;
            say(String.format(LF, "   %+6.1f %9.1f%% %9.1f%% %+9.1f %9.2f %+9.2f%s",
                    lat, 100 * rN[0], 100 * rS[0], 100 * d, 100 * sed, zz, sig ? "  <== 超 2SE" : ""));
        }
        say("");
        say(String.format(LF, "   ⇒ %d 对里 %d 对超过 2SE（占 %.0f%%）；rms(d/SE) = %.2f（对称时应 ~1）",
                nPairs, nSig, 100.0 * nSig / nPairs, Math.sqrt(ssZ / nPairs)));
        say(String.format(LF, "   ⇒ 平均 |南北差| = %.1f pt", 100 * sumAbsD / nPairs));
        say("");
        // ---- 3) 口径对比：**必须在同一个 N 上比**（E60） ----
        //  P490 的错不是「用了二项 SE」，而是「把 200 个 x 当成 200 个独立样本」。
        //  所以正确的对比是：同一个窗口（N = NP = 2000 点）上，
        //  二项 SE  vs  实测窗口间离散度 ⇒ 比值就是空间相关把 SE 放大的倍数。
        say("3. 口径对比：同一个窗口（N = " + NP + " 点）上，二项 SE vs 实测窗口间离散度");
        double avgP = 0, avgSd = 0;
        for (int i = 0; i < np; i++) { avgP += ps[i]; avgSd += ses[i] * Math.sqrt(NW); }
        avgP /= np; avgSd /= np;
        double naiveW = Math.sqrt(avgP * (1 - avgP) / NP);
        say(String.format(LF, "   平均窗口 p = %.4f", avgP));
        say(String.format(LF, "   二项 SE（把 %d 点当独立）      = %.2f pt", NP, 100 * naiveW));
        say(String.format(LF, "   实测窗口间 SE                   = %.2f pt", 100 * avgSd));
        say(String.format(LF, "   ⇒ 空间相关把 SE 放大 %.1f 倍 ⇒ **有效独立样本 n_eff = %d**（不是 %d）",
                100 * avgSd / (100 * naiveW), (int) Math.round(avgP * (1 - avgP) / (avgSd * avgSd)), NP));
        say(String.format(LF, "   ⇒ P490 用 200 点 + 二项 SE 时，真实 SE 应为 %.1f pt 而它用的是 %.1f pt（小 %.1f 倍）",
                100 * avgSd * Math.sqrt(NP / 200.0), 100 * Math.sqrt(avgP * (1 - avgP) / 200.0),
                avgSd * Math.sqrt(NP / 200.0) / Math.sqrt(avgP * (1 - avgP) / 200.0)));
        say("");
        say("   ★ 判读（跑之前写死）：");
        say("     (i)  |d| <= 2 se_d 的纬度 >= 90% 且 rms(d/SE) 与 1 同量级 ⇒ **南北对称**");
        say("          ⇒ 纬向平均温度必须是 |lat| 的偶函数 ⇒ **④(b) 的奇次项不能加**；");
        say("     (ii) p(φ) 随纬度的变化都在 2 SE 内 ⇒ ⟨κ⟩ 沿纬度是平的（与 P420 一致）。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
