package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P473：**D56 的修复验证 + D8 的重新裁决数据**。
 *
 * <p>D56（接线当天抓到的口径分裂）：{@code SimClimate.solveNode} 里 {@code tSea} 走活的
 * {@code Atmosphere.SST_PROVIDER}，而 {@code tSl -&gt; f.q} 与 {@code f.sst} 走的是**已废弃**的
 * {@code SimClimate.SST_PROVIDER}（恒 0）⇒ 接线之后同一个瓦片节点里两套 SST 口径。
 *
 * <p>D8（airMass 的「暖/冷」轴）：它当年被撤回的理由是「SST_PROVIDER = null ⇒ tSea == T_zm
 * ⇒ airT 只是浮点噪声」。**接线之后这个前提没了** ⇒ 必须重新量。
 *
 * <p>本探针三段：A 装线自证；B 证明 f.sst/f.q 现在**真的看到 SST' 了**（并量化与真值的插值差）；
 * C 在 SST 开/关两种口径下做 airMass 四类普查。
 */
public class P473 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P473] " + s); rep.flush(); System.out.println("[P473] " + s); System.out.flush(); }

    /** airMass 四类名为：0 = 海洋性+暖, 1 = 海洋性+冷, 2 = 大陆性+暖, 3 = 大陆性+冷。 */
    // ⚠ E34：索引必须**逐字抄源码**的三元表达式再填，不许凭「顺序大概是」的记忆。
    // 源码：airMass = mar >= 0.5 ? (airT >= 0 ? 0 : 2) : (airT >= 0 ? 1 : 3)
    // 而 mar = 1 - kappa（高 = 海洋性）⇒ 1 = 大陆性+暖、2 = 海洋性+冷。
    static final String[] AM = {"海洋性+暖", "大陆性+暖", "海洋性+冷", "大陆性+冷"};

    static int[] census(int[] xs, int[] zs) {
        int[] n = new int[4]; int tot = 0;
        for (int z : zs) {
            for (int x : xs) {
                OrographyField.OroSample oro = OrographyField.sample(x, z, SEED);
                ClimateCoords.Coords c = SimClimate.sample(x, z, SEED, oro);
                int a = c.airMass;
                if (a >= 0 && a < 4) n[a]++;
                tot++;
            }
        }
        say(String.format(LF, "   总点数 %d", tot));
        for (int i = 0; i < 4; i++) say(String.format(LF, "     %-10s %5d  %5.1f%%", AM[i], n[i], 100.0 * n[i] / Math.max(1, tot)));
        return n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p473_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        say("P473：D56 修复验证 + D8 重新裁决数据");
        say(String.format(LF, "  SEED=%d  ENABLED=%s", SEED, OceanField.ENABLED));
        say(String.format(LF, "  A. 装线前：SST_PROVIDER=%s  installedSeed=%d",
            Atmosphere.SST_PROVIDER == null ? "null" : "非 null", OceanField.installedSeed()));
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "     onWorld 之后：SST_PROVIDER=%s  installedSeed=%d",
            Atmosphere.SST_PROVIDER == null ? "**null**" : "已装", OceanField.installedSeed()));
        say("");

        // ---------- B. f.sst / f.q 现在看得到 SST' 吗 ----------
        say("B. 找一个 SST' 显著的副热带点，比较「注入的真值」与「SimClimate 用到的值」");
        int z30 = WorldContract.zOfLat(30.0);
        int bestX = 0; double bestV = -1e9;
        for (int x = -9_000_000; x <= 9_000_000; x += 250_000) {
            double v = Atmosphere.sstAnom(x, z30);
            if (v > bestV) { bestV = v; bestX = x; }
        }
        say(String.format(LF, "   沿 lat 30 扫出最大 SST' 的点：x=%d km   Atmosphere.sstAnom = %+.3f K", bestX / 1000, bestV));
        OrographyField.OroSample oro = OrographyField.sample(bestX, z30, SEED);
        ClimateCoords.Coords c = SimClimate.sample(bestX, z30, SEED, oro);
        say(String.format(LF, "   SimClimate.sample 的 c.sstAnom = %+.3f K   （旧实现恒为 0.000）", c.sstAnom));
        say(String.format(LF, "     ⇒ 与真值之差 %.4f K（瓦片 10 km 双线性插值的正常误差）", Math.abs(c.sstAnom - bestV)));
        say(String.format(LF, "     c.q = %.5f   c.temp = %.4f   c.mar = %.3f   c.airT = %+.4f   airMass = %d(%s)",
            c.q, c.temp, c.mar, c.airT, c.airMass, AM[Math.max(0, Math.min(3, c.airMass))]));
        double qNoSst;
        {
            OceanWiring.off();
            ClimateCoords.Coords c0 = SimClimate.sample(bestX, z30, SEED, OrofraphySafe(bestX, z30));
            qNoSst = c0.q;
            say(String.format(LF, "   同一点的 c.q（SST 关）= %.5f  ⇒ 开/关差 %+.5f（%.2f%%）",
                qNoSst, c.q - qNoSst, 100.0 * (c.q - qNoSst) / Math.max(1e-9, qNoSst)));
            OceanWiring.onWorld(SEED);
        }
        say("");

        // ---------- C. D8 的 airMass 四类普查 ----------
        say("C. D8 重新裁决数据：airMass 四类占比（同网格、同 seed，唯一变量 = SST' 有无）");
        int[] xs = new int[12], zs = new int[8];
        for (int i = 0; i < 12; i++) xs[i] = -1_100_000 + i * 200_000;
        for (int j = 0; j < 8; j++) zs[j] = 400_000 + j * 700_000;
        say("   --- SST 开（= 现在的生产口径）---");
        OceanWiring.onWorld(SEED);
        int[] on = census(xs, zs);
        say("   --- SST 关（= 接线前口径）---");
        OceanWiring.off();
        int[] off = census(xs, zs);
        say(String.format(LF, "   ⇒ 四类占比变化（开 - 关）：%+.1f / %+.1f / %+.1f / %+.1f 个百分点",
            pct(on[0], 96) - pct(off[0], 96), pct(on[1], 96) - pct(off[1], 96),
            pct(on[2], 96) - pct(off[2], 96), pct(on[3], 96) - pct(off[3], 96)));
        say("");
        say(String.format(LF, "D. reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只测量与开关接线，未改任何物理参数。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static double pct(int n, int tot) { return 100.0 * n / Math.max(1, tot); }
    static OrographyField.OroSample OrofraphySafe(int x, int z) { return OrographyField.sample(x, z, SEED); }
}
