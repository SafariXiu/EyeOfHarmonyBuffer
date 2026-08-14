package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import java.math.BigInteger;
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

            manager.addDebugSupply(playerId, delta);
            player.addChatMessage(new ChatComponentText(
                "已增加虚空算力: +" + delta.toString()
                    + "（当前虚空算力: " + manager.getDebugSupply(playerId) + "）"));
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
        // 算力已全盘接入 Orundum 队伍：个人网络即队伍网络（同队自动共享，无需手动维护算力组）
        BigInteger selfSupply = manager.getTotalSupply(playerId);
        BigInteger selfDemand = manager.getTotalDemand(playerId);

        player.addChatMessage(new ChatComponentText(
            "你的队伍算力网络: Supply = " + selfSupply.toString() +
                ", Demand = " + selfDemand.toString()));
        player.addChatMessage(new ChatComponentText("（算力按 Orundum 队伍自动共享，无需手动组队）"));
    }

    private BigInteger parseBigInteger(String s) {
        try {
            return new BigInteger(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
