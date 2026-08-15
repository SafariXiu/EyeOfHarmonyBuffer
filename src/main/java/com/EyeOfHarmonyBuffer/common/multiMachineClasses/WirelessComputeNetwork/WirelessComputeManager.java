package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;

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

    /** 算力网络键：统一解析为 Orundum 队伍（队伍即算力组，全盘接入 Orundum 体系），无队伍时回落 owner 本人。 */
    private static UUID resolveTeamKey(UUID ownerUUID) {
        if (ownerUUID == null) return null;
        return OrundumEnergyService.getTeamIdForUser(ownerUUID);
    }

    private final Map<UUID, WirelessComputeNetwork> networks = new HashMap<UUID, WirelessComputeNetwork>();

    private WirelessComputeNetwork getOrCreateNetwork(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        if (key == null) return null;
        WirelessComputeNetwork net = networks.get(key);
        if (net == null) {
            net = new WirelessComputeNetwork(key);
            networks.put(key, net);
        }
        return net;
    }

    public WirelessComputeNetwork getNetwork(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        if (key == null) return null;
        return networks.get(key);
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
        UUID key = resolveTeamKey(ownerUUID);
        WirelessComputeNetwork net = key == null ? null : networks.get(key);
        if (net == null) return false;
        return net.isConsumerSatisfied(ref);
    }

    /**
     * 只看本队网络供需（不要求该机器已注册为消费者）：
     * 用于状态显示（Waila/info），避免停机中的机器（如重复核心）误报“算力不足”。
     */
    public boolean isNetworkSatisfiedForOwner(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        WirelessComputeNetwork net = key == null ? null : networks.get(key);
        return net != null && net.isNetworkSatisfied();
    }

    public BigInteger getTotalSupply(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        WirelessComputeNetwork net = key == null ? null : networks.get(key);
        return net == null ? BigInteger.ZERO : net.getTotalSupply();
    }

    public BigInteger getTotalDemand(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        WirelessComputeNetwork net = key == null ? null : networks.get(key);
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
        net.setDebugSupply(supply);
    }

    /** 在现有调试虚空算力上追加（/ocdebug add）。 */
    public void addDebugSupply(UUID ownerUUID, BigInteger delta) {
        if (ownerUUID == null || delta == null || delta.signum() <= 0) return;
        WirelessComputeNetwork net = getOrCreateNetwork(ownerUUID);
        if (net == null) return;
        net.setDebugSupply(net.getDebugSupply().add(delta));
    }

    public BigInteger getDebugSupply(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        if (key == null) return BigInteger.ZERO;
        WirelessComputeNetwork net = networks.get(key);
        return net == null ? BigInteger.ZERO : net.getDebugSupply();
    }

    public void clearDebugSupply(UUID ownerUUID) {
        UUID key = resolveTeamKey(ownerUUID);
        if (key == null) return;
        WirelessComputeNetwork net = networks.get(key);
        if (net != null) {
            net.setDebugSupply(BigInteger.ZERO);
        }
    }
}
