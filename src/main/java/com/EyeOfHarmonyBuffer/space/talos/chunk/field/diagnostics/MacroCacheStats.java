package com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics;

public final class MacroCacheStats {

    public static final MacroCacheStats EMPTY = new MacroCacheStats(0, 0, 0L, 0);

    private final long hits;
    private final long misses;
    private final long totalLoadNanos;
    private final long evictions;

    public MacroCacheStats(long hits,
                           long misses,
                           long totalLoadNanos,
                           long evictions) {
        this.hits = hits;
        this.misses = misses;
        this.totalLoadNanos = totalLoadNanos;
        this.evictions = evictions;
    }

    public long hits() {
        return hits;
    }

    public long misses() {
        return misses;
    }

    public long total() {
        return hits + misses;
    }

    public double hitRate() {
        long total = total();
        return total == 0 ? 0.0D : (double) hits / (double) total;
    }

    public long totalLoadNanos() {
        return totalLoadNanos;
    }

    public long evictions() {
        return evictions;
    }

    @Override
    public String toString() {
        return "MacroCacheStats{hits=" + hits +
            ", misses=" + misses +
            ", hitRate=" + String.format("%.2f%%", hitRate() * 100.0) +
            ", evictions=" + evictions + '}';
    }
}
