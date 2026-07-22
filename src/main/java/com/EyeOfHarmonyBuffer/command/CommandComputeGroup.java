package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.ComputeGroup;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.ComputeGroupService;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.util.Set;
import java.util.UUID;

public class CommandComputeGroup extends CommandBase {

    @Override
    public String getCommandName() {
        return "ocgroup";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ocgroup <create|invite|accept|deny|kick|leave|info> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayer player = getCommandSenderAsPlayer(sender);

        if (args.length == 0) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String sub = args[0].toLowerCase();
        ComputeGroupService service = ComputeGroupService.INSTANCE;
        UUID playerId = player.getUniqueID();

        if ("create".equals(sub)) {
            handleCreate(player, args, service, playerId);
        } else if ("invite".equals(sub)) {
            handleInvite(player, args, service, playerId);
        } else if ("accept".equals(sub)) {
            handleAccept(player, args, service, playerId);
        } else if ("deny".equals(sub)) {
            handleDeny(player, args, service, playerId);
        } else if ("kick".equals(sub)) {
            handleKick(player, args, service, playerId);
        } else if ("leave".equals(sub)) {
            handleLeave(player, service, playerId);
        } else if ("info".equals(sub)) {
            handleInfo(player, service, playerId);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void handleCreate(EntityPlayer player, String[] args,
                              ComputeGroupService service, UUID playerId) {
        String name = args.length >= 2 ? joinStrings(args, 1) : null;

        if (service.getGroupOfPlayer(playerId) != null) {
            player.addChatMessage(new ChatComponentText("你已经在一个算力组中，不能重复创建。"));
            return;
        }

        ComputeGroup group = service.createGroup(playerId, name);
        if (group == null) {
            player.addChatMessage(new ChatComponentText("创建算力组失败。"));
        } else {
            player.addChatMessage(new ChatComponentText(
                "成功创建算力组: " + group.name + " (ID=" + group.id.toString().substring(0, 8) + ")"));
        }
    }

    private void handleInvite(EntityPlayer player, String[] args,
                              ComputeGroupService service, UUID playerId) {
        if (args.length < 2) {
            player.addChatMessage(new ChatComponentText("用法: /ocgroup invite <player>"));
            return;
        }

        ComputeGroup group = service.getGroupOfPlayer(playerId);
        if (group == null || !group.isLeader(playerId)) {
            player.addChatMessage(new ChatComponentText("你不是任何算力组的组长，无法邀请。"));
            return;
        }

        String targetName = args[1];
        EntityPlayer target = getPlayerByName(targetName);
        if (target == null) {
            player.addChatMessage(new ChatComponentText("找不到玩家: " + targetName));
            return;
        }

        UUID targetId = target.getUniqueID();
        if (service.getGroupOfPlayer(targetId) != null) {
            player.addChatMessage(new ChatComponentText("该玩家已经在某个算力组中。"));
            return;
        }

        boolean ok = service.invitePlayer(playerId, targetId);
        if (!ok) {
            player.addChatMessage(new ChatComponentText("邀请失败。"));
            return;
        }

        player.addChatMessage(new ChatComponentText("已邀请 " + target.getCommandSenderName() + " 加入算力组。"));
        target.addChatMessage(new ChatComponentText(
            player.getCommandSenderName() + " 邀请你加入算力组 " + group.name +
                "，使用 /ocgroup accept 或 /ocgroup deny 来接受/拒绝。"));
    }

    private void handleAccept(EntityPlayer player, String[] args,
                              ComputeGroupService service, UUID playerId) {
        Set<UUID> invites = service.getPendingInvites(playerId);
        if (invites.isEmpty()) {
            player.addChatMessage(new ChatComponentText("你当前没有任何算力组邀请。"));
            return;
        }

        UUID targetGroupId;
        if (args.length >= 2) {
            String prefix = args[1];
            targetGroupId = null;
            for (UUID gid : invites) {
                if (gid.toString().startsWith(prefix)) {
                    targetGroupId = gid;
                    break;
                }
            }
            if (targetGroupId == null) {
                player.addChatMessage(new ChatComponentText("没有匹配该 ID 前缀的算力组邀请。"));
                return;
            }
        } else {
            targetGroupId = invites.iterator().next();
        }

        boolean ok = service.acceptInvite(playerId, targetGroupId);
        if (!ok) {
            player.addChatMessage(new ChatComponentText("接受邀请失败。"));
            return;
        }

        ComputeGroup group = service.getGroupById(targetGroupId);
        player.addChatMessage(new ChatComponentText("你加入了算力组: " + (group != null ? group.name : "Unknown")));
    }

    private void handleDeny(EntityPlayer player, String[] args,
                            ComputeGroupService service, UUID playerId) {
        Set<UUID> invites = service.getPendingInvites(playerId);
        if (invites.isEmpty()) {
            player.addChatMessage(new ChatComponentText("你当前没有任何算力组邀请。"));
            return;
        }

        UUID targetGroupId;
        if (args.length >= 2) {
            String prefix = args[1];
            targetGroupId = null;
            for (UUID gid : invites) {
                if (gid.toString().startsWith(prefix)) {
                    targetGroupId = gid;
                    break;
                }
            }
            if (targetGroupId == null) {
                player.addChatMessage(new ChatComponentText("没有匹配该 ID 前缀的算力组邀请。"));
                return;
            }
        } else {
            targetGroupId = invites.iterator().next();
        }

        boolean ok = service.denyInvite(playerId, targetGroupId);
        if (!ok) {
            player.addChatMessage(new ChatComponentText("拒绝邀请失败。"));
        } else {
            player.addChatMessage(new ChatComponentText("你拒绝了该算力组的邀请。"));
        }
    }

    private void handleKick(EntityPlayer player, String[] args,
                            ComputeGroupService service, UUID playerId) {
        if (args.length < 2) {
            player.addChatMessage(new ChatComponentText("用法: /ocgroup kick <player>"));
            return;
        }

        ComputeGroup group = service.getGroupOfPlayer(playerId);
        if (group == null || !group.isLeader(playerId)) {
            player.addChatMessage(new ChatComponentText("你不是任何算力组的组长，无法踢人。"));
            return;
        }

        String targetName = args[1];
        EntityPlayer target = getPlayerByName(targetName);
        if (target == null) {
            player.addChatMessage(new ChatComponentText("找不到玩家: " + targetName));
            return;
        }

        UUID targetId = target.getUniqueID();
        boolean ok = service.kickPlayer(playerId, targetId);
        if (!ok) {
            player.addChatMessage(new ChatComponentText("踢出玩家失败。"));
        } else {
            player.addChatMessage(new ChatComponentText("已将 " + target.getCommandSenderName() + " 踢出算力组。"));
            target.addChatMessage(new ChatComponentText("你被踢出了算力组 " + group.name + "。"));
        }
    }

    private void handleLeave(EntityPlayer player,
                             ComputeGroupService service, UUID playerId) {
        ComputeGroup group = service.getGroupOfPlayer(playerId);
        if (group == null) {
            player.addChatMessage(new ChatComponentText("你当前不在任何算力组中。"));
            return;
        }

        boolean ok = service.leaveGroup(playerId);
        if (!ok) {
            player.addChatMessage(new ChatComponentText("退出算力组失败。"));
        } else {
            if (group.isLeader(playerId)) {
                player.addChatMessage(new ChatComponentText("你解散了算力组: " + group.name));
            } else {
                player.addChatMessage(new ChatComponentText("你离开了算力组: " + group.name));
            }
        }
    }

    private void handleInfo(EntityPlayer player,
                            ComputeGroupService service, UUID playerId) {
        ComputeGroup group = service.getGroupOfPlayer(playerId);
        if (group == null) {
            player.addChatMessage(new ChatComponentText("你当前不在任何算力组中。"));
            return;
        }

        player.addChatMessage(new ChatComponentText(
            "算力组: " + group.name +
                " (ID=" + group.id.toString().substring(0, 8) + ")"));
        player.addChatMessage(new ChatComponentText("组长: " + getNameByUUID(group.leader)));

        StringBuilder sb = new StringBuilder("成员: ");
        boolean first = true;
        for (UUID m : group.members) {
            if (!first) sb.append(", ");
            sb.append(getNameByUUID(m));
            first = false;
        }
        player.addChatMessage(new ChatComponentText(sb.toString()));
    }

    private String joinStrings(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private EntityPlayer getPlayerByName(String name) {
        return MinecraftServer.getServer().getConfigurationManager().func_152612_a(name);
    }

    private String getNameByUUID(UUID uuid) {
        if (uuid == null) return "Unknown";
        for (Object obj : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) obj;
                if (uuid.equals(p.getUniqueID())) {
                    return p.getCommandSenderName();
                }
            }
        }
        return uuid.toString().substring(0, 8);
    }
}
