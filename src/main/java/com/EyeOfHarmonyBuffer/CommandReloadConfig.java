package com.EyeOfHarmonyBuffer;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class CommandReloadConfig extends CommandBase {

    @Override
    public String getCommandName() {
        return "eoh_reloadconfig"; // 命令名称
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/eoh_reloadconfig - 重新加载 EyeOfHarmonyBuffer 的配置文件";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            Config.reloadConfig();

            sender.addChatMessage(new ChatComponentText("配置文件已成功重新加载！"));
        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText("配置文件重载失败！错误信息: " + e.getMessage()));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}