package com.EyeOfHarmonyBuffer.space.talos;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.List;

public final class ProfilingChunkProvider implements net.minecraft.world.chunk.IChunkProvider{

    private final IChunkProvider delegate;
    private final String name;

    public ProfilingChunkProvider(IChunkProvider delegate) {
        this.delegate = delegate;
        this.name = delegate.getClass().getName();
    }

    private static void logSlow(String tag, int x, int z, long startNs, long thresholdMs) {
        long ms = (System.nanoTime() - startNs) / 1_000_000L;
        if (ms >= thresholdMs) {
            System.out.println("[PERF] " + tag + " x=" + x + " z=" + z + " " + ms + "ms (" + Thread.currentThread().getName() + ")");
        }
        if (ms >= 2000) {
            System.out.println("[PERF] BIG STALL in " + tag + " stack:");
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                System.out.println("  at " + e);
            }
        }
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        long t0 = System.nanoTime();
        try {
            return delegate.provideChunk(x, z);
        } finally {
            logSlow("provideChunk", x, z, t0, 50);
        }
    }

    @Override
    public Chunk loadChunk(int x, int z) {
        long t0 = System.nanoTime();
        try {
            return delegate.loadChunk(x, z);
        } finally {
            logSlow("loadChunk", x, z, t0, 50);
        }
    }

    @Override
    public void populate(IChunkProvider provider, int x, int z) {
        long t0 = System.nanoTime();
        try {
            delegate.populate(provider, x, z);
        } finally {
            logSlow("populate", x, z, t0, 50);
        }
    }

    @Override public boolean chunkExists(int x, int z) { return delegate.chunkExists(x, z); }
    @Override public boolean saveChunks(boolean p1, IProgressUpdate p2) { return delegate.saveChunks(p1, p2); }
    @Override public boolean unloadQueuedChunks() { return delegate.unloadQueuedChunks(); }
    @Override public boolean canSave() { return delegate.canSave(); }
    @Override public String makeString() { return name + " -> " + delegate.makeString(); }
    @Override public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) { return delegate.getPossibleCreatures(type, x, y, z); }
    @Override public ChunkPosition func_147416_a(World world, String s, int x, int y, int z) { return delegate.func_147416_a(world, s, x, y, z); }
    @Override public int getLoadedChunkCount() { return delegate.getLoadedChunkCount(); }
    @Override public void recreateStructures(int x, int z) { delegate.recreateStructures(x, z); }
    @Override public void saveExtraData() { delegate.saveExtraData(); }
}
