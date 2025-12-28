package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.CoastProfile;
import com.EyeOfHarmonyBuffer.space.talos.biome.CoastProfiles;
import com.EyeOfHarmonyBuffer.space.talos.biome.CoastWidthField;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;

import java.util.*;

public final class DefaultCoastlineAtlas implements CoastlineAtlas {

    private static final int COAST_RADIUS_BLOCKS = 192;
    private static final int MAX_CACHE_ENTRIES = 64;

    private final SimplexNoiseOctave continentNoise;
    private final CoastWidthField coastWidthField;
    private final ContinentalField continentalField;

    private final Map<Long, CoastChunk> coastChunkCache =
        new LinkedHashMap<Long, CoastChunk>(MAX_CACHE_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CoastChunk> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };

    public DefaultCoastlineAtlas(ContinentalField continentalField, long seed) {
        this.continentNoise = new SimplexNoiseOctave(
            seed ^ Talos2Continent.CONTINENT_SALT,
            Talos2Continent.CONTINENT_OCTAVES
        );
        this.coastWidthField = new CoastWidthField(seed);
        this.continentalField = continentalField;
    }

    @Override
    public boolean isLand(int x, int z) {
        CoastChunk chunk = getChunk(x >> 4, z >> 4);
        return chunk.isLand(x, z, this.continentalField, this.continentNoise);
    }

    @Override
    public int distanceToCoast(int x, int z) {
        CoastChunk chunk = getChunk(x >> 4, z >> 4);
        return chunk.distanceToCoast(x, z);
    }

    @Override
    public int beachWidth(int x, int z, MacroBiome macroHint) {
        CoastProfile profile = CoastProfiles.forMacro(macroHint);
        return coastWidthField.beachWidthBlocks(x, z, profile);
    }

    @Override
    public int shelfWidth(int x, int z, MacroBiome macroHint) {
        CoastProfile profile = CoastProfiles.forMacro(macroHint);
        return coastWidthField.shelfWidthBlocks(x, z, profile);
    }

    private CoastChunk getChunk(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);

        CoastChunk chunk = coastChunkCache.get(key);
        if (chunk == null) {
            chunk = CoastChunk.build(
                this.continentalField,
                this.continentNoise,
                chunkX,
                chunkZ,
                COAST_RADIUS_BLOCKS
            );
            coastChunkCache.put(key, chunk);
        }
        return chunk;
    }

    private static final class CoastChunk {

        private static final int INF = 1_000_000;

        final int minX;
        final int minZ;
        final int size;
        final boolean[] land;
        final int[] dist;

        private CoastChunk(int minX, int minZ, int size, boolean[] land, int[] dist) {
            this.minX = minX;
            this.minZ = minZ;
            this.size = size;
            this.land = land;
            this.dist = dist;
        }

        static CoastChunk build(ContinentalField continentalField,
                                SimplexNoiseOctave continentNoise,
                                int chunkX,
                                int chunkZ,
                                int radius) {
            int cx = chunkX * 16 + 8;
            int cz = chunkZ * 16 + 8;
            int minX = cx - radius;
            int minZ = cz - radius;
            int size = radius * 2 + 1;

            boolean[] land = new boolean[size * size];
            for (int dz = 0; dz < size; dz++) {
                int gz = minZ + dz;
                for (int dx = 0; dx < size; dx++) {
                    int gx = minX + dx;
                    land[dz * size + dx] = sampleLandFlag(continentalField, continentNoise, gx, gz);
                }
            }

            int[] dist = new int[size * size];
            Arrays.fill(dist, INF);

            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    int idx = dz * size + dx;
                    boolean cellLand = land[idx];

                    boolean coast =
                        (dx > 0 && land[idx - 1] != cellLand)
                            || (dx < size - 1 && land[idx + 1] != cellLand)
                            || (dz > 0 && land[idx - size] != cellLand)
                            || (dz < size - 1 && land[idx + size]  != cellLand);

                    if (coast) {
                        dist[idx] = 0;
                        queue.add(idx);
                    }
                }
            }

            while (!queue.isEmpty()) {
                int idx = queue.removeFirst();
                int d0 = dist[idx];
                int nd = d0 + 1;
                int x = idx % size;
                int z = idx / size;

                if (x > 0) relax(idx - 1, dist, nd, queue);
                if (x < size - 1) relax(idx + 1, dist, nd, queue);
                if (z > 0) relax(idx - size, dist, nd, queue);
                if (z < size - 1) relax(idx + size, dist, nd, queue);
            }

            return new CoastChunk(minX, minZ, size, land, dist);
        }

        private static void relax(int idx, int[] dist, int candidate, ArrayDeque<Integer> queue) {
            if (dist[idx] > candidate) {
                dist[idx] = candidate;
                queue.add(idx);
            }
        }

        boolean isLand(int gx, int gz,
                       ContinentalField continentalField,
                       SimplexNoiseOctave continentNoise) {
            int lx = gx - minX;
            int lz = gz - minZ;
            if (lx < 0 || lx >= size || lz < 0 || lz >= size) {
                return sampleLandFlag(continentalField, continentNoise, gx, gz);
            }
            return land[lz * size + lx];
        }

        int distanceToCoast(int gx, int gz) {
            int lx = gx - minX;
            int lz = gz - minZ;
            if (lx < 0 || lx >= size || lz < 0 || lz >= size) {
                return COAST_RADIUS_BLOCKS + 1;
            }
            int d = dist[lz * size + lx];
            return d >= INF ? (COAST_RADIUS_BLOCKS + 1) : d;
        }
    }

    private static boolean sampleLandFlag(ContinentalField continentalField,
                                          SimplexNoiseOctave continentNoise,
                                          int gx,
                                          int gz) {
        if (continentalField != null) {
            double continentalness = continentalField.continentalBase(gx, gz); // [-1, 1]
            double c01 = clamp01(0.5D + continentalness * 0.5D);
            c01 = smoothstep(c01);
            return c01 >= Talos2Continent.C_LAND;
        }
        return Talos2Continent.isLand(continentNoise, gx, gz);
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }

    private static double smoothstep(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

}
