package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSpatialIndex;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class RiverSystemRegistry {

    private static final Int2ObjectOpenHashMap<RiverSystem> SYSTEMS =
        new Int2ObjectOpenHashMap<>();

    private RiverSystemRegistry() {}

    public static RiverSystem getOrCreate(int worldSeedInt) {
        RiverSystem sys = SYSTEMS.get(worldSeedInt);
        if (sys != null) return sys;

        long seed = worldSeedInt;
        RiverNetwork net = RiverRegistry.getNetworkForSeed(seed);
        if (net == null) {
            RiverSystem empty = new RiverSystem(
                new RiverNetwork(2, 16, 0, 0, 0, 0, seed, java.util.Collections.emptyList()),
                java.util.Collections.emptyList(),
                RiverSpatialIndex.build(java.util.Collections.emptyList(), RiverSpatialIndex.CELL_SIZE)
            );
            SYSTEMS.put(worldSeedInt, empty);
            return empty;
        }

        RiverSystem built = RiverSystem.buildFromNetwork(net);
        SYSTEMS.put(worldSeedInt, built);
        return built;
    }
}
