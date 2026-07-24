package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 环境气体检测与缓存辅助类。
 *
 * 目标：
 * <ul>
 *     <li>给任意坐标提供 “当前环境气体类型” 查询；</li>
 *     <li>提供一个事件驱动的、可缓存的环境系统，避免每 Tick 大范围扫描导致卡服；</li>
 *     <li>支持以环境提供者所在区块为中心的 7×7 区块 AoE 规则。</li>
 * </ul>
 *
 * 核心设计：
 * <ul>
 *     <li>按区块(chunk)缓存环境类型：{@link #ENV_BY_CHUNK}；</li>
 *     <li>按区块记录有哪些环境提供者：{@link #PROVIDERS_BY_CHUNK}；</li>
 *     <li>当 {@link IGasEnvironmentProvider} 的输出类型发生变化时，
 *         通过 {@link #onProviderEnvironmentChanged(IGasEnvironmentProvider, net.minecraft.world.World, int, int, int)}
 *         触发对其周围 7×7 区块的缓存重算；</li>
 *     <li>环境需求者 {@link IGasEnvironmentConsumer} 只需调用
 *         {@link #getEnvironmentAt(net.minecraft.world.World, int, int, int)} 或
 *         {@link #checkConsumer(IGasEnvironmentConsumer, net.minecraft.world.World, int, int, int)}，
 *         其内部只做 O(1) 的 HashMap 查询，不做扫描。</li>
 * </ul>
 */

public final class GasEnvironmentHelper {

    private static final int RADIUS_CHUNKS = 3;

    private GasEnvironmentHelper() {}

    /**
     * 每个区块当前的“主导环境类型”缓存。
     * <p>
     * key: 维度 + chunkX + chunkZ 打包成 long
     * value: 该区块最终生效的环境类型（综合周围 Provider AoE 及优先级规则）。
     */
    private static final Map<Long, GasEnvironmentType> ENV_BY_CHUNK = new HashMap<>();

    /**
     * 每个区块内部有哪些环境提供者。
     * <p>
     * key: 维度 + chunkX + chunkZ 打包成 long
     * value: 该区块内实现了 {@link IGasEnvironmentProvider} 的 Tile 集合。
     */
    private static final Map<Long, Set<IGasEnvironmentProvider>> PROVIDERS_BY_CHUNK = new HashMap<>();

    /**
     * 将 (dimId, chunkX, chunkZ) 打包成一个 long 用作 Map 的 key。
     *
     * @param dimId  维度 ID
     * @param chunkX 区块 X 坐标
     * @param chunkZ 区块 Z 坐标
     * @return 可用作 HashMap key 的 long 值
     */
    private static long chunkKey(int dimId, int chunkX, int chunkZ) {
        long dimPart = ((long) dimId & 0xFFFFFFFFL) << 32;
        long xPart   = ((long) (chunkX & 0xFFFF)) << 16;
        long zPart   = (long) (chunkZ & 0xFFFF);
        return dimPart | xPart | zPart;
    }

    /**
     * 在 Provider 所在的区块中注册该 Provider。
     * <p>
     * 建议在 Tile 的 {@code onFirstTick} / {@code validate} 等生命周期中调用。
     * 注册后会立即对其影响范围（以其所在区块为中心 7×7 区块）做一次环境缓存重算。
     *
     * @param provider 实现了 {@link IGasEnvironmentProvider} 的实例
     * @param world    世界对象
     * @param x        Provider 所在方块的 X
     * @param y        Provider 所在方块的 Y（当前未使用，但保留以便未来扩展 3D AoE）
     * @param z        Provider 所在方块的 Z
     */
    public static void registerProvider(IGasEnvironmentProvider provider,
                                        World world, int x, int y, int z) {
        if (provider == null || world == null || world.isRemote) return;

        int dim = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = chunkKey(dim, chunkX, chunkZ);

        Set<IGasEnvironmentProvider> set =
            PROVIDERS_BY_CHUNK.computeIfAbsent(key, k -> new HashSet<>());
        set.add(provider);

        onProviderEnvironmentChanged(provider, world, x, y, z);
    }

    /**
     * 从 Provider 所在的区块中注销该 Provider。
     * <p>
     * 建议在 Tile 的 {@code onRemoval} / {@code invalidate} 等生命周期中调用。
     * 注销后会对其原先影响范围（以其所在区块为中心 7×7 区块）做一次环境缓存重算。
     *
     * @param provider 实现了 {@link IGasEnvironmentProvider} 的实例
     * @param world    世界对象
     * @param x        Provider 所在方块的 X
     * @param y        Provider 所在方块的 Y
     * @param z        Provider 所在方块的 Z
     */
    public static void unregisterProvider(IGasEnvironmentProvider provider,
                                          World world, int x, int y, int z) {
        if (provider == null || world == null || world.isRemote) return;

        int dim = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = chunkKey(dim, chunkX, chunkZ);

        Set<IGasEnvironmentProvider> set = PROVIDERS_BY_CHUNK.get(key);
        if (set != null) {
            set.remove(provider);
            if (set.isEmpty()) {
                PROVIDERS_BY_CHUNK.remove(key);
            }
        }

        onProviderEnvironmentChanged(provider, world, x, y, z);
    }

    /**
     * 当某个 Provider 的输出环境类型发生变化时调用。
     * <p>
     * 例如：机器开机/关机、切换输入流体导致环境类型改变等。
     * 会以该 Provider 所在区块为中心，重新计算其周围 7×7 区块的环境缓存。
     *
     * @param provider 发生环境变化的 Provider
     * @param world    世界对象
     * @param x        Provider 所在方块的 X
     * @param y        Provider 所在方块的 Y
     * @param z        Provider 所在方块的 Z
     */
    public static void onProviderEnvironmentChanged(IGasEnvironmentProvider provider,
                                                    World world, int x, int y, int z) {
        if (provider == null || world == null || world.isRemote) return;

        int dim = world.provider.dimensionId;
        int providerChunkX = x >> 4;
        int providerChunkZ = z >> 4;

        for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
            for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
                int cx = providerChunkX + dx;
                int cz = providerChunkZ + dz;
                recomputeChunkEnvironment(dim, cx, cz);
            }
        }
    }

    /**
     * 重新计算某个区块当前的“主导环境类型”，并写入缓存。
     * <p>
     * 算法说明：
     * <ul>
     *     <li>一个区块可能被多个 Provider 的 AoE 覆盖；</li>
     *     <li>我们认为：任何「以该区块为中心，半径 3（7×7）内」的 Provider，都可能影响到此区块；</li>
     *     <li>从这些 Provider 的 {@code getProvidedEnvironmentType()} 中选出“优先级最高”的一个作为结果；</li>
     *     <li>优先级由 {@link GasEnvironmentType#priority} 决定。</li>
     * </ul>
     *
     * @param dim    维度 ID
     * @param chunkX 目标区块 X
     * @param chunkZ 目标区块 Z
     */
    private static void recomputeChunkEnvironment(int dim, int chunkX, int chunkZ) {
        GasEnvironmentType best = GasEnvironmentType.NONE;

        for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
            for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
                int pcx = chunkX + dx;
                int pcz = chunkZ + dz;
                long pKey = chunkKey(dim, pcx, pcz);

                Set<IGasEnvironmentProvider> providers = PROVIDERS_BY_CHUNK.get(pKey);
                if (providers == null || providers.isEmpty()) continue;

                for (IGasEnvironmentProvider p : providers) {
                    GasEnvironmentType type = p.getProvidedEnvironmentType();
                    if (type == null || type == GasEnvironmentType.NONE) continue;

                    if (type.priority > best.priority) {
                        best = type;
                    }
                }
            }
        }

        long key = chunkKey(dim, chunkX, chunkZ);
        if (best == GasEnvironmentType.NONE) {
            ENV_BY_CHUNK.remove(key);
        } else {
            ENV_BY_CHUNK.put(key, best);
        }
    }

    /**
     * 获取指定世界中某个坐标当前的环境气体类型。
     * <p>
     * 新实现：
     * <ul>
     *     <li>不再直接查看该坐标上的 TileEntity 是否实现 {@link IGasEnvironmentProvider}；</li>
     *     <li>改为按“区块缓存”的结果返回环境类型；</li>
     *     <li>环境类型已经综合了周围 Provider AoE 及优先级规则。</li>
     * </ul>
     *
     * @param world 世界对象
     * @param x     方块 X 坐标
     * @param y     方块 Y 坐标（当前未使用，保留扩展）
     * @param z     方块 Z 坐标
     * @return 当前坐标的环境气体类型；若无任何 Provider 影响则为 {@link GasEnvironmentType#NONE}
     */
    public static GasEnvironmentType getEnvironmentAt(World world, int x, int y, int z) {
        if (world == null) return GasEnvironmentType.NONE;
        int dim = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = chunkKey(dim, chunkX, chunkZ);
        GasEnvironmentType type = ENV_BY_CHUNK.get(key);
        return type == null ? GasEnvironmentType.NONE : type;
    }

    /**
     * 对一个环境需求者执行一次“环境是否满足”的检查。
     * <p>
     * 检查流程：
     * <ol>
     *     <li>读取 {@link IGasEnvironmentConsumer#getRequiredEnvironmentType()}；
     *         若返回 {@code null} 或 {@link GasEnvironmentType#NONE}，则认为不参与环境系统，直接返回。</li>
     *     <li>调用 {@link #getEnvironmentAt(World, int, int, int)} 取得当前位置的实际环境类型。</li>
     *     <li>若实际环境类型与所需不一致，则调用
     *         {@link IGasEnvironmentConsumer#onEnvironmentNotSatisfied(GasEnvironmentType, GasEnvironmentType)}。</li>
     * </ol>
     *
     * 典型调用位置：
     * <ul>
     *     <li>在实现了 {@link IGasEnvironmentConsumer} 的机器的 Tick 方法中，
     *         例如 {@code onPostTick}，并且只在服务端(world.isRemote == false)调用。</li>
     * </ul>
     *
     * @param consumer 实现了 {@link IGasEnvironmentConsumer} 的机器实例（通常为 {@code this}）
     * @param world    世界对象
     * @param x        机器所在的 X 坐标
     * @param y        机器所在的 Y 坐标
     * @param z        机器所在的 Z 坐标
     */
    public static void checkConsumer(IGasEnvironmentConsumer consumer,
                                     World world, int x, int y, int z) {
        GasEnvironmentType required = consumer.getRequiredEnvironmentType();
        if (required == null || required == GasEnvironmentType.NONE) return;

        GasEnvironmentType actual = getEnvironmentAt(world, x, y, z);
        if (actual != required) {
            consumer.onEnvironmentNotSatisfied(required, actual);
        }
    }
}
