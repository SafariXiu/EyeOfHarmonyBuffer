package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.math.BigInteger;
import java.util.UUID;

public final class WirelessComputeHelper {

    private WirelessComputeHelper() {}

    public static void updateProvider(IWirelessComputeProvider provider) {
        if (provider == null) return;
        UUID owner = provider.getOwnerUUID();
        WirelessNodeRef ref = provider.getWirelessNodeRef();
        BigInteger supply = provider.getProvidedCompute();
        WirelessComputeManager.getInstance().registerProvider(owner, ref, supply);
    }

    public static void unregisterProvider(IWirelessComputeProvider provider) {
        if (provider == null) return;
        UUID owner = provider.getOwnerUUID();
        WirelessNodeRef ref = provider.getWirelessNodeRef();
        WirelessComputeManager.getInstance().unregisterProvider(owner, ref);
    }

    public static void updateConsumer(IWirelessComputeConsumer consumer) {
        if (consumer == null) return;
        UUID owner = consumer.getOwnerUUID();
        WirelessNodeRef ref = consumer.getWirelessNodeRef();
        BigInteger demand = consumer.getRequiredCompute();
        WirelessComputeManager.getInstance().registerConsumer(owner, ref, demand);
    }

    public static void unregisterConsumer(IWirelessComputeConsumer consumer) {
        if (consumer == null) return;
        UUID owner = consumer.getOwnerUUID();
        WirelessNodeRef ref = consumer.getWirelessNodeRef();
        WirelessComputeManager.getInstance().unregisterConsumer(owner, ref);
    }

    public static boolean isConsumerSatisfiedPersonal(IWirelessComputeConsumer consumer) {
        if (consumer == null) return false;
        UUID owner = consumer.getOwnerUUID();
        WirelessNodeRef ref = consumer.getWirelessNodeRef();
        return WirelessComputeManager.getInstance().isConsumerSatisfied(owner, ref);
    }

    /**
     * 队伍算力判定：算力网络已全盘接入 Orundum 体系（网络键 = Orundum 队伍），
     * 个人判定即队伍判定——同队成员自动共享本队算力，无需手动维护算力组。
     * 保留该方法名以兼容现有调用点（戴森核心/模块等）。
     */
    public static boolean isConsumerSatisfiedInGroup(IWirelessComputeConsumer consumer) {
        return isConsumerSatisfiedPersonal(consumer);
    }
}
