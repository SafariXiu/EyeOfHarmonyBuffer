package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ComputeGroup {

    public final UUID id;
    public UUID leader;
    public String name;

    /** 成员集合（至少包含 leader） */
    public final Set<UUID> members = new HashSet<UUID>();

    public ComputeGroup(UUID id, UUID leader, String name) {
        this.id = id;
        this.leader = leader;
        this.name = name;
        this.members.add(leader);
    }

    public boolean isMember(UUID player) {
        return player != null && members.contains(player);
    }

    public boolean isLeader(UUID player) {
        return player != null && player.equals(leader);
    }
}
