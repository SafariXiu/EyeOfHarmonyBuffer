package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单个队伍的无线算力网络（算力已全盘接入 Orundum 体系：键 = Orundum 队伍，队伍即算力组）。
 * key = WirelessNodeRef(维度+坐标)。
 */
public class WirelessComputeNetwork {

    /** 节点失联阈值：超过该 tick 数未刷新即视为残留并清理（正常机器每个 onPostTick 都会刷新）。 */
    private static final long STALE_TICKS = 100;

    /** 队伍 UUID（无队伍时为单人自身）。 */
    private final UUID ownerUUID;

    private final Map<WirelessNodeRef, BigInteger> providers = new HashMap<WirelessNodeRef, BigInteger>();
    private final Map<WirelessNodeRef, BigInteger> consumers = new HashMap<WirelessNodeRef, BigInteger>();

    /** 节点最近一次刷新所在的内部 tick（超时清理用）。 */
    private final Map<WirelessNodeRef, Long> providerLastSeen = new HashMap<WirelessNodeRef, Long>();
    private final Map<WirelessNodeRef, Long> consumerLastSeen = new HashMap<WirelessNodeRef, Long>();

    /** 调试虚空算力（/ocdebug）：独立于 providers，不受超时清理影响，也不参与 isEmpty 之外的生命周期。 */
    private BigInteger debugSupply = BigInteger.ZERO;

    private long tickCounter = 0;

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
        providerLastSeen.put(ref, tickCounter);
        if (old == null) {
            totalSupply = totalSupply.add(supply);
        } else {
            totalSupply = totalSupply.subtract(old).add(supply);
        }
    }

    public void unregisterProvider(WirelessNodeRef ref) {
        if (ref == null) return;
        BigInteger old = providers.remove(ref);
        providerLastSeen.remove(ref);
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
        consumerLastSeen.put(ref, tickCounter);
        if (old == null) {
            totalDemand = totalDemand.add(demand);
        } else {
            totalDemand = totalDemand.subtract(old).add(demand);
        }
    }

    public void unregisterConsumer(WirelessNodeRef ref) {
        if (ref == null) return;
        BigInteger old = consumers.remove(ref);
        consumerLastSeen.remove(ref);
        if (old != null) {
            totalDemand = totalDemand.subtract(old);
        }
    }

    /** 本队网络自身是否供大于需（含调试虚空算力） */
    public boolean isNetworkSatisfied() {
        return getTotalSupply().compareTo(totalDemand) >= 0;
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
        return totalSupply.add(debugSupply);
    }

    public BigInteger getTotalDemand() {
        return totalDemand;
    }

    /** 设置调试虚空算力（/ocdebug set/add），非正数视为清除。 */
    public void setDebugSupply(BigInteger supply) {
        this.debugSupply = supply == null || supply.signum() <= 0 ? BigInteger.ZERO : supply;
    }

    public BigInteger getDebugSupply() {
        return debugSupply;
    }

    public boolean isEmpty() {
        return providers.isEmpty() && consumers.isEmpty() && debugSupply.signum() == 0;
    }

    /**
     * 每 tick 清理失联节点：正常机器每个 onPostTick 都会刷新注册，
     * 超过 {@link #STALE_TICKS}（5 秒）未刷新的节点视为残留
     * （崩溃 / 强制卸载等未走 onUnload 注销的异常路径），直接移除。
     */
    public void tick() {
        tickCounter++;

        // 副本遍历：unregisterProvider/unregisterConsumer 会修改 lastSeen 表
        for (WirelessNodeRef ref : new HashMap<WirelessNodeRef, Long>(providerLastSeen).keySet()) {
            Long last = providerLastSeen.get(ref);
            if (last != null && tickCounter - last > STALE_TICKS) {
                unregisterProvider(ref);
            }
        }
        for (WirelessNodeRef ref : new HashMap<WirelessNodeRef, Long>(consumerLastSeen).keySet()) {
            Long last = consumerLastSeen.get(ref);
            if (last != null && tickCounter - last > STALE_TICKS) {
                unregisterConsumer(ref);
            }
        }
    }
}
