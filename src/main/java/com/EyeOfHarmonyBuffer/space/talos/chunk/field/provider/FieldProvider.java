package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider;

public interface FieldProvider<T> extends AutoCloseable {

    T sample(int blockX, int blockZ);

    default void invalidateCaches() {}

    default void dispose() {}

    @Override
    default void close() {
        dispose();
    }
}
