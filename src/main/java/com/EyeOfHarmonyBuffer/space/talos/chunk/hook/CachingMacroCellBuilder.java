package com.EyeOfHarmonyBuffer.space.talos.chunk.hook;

import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.MacroCacheInvalidator;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class CachingMacroCellBuilder implements IMacroCellProvider, MacroCacheInvalidator {

    private final IMacroCellProvider delegate;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final LinkedHashMap<Long, ChunkProviderTalos2.ChunkShoreCache> cache;
    private final int maxEntries;

    public CachingMacroCellBuilder(IMacroCellProvider delegate) {
        this(delegate, 512);
    }

    public CachingMacroCellBuilder(IMacroCellProvider delegate, int maxEntries) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.maxEntries = Math.max(32, maxEntries);
        this.cache = new LinkedHashMap<>(this.maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ChunkProviderTalos2.ChunkShoreCache> eldest) {
                return size() > CachingMacroCellBuilder.this.maxEntries;
            }
        };
    }

    @Override
    public ChunkProviderTalos2.ChunkShoreCache build(int chunkX, int chunkZ) {
        long key = toKey(chunkX, chunkZ);

        lock.readLock().lock();
        try {
            ChunkProviderTalos2.ChunkShoreCache cached = cache.get(key);
            if (cached != null) {
                return cloneCache(cached);
            }
        } finally {
            lock.readLock().unlock();
        }

        ChunkProviderTalos2.ChunkShoreCache fresh = delegate.build(chunkX, chunkZ);
        ChunkProviderTalos2.ChunkShoreCache snapshot = cloneCache(fresh);

        lock.writeLock().lock();
        try {
            cache.put(key, snapshot);
        } finally {
            lock.writeLock().unlock();
        }
        return fresh;
    }

    @Override
    public ChunkProviderTalos2.ChunkShoreCache peekCached(int chunkX, int chunkZ) {
        long key = toKey(chunkX, chunkZ);
        lock.readLock().lock();
        try {
            ChunkProviderTalos2.ChunkShoreCache cached = cache.get(key);
            return (cached != null) ? cloneCache(cached) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void invalidate(int chunkX, int chunkZ) {
        long key = toKey(chunkX, chunkZ);
        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void invalidateAll() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static long toKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static ChunkProviderTalos2.ChunkShoreCache cloneCache(ChunkProviderTalos2.ChunkShoreCache src) {
        ChunkProviderTalos2.ChunkShoreCache dst = new ChunkProviderTalos2.ChunkShoreCache();

        copy(src.isLand,         dst.isLand);
        copy(src.dist,           dst.dist);
        copy(src.beachW,         dst.beachW);
        copy(src.shelfW,         dst.shelfW);
        copy(src.baselineLocked, dst.baselineLocked);

        copy(src.macroPrimary,   dst.macroPrimary);
        copy(src.macroSecondary, dst.macroSecondary);
        copy(src.macroBlend,     dst.macroBlend);

        copy(src.macroWet,       dst.macroWet);
        copy(src.macroCold,      dst.macroCold);
        copy(src.macroCoast,     dst.macroCoast);

        copy(src.macroPlateau,   dst.macroPlateau);
        copy(src.macroTier,      dst.macroTier);
        copy(src.macroPlateId,   dst.macroPlateId);
        copy(src.macroBaseHeight,dst.macroBaseHeight);

        copy(src.macroPatchVariant, dst.macroPatchVariant);
        copy(src.macroPatchSingle,  dst.macroPatchSingle);
        copy(src.macroPatchEdge,    dst.macroPatchEdge);

        copy(src.anchorWeight,   dst.anchorWeight);
        copy(src.hardEdge,       dst.hardEdge);
        copy(src.boundaryTouched,dst.boundaryTouched);

        for (int x = 0; x < ChunkProviderTalos2.ChunkShoreCache.GRID_SIZE; x++) {
            for (int z = 0; z < ChunkProviderTalos2.ChunkShoreCache.GRID_SIZE; z++) {
                ChunkProviderTalos2.ChunkShoreCache.MacroCell s = src.macroContext[x][z];
                ChunkProviderTalos2.ChunkShoreCache.MacroCell d = dst.macroContext[x][z];

                d.primary = s.primary;
                d.secondary = s.secondary;
                d.blendPrimary = s.blendPrimary;
                d.tier = s.tier;
                d.plateId = s.plateId;
                d.plateauAnchor = s.plateauAnchor;
                d.isLand = s.isLand;
                d.distToCoast = s.distToCoast;
                d.beachWidth = s.beachWidth;
                d.shelfWidth = s.shelfWidth;
                d.macroBaseHeight = s.macroBaseHeight;
                d.patchVariant = s.patchVariant;
                d.patchSingleBiome = s.patchSingleBiome;
                d.patchEdgeBlend = s.patchEdgeBlend;
                d.anchorWeight = s.anchorWeight;
                d.hardEdge = s.hardEdge;
                d.resolvedBiome = s.resolvedBiome;
            }
        }

        return dst;
    }

    private static void copy(boolean[][] src, boolean[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }

    private static void copy(byte[][] src, byte[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }

    private static void copy(short[][] src, short[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }

    private static void copy(float[][] src, float[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }

    private static void copy(MacroTag[][] src, MacroTag[][] dst) {
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
    }
}
