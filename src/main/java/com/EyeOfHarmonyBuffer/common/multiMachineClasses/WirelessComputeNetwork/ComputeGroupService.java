package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.*;

public class ComputeGroupService {

    public static final ComputeGroupService INSTANCE = new ComputeGroupService();


    private final Map<UUID, ComputeGroup> groups = new HashMap<UUID, ComputeGroup>();

    private final Map<UUID, UUID> playerToGroup = new HashMap<UUID, UUID>();

    private final Map<UUID, Set<UUID>> pendingInvites = new HashMap<UUID, Set<UUID>>();

    private ComputeGroupService() {}

    public Set<UUID> getGroupMembers(UUID playerUuid) {
        if (playerUuid == null) return Collections.emptySet();
        UUID groupId = playerToGroup.get(playerUuid);
        if (groupId == null) {
            return Collections.singleton(playerUuid);
        }
        ComputeGroup group = groups.get(groupId);
        if (group == null || group.members.isEmpty()) {
            return Collections.singleton(playerUuid);
        }
        return Collections.unmodifiableSet(group.members);
    }

    public ComputeGroup getGroupById(UUID groupId) {
        return groups.get(groupId);
    }

    public ComputeGroup getGroupOfPlayer(UUID player) {
        UUID groupId = playerToGroup.get(player);
        if (groupId == null) return null;
        return groups.get(groupId);
    }

    public ComputeGroup createGroup(UUID creator, String name) {
        if (creator == null) return null;
        if (playerToGroup.containsKey(creator)) {
            return null;
        }
        UUID groupId = UUID.randomUUID();
        if (name == null || name.trim().isEmpty()) {
            name = "Group-" + groupId.toString().substring(0, 8);
        }
        ComputeGroup group = new ComputeGroup(groupId, creator, name);
        groups.put(groupId, group);
        playerToGroup.put(creator, groupId);
        return group;
    }

    public boolean invitePlayer(UUID leader, UUID target) {
        if (leader == null || target == null) return false;
        ComputeGroup group = getGroupOfPlayer(leader);
        if (group == null) return false;
        if (!group.isLeader(leader)) return false;

        if (playerToGroup.containsKey(target)) {
            return false;
        }

        Set<UUID> invites = pendingInvites.get(target);
        if (invites == null) {
            invites = new HashSet<UUID>();
            pendingInvites.put(target, invites);
        }
        invites.add(group.id);
        return true;
    }

    public boolean acceptInvite(UUID player, UUID groupId) {
        if (player == null || groupId == null) return false;
        if (playerToGroup.containsKey(player)) {
            return false;
        }

        Set<UUID> invites = pendingInvites.get(player);
        if (invites == null || !invites.contains(groupId)) {
            return false;
        }

        ComputeGroup group = groups.get(groupId);
        if (group == null) {
            invites.remove(groupId);
            return false;
        }

        group.members.add(player);
        playerToGroup.put(player, groupId);
        invites.remove(groupId);
        if (invites.isEmpty()) {
            pendingInvites.remove(player);
        }
        return true;
    }

    public boolean denyInvite(UUID player, UUID groupId) {
        if (player == null || groupId == null) return false;
        Set<UUID> invites = pendingInvites.get(player);
        if (invites == null || !invites.contains(groupId)) {
            return false;
        }
        invites.remove(groupId);
        if (invites.isEmpty()) {
            pendingInvites.remove(player);
        }
        return true;
    }

    public boolean kickPlayer(UUID leader, UUID target) {
        if (leader == null || target == null) return false;

        ComputeGroup group = getGroupOfPlayer(leader);
        if (group == null) return false;
        if (!group.isLeader(leader)) return false;
        if (leader.equals(target)) return false;

        UUID groupId = playerToGroup.get(target);
        if (groupId == null || !groupId.equals(group.id)) {
            return false;
        }

        group.members.remove(target);
        playerToGroup.remove(target);
        return true;
    }

    public boolean leaveGroup(UUID player) {
        if (player == null) return false;
        ComputeGroup group = getGroupOfPlayer(player);
        if (group == null) return false;

        if (group.isLeader(player)) {
            disbandGroup(group.id);
            return true;
        } else {
            group.members.remove(player);
            playerToGroup.remove(player);
            return true;
        }
    }

