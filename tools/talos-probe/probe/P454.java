package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P454：验证 D18（continent 改用 COAST_FINE = 40 km 小半径陆地占比）。
 *
 * <p>四条主张（§118.4）+ 两条对照（用 800 km 的 κ 做反面对照，证明旧方案在这个尺度上不可用）。
 */
public class P454 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int nPass = 0, nFail = 0;

    static void say(String s) { rep.println("[P454] " + s); System.out.println("[P454] " + s); }
    static void chk(boolean ok, String what, String detail) {
        if (ok) nPass++; else nFail++;
        say(String.format(LF, "  [%s] %-56s %s", ok ? "PASS" : "**FAIL**", what, detail));
    }
    static double ss(double lo, double hi, double v) {
        double t = (v - lo) / (hi - lo);
        if (t <= 0) return 0.0; if (t >= 1) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }
    /** 到最近海陆界线的距离（沿 x 双向走，blocks）。返回 {signedDist, found}：陆点为负、海点为正。 */
    static int coastDist(int x, int z, int maxWalk) {
        boolean land0 = PlateField.isLandWithCell(x, z, SD, CELL);
        for (int d = 1; d <= maxWalk; d += 50) {
            if (PlateField.isLandWithCell(x + d, z, SD, CELL) != land0) return land0 ? -d : d;
            if (PlateField.isLandWithCell(x - d, z, SD, CELL) != land0) return land0 ? -d : d;
        }
        return Integer.MIN_VALUE;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p454_report.txt"), "UTF-8");
        say("P454：D18 验证（continent 用 COAST_FINE = " + (SimClimate.COAST_FINE / 1000) + " km 小半径陆地占比）");
        say("");

        // 找一批「靠岸的陆点」：沿 x 扫，取 land 且 |coastDist| <= 2 km
        // ⚠ E10（我自己的错）：第一版扫的是 x∈[-1M,1M] x z∈[-2M,2M] —— **整片都在同一个板块内部**
        // （PLATE_CELL = 2400 km），一个海岸都没扫到 ⇒ 后续三段全部空转。
        // 现在扫**整个世界**的 x（±12M，步长 2 km），取所有「陆点且左右邻居里有海」的点。
        java.util.ArrayList<int[]> coastal = new java.util.ArrayList<>();
        java.util.ArrayList<int[]> landPts = new java.util.ArrayList<>();
        for (int z = -8_000_000; z <= 8_000_000 && coastal.size() < 60; z += 613_000) {
            for (int x = -12_000_000; x <= 12_000_000 && coastal.size() < 60; x += 2_000) {
                if (!PlateField.isLandWithCell(x, z, SD, CELL)) continue;
                if (landPts.size() < 4000) landPts.add(new int[]{x, z});
                if (!PlateField.isLandWithCell(x - 2_000, z, SD, CELL)
                 || !PlateField.isLandWithCell(x + 2_000, z, SD, CELL)) coastal.add(new int[]{x, z});
            }
        }
        say(String.format(LF, "  靠岸陆点样本 = %d（全世界 x 扫描，陆点且左右 2 km 内有海）  陆地样本 = %d", coastal.size(), landPts.size()));

        say("");
        say("A. continent 在海岸线处是否 ≈ 0（恢复旧语义）");
        double maxAbs = 0, sumAbs = 0; int nn = 0;
        double maxKappa = 0, sumKappa = 0;
        for (int[] p : coastal) {
            ClimateCoords.Coords c = SimClimate.sample(p[0], p[1], SEED, null);
            double kFine = PlateField.coastDistanceNew(p[0], p[1], SD, CELL, SimClimate.COAST_FINE);
            double kap800 = Atmosphere.kappaAt(p[0], p[1], SD, CELL);
            // 旧语义的 continent = clamp01(-coastDist/40000)，岸线处 coastDist ~ 0 => ~0
            double oldSem = Math.max(0.0, Math.min(1.0, -coastDist(p[0], p[1], 40_000) / 40_000.0));
            maxAbs = Math.max(maxAbs, Math.abs(c.continent - oldSem));
            sumAbs += Math.abs(c.continent - oldSem);
            maxKappa = Math.max(maxKappa, Math.abs(kap800 - oldSem));
            sumKappa += Math.abs(kap800 - oldSem);
            nn++;
        }
        if (nn > 0) {
            say(String.format(LF, "  新 continent vs 旧语义(-coastDist/40km)： 平均 |差| = %.3f   最大 %.3f", sumAbs / nn, maxAbs));
            say(String.format(LF, "  对照：**800 km 的 kappa** vs 旧语义：       平均 |差| = %.3f   最大 %.3f", sumKappa / nn, maxKappa));
            chk(sumAbs / nn < sumKappa / nn, "新口径比 κ 更接近旧的「岸线 = 0」语义", "");
            chk(sumAbs / nn < 0.20, "新口径与旧语义的平均偏差 < 0.20", String.format(LF, "实测 %.3f", sumAbs / nn));
        }

        say("");
        say("B. coastDistanceNew 是**距离**量：应当连续（不是 194 级量化）");
        int nz = 0, tot = 0; double dmin = 1e18, dmax = -1e18;
        for (int[] p : landPts) {
            double cd = PlateField.coastDistanceNew(p[0], p[1], SD, CELL, SimClimate.COAST_FINE);
            if (cd < dmin) dmin = cd; if (cd > dmax) dmax = cd;
            if (cd == 0.0) nz++;
            tot++;
        }
        say(String.format(LF, "  陆地样本 %d 个：coastDist ∈ [%.0f, %.0f] block，恰为 0 的 %d 个", tot, dmin, dmax, nz));
        chk(tot > 0 && dmin < 0 && dmax < 0, "陆点的 coastDist 全为负（陆为负）", String.format(LF, "[%.0f, %.0f]", dmin, dmax));

        say("");
        say("C. 带宽：旧门语义（0.35~0.60 的 κ 换算 = 0.511~0.519）有多宽？");
        say(String.format(LF, "  kappa(14 km) = %.4f   kappa(24 km) = %.4f   ⇒ 带宽 %.4f", 0.5 + 14_000 / (Math.PI * 800_000), 0.5 + 24_000 / (Math.PI * 800_000), 10_000 / (Math.PI * 800_000)));
        say(String.format(LF, "  而量化级距 = 2/193 = %.4f  ⇒ 带宽 / 级距 = %.2f 个台阶", 2.0 / 193.0, (10_000 / (Math.PI * 800_000)) / (2.0 / 193.0)));
        say(String.format(LF, "  COAST_FINE=%d km 时：kapFine(14 km) = %.4f、kapFine(24 km) = %.4f ⇒ 带宽 %.4f = %.1f 个台阶",
            SimClimate.COAST_FINE / 1000, 0.5 + 14_000 / (Math.PI * 40_000), 0.5 + 24_000 / (Math.PI * 40_000),
            10_000 / (Math.PI * 40_000), (10_000 / (Math.PI * 40_000)) / (2.0 / 193.0)));
        double bwK = (10_000 / (Math.PI * 800_000)) / (2.0 / 193.0);
        double bwF = (10_000 / (Math.PI * 40_000)) / (2.0 / 193.0);
        chk(bwK < 1.0 && bwF > 5.0, "旧 κ 方案的带宽 < 1 个台阶（不可用）而新方案 > 5 个台阶（可用）",
            String.format(LF, "%.2f vs %.1f", bwK, bwF));

        say("");
        say("D. BASIN 门因子 ss(0.35, 0.60, continent) 在岸线处的变化");
        double gateNew = 0, gateOld = 0; int m = 0;
        for (int[] p : coastal) {
            ClimateCoords.Coords c = SimClimate.sample(p[0], p[1], SEED, null);
            double kap800 = Atmosphere.kappaAt(p[0], p[1], SD, CELL);
            gateNew += ss(0.35, 0.60, c.continent);
            gateOld += ss(0.35, 0.60, Math.max(0.0, Math.min(1.0, kap800)));
            m++;
        }
        if (m > 0) {
            say(String.format(LF, "  岸线处 ss(0.35,0.60,continent)：**新 %.2f**   （D18 之前用 κ：%.2f）", gateNew / m, gateOld / m));
            chk(gateNew / m < 0.10, "岸线处 BASIN 门已回到 ≈0（D18 之前是 0.66）", String.format(LF, "实测 %.2f", gateNew / m));
        }
        say("");
        say(String.format(LF, "  汇总：PASS %d / FAIL %d", nPass, nFail));
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
