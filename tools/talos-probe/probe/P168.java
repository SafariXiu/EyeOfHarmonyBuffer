package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;

/**
 * P168：LandformField 单瓦片求解性能 / MountainLayerV2 缓存 thrash 实测。
 *
 * 用法：java -cp out probe.P168 [tileZ]
 *
 * 输出：
 *   - 每个 LandformField 瓦片（100km x 50km，400x200 @250m）首次采样（= 触发 solve）的耗时
 *   - 该瓦片触发的 MountainLayerV2.solve 次数与其中耗时（SOLVE_COUNT / SOLVE_NANOS 计数器）
 *   - 4 个瓦片区域的采样指纹（修复前后必须逐位一致）
 */
public class P168 {

    static final int SEED = 1022228679;
    static final int TILE_X = 100_000;
    static final int TILE_Z = 50_000;

    public static void main(String[] args) {
        int tileZ = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        System.out.println("=== P168 LandformField 单瓦片实测  seed=" + SEED
            + "  tileZ=" + tileZ + "  (100km x 50km, " + (TILE_X / 250) + "x" + (TILE_Z / 250) + " @250m) ===");

        MountainLayerV2.clearCache();
        LandformField.clearCache();
        MountainLayerV2.resetStats();

        double acc = 0.0;
        long totalMs = 0;
        for (int t = 0; t < 4; t++) {
            long c0 = MountainLayerV2.SOLVE_COUNT.get();
            long n0 = MountainLayerV2.SOLVE_NANOS.get();
            long t0 = System.nanoTime();
            // 瓦片中心首次采样 → 触发该瓦片整片 solve
            LandformField.Sample s = LandformField.sample(t * TILE_X + TILE_X / 2, tileZ * TILE_Z + TILE_Z / 2, SEED);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            long c = MountainLayerV2.SOLVE_COUNT.get() - c0;
            long n = (MountainLayerV2.SOLVE_NANOS.get() - n0) / 1_000_000L;
            totalMs += ms;
            acc += s.low + s.hill + s.plat + s.mtn + s.peak + s.mtnAmt + s.h0;
            System.out.printf("[P168] LandformField tileX=%d solve=%5dms | MountainV2 solve=%6d 次, 累计 %6dms | low=%.5f mtn=%.5f%n",
                t, ms, c, n, s.low, s.mtn);
        }
        System.out.println("[P168] 4 瓦片合计: LandformField 耗时 " + totalMs + "ms, MountainV2 solve="
            + MountainLayerV2.SOLVE_COUNT.get() + " 次 / "
            + (MountainLayerV2.SOLVE_NANOS.get() / 1_000_000L) + "ms");

        // ---- 结果指纹（修复前后必须完全一致）----
        long h = 0xCBF29CE484222325L;
        long cnt = 0;
        for (int z = tileZ * TILE_Z - 2000; z <= tileZ * TILE_Z + TILE_Z + 2000; z += 500) {
            for (int x = -2000; x <= 4 * TILE_X + 2000; x += 500) {
                LandformField.Sample s = LandformField.sample(x, z, SEED);
                h = mix(h, s.low);
                h = mix(h, s.hill);
                h = mix(h, s.plat);
                h = mix(h, s.mtn);
                h = mix(h, s.peak);
                h = mix(h, s.mtnAmt);
                h = mix(h, s.h0);
                cnt++;
            }
        }
        System.out.printf("[P168] 指纹 samples=%d hash=%016X (acc=%.6f)%n", cnt, h, acc);
        System.out.println("[P168] 指纹覆盖 x=[-2000, " + (4 * TILE_X + 2000) + "] z=["
            + (tileZ * TILE_Z - 2000) + ", " + (tileZ * TILE_Z + TILE_Z + 2000) + "] step=500");
    }

    private static long mix(long h, double v) {
        long b = Double.doubleToRawLongBits(v);
        h ^= b;
        h *= 0x100000001B3L;
        h ^= h >>> 29;
        return h;
    }
}
