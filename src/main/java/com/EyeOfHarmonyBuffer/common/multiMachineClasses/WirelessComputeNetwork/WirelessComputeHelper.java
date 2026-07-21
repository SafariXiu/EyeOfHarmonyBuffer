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

    public static boolean isConsumerSatisfied(IWirelessComputeConsumer consumer) {
        if (consumer == null) return false;
        UUID owner = consumer.getOwnerUUID();
        WirelessNodeRef ref = consumer.getWirelessNodeRef();
        return WirelessComputeManager.getInstance().isConsumerSatisfied(owner, ref);
    }
}