    public boolean disbandGroup(UUID groupId) {
        ComputeGroup group = groups.remove(groupId);
        if (group == null) return false;
        for (UUID member : group.members) {
            playerToGroup.remove(member);
        }
        return true;
    }

    public Set<UUID> getPendingInvites(UUID player) {
        Set<UUID> res = pendingInvites.get(player);
        return res == null ? Collections.<UUID>emptySet() : Collections.unmodifiableSet(res);
    }

    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList groupList = new NBTTagList();
        for (ComputeGroup group : groups.values()) {
            NBTTagCompound g = new NBTTagCompound();
            g.setString("Id", group.id.toString());
            g.setString("Leader", group.leader.toString());
            g.setString("Name", group.name == null ? "" : group.name);

            NBTTagList memberList = new NBTTagList();
            for (UUID m : group.members) {
                memberList.appendTag(new NBTTagString(m.toString()));
            }
            g.setTag("Members", memberList);

            groupList.appendTag(g);
        }
        tag.setTag("Groups", groupList);

        NBTTagList ptgList = new NBTTagList();
        for (Map.Entry<UUID, UUID> e : playerToGroup.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Player", e.getKey().toString());
            c.setString("Group", e.getValue().toString());
            ptgList.appendTag(c);
        }
        tag.setTag("PlayerToGroup", ptgList);

        NBTTagList inviteOuterList = new NBTTagList();
        for (Map.Entry<UUID, Set<UUID>> e : pendingInvites.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Player", e.getKey().toString());
            NBTTagList inviteList = new NBTTagList();
            for (UUID gid : e.getValue()) {
                inviteList.appendTag(new NBTTagString(gid.toString()));
            }
            c.setTag("Groups", inviteList);
            inviteOuterList.appendTag(c);
        }
        tag.setTag("PendingInvites", inviteOuterList);
    }

    public void readFromNBT(NBTTagCompound tag) {
        groups.clear();
        playerToGroup.clear();
        pendingInvites.clear();
        if (tag == null) return;

        NBTTagList groupList = tag.getTagList("Groups", 10);
        for (int i = 0; i < groupList.tagCount(); i++) {
            NBTTagCompound g = groupList.getCompoundTagAt(i);
            try {
                UUID id = UUID.fromString(g.getString("Id"));
                UUID leader = UUID.fromString(g.getString("Leader"));
                String name = g.getString("Name");

                ComputeGroup group = new ComputeGroup(id, leader, name);

                group.members.clear();
                NBTTagList memberList = g.getTagList("Members", 8);
                for (int j = 0; j < memberList.tagCount(); j++) {
                    String s = memberList.getStringTagAt(j);
                    try {
                        UUID m = UUID.fromString(s);
                        group.members.add(m);
                    } catch (IllegalArgumentException ex) {
                    }
                }

                group.members.add(leader);

                groups.put(id, group);
            } catch (IllegalArgumentException ex) {
            }
        }

        NBTTagList ptgList = tag.getTagList("PlayerToGroup", 10);
        for (int i = 0; i < ptgList.tagCount(); i++) {
            NBTTagCompound c = ptgList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(c.getString("Player"));
                UUID groupId = UUID.fromString(c.getString("Group"));
                if (groups.containsKey(groupId)) {
                    playerToGroup.put(player, groupId);
                }
            } catch (IllegalArgumentException ex) {
            }
        }

        NBTTagList inviteOuterList = tag.getTagList("PendingInvites", 10);
        for (int i = 0; i < inviteOuterList.tagCount(); i++) {
            NBTTagCompound c = inviteOuterList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(c.getString("Player"));
                NBTTagList inviteList = c.getTagList("Groups", 8);
                Set<UUID> set = new HashSet<UUID>();
                for (int j = 0; j < inviteList.tagCount(); j++) {
                    String s = inviteList.getStringTagAt(j);
                    try {
                        UUID gid = UUID.fromString(s);
                        if (groups.containsKey(gid)) {
                            set.add(gid);
                        }
                    } catch (IllegalArgumentException ex) {
                    }
                }
                if (!set.isEmpty()) {
                    pendingInvites.put(player, set);
                }
            } catch (IllegalArgumentException ex) {
            }
        }

        for (ComputeGroup group : groups.values()) {
            for (UUID m : group.members) {
                if (!playerToGroup.containsKey(m)) {
                    playerToGroup.put(m, group.id);
                }
            }
        }
    }
}
