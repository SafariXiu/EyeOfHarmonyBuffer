package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * P214：Q3(F-1) 验收 —— 量化【群系种类到底变了多少】。
 *
 * 用法：设环境变量 P214_TAG=before|after 后跑 runprobe4.bat P214
 * 输出：p214_kind_<tag>.txt，每行 "band hemi z <kind 数字串>"（x 0..200km，步长 1000，共 200 列）
 * 之后用外部脚本对比两份文件：变化点占比、变化连段长度、以及 per-band 拆分。
 * 采样 tile 主序（先 tileX 再列再行），避免缓存抖动。
 * 预算：约 1 分钟。
 */
public class P214 {

    static final int SEED = 1022228679;
    static final int STEP = 1000, NX = 200;   // x 0..199km
    static final int RPB = 8;                 // 每带每半球 8 行

    public static void main(String[] args) throws Exception {
        String tag = System.getenv().getOrDefault("P214_TAG", "run");
        File dir = new File("K:\\moder\\EyeOfHarmonyBuffer\\build\\eoh_probe\\mtn");
        PrintStream rep = new PrintStream(new File(dir, "p214_report_" + tag + ".txt"), "UTF-8");

        StringBuilder out = new StringBuilder();
        int rows = 5 * 2 * RPB;
        for (int tx = 0; tx < 2; tx++) {                  // tile 主序：先 tileX（0..200km 跨 tile 0 与 1）
            for (int b = 0; b < 5; b++) {
                for (int h = 0; h < 2; h++) {
                    for (int r = 0; r < RPB; r++) {
                        int zz = b * 100_000 + (int) ((r + 0.5) * 100_000.0 / RPB);
                        int z = h == 0 ? zz : GlobalCirculation.Z_CYCLE - zz;
                        // 每个 (b,h,r) 只在其所属 tileX 的列上输出，保证整行被覆盖
                        int lo = tx * 100_000, hi = (tx + 1) * 100_000;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < NX; i++) {
                            int x = i * STEP;
                            if (x < lo || x >= hi) { sb.append('-'); continue; }
                            sb.append(V2BiomeField.kind(x, z, SEED).ordinal() % 10);
                        }
                        out.append(b).append(' ').append(h).append(' ').append(z).append(' ')
                           .append(sb).append('\n');
                    }
                }
            }
        }
        Files.write(new File(dir, "p214_kind_" + tag + ".txt").toPath(),
            out.toString().getBytes(StandardCharsets.UTF_8));
        rep.println("[P214] tag=" + tag + " 行数=" + (5 * 2 * RPB * 2) + " 每行 " + NX + " 列");
        rep.close();
    }
}
