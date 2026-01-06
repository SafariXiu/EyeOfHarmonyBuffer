package com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManagerConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.Collections;
import java.util.Map;

public interface IMacroCellProvider extends Closeable {

    ChunkProviderTalos2.ChunkShoreCache build(int chunkX, int chunkZ);

    @Nullable
    default ChunkProviderTalos2.ChunkShoreCache peekCached(int chunkX, int chunkZ) {
        return null;
    }

    default void invalidate(int chunkX, int chunkZ) {
        // no-op
    }

    default void invalidateRegion(int chunkX, int chunkZ, int radius) {
        invalidateAll();
    }

    void invalidateAll();

    default void warmup(FieldManagerConfig config, long worldSeed) {
        // no-op
    }

    default DiagnosticsView getDiagnostics() {
        return DiagnosticsView.noop();
    }

    @Override
    default void close() {
        invalidateAll();
    }

    interface DiagnosticsView {
        DiagnosticsView EMPTY = Collections::emptyMap;

        Map<String, Object> asMap();

        static DiagnosticsView noop() {
            return EMPTY;
        }
    }
}
