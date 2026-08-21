package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.client.ReactorVideoPlayer;
import com.EyeOfHarmonyBuffer.client.ReactorVideoState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class CommandReactorVideo extends CommandBase {

    @Override
    public String getCommandName() {
        return "reactorvideo";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/reactorvideo <99999|hydro|stop>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText("用法: " + getCommandUsage(sender)));
            return;
        }

        String mode = args[0].toLowerCase();
        if (mode.equals("99999")) {
            ReactorVideoPlayer.INSTANCE.play(ReactorVideoState.POWER_99999);
            sender.addChatMessage(new ChatComponentText("播放反应堆视频: 99999MW"));
        } else if (mode.equals("hydro") || mode.equals("hydrogen")) {
            ReactorVideoPlayer.INSTANCE.play(ReactorVideoState.HYDROGEN_DETONATION);
            sender.addChatMessage(new ChatComponentText("播放反应堆视频: 氢气爆炸"));
        } else if (mode.equals("stop")) {
            ReactorVideoPlayer.INSTANCE.stop();
            sender.addChatMessage(new ChatComponentText("停止播放反应堆视频"));
        } else {
            sender.addChatMessage(new ChatComponentText("未知模式: " + mode));
            sender.addChatMessage(new ChatComponentText("用法: " + getCommandUsage(sender)));
        }
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
