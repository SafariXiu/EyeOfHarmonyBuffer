package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.misc.GlobalOrundumWorldSavedData;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OrundumFieldHelper {

    public static int RADIUS_CHUNKS = 20;

    private static final Map<Long, Map<UUID, Integer>> COVER_COUNT_BY_CHUNK_AND_TEAM = new HashMap<>();

    private OrundumFieldHelper() {
    }

    public static Map<Long, Map<UUID, Integer>> getInternalMapReadonly() {
        return COVER_COUNT_BY_CHUNK_AND_TEAM;
    }

    public static void replaceInternalMap(Map<Long, ? extends Map<UUID, Integer>> newMap) {
        COVER_COUNT_BY_CHUNK_AND_TEAM.clear();
        if (newMap == null) return;

        for (Map.Entry<Long, ? extends Map<UUID, Integer>> e : newMap.entrySet()) {
            Long key = e.getKey();
            Map<UUID, Integer> value = e.getValue();
            if (key == null || value == null || value.isEmpty()) continue;
            COVER_COUNT_BY_CHUNK_AND_TEAM.put(key, new HashMap<>(value));
        }
    }

    private static long chunkKey(int dimId, int chunkX, int chunkZ) {
        long dimPart = ((long) dimId & 0xFFFFFFFFL) << 32;
        long xPart = ((long) (chunkX & 0xFFFF)) << 16;
        long zPart = (long) (chunkZ & 0xFFFF);
        return dimPart | xPart | zPart;
    }

    public static void activateField(int dimId, int centerChunkX, int centerChunkZ, UUID teamId) {
        if (teamId == null) {
            return;
        }

        int radius = RADIUS_CHUNKS;

        for (int dx = -radius; dx <= radius; dx++) {
            int cx = centerChunkX + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int cz = centerChunkZ + dz;
                long key = chunkKey(dimId, cx, cz);

                Map<UUID, Integer> teamMap = COVER_COUNT_BY_CHUNK_AND_TEAM.get(key);
                if (teamMap == null) {
                    teamMap = new HashMap<>();
                    COVER_COUNT_BY_CHUNK_AND_TEAM.put(key, teamMap);
                }

                int oldCount = teamMap.getOrDefault(teamId, 0);
                int newCount = oldCount + 1;

                if (newCount <= 0) {
                    teamMap.remove(teamId);
                } else {
                    teamMap.put(teamId, newCount);
                }

                if (teamMap.isEmpty()) {
                    COVER_COUNT_BY_CHUNK_AND_TEAM.remove(key);
                }
            }
        }

        if (GlobalOrundumWorldSavedData.INSTANCE != null) {
            try {
                GlobalOrundumWorldSavedData.INSTANCE.markDirty();
            } catch (Exception e) {
                System.out.println("[EOHB] FAILED TO MARK GlobalOrundumWorldSavedData DIRTY (activateField)");
                e.printStackTrace();
            }
        }
    }

    public static void deactivateField(int dimId, int centerChunkX, int centerChunkZ, UUID teamId) {
        if (teamId == null) {
            return;
        }

        int radius = RADIUS_CHUNKS;

        for (int dx = -radius; dx <= radius; dx++) {
            int cx = centerChunkX + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int cz = centerChunkZ + dz;
                long key = chunkKey(dimId, cx, cz);

                Map<UUID, Integer> teamMap = COVER_COUNT_BY_CHUNK_AND_TEAM.get(key);
                if (teamMap == null) {
                    continue;
                }

                int oldCount = teamMap.getOrDefault(teamId, 0);
                int newCount = oldCount - 1;

                if (newCount <= 0) {
                    teamMap.remove(teamId);
                } else {
                    teamMap.put(teamId, newCount);
                }

                if (teamMap.isEmpty()) {
                    COVER_COUNT_BY_CHUNK_AND_TEAM.remove(key);
                }
            }
        }

        if (GlobalOrundumWorldSavedData.INSTANCE != null) {
            try {
                GlobalOrundumWorldSavedData.INSTANCE.markDirty();
            } catch (Exception e) {
                System.out.println("[EOHB] FAILED TO MARK GlobalOrundumWorldSavedData DIRTY (deactivateField)");
                e.printStackTrace();
            }
        }
    }

    public static boolean isPositionCoveredForUser(World world, int x, int y, int z, UUID userUuid) {
        if (world == null || userUuid == null) {
            return false;
        }

        UUID teamId = OrundumEnergyService.getTeamIdForUser(userUuid);
        if (teamId == null) {
            return false;
        }

        int dimId = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = chunkKey(dimId, chunkX, chunkZ);

        Map<UUID, Integer> teamMap = COVER_COUNT_BY_CHUNK_AND_TEAM.get(key);
        if (teamMap == null || teamMap.isEmpty()) {
            return false;
        }

        Integer count = teamMap.get(teamId);
        return count != null && count > 0;
    }

    public static void clearAll() {
        COVER_COUNT_BY_CHUNK_AND_TEAM.clear();

        if (GlobalOrundumWorldSavedData.INSTANCE != null) {
            try {
                GlobalOrundumWorldSavedData.INSTANCE.markDirty();
            } catch (Exception e) {
                System.out.println("[EOHB] FAILED TO MARK GlobalOrundumWorldSavedData DIRTY (clearAll)");
                e.printStackTrace();
            }
        }
    }

    public static int getTrackedChunkCount() {
        return COVER_COUNT_BY_CHUNK_AND_TEAM.size();
    }

    public static void onWorldUnload(int dimId) {
        long dimMask = ((long) dimId & 0xFFFFFFFFL) << 32;

        COVER_COUNT_BY_CHUNK_AND_TEAM.entrySet().removeIf(entry -> {
            long key = entry.getKey();
            long keyDimPart = key & 0xFFFFFFFF00000000L;
            return keyDimPart == dimMask;
        });

        if (GlobalOrundumWorldSavedData.INSTANCE != null) {
            try {
                GlobalOrundumWorldSavedData.INSTANCE.markDirty();
            } catch (Exception e) {
                System.out.println("[EOHB] FAILED TO MARK GlobalOrundumWorldSavedData DIRTY (onWorldUnload)");
                e.printStackTrace();
            }
        }
    }
}
