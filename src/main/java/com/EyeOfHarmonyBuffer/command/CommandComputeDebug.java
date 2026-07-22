package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.ComputeGroup;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.ComputeGroupService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeManager;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeNetwork;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import java.math.BigInteger;
import java.util.Set;
import java.util.UUID;

public class CommandComputeDebug extends CommandBase {

    @Override
    public String getCommandName() {
        return "ocdebug";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ocdebug <add|set|clear|info> ...";
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
        UUID playerId = player.getUniqueID();
        WirelessComputeManager manager = WirelessComputeManager.getInstance();

        if ("add".equals(sub)) {
            if (args.length < 2) {
                player.addChatMessage(new ChatComponentText("用法: /ocdebug add <amount>"));
                return;
            }
            BigInteger delta = parseBigInteger(args[1]);
            if (delta == null || delta.signum() <= 0) {
                player.addChatMessage(new ChatComponentText("amount 必须是正整数。"));
                return;
            }

            BigInteger beforeSupply = manager.getTotalSupply(playerId);
            WirelessComputeNetwork net = manager.getNetwork(playerId);
            BigInteger oldDebug = BigInteger.ZERO;
            if (net != null) {
            }

            player.addChatMessage(new ChatComponentText("当前实现中未启用 add 子命令，请使用 /ocdebug set 或 /ocdebug clear。"));
        } else if ("set".equals(sub)) {
            if (args.length < 2) {
                player.addChatMessage(new ChatComponentText("用法: /ocdebug set <amount>"));
                return;
            }
            BigInteger value = parseBigInteger(args[1]);
            if (value == null || value.signum() < 0) {
                player.addChatMessage(new ChatComponentText("amount 必须是非负整数。"));
                return;
            }

            manager.setDebugSupply(playerId, value);
            player.addChatMessage(new ChatComponentText("已将你的虚空算力设置为: " + value.toString()));
        } else if ("clear".equals(sub)) {
            manager.clearDebugSupply(playerId);
            player.addChatMessage(new ChatComponentText("已清除你的虚空算力。"));
        } else if ("info".equals(sub)) {
            // 显示个人网络与当前组的总供给/需求
            showInfo(player, playerId, manager);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void showInfo(EntityPlayer player, UUID playerId, WirelessComputeManager manager) {
        BigInteger selfSupply = manager.getTotalSupply(playerId);
        BigInteger selfDemand = manager.getTotalDemand(playerId);

        player.addChatMessage(new ChatComponentText(
            "你的个人网络: Supply = " + selfSupply.toString() +
                ", Demand = " + selfDemand.toString()));

        ComputeGroupService groupService = ComputeGroupService.INSTANCE;
        ComputeGroup group = groupService.getGroupOfPlayer(playerId);
        if (group == null) {
            player.addChatMessage(new ChatComponentText("你当前不在任何算力组中（组总量 = 个人总量）。"));
            return;
        }

        Set<UUID> members = groupService.getGroupMembers(playerId);
        BigInteger groupSupply = BigInteger.ZERO;
        BigInteger groupDemand = BigInteger.ZERO;
        for (UUID m : members) {
            groupSupply = groupSupply.add(manager.getTotalSupply(m));
            groupDemand = groupDemand.add(manager.getTotalDemand(m));
        }

        player.addChatMessage(new ChatComponentText(
            "当前算力组: " + group.name +
                " (成员数=" + members.size() + ")"));
        player.addChatMessage(new ChatComponentText(
            "组总网络: Supply = " + groupSupply.toString() +
                ", Demand = " + groupDemand.toString()));
    }

    private BigInteger parseBigInteger(String s) {
        try {
            return new BigInteger(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
