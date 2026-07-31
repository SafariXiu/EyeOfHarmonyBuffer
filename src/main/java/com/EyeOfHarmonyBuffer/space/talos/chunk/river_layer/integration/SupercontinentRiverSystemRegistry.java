package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSpatialIndex;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.RiverTemplate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.SupercontinentInfo;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.TemplateInstantiator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collections;

public final class SupercontinentRiverSystemRegistry {

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
            2, 16,
            0.0, 0.0,
            0.0, 0.0,
            seed,
            Collections.emptyList()
        );
        return new RiverSystem(
            net,
            Collections.emptyList(),
            RiverSpatialIndex.build(Collections.<RiverSegment>emptyList(), RiverSpatialIndex.CELL_SIZE)
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

        // 以「截断后」的河网构建运行时系统：
        //   - 河网只保留到「第一次入海 + buffer」处，出海后的河段不再参与
        //     河岸塑形 / 河谷雕刻 / 指令查询；
        //   - 因此 hasMouth 的端点就是地图上实际的截断河口，
        //     /talosRiverMouth 会直接 TP 到截断位置。
        RiverSystem built = RiverSystem.buildFromNetwork(clipped);
        SYSTEMS.put(k, built);
        return built;
    }
}
