package com.EyeOfHarmonyBuffer.common.misc;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class OrundumEnergyService {

    private OrundumEnergyService() {}

    public interface OrundumChangeListener {
        void onOrundumChanged(UUID teamId, BigInteger oldValue, BigInteger newValue);
    }

    private static final List<OrundumChangeListener> LISTENERS =
        new CopyOnWriteArrayList<OrundumChangeListener>();

    public static void addListener(OrundumChangeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(OrundumChangeListener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyOrundumChanged(UUID teamId, BigInteger oldValue, BigInteger newValue) {
        for (OrundumChangeListener l : LISTENERS) {
            try {
                l.onOrundumChanged(teamId, oldValue, newValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static UUID getTeamUuidForUser(UUID userUuid) {
        return SpaceProjectManager.getLeader(userUuid);
    }

    public static UUID getTeamIdForUser(UUID userUuid) {
        if (userUuid == null) return null;
        UUID leader = getTeamUuidForUser(userUuid);
        return leader != null ? leader : userUuid;
    }

    public static BigInteger getOrundumForUser(UUID userUuid) {
        if (userUuid == null) return BigInteger.ZERO;
        UUID teamId = getTeamIdForUser(userUuid);
        if (teamId == null) return BigInteger.ZERO;
        return GlobalOrundumStorage.getOrundum(teamId);
    }

    public static BigInteger getOrundumForTeam(UUID teamId) {
        return GlobalOrundumStorage.getOrundum(teamId);
    }

    public static boolean changeOrundumForUser(UUID userUuid, BigInteger delta) {
        if (userUuid == null || delta == null) return false;

        UUID teamId = getTeamIdForUser(userUuid);
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

        notifyOrundumChanged(teamId, current, next);

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
