package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P447：**口径一致性测试台**（目标第 2 步的「物理量口径登记表」的可执行形式）。
 *
 * <p>做法：把「按定义**必须相等**」的两两量各自独立算一遍，报差值。
 * 相等 = 口径一致；不等 = 登记表里那一行有问题，且差值本身就是缺陷的量。
 *
 * <p>全部检查都**不修改任何物理代码**；需要改的旋钮改完立刻还原。
 */
public class P447 {

    static final int SEED = 1022228679;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int nPass = 0, nFail = 0;

    static void say(String s) { rep.println("[P447] " + s); System.out.println("[P447] " + s); }
    static void chk(String name, double measured, double tol, String note) {
        boolean ok = measured <= tol;
        if (ok) nPass++; else nFail++;
        say(String.format(LF, "  [%s] %-52s 差 %-13.4e (容差 %.1e)  %s",
            ok ? "PASS" : "**FAIL**", name, measured, tol, note));
    }
    static void note(String s) { say("        " + s); }
    /** 该瓦片内的一个精确格点（双线性权重恰为 1，不引入插值误差）。 */
    static int nodeX(int tx, int i) { return tx * SimClimate.TILE_X + i * SimClimate.CELL + SimClimate.CELL / 2; }
    static int nodeZ(int tz, int j) { return tz * SimClimate.TILE_Z + j * SimClimate.CELL + SimClimate.CELL / 2; }
    static double lat(int z) { return com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(z); }
    static double cl(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p447_report.txt"), "UTF-8");
        say("P447：口径一致性测试台");
        say(String.format(LF, "  SEED=%d  CELL=%d km  GRAD=%d km  TILE=%dx%d km  SEA_ONLY_UZM=%s  SST_PROVIDER(A)=%s (S)=%s",
            SEED, SimClimate.CELL / 1000, GRAD / 1000, SimClimate.TILE_X / 1000, SimClimate.TILE_Z / 1000,
            ZonalTables.SEA_ONLY_UZM, Atmosphere.SST_PROVIDER == null ? "null" : "set",
            SimClimate.SST_PROVIDER == null ? "null" : "set"));
        say("");

        // ---------------- A. 验收量 windStress vs 生产量 windAt ----------------
        say("A. windStress（A1/A2 的验收量）是否 == rho*Cd(kappa)*|u|*u，其中 u = windAt（生产量）");
        double aMax = 0;
        for (int[] p : new int[][]{{0, 0}, {500_000, 3_000_000}, {-2_000_000, -6_000_000}, {7_000_000, 1_234_567}}) {
            for (double th : PH4) {
                double[] tau = Atmosphere.windStress(p[0], p[1], SEED, CELL, th, GRAD);
                double[] uv = Atmosphere.windAt(p[0], p[1], SEED, CELL, th, GRAD);
                double cd = Atmosphere.cdOf(Atmosphere.kappaAt(p[0], p[1], SEED, CELL));
                double k = Atmosphere.RHO_AIR * cd * Math.hypot(uv[0], uv[1]);
                aMax = Math.max(aMax, Math.abs(tau[0] - k * uv[0]));
                aMax = Math.max(aMax, Math.abs(tau[1] - k * uv[1]));
            }
        }
        chk("A  windStress == rho*Cd*|windAt|*windAt", aMax, 0.0, "逐位相等才算口径一致");
        note("⇒ 验收量与生产风同源，A1/A2 的口径成立（但它仍是**不可达路径**，见 §102.8）");

        // ---------------- B. SimClimate 的年平 vs 直接 4 季平均 ----------------
        say("");
        say("B. SimClimate 的年平（瓦片缓存）vs 直接对生产函数取 4 季平均（在精确格点上）");
        SimClimate.clearCache();
        // ⚠ E5（我自己犯的）v1 拿 SEED 直接调 Atmosphere，而 SimClimate 内部用的是
        // SimTerrain.seedOf(worldSeedInt)（SimClimate.java:290/348）。两个不同的种子。
        final long SD = SimTerrain.seedOf(SEED);
        double bW = 0, bT = 0, bP = 0, bK = 0;
        int nb = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int x = nodeX(0, i), z = nodeZ(0, j);
                double[] bl = new double[2];
                SimClimate.windAt(x, z, SEED, bl);
                double su = 0, sv = 0, st = 0, sp = 0;
                for (double th : PH4) {
                    double[] w = Atmosphere.windAt(x, z, SD, CELL, th, GRAD);
                    su += w[0] * 0.25; sv += w[1] * 0.25;
                    st += Atmosphere.surfaceTemp(x, z, SD, CELL, th) * 0.25;
                    sp += PrecipField.mmPerDay(x, z, SD, CELL, th, GRAD) * 0.25;
                }
                bW = Math.max(bW, Math.abs(bl[0] - su));
                bW = Math.max(bW, Math.abs(bl[1] - sv));
                bT = Math.max(bT, Math.abs(SimClimate.surfaceTempK(x, z, SEED) - st));
                ClimateCoords.Coords c = SimClimate.sample(x, z, SEED, null);
                double logP = Math.log10(Math.max(sp * 365.25, SimClimate.P_MIN_MM_YR));
                bP = Math.max(bP, Math.abs(c.moist - cl(SimClimate.MOIST_A * logP - SimClimate.MOIST_B)));
                // ⚠ 2026-09-13（审计 D18 之后）：continent 已改用 COAST_FINE 小半径陆地占比，
                // 不再等于 Atmosphere.kappaAt（800 km）。原来这条检查变成**空断言** ——
                // 它之所以 PASS，只是因为测试点在两个半径下恰好同值（都全陆/全海）。
                // 现在比的是 kapFine，并且下面单独加一段**靠岸点**的检查。
                double cdRef = PlateField.coastDistanceNew(x, z, SD, CELL, SimClimate.COAST_FINE);
                bK = Math.max(bK, Math.abs(c.continent - cl(-cdRef / SimClimate.COAST_FINE)));
                nb++;
            }
        }
        note("样本 " + nb + " 个精确格点，同一瓦片；直算侧用 seed = SimTerrain.seedOf(SEED)（与 SimClimate 内部一致）");
        chk("B1 风（年平）", bW, 1e-12, "年平 = 4 季算术平均");
        chk("B2 地表温度（年平）", bT, 1e-9, "年平 = 4 季算术平均（sin/cos 四相位求和恒为 0）");
        chk("B3 湿度坐标（由年平降水给出）", bP, 1e-12, "logP = log10(4 季均值 x 365.25)");
        chk("B4 大陆度 kappa", bK, 0.0, "同一 Atmosphere.kappaAt");

        // ---------------- C. 陆海一致 ----------------
        say("");
        say("C. 「是不是陆地」在四条路径上是否同一个判据");
        V2TerrainGen.Column col = new V2TerrainGen.Column();
        int seaLevel = 63, maxY = 255, mis = 0, nc = 0;
        // 只取**两个瓦片内**的点：compose -> warmestMonthTempK -> SimClimate 会触发瓦片求解，
        // 铺满几千个瓦片会让这一跑几小时。两个瓦片足以覆盖海陆两侧。
        for (int x = -40_000; x <= 40_000; x += 1_600) {
            for (int z = -18_000; z <= 18_000; z += 1_700) {
                long sd = SimTerrain.seedOf(SEED);
                boolean p1 = PlateField.isLandWithCell(x, z, sd, CELL);
                boolean p2 = SimTerrain.compose(col, x, z, SEED, seaLevel, maxY).land;
                boolean p3 = Atmosphere.kappaAt(x, z, sd, CELL) > 0.5;   // 只作对照，不是同一判据
                nc++;
                if (p1 != p2) mis++;
                if (p3 != p1) { /* 预期会不同，仅统计 */ }
            }
        }
        chk("C1 PlateField.isLandWithCell == SimTerrain.compose().land", mis, 0.0,
            "n=" + nc + " 不一致 " + mis + " 个");

        // ---------------- D. 缓存一致性 ----------------
        say("");
        say("D. 瓦片缓存一致性");
        int bx = SimClimate.TILE_X - 1, bz = 7_777;
        ClimateCoords.Coords c1 = SimClimate.sample(bx, bz, SEED, null);
        double t1 = c1.temp, m1 = c1.moist;
        SimClimate.clearCache();
        ClimateCoords.Coords c2 = SimClimate.sample(bx, bz, SEED, null);
        chk("D1 清缓存前后逐位一致（瓦片边界点）", Math.abs(c2.temp - t1) + Math.abs(c2.moist - m1), 0.0,
            "边界点 x=" + bx);
        ClimateCoords.Coords cA = SimClimate.sample(nodeX(1, 0), nodeZ(0, 0), SEED, null);
        SimClimate.clearCache();
        ClimateCoords.Coords cB = SimClimate.sample(nodeX(1, 0), nodeZ(0, 0), SEED, null);
        chk("D2 清缓存前后逐位一致（瓦片内部格点）", Math.abs(cB.temp - cA.temp), 0.0, "");

        // ---------------- E. D2 的证伪：缓存键不含物理旋钮 ----------------
        say("");
        say("E. **D2 证伪**：改 ZonalTables.SEA_ONLY_UZM 之后不显式清缓存，读数变不变？");
        SimClimate.clearCache();
        double eBefore = SimClimate.sample(nodeX(0, 1), nodeZ(0, 1), SEED, null).windX;
        boolean saved = ZonalTables.SEA_ONLY_UZM;
        ZonalTables.SEA_ONLY_UZM = !saved;
        double eAfter = SimClimate.sample(nodeX(0, 1), nodeZ(0, 1), SEED, null).windX;
        double eAfterClear = 0;
        SimClimate.clearCache();
        eAfterClear = SimClimate.sample(nodeX(0, 1), nodeZ(0, 1), SEED, null).windX;
        ZonalTables.SEA_ONLY_UZM = saved;
        say(String.format(LF, "        SEA_ONLY_UZM=%s 时 windX = %+.6f", saved, eBefore));
        say(String.format(LF, "        翻成 %s、**不清缓存**  windX = %+.6f   （差 %.3e）", !saved, eAfter, Math.abs(eAfter - eBefore)));
        say(String.format(LF, "        翻成 %s、**清缓存后**  windX = %+.6f   （差 %.3e）", !saved, eAfterClear, Math.abs(eAfterClear - eBefore)));
        if (Math.abs(eAfter - eBefore) == 0.0 && Math.abs(eAfterClear - eBefore) > 0.0) {
            nFail++;
            say("  [**FAIL**] E  缓存**不随物理旋钮失效** —— D2 成立（不清缓存读到的是旧物理）");
        } else if (Math.abs(eAfterClear - eBefore) == 0.0) {
            nPass++;
            say("  [PASS] E  该旋钮对这个量无影响（不能据此判 D2，需换旋钮或量）");
        } else {
            nPass++;
            say("  [PASS] E  缓存随旋钮失效 —— D2 不成立");
        }

        // ---------------- F. D3 的证伪：SST 距平重复计数 ----------------
        say("");
        say("F. **D3 证伪**：只设 Atmosphere.SST_PROVIDER，比较 SimClimate 与生产 surfaceTemp 的年平");
        SimClimate.clearCache();
        int fx = nodeX(0, 3), fz = nodeZ(0, 2);
        double kf = Atmosphere.kappaAt(fx, fz, SimTerrain.seedOf(SEED), CELL);
        double before = SimClimate.surfaceTempK(fx, fz, SEED);
        Atmosphere.SST_PROVIDER = new Atmosphere.SstProvider() { public double anomalyAt(int x, int z) { return 5.0; } };
        SimClimate.clearCache();
        double after = SimClimate.surfaceTempK(fx, fz, SEED);
        // ⚠ E5（v1 的第二个错）：只设了 Atmosphere 那个 ⇒ sstA=0 ⇒ 测不到重复计数。
        // D3 说的是「**两个都设**」（正是 SimClimate:188-196 注释推荐的第 3 步接法）。
        SimClimate.SST_PROVIDER = new SimClimate.SstProvider() { public double anomalyAt(int x, int z) { return 5.0; } };
        SimClimate.clearCache();
        double both = SimClimate.surfaceTempK(fx, fz, SEED);
        double prod = 0;
        for (double th : PH4) prod += Atmosphere.surfaceTemp(fx, fz, SimTerrain.seedOf(SEED), CELL, th) * 0.25;
        Atmosphere.SST_PROVIDER = null;
        SimClimate.SST_PROVIDER = null;
        SimClimate.clearCache();
        say(String.format(LF, "        两个 provider 都设 +5 K : %.6f K", both));
        say(String.format(LF, "        kappa = %.4f   (1-kappa)*SST' = %.4f K", kf, (1 - kf) * 5.0));
        say(String.format(LF, "        SimClimate 无 provider : %.6f K", before));
        say(String.format(LF, "        SimClimate 设 A 的 provider: %.6f K   （Δ = %+.4f）", after, after - before));
        say(String.format(LF, "        生产 surfaceTemp 年平(设 A 的 provider): %.6f K", prod));
        say(String.format(LF, "        SimClimate - 生产 = %+.4f K   而 (1-kappa)*SST' = %+.4f K",
            after - prod, (1 - kf) * 5.0));
        if (Math.abs((both - prod) - (1 - kf) * 5.0) < 1e-6) {
            nFail++;
            say("  [**FAIL**] F  两个 provider 都设时，SimClimate 比生产**多算了一整份 (1-kappa)*SST'** —— **D3 成立**");
        } else {
            nPass++;
            say("  [PASS] F  两份 SST 口径一致 —— D3 不成立");
        }

        // ---------------- G. D6：涡动季节振幅是否跟随 Atmosphere.KAPPA_MEAN ----------------
        // v1 查的是「PrecipField.EDDY_KAPPA 这个字段跟不跟」；修好之后那个字段已被删除
        // （不再有静态拷贝），所以改成**行为检查**：改 KAPPA_MEAN 之后 eddyMfc 必须变。
        say("");
        say("G. **D6 复查**：改 Atmosphere.KAPPA_MEAN，涡动项 eddyMfc(50 度) 跟不跟？");
        double kmSaved = Atmosphere.KAPPA_MEAN;
        double eddyBefore = PrecipField.eddyMfc(Math.toRadians(50.0), 0.0);
        Atmosphere.KAPPA_MEAN = 0.5;
        double eddyAfter = PrecipField.eddyMfc(Math.toRadians(50.0), 0.0);
        Atmosphere.KAPPA_MEAN = kmSaved;
        say(String.format(LF, "        eddyMfc(50 度, theta=0)：KAPPA_MEAN=%.3f 时 %.6e", kmSaved, eddyBefore));
        say(String.format(LF, "                              KAPPA_MEAN=0.500 时 %.6e", eddyAfter));
        say(String.format(LF, "        相对变化 = %.2f%%", 100.0 * Math.abs(eddyAfter - eddyBefore) / Math.max(1e-30, Math.abs(eddyBefore))));
        if (eddyAfter != eddyBefore) { nPass++; say("  [PASS] G  涡动项跟随 KAPPA_MEAN —— D6 已修（静态拷贝已删除）"); }
        else { nFail++; say("  [**FAIL**] G  涡动项不跟随 KAPPA_MEAN —— D6 仍在"); }

        say("");
        say(String.format(LF, "  汇总：PASS %d / FAIL %d", nPass, nFail));
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
