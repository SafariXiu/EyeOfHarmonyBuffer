package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverBodyIndex;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSpatialIndex;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosBaseTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.RiverTemplate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.SupercontinentInfo;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.TemplateInstantiator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SupercontinentRiverSystemRegistry {

    /** 海平面（与区块生成器一致）。 */
    private static final int SEA_LEVEL = 64;
    /** 水体中心基础高度超过该值（blocks）时，视为「高山上的不合理水体」直接丢弃。 */
    private static final double MAX_BODY_CENTER_HEIGHT = 82.0;

    private static final Long2ObjectOpenHashMap<RiverSystem> SYSTEMS =
        new Long2ObjectOpenHashMap<RiverSystem>();

    private SupercontinentRiverSystemRegistry() {}

    private static long key(int worldSeedInt, int superId, String templateId) {
        long k = (((long) worldSeedInt) << 32) ^ (superId & 0xffffffffL);
        k ^= (long) templateId.hashCode();
        return k;
    }

    private static RiverSystem emptySystem(long seed) {
        RiverNetwork net = new RiverNetwork(
            3, 16,
            0.0, 0.0,
            0.0, 0.0,
            seed,
            Collections.emptyList()
        );
        return new RiverSystem(
            net,
            Collections.emptyList(),
            RiverSpatialIndex.build(Collections.<RiverSegment>emptyList(), RiverSpatialIndex.CELL_SIZE),
            RiverBodyIndex.build(Collections.emptyList(), RiverBodyIndex.CELL_SIZE)
        );
    }

    /**
     * 获取或构建某个 (worldSeedInt, superId, templateId) 对应的 RiverSystem。
     */
    public static RiverSystem getOrCreate(int worldSeedInt,
                                          SupercontinentInfo info,
                                          String templateId,
                                          double scaleFactor) {
        if (info == null || templateId == null) {
            return emptySystem(worldSeedInt);
        }

        long k = key(worldSeedInt, info.superId, templateId);
        RiverSystem sys = SYSTEMS.get(k);
        if (sys != null) {
            return sys;
        }

        RiverTemplate tpl = RiverRegistry.getTemplate(templateId);
        if (tpl == null) {
            sys = emptySystem(worldSeedInt);
            SYSTEMS.put(k, sys);
            return sys;
        }

        RiverNetwork instantiated = TemplateInstantiator.buildNetworkForSupercontinent(
            tpl, info, scaleFactor
        );

        double bufferLenBlocks = CoastClipper.DEFAULT_BUFFER_BLOCKS;
        RiverNetwork clipped = CoastClipper.clipNetworkAtCoast(
            instantiated,
            worldSeedInt,
            bufferLenBlocks
        );

        // 高山水体过滤：中心基础高度超过阈值的湖/湿地/穿河湖/牛轭湖
        // 不雕刻、不列出、不传送（否则会出现“幽灵水体”）。
        RiverNetwork heightFiltered = filterHighTerrainBodies(
            clipped, worldSeedInt
        );

        // 以「截断后」的河网构建运行时系统：
        //   - 河网只保留到「第一次入海 + buffer」处，出海后的河段不再参与
        //     河岸塑形 / 河谷雕刻 / 指令查询；
        //   - 因此 hasMouth 的端点就是地图上实际的截断河口，
        //     /talosRiverMouth 会直接 TP 到截断位置。
        RiverSystem built = RiverSystem.buildFromNetwork(heightFiltered);
        SYSTEMS.put(k, built);
        return built;
    }

    private static RiverNetwork filterHighTerrainBodies(
        RiverNetwork network, int worldSeedInt
    ) {
        List<RiverBodyData> bodies = network.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return network;
        }

        List<RiverBodyData> kept = new ArrayList<RiverBodyData>();
        for (RiverBodyData b : bodies) {
            double h = TalosBaseTerrain.sampleBaseHeight(
                (int) Math.floor(b.getCenterX()),
                (int) Math.floor(b.getCenterZ()),
                worldSeedInt,
                SEA_LEVEL
            );
            if (h > MAX_BODY_CENTER_HEIGHT) {
                continue;
            }
            kept.add(b);
        }

        if (kept.size() == bodies.size()) {
            return network;
        }

        return new RiverNetwork(
            network.getVersion(),
            network.getCoordinateScale(),
            network.getMinX(),
            network.getMinZ(),
            network.getMaxX(),
            network.getMaxZ(),
            network.getSeed(),
            network.getEdges(),
            Collections.unmodifiableList(kept)
        );
    }
}
