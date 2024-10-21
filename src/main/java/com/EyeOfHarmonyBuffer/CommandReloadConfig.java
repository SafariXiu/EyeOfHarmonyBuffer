package com.EyeOfHarmonyBuffer;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.EyeOfHarmonyBuffer.Config.Config;

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
            // 调用 Config.reloadConfig() 重新加载所有配置文件
            Config.reloadConfig();

            // 向玩家发送成功消息
            sender.addChatMessage(new ChatComponentText("配置文件已成功重新加载！"));
        } catch (Exception e) {
            // 捕获异常并向玩家发送错误信息
            sender.addChatMessage(new ChatComponentText("配置文件重载失败！错误信息: " + e.getMessage()));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // 仅限管理员使用
    }
}
