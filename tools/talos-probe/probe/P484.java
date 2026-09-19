package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeSelect;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P484：D72（海冰改成温度判据）+ D73（群系雪门与方块层同源）的验证。
 *
 * <p>三段：
 * <ol>
 *   <li>A. 接线自证（源码扫描）：造冰那一行确实换了、旧几何判据确实不再被引用；雪门确实换了；</li>
 *   <li>B. 解析自检 + 新判据下的冰缘纬度；</li>
 *   <li>C. D73 的影响面：用 SimTerrain.SNOW_FROM_TEMP 这个真实开关翻转，逐位比较下游 16 通道权重。</li>
 * </ol>
 */
public class P484 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P484] " + s); rep.flush(); System.out.println("[P484] " + s); System.out.flush(); }

    static boolean isSea(int x, int z) { return !PlateField.isLandWithCell(x, z, SD, CELL); }

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(Paths.get(ROOT.getPath(), "src", "main", "java", "com", "EyeOfHarmonyBuffer", rel)),
            StandardCharsets.UTF_8);
    }

    /** 源码扫描：某个符号在代码行（去掉注释行）里出现几次。 */
    static int codeHits(String text, String sym) {
        int n = 0;
        for (String ln : text.split("\\r?\\n")) {
            String t = ln.trim();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
            if (t.contains(sym)) n++;
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p484_report.txt"), "UTF-8");
        say("P484：D72 + D73 的验证");
        say(String.format(LF, "  SimTerrain.SEA_ICE_T=%.2f K（%.2f C，海水冰点）  SNOW_T=%.2f K  SNOW_FROM_TEMP=%s",
            SimTerrain.SEA_ICE_T, SimTerrain.SEA_ICE_T - 273.15, SimTerrain.SNOW_T, SimTerrain.SNOW_FROM_TEMP));
        say("");

        say("A. 接线自证（源码扫描；注释行不计）");
        String cp = read("space/talos/chunk/world/ChunkProviderTalos2.java");
        String bs = read("space/talos/chunk/world/V2BiomeSelect.java");
        int cpNew = codeHits(cp, "coldestMonthTempK");
        int cpOld = codeHits(cp, "PolarZone.isPolar");
        int cpImp = codeHits(cp, "continent_layer.PolarZone");
        int bsNew = codeHits(bs, "warmestMonthTempK");
        int bsOld = codeHits(bs, "snowLineY(z)");
        say(String.format(LF, "   ChunkProviderTalos2：coldestMonthTempK 命中 %d（应 >=1）；PolarZone.isPolar %d（应 0）；import %d（应 0）",
            cpNew, cpOld, cpImp));
        say(String.format(LF, "   V2BiomeSelect：warmestMonthTempK 命中 %d（应 >=1）；几何 snowLineY(z) %d（应 1 = 回滚分支）",
            bsNew, bsOld));
        boolean wireOk = cpNew >= 1 && cpOld == 0 && cpImp == 0 && bsNew >= 1 && bsOld == 1;
        say(String.format(LF, "   ⇒ 接线自检：%s", wireOk ? "**通过**" : "**不通过**"));
        say(String.format(LF, "  GATE_D72_WIRING=%s", wireOk ? "PASS" : "FAIL"));
        say("  GATE_D72_ICE_EDGE=REVIEW");
        say("");

        OceanWiring.onWorld(SEED);
        say(String.format(LF, "   装线：SST_PROVIDER=%s  installedSeed=%d",
            Atmosphere.SST_PROVIDER == null ? "**null**" : "已装", OceanField.installedSeed()));
        say("");

        say("B. 解析自检：最冷月 == 年平 - A，最暖月 == 年平 + A");
        double maxd = 0;
        for (int z = 2_000_000; z <= 18_000_000; z += 2_000_000) {
            for (int x = -6_000_000; x <= 6_000_000; x += 6_000_000) {
                double t = SimClimate.surfaceTempK(x, z, SEED);
                double a = SimTerrain.seasonalAmpK(x, z, SEED);
                maxd = Math.max(maxd, Math.abs(SimTerrain.coldestMonthTempK(x, z, SEED) - (t - a)));
                maxd = Math.max(maxd, Math.abs(SimTerrain.warmestMonthTempK(x, z, SEED) - (t + a)));
            }
        }
        say(String.format(LF, "   18 个点上两式最大偏差 = %.3e（应 ~1e-15）", maxd));
        say("");
        say("B2. 新判据的冰缘：沿 z 以 333 km 步长扫一个 20M 周期（x = -6M），只看真海");
        int prevIce = -1, nSea = 0, nIce = 0;
        double warmestIce = -1e9; int wiZ = 0;
        List<String> edges = new ArrayList<String>();
        for (int z = 0; z < WorldContract.Z_CYCLE; z += 333_333) {
            if (!isSea(-6_000_000, z)) continue;
            nSea++;
            double tCold = SimTerrain.coldestMonthTempK(-6_000_000, z, SEED);
            int ice = tCold < SimTerrain.SEA_ICE_T ? 1 : 0;
            nIce += ice;
            if (ice == 1 && tCold > warmestIce) { warmestIce = tCold; wiZ = z; }
            if (prevIce >= 0 && ice != prevIce) {
                edges.add(String.format(LF, "     冰缘 %s 于 z=%9d  lat=%+7.2f  T最冷=%.2f K（%+.2f C）",
                    ice == 1 ? "出现" : "消失", z, Math.toDegrees(WorldContract.latOf(z)), tCold, tCold - 273.15));
            }
            prevIce = ice;
        }
        for (String e : edges) say(e);
        say(String.format(LF, "   ⇒ 采样 %d 个海点，其中结冰 %d 个（%.1f%%）", nSea, nIce, 100.0 * nIce / Math.max(1, nSea)));
        say(String.format(LF, "   ⇒ 最暖的结冰点：z=%d lat=%+.2f  T最冷=%.2f K（%+.2f C）—— 应正好贴着冰点 %.2f C",
            wiZ, Math.toDegrees(WorldContract.latOf(wiZ)), warmestIce, warmestIce - 273.15, SimTerrain.SEA_ICE_T - 273.15));
        say("   （旧口径在同样的海里给出纬度 +4.05 度、海温 +25.55 C 的冰 —— 见 P483）");
        say("");

        say("C. D73 的影响面：翻转 SimTerrain.SNOW_FROM_TEMP（几何雪门 <-> 温度雪门），逐位比较群系权重");
        int[] xs = {-9_000_000, -5_000_000, -1_000_000, 3_000_000, 7_000_000};
        int[] zs = {1_000_000, 5_000_000, 9_000_000, 13_000_000, 17_000_000, 19_000_000};
        int nP = 0, nChg = 0, nLand = 0;
        double maxDiff = 0;
        for (int z : zs) {
            for (int x : xs) {
                OrographyField.OroSample o = OrographyField.sample(x, z, SEED);
                double[] wOld = new double[V2BiomeSelect.KINDS];
                double[] wNew = new double[V2BiomeSelect.KINDS];
                SimTerrain.SNOW_FROM_TEMP = false;
                V2BiomeSelect.accumulateWeights(x, z, SEED, o, o.isLand, wOld);
                SimTerrain.SNOW_FROM_TEMP = true;
                V2BiomeSelect.accumulateWeights(x, z, SEED, o, o.isLand, wNew);
                nP++;
                if (o.isLand) nLand++;
                double d = 0, s0 = 0, s1 = 0;
                for (int q = 0; q < wOld.length; q++) {
                    d = Math.max(d, Math.abs(wOld[q] - wNew[q]));
                    s0 += wOld[q]; s1 += wNew[q];
                }
                if (d > 1e-9) nChg++;
                maxDiff = Math.max(maxDiff, d);
                if (nP <= 10) say(String.format(LF, "   (%9d,%9d) %-4s 旧权重和 %9.4f  新权重和 %9.4f  最大差 %.4f",
                    x, z, o.isLand ? "陆" : "海", s0, s1, d));
            }
        }
        SimTerrain.SNOW_FROM_TEMP = true;
        say(String.format(LF, "   ⇒ %d 个点（陆地 %d）中权重改变 %d 个（%.1f%%），最大差 %.4f",
            nP, nLand, nChg, 100.0 * nChg / nP, maxDiff));
        say("   （真实开关的逐位比较，不是代理；但样本只有 30 点，只用于判断「是不是可忽略」）");
        say("");
        say(String.format(LF, "D. 现场复原：SNOW_FROM_TEMP=%s  SEA_ICE_T=%.2f  reentryBlocked=%d",
            SimTerrain.SNOW_FROM_TEMP, SimTerrain.SEA_ICE_T, OceanField.reentryBlocked));
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
