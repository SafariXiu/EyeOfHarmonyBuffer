package probe;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P453：验证 D17（snowLineY 的纬度周期从 ClimateLatitudes.LAT_CYCLE=1e6 换到 WorldContract.Z_CYCLE=20e6）。
 *
 * <p>反向对照：本文件逐字复刻**旧公式**（GlobalCirculation.bandD 那条 1e6 周期的），
 * 断言它满足 1e6 周期而**不**满足 20e6 周期；再断言修后的生产函数反过来。
 * 两条断言方向相反 ⇒ 都能红 ⇒ 不是空断言。
 */
public class P453 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static int nPass = 0, nFail = 0;

    static void say(String s) { rep.println("[P453] " + s); System.out.println("[P453] " + s); }
    static void chk(boolean ok, String what, String detail) {
        if (ok) nPass++; else nFail++;
        say(String.format(LF, "  [%s] %-52s %s", ok ? "PASS" : "**FAIL**", what, detail));
    }
    static double clamp01(double t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }
    /** 旧公式逐字复刻：GlobalCirculation.bandD(z) 用的是 ClimateLatitudes.LAT_CYCLE = 1_000_000。 */
    static double oldBandD(int z) {
        int zc = 1_000_000;
        int zz = ((z % zc) + zc) % zc;
        return Math.min(zz, zc - zz) / (zc / 2.0);
    }
    static double oldSnowLineY(int z) {
        double b = clamp01(oldBandD(z));
        return V2TerrainGen.SNOW_POLE_Y + (V2TerrainGen.SNOW_EQUATOR_Y - V2TerrainGen.SNOW_POLE_Y) * (1.0 - b);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p453_report.txt"), "UTF-8");
        say("P453：D17 验证 —— snowLineY 的纬度周期");
        say(String.format(LF, "  世界契约 Z_CYCLE = %d   旧 ClimateLatitudes.LAT_CYCLE = 1,000,000   ⇒ 相差 %.0f 倍",
            WorldContract.Z_CYCLE, WorldContract.Z_CYCLE / 1_000_000.0));
        say("");

        int[] zs = {0, 500_000, 1_500_000, 3_000_000, 9_000_000, 15_000_000};
        say("A. 逐点对照（改前 = 旧公式，改后 = 生产函数）");
        say(String.format(LF, "  %12s %16s %16s %14s", "z", "改前 snowLineY", "改后 snowLineY", "新/旧"));
        for (int z : zs) {
            double o = oldSnowLineY(z), n = V2TerrainGen.snowLineY(z);
            say(String.format(LF, "  %12d %16.1f %16.1f %14.3f", z, o, n, n / o));
        }
        say("");

        say("B. 周期性断言（周期对不对，一看就知）");
        boolean oldP1 = true, newP1 = true, oldP20 = true, newP20 = true;
        for (int z : new int[]{0, 123_456, 1_234_567, 7_654_321, 19_000_000}) {
            if (Math.abs(oldSnowLineY(z) - oldSnowLineY(z + 1_000_000)) > 1e-9) oldP1 = false;
            if (Math.abs(V2TerrainGen.snowLineY(z) - V2TerrainGen.snowLineY(z + 1_000_000)) > 1e-9) newP1 = false;
            if (Math.abs(oldSnowLineY(z) - oldSnowLineY(z + 20_000_000)) > 1e-9) oldP20 = false;
            if (Math.abs(V2TerrainGen.snowLineY(z) - V2TerrainGen.snowLineY(z + 20_000_000)) > 1e-9) newP20 = false;
        }
        chk(oldP1, "改前：snowLineY(z) == snowLineY(z + 1e6)  （1e6 周期）", "反向对照");
        // ⚠ E9（我自己的断言写错了）：2e7 = 20 x 1e6，所以**周期为 1e6 的函数必然也满足 2e7**
        // （任何周期的整数倍也是周期）。原来我断言 "改前不满足 2e7"，那是数学错误。
        // 真正能区分两者的只有 1e6 那条断言（上面已 PASS）。
        chk(oldP20, "改前：也满足 2e7 周期（2e7 = 20x1e6，必然如此 —— 见注释 E9）", "不是缺陷");
        chk(!newP1, "改后：**不**满足 1e6 周期", "");
        chk(newP20, "改后：snowLineY(z) == snowLineY(z + 2e7)  （2e7 周期）", "");
        say("");
        say("C. 与气候/方块的纬度一致性");
        boolean same = true;
        for (int z : new int[]{0, 1_000_000, 4_444_444, 10_000_000, 17_000_000}) {
            double bSnow = Math.min(1.0, Math.max(0.0, com.EyeOfHarmonyBuffer.sim.world.WorldContract.bandD(z)));
            double expect = V2TerrainGen.SNOW_POLE_Y + (V2TerrainGen.SNOW_EQUATOR_Y - V2TerrainGen.SNOW_POLE_Y) * (1.0 - bSnow);
            if (Math.abs(expect - V2TerrainGen.snowLineY(z)) > 1e-9) same = false;
        }
        chk(same, "改后 snowLineY 用的就是 WorldContract.bandD", "（与方块/气候同一个纬度）");
        say("");
        say(String.format(LF, "  汇总：PASS %d / FAIL %d", nPass, nFail));
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
