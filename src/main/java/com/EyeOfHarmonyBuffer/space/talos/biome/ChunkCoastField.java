package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.Talos2Continent;

import java.util.ArrayDeque;

public final class ChunkCoastField {

    public final int radius;
    public final int minX, minZ;
    public final int size;
    public final boolean[] land;
    public final int[] dist;

    private ChunkCoastField(int radius, int minX, int minZ, int size, boolean[] land, int[] dist) {
        this.radius = radius;
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.land = land;
        this.dist = dist;
    }

    public static ChunkCoastField build(SimplexNoiseOctave continentNoise, int chunkX, int chunkZ, int radius) {
        final int cx = chunkX * 16 + 8;
        final int cz = chunkZ * 16 + 8;

        final int minX = cx - radius;
        final int minZ = cz - radius;
        final int size = radius * 2 + 1;

        final boolean[] land = new boolean[size * size];
        for (int dz = 0; dz < size; dz++) {
            int gz = minZ + dz;
            for (int dx = 0; dx < size; dx++) {
                int gx = minX + dx;
                land[dz * size + dx] = Talos2Continent.isLand(continentNoise, gx, gz);
            }
        }

        final int INF = 1_000_000;
        final int[] dist = new int[size * size];
        for (int i = 0; i < dist.length; i++) dist[i] = INF;

        ArrayDeque<Integer> q = new ArrayDeque<>();

        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int idx = dz * size + dx;
                boolean L = land[idx];

                boolean coast = false;
                if (dx > 0           && land[idx - 1]      != L) coast = true;
                else if (dx < size-1 && land[idx + 1]      != L) coast = true;
                else if (dz > 0           && land[idx - size] != L) coast = true;
                else if (dz < size-1 && land[idx + size] != L) coast = true;

                if (coast) {
                    dist[idx] = 0;
                    q.add(idx);
                }
            }
        }

        while (!q.isEmpty()) {
            int idx = q.removeFirst();
            int d0 = dist[idx];
            int x = idx % size;
            int z = idx / size;

            int nd = d0 + 1;

            if (x > 0) {
                int j = idx - 1;
                if (dist[j] > nd) { dist[j] = nd; q.add(j); }
            }
            if (x < size - 1) {
                int j = idx + 1;
                if (dist[j] > nd) { dist[j] = nd; q.add(j); }
            }
            if (z > 0) {
                int j = idx - size;
                if (dist[j] > nd) { dist[j] = nd; q.add(j); }
            }
            if (z < size - 1) {
                int j = idx + size;
                if (dist[j] > nd) { dist[j] = nd; q.add(j); }
            }
        }

        return new ChunkCoastField(radius, minX, minZ, size, land, dist);
    }

    public boolean isLandAt(SimplexNoiseOctave continentNoise, int gx, int gz) {
        int lx = gx - minX;
        int lz = gz - minZ;
        if (lx < 0 || lx >= size || lz < 0 || lz >= size) {
            return Talos2Continent.isLand(continentNoise, gx, gz); // fallback
        }
        return land[lz * size + lx];
    }

    public int distToCoastAt(SimplexNoiseOctave continentNoise, int gx, int gz) {
        int lx = gx - minX;
        int lz = gz - minZ;
        if (lx < 0 || lx >= size || lz < 0 || lz >= size) {
            return radius + 1;
        }
        int d = dist[lz * size + lx];
        return d >= 1_000_000 ? (radius + 1) : d;
    }
}
