package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P476：**D58 的复现与验证** —— 改 OceanField 的旋钮，SimClimate 的瓦片**会不会**失效？
 *
 * <p>预期（读码得到的假设）：
 * <ul>
 *   <li>{@code OceanField.resetIfStale} 的指纹**含** ROW_H/A_H/GRAD/JET_RANGE_RD/ROWS ⇒
 *       OceanField 自己的海盆缓存**会**失效并重解（SST' 真的变了）；</li>
 *   <li>但 {@code SimClimate.configStamp()} **不含** OceanField 的任何参数 ⇒
 *       SimClimate 的瓦片**不会**失效 ⇒ 旧瓦片（用旧 SST' 算的）被继续使用。</li>
 * </ul>
 *
 * <p>判据（跑之前写死）：
 * <ol>
 *   <li>v2 == v1  ⇒ 复现 D58（改旋钮后不清缓存，拿到的还是旧瓦片）；</li>
 *   <li>v3 != v1  ⇒ 坐实（清缓存后底层确实变了）；</li>
 *   <li>修好之后应当反过来：<b>v2 == v3 != v1</b>。</li>
 * </ol>
 */
public class P476 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P476] " + s); rep.flush(); System.out.println("[P476] " + s); System.out.flush(); }

    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; say(String.format(LF, "   [OK]   %s   %s", what, detail)); }
        else { fail++; say(String.format(LF, "   [FAIL] %s   %s", what, detail)); }
    }

    /** 同一点上取「SimClimate 用到的 SST'」与「注入口的真值」。 */
    static double[] probePoint(int x, int z) {
        return new double[]{
            SimClimate.surfaceTempK(x, z, SEED),
            Atmosphere.sstAnom(x, z),
            Atmosphere.kappaAt(x, z, com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED),
                com.EyeOfHarmonyBuffer.sim.litho.PlateField.PLATE_CELL)
        };
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p476_report.txt"), "UTF-8");
        say("P476：D58 —— 改 OceanField 旋钮，SimClimate 瓦片会不会失效？");
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "  装线：installedSeed=%d  ROW_H=%.0f  A_H=%.1e  JET_RANGE_RD=%.1f",
            OceanField.installedSeed(), OceanField.ROW_H, OceanField.A_H, OceanField.JET_RANGE_RD));
        say("");

        int z30 = WorldContract.zOfLat(30.0);
        int x30 = -2_000_000;                       // P473 找出的副热带暖舌点
        say(String.format(LF, "测点 x=%d km  z=%d（lat %.2f）", x30 / 1000, z30,
            Math.toDegrees(WorldContract.latOf(z30, ZC))));
        say("");

        // ---- 第 1 组：改 A_H（10 倍） ----
        say("A. 改 A_H：1.9e4 -> 2.0e5（Munk 层厚 delta ∝ A_H^(1/3)，预期 SST' 明显变）");
        double[] v1 = probePoint(x30, z30);
        say(String.format(LF, "   v1（原参数）：surfaceTempK=%.6f  SST'=%.6f", v1[0], v1[1]));
        double aH0 = OceanField.A_H;
        OceanField.A_H = 2.0e5;
        double[] v2 = probePoint(x30, z30);
        say(String.format(LF, "   v2（改后、**不清缓存**）：surfaceTempK=%.6f  SST'=%.6f", v2[0], v2[1]));
        SimClimate.clearCache();
        double[] v3 = probePoint(x30, z30);
        say(String.format(LF, "   v3（改后、清缓存）：surfaceTempK=%.6f  SST'=%.6f", v3[0], v3[1]));
        check("底层真的变了（SST' v3 != v1）", v3[1] != v1[1], String.format(LF, "%.6f vs %.6f（差 %.3e）", v3[1], v1[1], v3[1] - v1[1]));
        check("瓦片在不清缓存时**跟上了**底层（v2 == v3）  ← D58 的判据", v2[0] == v3[0],
            String.format(LF, "v2=%.6f  v3=%.6f  v1=%.6f", v2[0], v3[0], v1[0]));
        // ⚠ 这一条是**诊断**，不是判据：修好 D58 之前它应当是 true（缺陷复现），
        // 修好之后应当是 false。把它算进 pass/fail 会让「修好了」看起来像「多了一个失败」。
        // ⇒ 只报，不计分。
        say(String.format(LF, "   [诊断] 不清缓存时拿到旧瓦片（v2 == v1）？ %s  ⇒ %s",
            v2[0] == v1[0] ? "是" : "否",
            v2[0] == v1[0] ? "**D58 复现**（缺陷在）" : "D58 已修（瓦片跟上了底层）"));
        OceanField.A_H = aH0;
        SimClimate.clearCache();
        say("");

        // ---- 第 2 组：改 JET_RANGE_RD（关掉东边界急流） ----
        // ⚠⚠ E40 的修正（纪律 29）：JET_RANGE_RD 只作用在**东边界** R_d 范围内那一段
        // （solveRow 里 if (d > 0 && d < JET_RANGE_RD * rd)，d = eastX - x）。
        // 我第一版拿 x=-2000 km（**西边界暖舌**）当测点 ⇒ 它在作用域**外** ⇒
        // 「SST' 没变」是**零信息**，却被判成 FAIL。现在测点改成东端内侧 30 km，
        // 并且**把「在作用域内」本身写成一条断言**。
        say("B. 改 JET_RANGE_RD：4.0 -> 0.0（关掉东边界急流项）");
        int[] spB = OceanField.spanOf(0, z30, SEED);
        if (spB == null) {
            say("   +30 行没有海盆，B 段跳过");
        } else {
            double f30 = WorldContract.coriolis(WorldContract.latOf(z30, ZC));
            double rd = CoastalLayer.rossbyRadius(f30);
            int xJet = spB[2] - 30_000;                       // 东端内侧 30 km
            double rangeM = OceanField.JET_RANGE_RD * rd;     // 急流作用范围（m）
            double distM = spB[2] - xJet;
            say(String.format(LF, "   海盆 [%d, %d] km；R_d = %.1f km；急流范围 = %.0f x R_d = %.1f km",
                spB[1] / 1000, spB[2] / 1000, rd / 1000.0, OceanField.JET_RANGE_RD, rangeM / 1000.0));
            say(String.format(LF, "   测点 = 东端内侧 30 km（x %d km，距东端 %.1f km）", xJet / 1000, distM / 1000.0));
            check("测点落在 JET_RANGE_RD 的**作用域内**（纪律 29）", distM < rangeM,
                String.format(LF, "%.1f km < %.1f km", distM / 1000.0, rangeM / 1000.0));
            double[] w1 = probePoint(xJet, z30);
            double jr0 = OceanField.JET_RANGE_RD;
            OceanField.JET_RANGE_RD = 0.0;
            double[] w2 = probePoint(xJet, z30);
            SimClimate.clearCache();
            double[] w3 = probePoint(xJet, z30);
            say(String.format(LF, "   w1=%.6f (SST' %.6f)   w2=%.6f (SST' %.6f)   w3=%.6f (SST' %.6f)",
                w1[0], w1[1], w2[0], w2[1], w3[0], w3[1]));
            check("底层真的变了（SST' w3 != w1）", w3[1] != w1[1], String.format(LF, "%.6f vs %.6f（差 %.3e）", w3[1], w1[1], w3[1] - w1[1]));
            check("瓦片在不清缓存时**跟上了**底层（w2 == w3）  ← D58 的判据", w2[0] == w3[0],
                String.format(LF, "w2=%.6f  w3=%.6f  w1=%.6f", w2[0], w3[0], w1[0]));
            OceanField.JET_RANGE_RD = jr0;
            SimClimate.clearCache();
        }
        say("");

        // ---- 收尾：恢复并确认回到 v1 ----
        say("C. 恢复参数后必须逐位回到 v1");
        double[] back = probePoint(x30, z30);
        check("恢复后 surfaceTempK 逐位 == v1", back[0] == v1[0], String.format(LF, "%.6f vs %.6f", back[0], v1[0]));
        check("恢复后 SST' 逐位 == v1", back[1] == v1[1], String.format(LF, "%.6f vs %.6f", back[1], v1[1]));
        say("");
        say(String.format(LF, "   ⇒ 断言通过 %d，失败 %d", pass, fail));
        say("   【判据说明】A/B 两段的「瓦片跟上了底层（v2 == v3 / w2 == w3）」是 D58 的判据；");
        say("     「不清缓存时拿到旧瓦片」是**诊断**（复现缺陷用），不计分 —— 修好之后它应当为「否」。");
        say(String.format(LF, "   ⇒ reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只读+改自己的探针侧旋钮，退出前已全部恢复；未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
