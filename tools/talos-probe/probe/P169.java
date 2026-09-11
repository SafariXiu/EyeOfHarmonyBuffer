package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;

/**
 * P169：常量漂移修复（B = V2BiomeField NX/NZ+halo，C = MountainLayerV2 NZ+halo，D = LandformField 常量顺序）
 * 的 ①等价性 ②性能/内存 前后对比。
 *
 * 输出分两类：
 *   [P169-PERF] 性能：LandformField 单瓦片耗时 / V2BiomeField 单瓦片耗时+峰值内存 / MountainV2 solve 次数与单次耗时
 *   [P169-FP]   指纹：按"距格边界的格数"分带，逐带输出采样点数的哈希（同一带内 before/after 必须一致）
 *   指纹分带的意义：halo 只应改变贴着格边界的那 1~2 格宽的带；深内部（deep）变化即为回归。
 */
public class P169 {

    static final int SEED = 1022228679;
    static final int M_CELL = 50_000;    // MountainLayerV2 belt 格
    static final int CELL = 250;
    static final int TILE_X = 100_000;   // LandformField / V2BiomeField 瓦片
    static final int TILE_Z = 50_000;
    static final int NX_M = 200, NZ_M = 200;   // 目标口径（MountainV2: X=200 格 = 50km, Z=200 格 = 50km）
    static final int NX_B = 400, NZ_B = 200;   // 目标口径（BiomeField: 400x200 @250m）

    static final Map<String, long[]> FP = new HashMap<String, long[]>();   // key -> {n, hash}

    public static void main(String[] args) {
        System.out.println("=== P169 等价性 + 性能对比 seed=" + SEED + " ===");

        // ---------------- 1) 等价性指纹（顺便预热 JIT） ----------------
        fingerprints();

        // ---------------- 2) 性能 ----------------
        perfMountainAndLandform();
        perfBiome();

        // ---------------- 3) 边界接缝样例 ----------------
        seamSamples();
    }

    // ============================================================ 指纹

    static void fingerprints() {
        long t0 = System.nanoTime();
        // 区域挑在"有山带"的纬度上（tileZ 4..6 / belt cellZ 4..6，P168 实测这里有 belts=1）：
        //   横跨 belt 格边界 x = 0 / 50k / 100k 与 z = 250k / 300k
        //   同时横跨群系瓦片边界 x = 0 / 100k 与 z = 250k / 300k
        for (int z = 249_000; z <= 301_000; z += CELL) {
            for (int x = -1000; x <= 101_000; x += CELL) {
                mountainFP(x, z);
                biomeFP(x, z);
                landformFP(x, z);
            }
        }
        System.out.println("[P169-FP] 采样完成 " + ((System.nanoTime() - t0) / 1_000_000) + "ms");
        String[] order = {
            "mountain.z.0", "mountain.z.1", "mountain.z.2", "mountain.z.3", "mountain.z.deep", "mountain.z.hi",
            "mountain.x.0", "mountain.x.1", "mountain.x.2", "mountain.x.3", "mountain.x.deep", "mountain.x.hi",
            "biome.x.0", "biome.x.1", "biome.x.2", "biome.x.3", "biome.x.deep", "biome.x.hi",
            "biome.z.0", "biome.z.1", "biome.z.2", "biome.z.3", "biome.z.deep", "biome.z.hi",
            "biome.deep", "landform.deep", "landform.edge",
        };
        for (String k : order) {
            long[] a = FP.get(k);
            System.out.printf("[P169-FP] %-16s n=%7d nz=%7d hash=%016X%n",
                k, a == null ? 0 : a[0], a == null ? 0 : a[2], a == null ? 0 : a[1]);
        }
    }

    /** 累加一个采样点：n = 点数，hash = 逐位哈希，nz = 非零点数（证明该带确实有内容）。 */
    static void acc(String key, long v, boolean nonzero) {
        long[] a = FP.get(key);
        if (a == null) {
            a = new long[]{0L, 0xCBF29CE484222325L, 0L};
            FP.put(key, a);
        }
        a[0]++;
        if (nonzero) {
            a[2]++;
        }
        a[1] ^= v;
        a[1] *= 0x100000001B3L;
        a[1] ^= a[1] >>> 29;
    }

