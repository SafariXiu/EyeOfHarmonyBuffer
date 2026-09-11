package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * P209：F-1（群系抖动在 Z 上每 200k 复读）修复的验收。
 *
 * 用法：runprobe4.bat P209 before|after   （tag 只用于输出文件名）
 * 输出：
 *   p209_report_<tag>.txt —— 逐行 kind/bias/scale 的哈希 + 边界条数
 *   p209_bounds_<tag>.txt —— 每行的群系边界 x 位置（供修前/修后对齐比较）
 * 判据（先声明）：
 *   1) biasHash / scaleHash **必须逐位相同**（抖动只作用在"种类"的查询坐标上，不碰 bias/scale）
 *   2) kindHash 应当变化，但边界位移必须落在 JITTER_AMP = 50 格这个尺度内
 * 预算：约 2-3 分钟（只有 3 个 tile 的窗口要建）。
 */
public class P209 {

    static final int SEED = 1022228679;
    static final int STEP = Integer.parseInt(System.getenv().getOrDefault("P209_STEP", "250"));
    static final int SPAN = 100_000;
    static final int[] ROWS = {50_000, 150_000, 250_000, 350_000, 650_000, 850_000};
    static final int[] TILES = {0, 1, 2};

    public static void main(String[] args) throws Exception {
        String env = System.getenv("P209_TAG");
        String tag = env != null ? env : (args.length > 0 ? args[0] : "run");
        File dir = new File("K:\\moder\\EyeOfHarmonyBuffer\\build\\eoh_probe\\mtn");
        PrintStream rep = new PrintStream(new File(dir, "p209_report_" + tag + ".txt"), "UTF-8");
        StringBuilder bounds = new StringBuilder();

        say(rep, "tag=" + tag + "  JITTER_AMP=" + V2BiomeField.JITTER_AMP
            + " JITTER_LEN=" + V2BiomeField.JITTER_LEN + " OCT=" + V2BiomeField.JITTER_OCT);
        // ---- Z 复读度量（F-1 的核心判据）：kind(x,z) 与 kind(x, z+200_000) 相同的比例 ----
        // 若抖动项在 Z 上每 200k 逐位复读，这一比例会明显偏高；去掉复读后应回落到"基础场自己的"水平。
        // **两趟连续扫描**，不要逐对交替查询：z 上每步跳 200km 会让
        // LandformField/V2BiomeField（Z 瓦片高 50km）每步跨 4 个瓦片 → 缓存抖动 →
        // 实测 6000 次采样触发 7165 次 MountainLayerV2 求解（第 4 次跑飞，见 runaway4_evidence.txt）。
        final int NI = 25, NJ = 40;
        int sameK = 0, totK = 0;
        int[] k1 = new int[NJ * NI], k2 = new int[NJ * NI];
        for (int tileX : TILES) {
            for (int j = 0; j < NJ; j++) {          // 趟1：z 升序（瓦片单调推进）
                for (int i = 0; i < NI; i++) {
                    k1[j * NI + i] = V2BiomeField.kind(tileX * SPAN + 2_000 + i * 4_000, j * 20_000, SEED).ordinal();
                }
            }
            for (int j = 0; j < NJ; j++) {          // 趟2：z+200k，同样升序
                for (int i = 0; i < NI; i++) {
                    k2[j * NI + i] = V2BiomeField.kind(tileX * SPAN + 2_000 + i * 4_000, j * 20_000 + 200_000, SEED).ordinal();
                }
            }
            for (int q = 0; q < NJ * NI; q++) { if (k1[q] == k2[q]) sameK++; totK++; }
        }
        say(rep, String.format("Z-200k 群系一致率 = %d/%d = %.2f%%", sameK, totK, 100.0 * sameK / totK));
        if ("period".equals(System.getenv().getOrDefault("P209_MODE", ""))) { rep.close(); return; }
        // tile 主序：先 tileX，再该 tile 的 x，再行（保证窗口缓存命中）
        for (int tileX : TILES) {
            int x0 = tileX * SPAN;
            for (int z : ROWS) {
                long kindH = 1469598103934665603L, biasH = kindH, scaleH = kindH;
                List<Integer> bs = new ArrayList<>();
                int prev = Integer.MIN_VALUE;
                for (int x = x0; x < x0 + SPAN; x += STEP) {
                    int k = V2BiomeField.kind(x, z, SEED).ordinal();
                    V2BiomeField.Sample s = V2BiomeField.sample(x, z, SEED);
                    kindH = mix(kindH, k);
                    biasH = mix(biasH, Double.doubleToLongBits(s.bias));
                    scaleH = mix(scaleH, Double.doubleToLongBits(s.scale));
                    if (prev != Integer.MIN_VALUE && k != prev) bs.add(x);
                    prev = k;
                }
                StringBuilder sb = new StringBuilder();
                for (int b : bs) sb.append(b).append(',');
                bounds.append(tileX).append(' ').append(z).append(' ').append(sb).append('\n');
                say(rep, String.format("tileX=%d z=%6d  kindHash=%016X biasHash=%016X scaleHash=%016X nBounds=%d",
                    tileX, z, kindH, biasH, scaleH, bs.size()));
            }
        }
        Files.write(new File(dir, "p209_bounds_" + tag + ".txt").toPath(),
            bounds.toString().getBytes(StandardCharsets.UTF_8));
        rep.close();
    }

    static long mix(long h, long v) {
        h ^= v;
        return h * 1099511628211L;
    }

    static void say(PrintStream p, String s) { System.out.println("[P209] " + s); p.println("[P209] " + s); }
}
