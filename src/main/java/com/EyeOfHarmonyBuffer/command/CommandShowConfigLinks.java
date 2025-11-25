package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.utils.EOHBChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.Arrays;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class CommandShowConfigLinks implements ICommand {

    @Override
    public String getCommandName() {
        return "eoh_configlist";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/eoh_configlist";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("ecfglist");
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();

        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin02, "/openEOHBconfig main.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin03, "/openEOHBconfig items.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin04, "/openEOHBconfig fluids.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin05, "/openEOHBconfig MachineLoaderConfig.cfg");
        EOHBChatUtils.sendConfigLink(mc, EOHB_Client_PlayerJoin06, "/openEOHBconfig folder");
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