    /**
     * 带号：0/1/2/3 = 距格边界第 1/2/3/4 个格；deep = 深内部；hi = 远边界。
     *
     * deep 取 [3.5, n-3.5] 是**保守**口径：BiomeField 的盒滤波 2 遍 × 半径 1 会把边界列的影响
     * 扩散到第 0~2 列，故第 3 格起才算"不应受 halo/clamp 影响"的内部。
     */
    static String band(double f, int n) {
        if (f < 0.5) return ".0";
        if (f < 1.5) return ".1";
        if (f < 2.5) return ".2";
        if (f < 3.5) return ".3";
        if (f > n - 3.5) return ".hi";
        return ".deep";
    }

    static void mountainFP(int x, int z) {
        int cx = MountainLayerV2.cellOfX(x), cz = MountainLayerV2.cellOfZ(z);
        double fx = (x - cx * (double) M_CELL) / CELL - 0.5;
        double fz = (z - cz * (double) M_CELL) / CELL - 0.5;
        double a = MountainLayerV2.auth(x, z, SEED);
        double u = MountainLayerV2.uplift(x, z, SEED);
        double s = MountainLayerV2.slope01(x, z, SEED);
        long h = mix(mix(mix(0xCBF29CE484222325L, a), u), s);
        boolean nz = a != 0.0 || u != 0.0 || s != 0.0;
        acc("mountain.z" + band(fz, NZ_M), h, nz);
        acc("mountain.x" + band(fx, NX_M), h, nz);
    }

    static void biomeFP(int x, int z) {
        int tx = V2BiomeField.tileOfX(x), tz = V2BiomeField.tileOfZ(z);
        double fx = (x - tx * (double) TILE_X) / CELL - 0.5;
        double fz = (z - tz * (double) TILE_Z) / CELL - 0.5;
        V2BiomeField.Sample s = V2BiomeField.sample(x, z, SEED);
        long h = mix(mix(mix(0xCBF29CE484222325L, s.kind == null ? -1 : s.kind.ordinal()), s.bias), s.scale);
        boolean nz = s.land;
        acc("biome.x" + band(fx, NX_B), h, nz);
        acc("biome.z" + band(fz, NZ_B), h, nz);
        if (band(fx, NX_B).equals(".deep") && band(fz, NZ_B).equals(".deep")) {
            acc("biome.deep", h, nz);
        }
    }

    static void landformFP(int x, int z) {
        LandformField.Sample s = LandformField.sample(x, z, SEED);
        long h = mix(mix(mix(mix(mix(mix(mix(0xCBF29CE484222325L, s.low), s.hill), s.plat),
            s.mtn), s.peak), s.mtnAmt), s.h0);
        // LandformField 只依赖山层（不读 V2BiomeField）→ 按 belt 格边界分带即可
        int mcx = MountainLayerV2.cellOfX(x), mcz = MountainLayerV2.cellOfZ(z);
        double mfx = (x - mcx * (double) M_CELL) / CELL - 0.5;
        double mfz = (z - mcz * (double) M_CELL) / CELL - 0.5;
        boolean deep = band(mfx, NX_M).equals(".deep") && band(mfz, NZ_M).equals(".deep");
        acc(deep ? "landform.deep" : "landform.edge", h, s.mtnAmt > 0.0);
    }

    static long mix(long h, double v) {
        return mix(h, Double.doubleToRawLongBits(v));
    }

