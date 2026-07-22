package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Set;
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

    public static boolean isConsumerSatisfiedInGroup(IWirelessComputeConsumer consumer) {
        if (consumer == null) return false;

        UUID owner = consumer.getOwnerUUID();
        if (owner == null) return false;

        WirelessNodeRef ref = consumer.getWirelessNodeRef();
        if (ref == null) return false;

        WirelessComputeManager manager = WirelessComputeManager.getInstance();

        WirelessComputeNetwork selfNet = manager.getNetwork(owner);
        if (selfNet == null || !selfNet.isConsumerRegistered(ref)) {
            return false;
        }

        Set<UUID> members = ComputeGroupService.INSTANCE.getGroupMembers(owner);
        if (members == null || members.isEmpty()) {
            members = Collections.singleton(owner);
        }

        BigInteger totalSupply = BigInteger.ZERO;
        BigInteger totalDemand = BigInteger.ZERO;

        for (UUID member : members) {
            WirelessComputeNetwork net = manager.getNetwork(member);
            if (net != null) {
                totalSupply = totalSupply.add(net.getTotalSupply());
                totalDemand = totalDemand.add(net.getTotalDemand());
            }
        }

        return totalSupply.compareTo(totalDemand) >= 0;
    }
}
