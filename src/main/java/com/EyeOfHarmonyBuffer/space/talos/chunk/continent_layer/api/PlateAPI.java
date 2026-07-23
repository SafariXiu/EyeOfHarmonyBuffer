package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PlateCenter;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.SuperContinentCenter;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.WorldgenCore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.List;

/**
 * 板块 / 超级大陆辅助 API：
 *   - 基于 WorldgenCore 的 SuperContinentCenter + PlateCenter；
 *   - 提供：
 *       * 通过当前世界坐标拿板块中心（PlateCenterInfo）
 *       * 通过当前世界坐标拿超级大陆中心（SuperCenterInfo）
 *       * 简单的“大陆外缘方向”向量（从 superCenter 指向 plateCenter）
 *
 * 设计要点：
 *   - 不改动 WorldgenCore / WorldgenAPI 原有逻辑；
 *   - 只做只读查询 + 轻量缓存；
 *   - 主路径是 “以某个世界坐标为锚点” 查询当前板块 / 超级大陆。
 */

public final class PlateAPI {

    private PlateAPI() {}

    /**
     * 缓存：(worldSeedInt, plateId) -> PlateCenterInfo
     * key = ((long)worldSeedInt << 32) ^ (plateId & 0xffffffffL)
     */
    private static final Long2ObjectOpenHashMap<PlateCenterInfo> PLATE_CENTER_CACHE =
        new Long2ObjectOpenHashMap<>();

    /**
     * 缓存：(worldSeedInt, superId) -> SuperCenterInfo
     * key = ((long)worldSeedInt << 32) ^ (superId & 0xffffffffL)
     */
    private static final Long2ObjectOpenHashMap<SuperCenterInfo> SUPER_CENTER_CACHE =
        new Long2ObjectOpenHashMap<>();

    private static long packPlateKey(int worldSeedInt, int plateId) {
        return (((long) worldSeedInt) << 32) ^ (plateId & 0xffffffffL);
    }

    private static long packSuperKey(int worldSeedInt, int superId) {
        return (((long) worldSeedInt) << 32) ^ (superId & 0xffffffffL);
    }

    /**
     * 以当前世界坐标为锚点，获取所在板块的中心信息。
     *
     * 步骤：
     *   1) isLandRaw(x,z) -> LandResult -> plateId / superId
     *   2) 根据 (x,z,worldSeed) 获取影响该点的 SuperContinentCenter 列表
     *   3) 在其中找到 superId 匹配的那个 center
     *   4) 对该 super center 调 generatePlateCentersForSuper
     *   5) 在 PlateCenter 列表中找到 continentId == plateId 的那条
     *
     * 返回：
     *   - PlateCenterInfo（会写入缓存），或 null（海洋 / 板块未找到）
     */
    public static PlateCenterInfo getPlateCenterAt(int worldX, int worldZ, int worldSeedInt) {

        WorldgenCore.LandResult land =
            WorldgenCore.isLandRaw(worldX, worldZ, worldSeedInt);

        if (!land.isLand || land.plateId == 0 || land.superId == 0) {
            return null;
        }

        int plateId = land.plateId;
        int superId = land.superId;

        long key = packPlateKey(worldSeedInt, plateId);
        PlateCenterInfo cached = PLATE_CENTER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        List<SuperContinentCenter> centers =
            WorldgenCore.getCandidateCentersForRect(
                worldX, worldZ, worldX, worldZ, worldSeedInt
            );

        SuperContinentCenter targetCenter = null;
        for (SuperContinentCenter c : centers) {
            if (c.superId == superId) {
                targetCenter = c;
                break;
            }
        }

        if (targetCenter == null) {
            return null;
        }

        List<PlateCenter> plates =
            WorldgenCore.generatePlateCentersForSuper(targetCenter);

        PlateCenter matched = null;
        for (PlateCenter pc : plates) {
            if (pc.continentId == plateId) {
                matched = pc;
                break;
            }
        }

        if (matched == null) {
            return null;
        }

        PlateCenterInfo info = new PlateCenterInfo(
            matched.continentId,
            matched.superId,
            matched.worldX,
            matched.worldZ,
            matched.radius
        );

        PLATE_CENTER_CACHE.put(key, info);
        return info;
    }

