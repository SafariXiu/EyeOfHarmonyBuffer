package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import gregtech.commands.GTBaseCommand;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CommandOrundum extends GTBaseCommand {

    @Override
    public String getCommandName() {
        return "orundum";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/orundum <add|set|join|kick|leave|display>";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "add", "set", "join", "kick", "leave", "display");
        }

        if (args.length >= 2
            && Arrays.asList("add", "set", "join", "kick", "display").contains(args[0])) {
            return getListOfStringsMatchingLastWord(args, getAllUsernames());
        }

        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return;
        }

        String sub = args[0];

        try {
            switch (sub) {
                case "add":
                case "set":
                case "display":
                case "kick":
                    if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                        sendError(sender, "你没有权限使用此子命令。");
                        return;
                    }
                    break;
                case "join":
                case "leave":
                    break;
                default:
                    sendError(sender, "未知子命令: " + sub);
                    sendUsage(sender);
                    return;
            }

            switch (sub) {
                case "add":
                    handleAdd(sender, args);
                    break;
                case "set":
                    handleSet(sender, args);
                    break;
                case "join":
                    handleJoin(sender, args);
                    break;
                case "display":
                    handleDisplay(sender, args);
                    break;
                case "kick":
                    handleKick(sender, args);
                    break;
                case "leave":
                    handleLeave(sender, args);
                    break;
            }

        } catch (Exception e) {
            sendError(sender, "执行命令时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleAdd(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Usage: /orundum add <player> <amount>");
            return;
        }

        String username = args[1];
        String amountStr = args[2];

        UUID uuid = SpaceProjectManager.getPlayerUUIDFromName(username);
        if (uuid == null) {
            sendError(sender, "Player not found: " + username);
            return;
        }

        BigInteger delta;
        try {
            delta = new BigInteger(amountStr);
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid amount: " + amountStr);
            return;
        }

        boolean ok = OrundumEnergyService.changeOrundumForUser(uuid, delta);
        BigInteger current = OrundumEnergyService.getOrundumForUser(uuid);

        String formattedUser = EnumChatFormatting.BLUE + username + EnumChatFormatting.RESET;
        String formattedDelta = EnumChatFormatting.RED + delta.toString() + EnumChatFormatting.RESET;
        String formattedCurrent = EnumChatFormatting.RED + current.toString() + EnumChatFormatting.RESET;

        if (ok) {
            sendInfo(
                sender,
                "Added " + formattedDelta + " Orundum to " + formattedUser
                    + ". Now has " + formattedCurrent + " Orundum.");
        } else {
            sendError(
                sender,
                "Failed to add " + formattedDelta
                    + " Orundum to "
                    + formattedUser
                    + " (would become negative).");
        }
    }

    private void handleSet(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Usage: /orundum set <player> <amount>");
            return;
        }

        String username = args[1];
        String amountStr = args[2];

        UUID uuid = SpaceProjectManager.getPlayerUUIDFromName(username);
        if (uuid == null) {
            sendError(sender, "Player not found: " + username);
            return;
        }

        BigInteger value;
        try {
            value = new BigInteger(amountStr);
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid amount: " + amountStr);
            return;
        }

        if (value.signum() < 0) {
            sendError(sender, "Cannot set Orundum to a negative value.");
            return;
        }

        UUID teamId = SpaceProjectManager.getLeader(uuid);
        OrundumEnergyService.setOrundumForTeam(teamId, value);

        String formattedUser = EnumChatFormatting.BLUE + username + EnumChatFormatting.RESET;
        String formattedValue = EnumChatFormatting.RED + value.toString() + EnumChatFormatting.RESET;

        sendInfo(
            sender,
            "Set " + formattedUser + "'s Orundum network to " + formattedValue + " Orundum.");
    }

    private void handleJoin(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Usage: /orundum join <user_joining> <user_to_join>");
            return;
        }

        String usernameSubject = args[1];
        String usernameTeam = args[2];

        UUID uuidSubject = SpaceProjectManager.getPlayerUUIDFromName(usernameSubject);
        UUID uuidTeam = SpaceProjectManager.getPlayerUUIDFromName(usernameTeam);

        if (uuidSubject == null) {
            sendError(sender, "Player not found: " + usernameSubject);
            return;
        }
        if (uuidTeam == null) {
            sendError(sender, "Player not found: " + usernameTeam);
            return;
        }

        String formattedSubject = EnumChatFormatting.BLUE + usernameSubject + EnumChatFormatting.RESET;
        String formattedTeam = EnumChatFormatting.BLUE + usernameTeam + EnumChatFormatting.RESET;

        if (uuidSubject.equals(uuidTeam)) {
            SpaceProjectManager.putInTeam(uuidSubject, uuidSubject);
            sendInfo(sender, "User " + formattedSubject + " has rejoined their own Orundum network.");
            return;
        }

        UUID leaderSubject = SpaceProjectManager.getLeader(uuidSubject);
        UUID leaderTeam = SpaceProjectManager.getLeader(uuidTeam);

        if (leaderSubject.equals(leaderTeam)) {
            sendInfo(sender, "They are already in the same Orundum network.");
            return;
        }

        SpaceProjectManager.putInTeam(uuidSubject, uuidTeam);

        sendInfo(
            sender,
            "Success! " + formattedSubject + " has joined "
                + formattedTeam + "'s Orundum network.");
        sendInfo(
            sender,
            "To undo this, use /orundum join "
                + usernameSubject
                + " "
                + usernameSubject
                + " to rejoin own network.");
    }

    private void handleDisplay(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /orundum display <player>");
            return;
        }

        String username = args[1];
        UUID uuid = SpaceProjectManager.getPlayerUUIDFromName(username);
        if (uuid == null) {
            sendError(sender, "Player not found: " + username);
            return;
        }

        UUID teamId = SpaceProjectManager.getLeader(uuid);
        BigInteger value = OrundumEnergyService.getOrundumForTeam(teamId);

        String formattedUser = EnumChatFormatting.BLUE + username + EnumChatFormatting.RESET;
        String formattedValue = EnumChatFormatting.RED + value.toString() + EnumChatFormatting.RESET;

        sendInfo(
            sender,
            "User " + formattedUser
                + " has "
                + formattedValue
                + " Orundum in their network.");

        if (!uuid.equals(teamId)) {
            String leaderName = SpaceProjectManager.getPlayerNameFromUUID(teamId);
            sendInfo(
                sender,
                "User " + formattedUser
                    + " is currently in Orundum network of "
                    + EnumChatFormatting.BLUE
                    + leaderName
                    + EnumChatFormatting.RESET
                    + ".");
        }
    }

    /** 自己退队：回到以自己为队长的独立网络。 */
    private void handleLeave(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            sendError(sender, "Only players can leave a team. Console can use /orundum kick <player>.");
            return;
        }

        EntityPlayer player = (EntityPlayer) sender;
        UUID self = player.getUniqueID();
        UUID leader = SpaceProjectManager.getLeader(self);
        if (leader == null || leader.equals(self)) {
            sendError(sender, "You are not in a team.");
            return;
        }

        String leaderName = SpaceProjectManager.getPlayerNameFromUUID(leader);
        SpaceProjectManager.putInTeam(self, self);
        sendInfo(sender, "You have left " + leaderName + "'s Orundum network and returned to your own.");
    }

    /** 队长踢人（或 OP 代踢）：被踢者回到以自己为队长的独立网络。 */
    private void handleKick(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /orundum kick <player>");
            return;
        }

        String username = args[1];
        UUID target = SpaceProjectManager.getPlayerUUIDFromName(username);
        if (target == null) {
            sendError(sender, "Player not found: " + username);
            return;
        }

        UUID senderUuid = sender instanceof EntityPlayer ? ((EntityPlayer) sender).getUniqueID() : null;
        UUID targetLeader = SpaceProjectManager.getLeader(target);

        boolean isLeader = senderUuid != null && senderUuid.equals(targetLeader);
        boolean isAdmin = sender.canCommandSenderUseCommand(2, getCommandName());
        if (!isLeader && !isAdmin) {
            sendError(sender, "Only the team leader can kick " + username + ".");
            return;
        }
        if (senderUuid != null && senderUuid.equals(target)) {
            sendError(sender, "You cannot kick yourself. Use /orundum leave instead.");
            return;
        }
        if (targetLeader == null || targetLeader.equals(target)) {
            sendError(sender, username + " is not in a team.");
            return;
        }

        String leaderName = SpaceProjectManager.getPlayerNameFromUUID(targetLeader);
        SpaceProjectManager.putInTeam(target, target);

        sendInfo(
            sender,
            username + " has been removed from " + leaderName + "'s Orundum network.");

        EntityPlayerMP targetPlayer = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(username);
        if (targetPlayer != null) {
            targetPlayer.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED
                        + "You have been removed from "
                        + leaderName
                        + "'s Orundum network by "
                        + sender.getCommandSenderName()
                        + "."));
        }
    }

    private void sendUsage(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.YELLOW
                + "Usage: /orundum <add|set|join|kick|leave|display>"));
        sender.addChatMessage(
            new ChatComponentText("/orundum add <player> <amount>    - add Orundum (can be negative)"));
        sender.addChatMessage(
            new ChatComponentText("/orundum set <player> <amount>    - set Orundum (non‑negative)"));
        sender.addChatMessage(
            new ChatComponentText("/orundum join <user_joining> <user_to_join> - share Orundum network"));
        sender.addChatMessage(
            new ChatComponentText("/orundum kick <player>              - leader removes a member from the network"));
        sender.addChatMessage(
            new ChatComponentText("/orundum leave                      - leave your current network"));
        sender.addChatMessage(
            new ChatComponentText("/orundum display <player>         - show Orundum in network"));
    }

    private void sendInfo(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + msg));
    }

    private void sendError(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + msg));
    }

    // 命令整体放行（0）：组相关的 join/leave 任何人可用；add/set/kick/display 在 processCommand 内单独查 OP2。
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
