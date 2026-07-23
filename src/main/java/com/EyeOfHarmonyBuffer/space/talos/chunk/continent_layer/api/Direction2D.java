package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

/**
 * 简单 2D 方向向量（单位向量）。
 */

public final class Direction2D {

    /** X 方向分量（单位向量） */
    public final double dx;

    /** Z 方向分量（单位向量） */
    public final double dz;

    public Direction2D(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 1e-8) {
            this.dx = 0.0;
            this.dz = 1.0;
        } else {
            this.dx = dx / len;
            this.dz = dz / len;
        }
    }

    @Override
    public String toString() {
        return "Direction2D{" +
            "dx=" + dx +
            ", dz=" + dz +
            '}';
    }
}