    /**
     * 通过 plateId 获取板块中心。
     *
     * 说明：
     *   - 由于 plateId 本身无法直接反推出它位于哪一个超级大陆格子，
     *     因此本方法只能依赖缓存：
     *       * 如果之前调用过 getPlateCenterAt(...)，则缓存里已有，可直接返回；
     *       * 否则返回 null。
     *
     * 建议：
     *   - 在绝大多数场景下，请优先使用 getPlateCenterAt(worldX,worldZ,worldSeedInt)，
     *     即“从某个代表点出发”获取本板块的中心。
     */
    public static PlateCenterInfo getPlateCenter(int plateId, int worldSeedInt) {
        long key = packPlateKey(worldSeedInt, plateId);
        return PLATE_CENTER_CACHE.get(key);
    }

    /**
     * 以当前世界坐标为锚点，获取所在超级大陆的中心信息。
     */
    public static SuperCenterInfo getSuperCenterAt(int worldX, int worldZ, int worldSeedInt) {

        WorldgenCore.LandResult land =
            WorldgenCore.isLandRaw(worldX, worldZ, worldSeedInt);

        if (!land.isLand || land.superId == 0) {
            return null;
        }

        int superId = land.superId;
        long key = packSuperKey(worldSeedInt, superId);

        SuperCenterInfo cached = SUPER_CENTER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        List<SuperContinentCenter> centers =
            WorldgenCore.getCandidateCentersForRect(
                worldX, worldZ, worldX, worldZ, worldSeedInt
            );

        SuperContinentCenter targetCenter = null;
        for (SuperContinentCenter c : centers) {
            if (c.superId == superId) {
                targetCenter = c;
                break;
            }
        }

        if (targetCenter == null) {
            return null;
        }

        SuperCenterInfo info = new SuperCenterInfo(
            targetCenter.superId,
            targetCenter.worldX,
            targetCenter.worldZ,
            targetCenter.baseRadius
        );

        SUPER_CENTER_CACHE.put(key, info);
        return info;
    }

    /**
     * 通过 superId 获取超级大陆中心。
     *
     * 同 getPlateCenter 的说明：
     *   - 只能返回已经在 getSuperCenterAt(...) 过程中缓存过的 super；
     *   - 否则返回 null。
     */
    public static SuperCenterInfo getSuperCenter(int superId, int worldSeedInt) {
        long key = packSuperKey(worldSeedInt, superId);
        return SUPER_CENTER_CACHE.get(key);
    }

    /**
     * 以“当前世界坐标所在板块”为基准，返回一个“大致的外缘方向”向量：
     *
     * 定义：
     *   - direction ≈ 从超级大陆中心指向板块中心的单位向量；
     *   - 可用作“河流大致流向海岸”的参考方向。
     *
     * 若无法获取板块 / 超级大陆信息，则返回 (0,1)。
     */
    public static Direction2D getPlateOutflowDirectionAt(int worldX,
                                                         int worldZ,
                                                         int worldSeedInt) {

        WorldgenCore.LandResult land =
            WorldgenCore.isLandRaw(worldX, worldZ, worldSeedInt);

        if (!land.isLand || land.plateId == 0 || land.superId == 0) {
            return new Direction2D(0.0, 1.0);
        }

        PlateCenterInfo plate = getPlateCenterAt(worldX, worldZ, worldSeedInt);
        SuperCenterInfo superCenter = getSuperCenterAt(worldX, worldZ, worldSeedInt);

        if (plate == null || superCenter == null) {
            return new Direction2D(0.0, 1.0);
        }

        double dx = plate.centerX - superCenter.worldX;
        double dz = plate.centerZ - superCenter.worldZ;

        return new Direction2D(dx, dz);
    }

    /**
     * （可选）通过 plateId 直接拿“外缘方向”。
     *
     * 限制：
     *   - 需要缓存中已存在 PlateCenterInfo 和 SuperCenterInfo，
     *     否则将返回默认 (0,1)。
     *
     * 一般推荐用 getPlateOutflowDirectionAt(worldX,worldZ,worldSeedInt)。
     */
    public static Direction2D getPlateOutflowDirection(int plateId, int worldSeedInt) {
        PlateCenterInfo plate = getPlateCenter(plateId, worldSeedInt);
        if (plate == null || plate.superId == 0) {
            return new Direction2D(0.0, 1.0);
        }

        SuperCenterInfo superCenter = getSuperCenter(plate.superId, worldSeedInt);
        if (superCenter == null) {
            return new Direction2D(0.0, 1.0);
        }

        double dx = plate.centerX - superCenter.worldX;
        double dz = plate.centerZ - superCenter.worldZ;
        return new Direction2D(dx, dz);
    }
}
