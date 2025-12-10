package com.EyeOfHarmonyBuffer.common.misc;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.UUID;

public abstract class GlobalOrundumStorage {

    private static final HashMap<UUID, BigInteger> GLOBAL_ORUNDUM = new HashMap<>(100, 0.9f);

    static HashMap<UUID, BigInteger> getInternalMap() {
        return GLOBAL_ORUNDUM;
    }

    static void clear() {
        GLOBAL_ORUNDUM.clear();
    }

    public static BigInteger getOrundum(UUID teamId) {
        if (teamId == null) return BigInteger.ZERO;
        return GLOBAL_ORUNDUM.getOrDefault(teamId, BigInteger.ZERO);
    }

    static void setOrundumRaw(UUID teamId, BigInteger value) {
        if (teamId == null || value == null) return;
        GLOBAL_ORUNDUM.put(teamId, value);
    }
}
