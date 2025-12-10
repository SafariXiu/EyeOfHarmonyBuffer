package com.EyeOfHarmonyBuffer.common.misc;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.UUID;

public class OrundumEnergyService {

    private OrundumEnergyService() {}

    private static UUID getTeamUuidForUser(UUID userUuid) {
        return SpaceProjectManager.getLeader(userUuid);
    }

    public static BigInteger getOrundumForUser(UUID userUuid) {
        if (userUuid == null) return BigInteger.ZERO;
        UUID teamId = getTeamUuidForUser(userUuid);
        return GlobalOrundumStorage.getOrundum(teamId);
    }

    public static BigInteger getOrundumForTeam(UUID teamId) {
        return GlobalOrundumStorage.getOrundum(teamId);
    }

    public static boolean changeOrundumForUser(UUID userUuid, BigInteger delta) {
        if (userUuid == null || delta == null) return false;

        UUID teamId = getTeamUuidForUser(userUuid);
        return changeOrundumForTeam(teamId, delta);
    }

    public static boolean changeOrundumForTeam(UUID teamId, BigInteger delta) {
        if (teamId == null || delta == null) return false;

        HashMap<UUID, BigInteger> map = GlobalOrundumStorage.getInternalMap();
        BigInteger current = map.getOrDefault(teamId, BigInteger.ZERO);
        BigInteger next = current.add(delta);

        if (next.signum() < 0) {
            return false;
        }

        map.put(teamId, next);

        if (GlobalOrundumWorldSavedData.INSTANCE != null) {
            try {
                GlobalOrundumWorldSavedData.INSTANCE.markDirty();
            } catch (Exception e) {
                System.out.println("FAILED TO MARK GlobalOrundumWorldSavedData DIRTY");
                e.printStackTrace();
            }
        }

        return true;
    }

    public static void setOrundumForTeam(UUID teamId, BigInteger value) {
        if (teamId == null || value == null) return;

        GlobalOrundumStorage.setOrundumRaw(teamId, value);

        if (GlobalOrundumWorldSavedData.INSTANCE != null) {
            try {
                GlobalOrundumWorldSavedData.INSTANCE.markDirty();
            } catch (Exception e) {
                System.out.println("FAILED TO MARK GlobalOrundumWorldSavedData DIRTY");
                e.printStackTrace();
            }
        }
    }
}
