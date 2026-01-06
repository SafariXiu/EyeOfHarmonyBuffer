package com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;

import java.io.Closeable;

public interface IMacroCellProvider extends Closeable {

    ChunkProviderTalos2.ChunkShoreCache build(int chunkX, int chunkZ);

    ChunkProviderTalos2.ChunkShoreCache peekCached(int chunkX, int chunkZ);

    void invalidate(int chunkX, int chunkZ);

    void invalidateAll();

    @Override
    default void close() {
        invalidateAll();
    }
}
