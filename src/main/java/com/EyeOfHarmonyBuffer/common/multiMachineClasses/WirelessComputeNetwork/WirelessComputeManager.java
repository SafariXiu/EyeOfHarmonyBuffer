package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class WirelessComputeManager {

    private static final WirelessComputeManager INSTANCE = new WirelessComputeManager();

    public static WirelessComputeManager getInstance() {
        return INSTANCE;
    }

    private WirelessComputeManager() {}

    private final Map<UUID, WirelessComputeNetwork> networks = new HashMap<UUID, WirelessComputeNetwork>();

    private WirelessComputeNetwork getOrCreateNetwork(UUID ownerUUID) {
        if (ownerUUID == null) return null;
        WirelessComputeNetwork net = networks.get(ownerUUID);
        if (net == null) {
            net = new WirelessComputeNetwork(ownerUUID);
            networks.put(ownerUUID, net);
        }
        return net;
    }

    public void serverTick(World world) {
        if (world.isRemote) {
            return; // 只在服务端跑
        }

        Iterator<Map.Entry<UUID, WirelessComputeNetwork>> it = networks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WirelessComputeNetwork> entry = it.next();
            WirelessComputeNetwork net = entry.getValue();
            net.tick();
            if (net.isEmpty()) {
                it.remove();
            }
        }
    }

    public void registerProvider(UUID ownerUUID, WirelessNodeRef ref, BigInteger supply) {
        WirelessComputeNetwork net = getOrCreateNetwork(ownerUUID);
        if (net != null) {
            net.registerProvider(ref, supply);
        }
    }

    public void unregisterProvider(UUID ownerUUID, WirelessNodeRef ref) {
        WirelessComputeNetwork net = networks.get(ownerUUID);
        if (net != null) {
            net.unregisterProvider(ref);
        }
    }

    public void registerConsumer(UUID ownerUUID, WirelessNodeRef ref, BigInteger demand) {
        WirelessComputeNetwork net = getOrCreateNetwork(ownerUUID);
        if (net != null) {
            net.registerConsumer(ref, demand);
        }
    }

    public void unregisterConsumer(UUID ownerUUID, WirelessNodeRef ref) {
        WirelessComputeNetwork net = networks.get(ownerUUID);
        if (net != null) {
            net.unregisterConsumer(ref);
        }
    }

    public boolean isConsumerSatisfied(UUID ownerUUID, WirelessNodeRef ref) {
        WirelessComputeNetwork net = networks.get(ownerUUID);
        if (net == null) return false;
        return net.isConsumerSatisfied(ref);
    }

    public BigInteger getTotalSupply(UUID ownerUUID) {
        WirelessComputeNetwork net = networks.get(ownerUUID);
        return net == null ? BigInteger.ZERO : net.getTotalSupply();
    }

    public BigInteger getTotalDemand(UUID ownerUUID) {
        WirelessComputeNetwork net = networks.get(ownerUUID);
        return net == null ? BigInteger.ZERO : net.getTotalDemand();
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        // 目前先留空，将来如果要跨重启保持算力状态再实现
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        networks.clear();
        if (tag == null) return;
    }
}
