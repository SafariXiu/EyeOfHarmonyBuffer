package com.EyeOfHarmonyBuffer.Command;

import com.EyeOfHarmonyBuffer.utils.EOHBChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class CommandShowConfigLinks implements ICommand {

    @Override
    public String getCommandName() {
        return "EOHBconfiglist";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/EOHBconfiglist";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("ecfglist");
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) sender;
        Minecraft mc = Minecraft.getMinecraft();

        player.addChatMessage(new ChatComponentText(EOHB_Text_SeparatingLine));
        player.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin00));
        player.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01));
        player.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01_1));

        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin02, "/openEOHBconfig main.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin03, "/openEOHBconfig items.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin04, "/openEOHBconfig fluids.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin05, "/openEOHBconfig MachineLoaderConfig.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin06, "/openEOHBconfig folder");

        player.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin07));
        player.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01_1));
        player.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin07_2));
        player.addChatMessage(new ChatComponentText(EOHB_Text_SeparatingLine));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }

}
