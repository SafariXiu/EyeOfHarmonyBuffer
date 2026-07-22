package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class WirelessComputeManager {

    private static final WirelessNodeRef DEBUG_REF =
        new WirelessNodeRef(Integer.MIN_VALUE, 0, 0, 0);

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

    public WirelessComputeNetwork getNetwork(UUID ownerUUID) {
        if (ownerUUID == null) return null;
        return networks.get(ownerUUID);
    }

    public void serverTick(World world) {
        if (world.isRemote) {
            return;
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

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (tag == null) {
            tag = new NBTTagCompound();
        }

        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        networks.clear();
    }

    public void setDebugSupply(UUID ownerUUID, BigInteger supply) {
        if (ownerUUID == null || supply == null) return;
        WirelessComputeNetwork net = getOrCreateNetwork(ownerUUID);
        if (net == null) return;

        if (supply.signum() <= 0) {
            net.unregisterProvider(DEBUG_REF);
        } else {
            net.registerProvider(DEBUG_REF, supply);
        }
    }

    public void clearDebugSupply(UUID ownerUUID) {
        if (ownerUUID == null) return;
        WirelessComputeNetwork net = networks.get(ownerUUID);
        if (net != null) {
            net.unregisterProvider(DEBUG_REF);
        }
    }
}
