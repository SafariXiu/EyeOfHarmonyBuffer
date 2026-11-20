package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.NoiseUtil;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.SimplexNoise2D;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 第四层内部用的噪声封装。
 *
 * 两套底层：
 *   - noise2 / fbm2D：旧的 Value Noise（保留给兼容 / 洞穴等调用方）；
 *   - noise2S / fbm2DS / warpedFbm2D：SimplexNoise2D 梯度噪声 + 域扭曲
 *     （地形塑形换代，消除网格对齐伪影，产生更有机的山脊 / 河谷）。
 *
 * SimplexNoise2D 构造开销小（512 项置换表），按 seed 缓存实例。
 */
public final class TerrainNoise {

    private TerrainNoise() {}

    // ===== 旧 Value Noise（兼容保留） =====

    public static double noise2(long seed, double fx, double fz) {
        int seedInt = (int)(seed & 0x7FFFFFFF);
        double n01 = NoiseUtil.coreNoise2D(fx, fz, seedInt); // [0,1)
        return n01 * 2.0 - 1.0; // [-1,1]
    }

    public static double fbm2D(long seed,
                               double x, double z,
                               double baseFreq, double baseAmp,
                               int octaves) {
        if (octaves <= 0 || baseAmp == 0.0 || baseFreq == 0.0) {
            return 0.0;
        }
        double sum  = 0.0;
        double freq = baseFreq;
        double amp  = baseAmp;
        for (int i = 0; i < octaves; i++) {
            double n = noise2(seed + i * 0x9E3779B97F4A7C15L, x * freq, z * freq);
            sum += n * amp;
            freq *= 2.0;
            amp  *= 0.5;
        }
        return sum;
    }

    // ===== SimplexNoise2D 梯度噪声（地形塑形换代） =====

    private static final ConcurrentHashMap<Long, SimplexNoise2D> SIMPLEX_CACHE =
        new ConcurrentHashMap<Long, SimplexNoise2D>();

    /**
     * 直接映射槽位缓存（性能关键路径）。
     *
     * 每列地形要取 10~15 次噪声实例；走 ConcurrentHashMap 会**装箱 long**（每次分配一个 Long）
     * 并做哈希查找 —— 实测这部分占列耗时的一大块。这里用不可变 Slot + 16 槽直接映射：
     * 命中只需一次数组读 + long 比较（~1ns），未命中才回落到 map。
     * Slot 的 final 字段保证安全发布，多线程下不会读到半初始化对象。
     */
    private static final int SLOT_BITS = 4;
    private static final int SLOT_MASK = (1 << SLOT_BITS) - 1;
    private static final Slot[] SLOTS = new Slot[1 << SLOT_BITS];

    private static final class Slot {
        final long seed;
        final SimplexNoise2D inst;

        Slot(long seed, SimplexNoise2D inst) {
            this.seed = seed;
            this.inst = inst;
        }
    }

    private static SimplexNoise2D simplexFor(long seed) {
        int idx = (int) ((seed ^ (seed >>> 32)) & SLOT_MASK);
        Slot sl = SLOTS[idx];
        if (sl != null && sl.seed == seed) {
            return sl.inst;
        }
        SimplexNoise2D s = SIMPLEX_CACHE.get(Long.valueOf(seed));
        if (s == null) {
            s = new SimplexNoise2D(seed);
            SimplexNoise2D prev = SIMPLEX_CACHE.putIfAbsent(Long.valueOf(seed), s);
            if (prev != null) {
                s = prev;
            }
        }
        SLOTS[idx] = new Slot(seed, s);
        return s;
    }

    /** 梯度 simplex 2D 噪声，返回约 [-1,1]（连续、无断点）。 */
    public static double noise2S(long seed, double x, double z) {
        return simplexFor(seed).noise2(x, z);
    }

    /** 梯度 simplex 版 FBM。 */
    public static double fbm2DS(long seed,
                                double x, double z,
                                double baseFreq, double baseAmp,
                                int octaves) {
        if (octaves <= 0 || baseAmp == 0.0 || baseFreq == 0.0) {
            return 0.0;
        }
        SimplexNoise2D n = simplexFor(seed);
        double sum  = 0.0;
        double freq = baseFreq;
        double amp  = baseAmp;
        for (int i = 0; i < octaves; i++) {
            sum += n.noise2(x * freq, z * freq) * amp;
            freq *= 2.0;
            amp  *= 0.5;
        }
        return sum;
    }

    /**
     * 域扭曲 FBM：先用低频噪声扭曲采样坐标，再做 FBM。
     * 扭曲幅度 warpAmp 建议 ≈ 0.4~0.6 × 波长（1/warpFreq），
     * 使山脊 / 河谷 / 边界自然弯曲，消除平铺感。
     */
    public static double warpedFbm2D(long seed,
                                     double x, double z,
                                     double freq, double amp, int octaves,
                                     double warpFreq, double warpAmp) {
        SimplexNoise2D n = simplexFor(seed);
        double wx = x + warpAmp * n.noise2(x * warpFreq, z * warpFreq);
        double wz = z + warpAmp * n.noise2(x * warpFreq + 1000.0, z * warpFreq + 2000.0);
        double sum  = 0.0;
        double f = freq;
        double a = amp;
        for (int i = 0; i < octaves; i++) {
            sum += n.noise2(wx * f, wz * f) * a;
            f *= 2.0;
            a *= 0.5;
        }
        return sum;
    }
}
