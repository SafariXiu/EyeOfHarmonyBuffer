package com.EyeOfHarmonyBuffer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class CommandOpenConfig implements ICommand {

    @Override
    public String getCommandName() {
        return "openEOHBconfig";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/openEOHBconfig <file.cfg|folder>";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("oeohbcfg");
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        try {
            File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config/EyeOfHarmonyBuffer");

            if (!configDir.exists()) {
                sender.addChatMessage(new ChatComponentText("§c配置目录不存在！"));
                return;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("folder")) {
                Desktop.getDesktop().open(configDir);
                sender.addChatMessage(new ChatComponentText("§a已打开配置文件夹。"));
                return;
            }

            String fileName = args.length > 0 ? args[0] : "main.cfg";
            File target = new File(configDir, fileName);

            if (!target.exists()) {
                target.createNewFile();
            }

            Desktop.getDesktop().open(target);
            sender.addChatMessage(new ChatComponentText("§a已打开：" + target.getName()));
        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText("§c打开配置失败：" + e.getMessage()));
        }
    }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) { return null; }
    @Override public boolean isUsernameIndex(String[] args, int index) { return false; }
    @Override public int compareTo(Object o) { return 0; }
}
