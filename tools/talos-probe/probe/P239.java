package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;

import java.io.File;
import java.io.PrintStream;

/**
 * P239：**V2BiomeField 瓦片缓存的容量与淘汰策略**。
 *
 * <h3>为什么要量</h3>
 * 本类的缓存原来是"没名字的 12 + 随便踢一个"，而另外三个同类缓存（RelaxedClimate /
 * LandformField / MountainLayerV2）都是 LRU。本仓已经记过代价：LandformField 的注释写着
 * "『任意淘汰』造成的重复求解（P169 的 88k 点扫描里实测 23,822 次）"。
 * 但"策略不对"不等于"容量也不对" —— 容量必须**量**，不能拍。
 *
 * <h3>四种访问模式</h3>
 * <ul>
 *   <li><b>P 生产</b>：玩家在一片区域内活动 —— 同一瓦片内取样，重复 8 遍。
 *       本类 {@code sample()} **只读 (x,z) 所在的那一个瓦片**（halo 是瓦片内部的 1 格，不跨瓦片），
 *       而瓦片是 100km × 50km —— 一个玩家的加载区连一个瓦片都填不满 ⇒ 工作集 = <b>1</b>。</li>
 *   <li><b>W 巡航</b>：沿 Z 走满一个纬度周期 = 20 个瓦片，扫 2 遍。第 2 遍的重解数 =
 *       {@code max(0, 20 − 容量)}，这是容量的**下界判据**。</li>
 *   <li><b>A 对抗</b>（照 P173 那套）：1 个热瓦片 + (N−1) 个冷瓦片轮转 3 轮。
 *       LRU 的经典病理：容量略小于工作集时每一轮都恰好淘汰"下轮就要用"的那块。</li>
 *   <li><b>F 指纹</b>：同一批点连查两遍必须**逐位一致**（缓存策略不得影响取值）。</li>
 * </ul>
 *
 * 用法：runprobe4.bat P239   输出：p239_report.txt
 */
public class P239 {

    static final int SEED = 1022228679;
    static final int CELL = V2BiomeField.CELL;
    static final int TX = V2BiomeField.TILE_X;
    static final int TZ = V2BiomeField.TILE_Z;
    /** 每个 Field 的常驻字节（解析式，与 Field 的字段一一对应）。 */
    static final double FIELD_MB =
        (V2BiomeField.SX * (double) V2BiomeField.SZ * (4 + 4 + V2BiomeSelect0.KINDS) + V2BiomeField.SX * (double) V2BiomeField.SZ) / 1048576.0;

    /** 只为拿 KINDS 而不引入别的依赖。 */
    static final class V2BiomeSelect0 { static final int KINDS = 16; }

    static final int[] LIMITS = { 4, 8, 12, 16, 20, 24 };

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p239_report.txt"), "UTF-8");
        say("===== P239：V2BiomeField 瓦片缓存容量与淘汰策略 =====");
        say(String.format("  瓦片 %dkm x %dkm；每 Field ≈ %.2f MB；sample() 只读单瓦片（不跨瓦片 halo）",
            TX / 1000, TZ / 1000, FIELD_MB));
        say(String.format("  生产工作集：一个玩家的加载区 ~1 瓦片（瓦片 = %d x %d blocks）", TX, TZ));
        say("");
        say("  容量 | 模式P重解 | 模式W重解(理想20) | 模式A重解(理想18) | W耗时ms | 每次重解ms | 常驻MB");
        for (int lim : LIMITS) {
            V2BiomeField.CACHE_LIMIT = lim;
            long t0 = System.nanoTime();
            int w = runCruise();
            double wMs = (System.nanoTime() - t0) / 1e6;
            int p = runProduction();
            int a = runAdversarial(18);
            say(String.format("  %4d | %8d | %17d | %17d | %7.0f | %10.1f | %6.1f",
                lim, p, w, a, wMs, wMs / Math.max(1, w), lim * FIELD_MB));
        }

        // 取值不受策略影响：同一批点连查两遍逐位一致
        V2BiomeField.CACHE_LIMIT = 12;
        V2BiomeField.clearCache();
        long h1 = scanHash(0, 0, 400);
        long h2 = scanHash(0, 0, 400);
        say("");
        say("  模式F 取值指纹：连查两遍 " + (h1 == h2 ? "逐位一致 PASS" : "不一致 FAIL")
            + "  hash=" + String.format("%016X", h1));

        say("");
        say("BIOME_CACHE_STATUS=DONE");
        rep.flush();
        rep.close();
        System.exit(0);
    }

    /** 生产：同一瓦片内 8 遍扫描，每遍 600 个点。 */
    static int runProduction() {
        V2BiomeField.clearCache();
        V2BiomeField.resetStats();
        long c0 = V2BiomeField.SOLVE_COUNT.get();
        for (int pass = 0; pass < 8; pass++) {
            for (int k = 0; k < 600; k++) {
                int x = (k * 37) % 40_000 + 1_000;
                int z = (k * 53) % 20_000 + 1_000;
                V2BiomeField.sample(x, z, SEED);
            }
        }
        return (int) (V2BiomeField.SOLVE_COUNT.get() - c0);
    }

    /** 巡航：z 走满一个纬度周期（20 个瓦片），扫两遍；第 2 遍的重解是容量的判据。 */
    static int runCruise() {
        V2BiomeField.clearCache();
        V2BiomeField.resetStats();
        long c0 = V2BiomeField.SOLVE_COUNT.get();
        int nTiles = 1_000_000 / TZ;
        for (int pass = 0; pass < 2; pass++) {
            for (int t = 0; t < nTiles; t++) {
                V2BiomeField.sample(5_000, t * TZ + 1_000, SEED);
            }
        }
        return (int) (V2BiomeField.SOLVE_COUNT.get() - c0);
    }

    /** 对抗：1 个热瓦片 + (n-1) 个冷瓦片轮转 3 轮（P173 那套）。 */
    static int runAdversarial(int n) {
        V2BiomeField.clearCache();
        V2BiomeField.resetStats();
        long c0 = V2BiomeField.SOLVE_COUNT.get();
        for (int round = 0; round < 3; round++) {
            V2BiomeField.sample(5_000, 5_000, SEED);              // 热瓦片 (0,0)
            for (int t = 1; t < n; t++) {
                V2BiomeField.sample(5_000, t * TZ + 1_000, SEED);  // 冷瓦片 (0,t)
            }
        }
        return (int) (V2BiomeField.SOLVE_COUNT.get() - c0);
    }

    /** 取值指纹（覆盖 kind/bias/scale，逐位）。 */
    static long scanHash(int ax, int az, int n) {
        long h = 0xCBF29CE484222325L;
        for (int k = 0; k < n; k++) {
            int x = ax + (k * 997) % 60_000;
            int z = az + (k * 1_009) % 40_000;
            V2BiomeField.Sample s = V2BiomeField.sample(x, z, SEED);
            h ^= (s.kind == null ? -1 : s.kind.ordinal());
            h *= 0x100000001B3L;
            h ^= Double.doubleToRawLongBits(s.bias);
            h *= 0x100000001B3L;
            h ^= Double.doubleToRawLongBits(s.scale);
            h *= 0x100000001B3L;
        }
        return h;
    }

    static void say(String s) {
        System.out.println("[P239] " + s);
        rep.println("[P239] " + s);
    }
}
