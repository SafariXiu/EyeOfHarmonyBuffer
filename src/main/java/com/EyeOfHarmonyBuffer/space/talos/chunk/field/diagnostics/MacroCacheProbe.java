package com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics;

public interface MacroCacheProbe {

    void recordHit();

    void recordMiss();

    void recordLoadNanos(long nanos);

    void recordEviction();

    MacroCacheStats snapshot();

    MacroCacheProbe NOOP = new MacroCacheProbe() {
        @Override public void recordHit() {}
        @Override public void recordMiss() {}
        @Override public void recordLoadNanos(long nanos) {}
        @Override public void recordEviction() {}
        @Override public MacroCacheStats snapshot() {
            return MacroCacheStats.EMPTY;
        }
    };
}
