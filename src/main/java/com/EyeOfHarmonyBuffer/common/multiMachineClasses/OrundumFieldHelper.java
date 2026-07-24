package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public final class OrundumFieldHelper {

    public static int RADIUS_CHUNKS = 20;

    private static final Map<Long, Integer> COVER_COUNT_BY_CHUNK = new HashMap<>();

    private OrundumFieldHelper() {
    }

    private static long chunkKey(int dimId, int chunkX, int chunkZ) {
        long dimPart = ((long) dimId & 0xFFFFFFFFL) << 32;
        long xPart = ((long) (chunkX & 0xFFFF)) << 16;
        long zPart = (long) (chunkZ & 0xFFFF);
        return dimPart | xPart | zPart;
    }

    public static void activateField(int dimId, int centerChunkX, int centerChunkZ) {
        int radius = RADIUS_CHUNKS;

        for (int dx = -radius; dx <= radius; dx++) {
            int cx = centerChunkX + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int cz = centerChunkZ + dz;
                long key = chunkKey(dimId, cx, cz);

                int oldCount = COVER_COUNT_BY_CHUNK.getOrDefault(key, 0);
                int newCount = oldCount + 1;

                if (newCount <= 0) {
                    COVER_COUNT_BY_CHUNK.remove(key);
                } else {
                    COVER_COUNT_BY_CHUNK.put(key, newCount);
                }
            }
        }
    }

    public static void deactivateField(int dimId, int centerChunkX, int centerChunkZ) {
        int radius = RADIUS_CHUNKS;

        for (int dx = -radius; dx <= radius; dx++) {
            int cx = centerChunkX + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int cz = centerChunkZ + dz;
                long key = chunkKey(dimId, cx, cz);

                int oldCount = COVER_COUNT_BY_CHUNK.getOrDefault(key, 0);
                int newCount = oldCount - 1;

                if (newCount <= 0) {
                    COVER_COUNT_BY_CHUNK.remove(key);
                } else {
                    COVER_COUNT_BY_CHUNK.put(key, newCount);
                }
            }
        }
    }

    public static boolean isPositionCovered(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }

        int dimId = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        long key = chunkKey(dimId, chunkX, chunkZ);
        Integer count = COVER_COUNT_BY_CHUNK.get(key);
        return count != null && count > 0;
    }

    public static void clearAll() {
        COVER_COUNT_BY_CHUNK.clear();
    }

    public static int getTrackedChunkCount() {
        return COVER_COUNT_BY_CHUNK.size();
    }

    public static void onWorldUnload(int dimId) {

    }
}
