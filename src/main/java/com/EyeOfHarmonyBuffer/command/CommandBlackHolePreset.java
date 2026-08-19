package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.blackhole.client.SkyProviderEmeraldThrone;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/**
 * 黑洞渲染调参命令（客户端）：
 * {@code /bhpreset} —— 切换到下一个参数预设并显示当前值；
 * {@code /bhpreset <n>} —— 直接指定预设编号。
 */
public class CommandBlackHolePreset extends CommandBase {

    @Override
    public String getCommandName() {
        return "bhpreset";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bhpreset [0-7]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0) {
            try {
                SkyProviderEmeraldThrone.setPreset(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText("[EOHB] 用法: /bhpreset [0-7]"));
            }
        } else {
            SkyProviderEmeraldThrone.nextPreset();
        }
    }
}
