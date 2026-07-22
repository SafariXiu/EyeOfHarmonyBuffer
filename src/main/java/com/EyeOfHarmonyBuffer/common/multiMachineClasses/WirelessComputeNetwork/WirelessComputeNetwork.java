package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单个玩家的无线算力网络。
 * key = WirelessNodeRef(维度+坐标)，不含组/联盟概念。
 */

public class WirelessComputeNetwork {

    private final UUID ownerUUID;

    private final Map<WirelessNodeRef, BigInteger> providers = new HashMap<WirelessNodeRef, BigInteger>();
    private final Map<WirelessNodeRef, BigInteger> consumers = new HashMap<WirelessNodeRef, BigInteger>();

    private BigInteger totalSupply = BigInteger.ZERO;
    private BigInteger totalDemand = BigInteger.ZERO;

    public WirelessComputeNetwork(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void registerProvider(WirelessNodeRef ref, BigInteger supply) {
        if (ref == null) return;
        if (supply == null || supply.signum() < 0) {
            supply = BigInteger.ZERO;
        }
        BigInteger old = providers.put(ref, supply);
        if (old == null) {
            totalSupply = totalSupply.add(supply);
        } else {
            totalSupply = totalSupply.subtract(old).add(supply);
        }
    }

    public void unregisterProvider(WirelessNodeRef ref) {
        if (ref == null) return;
        BigInteger old = providers.remove(ref);
        if (old != null) {
            totalSupply = totalSupply.subtract(old);
        }
    }

    public void registerConsumer(WirelessNodeRef ref, BigInteger demand) {
        if (ref == null) return;
        if (demand == null || demand.signum() < 0) {
            demand = BigInteger.ZERO;
        }
        BigInteger old = consumers.put(ref, demand);
        if (old == null) {
            totalDemand = totalDemand.add(demand);
        } else {
            totalDemand = totalDemand.subtract(old).add(demand);
        }
    }

    public void unregisterConsumer(WirelessNodeRef ref) {
        if (ref == null) return;
        BigInteger old = consumers.remove(ref);
        if (old != null) {
            totalDemand = totalDemand.subtract(old);
        }
    }

    /** 个人网络自身是否供大于需 */
    public boolean isNetworkSatisfied() {
        return totalSupply.compareTo(totalDemand) >= 0;
    }

    public boolean isConsumerRegistered(WirelessNodeRef ref) {
        return ref != null && consumers.containsKey(ref);
    }

    /**
     * 在“只看个人网络”的语义下：该 consumer 是否存在并且网络总供给足够。
     */
    public boolean isConsumerSatisfied(WirelessNodeRef ref) {
        if (ref == null) return false;
        if (!consumers.containsKey(ref)) {
            return false;
        }
        return isNetworkSatisfied();
    }

    public BigInteger getTotalSupply() {
        return totalSupply;
    }

    public BigInteger getTotalDemand() {
        return totalDemand;
    }

    public boolean isEmpty() {
        return providers.isEmpty() && consumers.isEmpty();
    }

    /** 预留将来每 tick 的更复杂逻辑 */
    public void tick() {

    }
}
