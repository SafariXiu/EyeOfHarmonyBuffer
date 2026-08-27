package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.common.transition.DimensionTransitionManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 维度转场指令：/eohbwarp [目标维度]
 * 以玩家当前位置为转场中心。仅塔罗斯2（14001）可触发；目标维度默认主世界（0）。
 * 机器驱动后续通过 {@link DimensionTransitionManager#startTransition} 接入，本指令仅用于调试/临时使用。
 */
public class CommandWarp extends CommandBase {

    @Override
    public String getCommandName() {
        return "eohbwarp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/eohbwarp [目标维度] - 从塔罗斯2传送到目标维度（默认主世界），带转场特效";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        int target = MainConfig.DimensionTransitionTargetDimension;
        if (args.length >= 1) {
            try {
                target = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.addChatMessage(new net.minecraft.util.ChatComponentText("[EOHB] 目标维度必须是整数，用法：" + getCommandUsage(sender)));
                return;
            }
        }
        DimensionTransitionManager.startTransition(player,
            (int) Math.floor(player.posX),
            (int) Math.floor(player.posY),
            (int) Math.floor(player.posZ),
            target);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
