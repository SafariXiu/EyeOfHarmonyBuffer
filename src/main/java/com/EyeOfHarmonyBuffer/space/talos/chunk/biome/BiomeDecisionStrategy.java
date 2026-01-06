package com.EyeOfHarmonyBuffer.space.talos.chunk.biome;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.StrategyMode;

public interface BiomeDecisionStrategy extends AutoCloseable {

    StrategyMode getMode();

    String getVersion();

    @Override
    default void close() {
        dispose();
    }

    default void dispose() {
        // optional override
    }
}
