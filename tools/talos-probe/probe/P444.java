package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * P444：**间断扫描器 + p' 分量梯度量级**（审计用）。
 *
 * <h3>要证伪/证实的三个假设</h3>
 * <ol>
 *   <li><b>H1</b>：{@link ZonalTables#pRefSlopePerRad} 返回的是**10 度分段线性表的段内斜率**
 *       ⇒ 它在每个 10 度倍数处**跳变**，并且因为 <code>latDeg>=0?s:-s</code> 在 lat=0 处**再翻一次符号**
 *       （返回 ±2292 而不是真导数 0）。这个量经 <code>pzRef</code> 直接驱动 <code>wind()</code> 的 v
 *       ⇒ **v 在每条 10 度线上是阶跃的**（分段常数），而 <code>mmPerDay</code> 的 divU 里有 dv/dz
 *       ⇒ 每 10 度一条**假辐合/辐散尖峰**。</li>
 *   <li><b>H2</b>：p' 的 (S) 季节项与 (C) cell 项各自的**经向梯度量级**到底差多少
 *       （P443 的归因说 (C) 占全部，但推算出 (S) 也不该是零 ⇒ 必须直接打印）。</li>
 *   <li><b>H3</b>：v 的实际阶跃幅度。</li>
 * </ol>
 */
public class P444 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final HashMap<Long, Double> KC = new HashMap<>();

    static void say(String s) { rep.println("[P444] " + s); System.out.println("[P444] " + s); }

    static double kap(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Double v = KC.get(key);
        if (v != null) return v;
        double k = Atmosphere.kappaAt(x, z, SD, CELL);
        KC.put(key, k);
        return k;
    }
    static double pS(double latRad, double k, double th) {
        return -Atmosphere.K_P * Atmosphere.CHI * Atmosphere.seasonalAnomaly(latRad, k, th);
    }
    static double pC(double latRad, double k, double th) { return Atmosphere.cellPressure(latRad, k, th); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p444_report.txt"), "UTF-8");
        say("P444：间断扫描器 + p' 分量梯度量级（审计）");
        say("");

        // ================= H1/h3: pRefSlopePerRad 的跳变 =================
        say("A. pRefSlopePerRad(latDeg) 的间断扫描（0.002 度分辨率，|lat| <= 90）");
        double step = 0.002, prev = ZonalTables.pRefSlopePerRad(-90.0), maxJump = 0;
        int nJump = 0;
        StringBuilder jumps = new StringBuilder();
        for (double a = -90.0 + step; a <= 90.0 + 1e-12; a += step) {
            double cur = ZonalTables.pRefSlopePerRad(a);
            double d = Math.abs(cur - prev);
            if (d > 1.0) {
                nJump++;
                maxJump = Math.max(maxJump, d);
                if (nJump <= 40) jumps.append(String.format(LF, "    lat=%+9.4f  %.1f -> %.1f   jump=%.1f Pa/rad%n", a, prev, cur, d));
            }
            prev = cur;
        }
        say(String.format(LF, "  跳变次数 = %d   最大跳变 = %.1f Pa/rad", nJump, maxJump));
        say(jumps.toString().trim());
        say("  （对照：赤道槽 100800 / 副高 101800 @30 度 ⇒ 10 度分段斜率的典型量级 ~1146~2292 Pa/rad）");
        say("");

        // ================= H3: v 的实际阶跃 =================
        say("B. v 的阶跃：固定 x，跨每条 10 度线的 windAt v 读数（两侧各取 1 m / 1 km）");
        int[] xs = {-4_000_000, 0, 4_000_000};
        double maxVJump1m = 0;
        for (int x : xs) {
            say(String.format(LF, "  x = %d", x));
            say(String.format(LF, "    %-9s %14s %14s %12s", "纬度线", "v(线-1m)", "v(线+1m)", "阶跃 m/s"));
            for (int L = -60; L <= 60; L += 10) {
                int zLine = (int) (L / 90.0 * (ZC / 2));
                double vM = Atmosphere.windAt(x, zLine - 1, SD, CELL, 0.0, GRAD)[1];
                double vP = Atmosphere.windAt(x, zLine + 1, SD, CELL, 0.0, GRAD)[1];
                double j = Math.abs(vP - vM);
                maxVJump1m = Math.max(maxVJump1m, j);
                say(String.format(LF, "    %-9d %14.4f %14.4f %12.4f", L, vM, vP, j));
            }
            if (x != xs[0]) continue;
            // 与「不在 10 度线上的地方」对照：取 5 度线
            say("    对照（5 度线，不应有阶跃）：");
            for (int L = -55; L <= 55; L += 10) {
                int zLine = (int) (L / 90.0 * (ZC / 2));
                double vM = Atmosphere.windAt(x, zLine - 1, SD, CELL, 0.0, GRAD)[1];
                double vP = Atmosphere.windAt(x, zLine + 1, SD, CELL, 0.0, GRAD)[1];
                say(String.format(LF, "    %-9d %14.4f %14.4f %12.4f", L, vM, vP, Math.abs(vP - vM)));
            }
        }
        say(String.format(LF, "  最大 1 m 跨度上的 v 阶跃 = %.4f m/s", maxVJump1m));
        say("");

        // ================= H2: p' 分量梯度量级 =================
        say("C. p' 分量梯度量级（|pxS| |pxC| |pzS| |pzC| 的 max / mean，粗网格）");
        double mxS = 0, mxC = 0, mzS = 0, mzC = 0, sS = 0, sC = 0, szS = 0, szC = 0;
        int n = 0;
        for (double latDeg = -60; latDeg <= 60.0001; latDeg += 2.5) {
            int z = (int) (latDeg / 90.0 * (ZC / 2));
            double lat = WorldContract.latOf(z);
            double latP = WorldContract.latOf(z + GRAD), latM = WorldContract.latOf(z - GRAD);
            for (int x = -10_000_000; x <= 10_000_000; x += 400_000) {
                double kC = kap(x, z);
                double kP = kap(x, z + GRAD), kM = kap(x, z - GRAD);
                double kE = kap(x + GRAD, z), kW = kap(x - GRAD, z);
                for (double th : PH4) {
                    double pxS = Math.abs((pS(lat, kE, th) - pS(lat, kW, th)) / (2.0 * GRAD));
                    double pxC = Math.abs((pC(lat, kE, th) - pC(lat, kW, th)) / (2.0 * GRAD));
                    double pzS = Math.abs((pS(latP, kP, th) - pS(latM, kM, th)) / (2.0 * GRAD));
                    double pzC = Math.abs((pC(latP, kP, th) - pC(latM, kM, th)) / (2.0 * GRAD));
                    mxS = Math.max(mxS, pxS); mxC = Math.max(mxC, pxC);
                    mzS = Math.max(mzS, pzS); mzC = Math.max(mzC, pzC);
                    sS += pxS; sC += pxC; szS += pzS; szC += pzC; n++;
                    if (kC < 0) say("  !! kappa < 0");
                }
            }
        }
        say(String.format(LF, "  |px|: 季节项 max %.3e  mean %.3e   |  cell 项 max %.3e  mean %.3e   (Pa/m)", mxS, sS / n, mxC, sC / n));
        say(String.format(LF, "  |pz|: 季节项 max %.3e  mean %.3e   |  cell 项 max %.3e  mean %.3e   (Pa/m)", mzS, szS / n, mzC, szC / n));
        say(String.format(LF, "  ⇒ |pz_cell|/|pz_seasonal| (max) = %.1f 倍", mzC / Math.max(1e-30, mzS)));
        say("  样本 n = " + n);
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
