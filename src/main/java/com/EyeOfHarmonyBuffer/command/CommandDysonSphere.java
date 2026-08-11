package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereNetwork;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.PacketDysonSphereState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 戴森球阶段切换指令（测试/调试用）。
 * <pre>
 * /dyson stage &lt;1-5&gt;              - 切换到指定阶段（云/框架数量预设）
 * /dyson cloud &lt;数量&gt;             - 直接设置戴森云数量（0-50000）
 * /dyson frame &lt;数量&gt;             - 直接设置框架数量（0-500000）
 * /dyson reset                    - 重置为未开始
 * </pre>
 */
public class CommandDysonSphere extends CommandBase {

    @Override
    public String getCommandName() {
        return "dyson";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dyson <stage|progress|reset>";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "stage", "cloud", "frame", "reset");
        }
        if (args.length == 2 && "stage".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5");
        }
        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return;
        }

        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            sendError(sender, "你没有权限使用此命令。");
            return;
        }

        DysonSphereWorldData data = DysonSphereWorldData.get(sender.getEntityWorld());
        if (data == null) {
            sendError(sender, "戴森球存档尚未加载。");
            return;
        }

        String sub = args[0];
        int newStage = data.getStage();
        float newProgress = data.getProgress();
        int newCloud = data.getCloudCount();
        int newFrame = data.getFrameCount();
        String owner = data.getOwnerName();

        switch (sub) {
            case "stage": {
                if (args.length < 2) {
                    sendError(sender, "用法: /dyson stage <1-5> [progress]");
                    return;
                }
                try {
                    newStage = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sendError(sender, "阶段必须是 1-5 的整数。");
                    return;
                }
                if (newStage < 1 || newStage > 5) {
                    sendError(sender, "阶段必须是 1-5 的整数。");
                    return;
                }
                switch (newStage) {
                    case 1:
                        newCloud = 10_000;
                        newFrame = 0;
                        break;
                    case 2:
                        newCloud = 20_000;
                        newFrame = DysonSphereState.FRAME_MIN;
                        break;
                    case 3:
                        newCloud = DysonSphereState.CLOUD_LEVEL_3;
                        newFrame = DysonSphereState.FRAME_STAGE_2;
                        break;
                    case 4:
                        newCloud = 5_000;
                        newFrame = DysonSphereState.FRAME_STAGE_3;
                        break;
                    default:
                        newCloud = 0;
                        newFrame = DysonSphereState.FRAME_COMPLETE;
                        break;
                }
                break;
            }
            case "cloud": {
                if (args.length < 2) {
                    sendError(sender, "用法: /dyson cloud <数量>");
                    return;
                }
                try {
                    newCloud = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sendError(sender, "数量必须是整数。");
                    return;
                }
                newCloud = Math.max(0, Math.min(DysonSphereState.CLOUD_CAP, newCloud));
                break;
            }
            case "frame": {
                if (args.length < 2) {
                    sendError(sender, "用法: /dyson frame <数量>");
                    return;
                }
                try {
                    newFrame = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sendError(sender, "数量必须是整数。");
                    return;
                }
                newFrame = Math.max(0, Math.min(DysonSphereState.FRAME_COMPLETE, newFrame));
                break;
            }
            case "reset":
                newStage = DysonSphereState.STAGE_NONE;
                newProgress = 0.0F;
                newCloud = 0;
                newFrame = 0;
                owner = "";
                break;
            default:
                sendUsage(sender);
                return;
        }

        newProgress = Math.max(
            (float) newCloud / DysonSphereState.CLOUD_CAP,
            (float) newFrame / DysonSphereState.FRAME_COMPLETE);

        data.setState(newStage, newProgress, newCloud, newFrame, owner);
        DysonSphereNetwork.INSTANCE.sendToAll(
            new PacketDysonSphereState(newStage, newProgress, newCloud, newFrame, owner));

        sendInfo(sender, "戴森球状态已更新: 云=" + newCloud + " 框架=" + newFrame
            + " 阶段=" + newStage
            + (owner.isEmpty() ? "" : " 归属=" + owner));
    }

    private void sendUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(
            EnumChatFormatting.YELLOW
                + "用法: /dyson stage <1-5> | /dyson cloud <数量> | /dyson frame <数量> | /dyson reset"));
    }

    private void sendInfo(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + msg));
    }

    private void sendError(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + msg));
    }
}