    static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001B3L;
        h ^= h >>> 29;
        return h;
    }

    // ============================================================ 性能

    static void perfMountainAndLandform() {
        MountainLayerV2.clearCache();
        LandformField.clearCache();
        MountainLayerV2.resetStats();
        System.out.println("[P169-PERF] --- LandformField 单瓦片（100km x 50km, 400x200 @250m）---");
        long total = 0;
        for (int t = 0; t < 4; t++) {
            long c0 = MountainLayerV2.SOLVE_COUNT.get();
            long n0 = MountainLayerV2.SOLVE_NANOS.get();
            long t0 = System.nanoTime();
            LandformField.Sample s = LandformField.sample(t * TILE_X + TILE_X / 2, 5 * TILE_Z + TILE_Z / 2, SEED);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            long c = MountainLayerV2.SOLVE_COUNT.get() - c0;
            long n = (MountainLayerV2.SOLVE_NANOS.get() - n0) / 1_000_000L;
            total += ms;
            System.out.printf("[P169-PERF] tileX=%d LandformField=%4dms | MountainV2 solve=%3d 次 共%4dms 均%s | low=%.5f mtn=%.5f%n",
                t, ms, c, n, c > 0 ? String.format("%.1fms", n / (double) c) : "-", s.low, s.mtn);
        }
        System.out.println("[P169-PERF] LandformField 4 瓦片合计 " + total + "ms；MountainV2 solve="
            + MountainLayerV2.SOLVE_COUNT.get() + " 次 / " + (MountainLayerV2.SOLVE_NANOS.get() / 1_000_000L) + "ms");

        // 单个 belt 格的冷解耗时（50km x 50km 区域密集扫描）
        MountainLayerV2.clearCache();
        MountainLayerV2.resetStats();
        long t0 = System.nanoTime();
        double acc = 0;
        for (int z = 250_000; z < 300_000; z += CELL) {
            for (int x = 0; x < M_CELL; x += CELL) {
                acc += MountainLayerV2.uplift(x, z, SEED) + MountainLayerV2.auth(x, z, SEED);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        long c = MountainLayerV2.SOLVE_COUNT.get();
        System.out.printf("[P169-PERF] 单 belt 格冷解：%d 次 solve 共 %dms（均 %.1fms/次）acc=%.3f%n",
            c, ms, c > 0 ? ms / (double) c : 0.0, acc);
    }

    static void perfBiome() {
        V2BiomeField.clearCache();
        System.out.println("[P169-PERF] --- V2BiomeField 单瓦片（100km x 50km）---");
        long total = 0;
        for (int t = 0; t < 4; t++) {
            gc();
            long used0 = used();
            long peak0 = peakHeap();
            resetPeaks();
            long t0 = System.nanoTime();
            V2BiomeField.Sample s = V2BiomeField.sample(t * TILE_X + TILE_X / 2, 5 * TILE_Z + TILE_Z / 2, SEED);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            long peak = peakHeap() - peak0;
            gc();
            long used1 = used();
            total += ms;
            System.out.printf("[P169-PERF] tileX=%d BiomeField=%5dms | 峰值堆增量≈%4dMB 常驻增量≈%3dMB | kind=%s bias=%.4f%n",
                t, ms, peak >> 20, (used1 - used0) >> 20, s.kind, s.bias);
        }
        System.out.println("[P169-PERF] BiomeField 4 瓦片合计 " + total + "ms");
        gc();
        System.out.println("[P169-PERF] 当前堆占用（GC 后）≈ " + (used() >> 20) + "MB");
    }

    static long used() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    static void gc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {
            }
        }
    }

    static void resetPeaks() {
        for (MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans()) {
            if (m.getType() == MemoryType.HEAP) {
                try {
                    m.resetPeakUsage();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static long peakHeap() {
        long sum = 0;
        for (MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans()) {
            if (m.getType() == MemoryType.HEAP) {
                MemoryUsage u = m.getPeakUsage();
                if (u != null && u.getUsed() > 0) {
                    sum += u.getUsed();
                }
            }
        }
        return sum;
    }

    // ============================================================ 接缝样例

    static void seamSamples() {
        System.out.println("[P169-SEAM] 群系瓦片边界 x=100000（tileX 0 -> 1）:");
        for (int dx = -300; dx <= 300; dx += 100) {
            int x = TILE_X + dx;
            V2BiomeField.Sample s = V2BiomeField.sample(x, 25_000, SEED);
            System.out.printf("[P169-SEAM]   x=%7d kind=%-18s bias=%.5f scale=%.5f land=%b%n",
                x, s.kind, s.bias, s.scale, s.land);
        }
        System.out.println("[P169-SEAM] 山层 belt 格边界 z=50000（cellZ 0 -> 1）:");
        for (int dz = -300; dz <= 300; dz += 100) {
            int z = M_CELL + dz;
            System.out.printf("[P169-SEAM]   z=%7d auth=%.5f uplift=%.5f slope=%.5f%n", z,
                MountainLayerV2.auth(25_000, z, SEED),
                MountainLayerV2.uplift(25_000, z, SEED),
                MountainLayerV2.slope01(25_000, z, SEED));
        }
    }
}
