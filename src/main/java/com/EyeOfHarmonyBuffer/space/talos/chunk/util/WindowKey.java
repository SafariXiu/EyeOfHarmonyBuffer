package com.EyeOfHarmonyBuffer.space.talos.chunk.util;

/**
 * 瓦片 / 窗口级缓存的键：{@code (种子, 坐标A, 坐标B) -> 64 位 long}。
 *
 * <h3>为什么必须共用这一个实现</h3>
 * 这段式子原先在 4 个层里**各抄了一份**（{@code RelaxedClimate.cacheKey}、
 * {@code LandformField.tileKey}、{@code MountainLayerV2.cellKey}、{@code V2BiomeField.tileKey}），
 * 写法都是：
 * <pre>
 *   ((long) seed &lt;&lt; 40) ^ ((long) (tileX &amp; 0xFFFFF) &lt;&lt; 20) ^ (tileZ &amp; 0xFFFFFL)
 * </pre>
 * 它有一个没人注意到的**位段截断**：{@code seed} 是 int（32 位），左移 40 位后只有低 24 位
 * 留在 long 里，高 8 位被移出 64 位边界**静默丢弃**。于是
 * {@code seed} 与 {@code seed + 2^24}（= +16,777,216）这两个**不同的世界**会共用同一批
 * 已解窗口 —— 表现为"换了种子，地形/气候没换"。4 份拷贝意味着修的时候要同时改 4 处、
 * 漏一处就复发；这也是引入本类的唯一理由。
 *
 * <h3>现在的写法：可证明的单射</h3>
 * 三个入参的**全部 32 位**都参与混合，不做位移打包 ——
 * 32(seed) + 20(a) + 20(b) = 72 位 &gt; 64 位，**位移打包在数学上就装不下**，
 * 所以任何"每个字段各占一段"的打包都必然丢信息（旧写法丢的就是 seed 的高位）。
 *
 * 本实现是**单射**（不是"概率上很难碰撞"）：设 {@code m} 为
 * {@link #mix64}，它对每个输入都是双射（三个 {@code x ^= x >>> k} 是双射，
 * 乘奇数模 2^64 也是双射）。于是
 * <pre>
 *   of(seed, a, b) = m( m( m(H0 ^ S(seed)) ^ a*KA ) ^ b*KB )
 * </pre>
 * 由外向内逐层反推：最外层给出 b 唯一（KA/KB 为奇数 ⇒ {@code x -> x*K} 是双射，
 * 而内层结果相同 ⇒ 异或前相同），再给出 a 唯一，最后给出 seed 唯一。
 * 因此 {@code of(s1,a1,b1) == of(s2,a2,b2)} ⟺ 三个字段全等，**在整个 int 定义域上成立**，
 * 与 tile 尺寸、世界边界、坐标范围都无关（旧写法在 tile 超出 ±2^19 或 seed 差值达 2^24
 * 时才会暴露，所以它能藏很久）。
 *
 * {@code TalosContract} 的 T6a 在真实定义域上暴力查重（并同时用旧公式跑一遍作为反向对照），
 * 让"这条断言能红"有实测证据，而不是靠本段文字。
 *
 * <h3>不做的事</h3>
 * 本类**不是哈希**，也不承诺"雪崩/均匀分布"——缓存键只需要单射与便宜。
 * 键的位模式没有语义，**任何代码都不得从键里反解坐标**（旧实现有一对
 * {@code decodeTileX/decodeTileZ} 就是靠位段反解的，已随本类一起删除）。
 */
public final class WindowKey {

    private WindowKey() {
    }

    /** 混合常数（splitmix64 收尾那两个公开常数 + 黄金比），与 {@code TectonicMath.mix64} 同一族。 */
    private static final long KA = 0xC2B2AE3D27D4EB4FL;
    private static final long KB = 0x9E3779B97F4A7C15L;

    /**
     * 三个 int 的全部 32 位 -> 一个 64 位键。**单射**（论证见类注释）。
     *
     * @param seed 世界种子（int，含负值）
     * @param a    第一维瓦片索引（如 tileX / cellX），int 全域可用
     * @param b    第二维瓦片索引（如 tileZ / cellZ），int 全域可用
     */
    public static long of(int seed, int a, int b) {
        long h = 0x9E3779B97F4A7C15L;
        h = mix64(h ^ (seed & 0xFFFFFFFFL));
        h = mix64(h ^ (((long) a & 0xFFFFFFFFL) * KA));
        h = mix64(h ^ (((long) b & 0xFFFFFFFFL) * KB));
        return h;
    }

    /** splitmix64 的收尾混合：对每个输入都是双射（见类注释的单射论证）。 */
    private static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
