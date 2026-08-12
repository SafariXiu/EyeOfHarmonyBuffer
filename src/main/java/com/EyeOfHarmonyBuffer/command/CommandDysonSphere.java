package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 戴森球阶段切换指令（测试/调试用），作用于执行者所属的 Orundum 队伍。
 * <pre>
 * /dyson stage &lt;1-5&gt;              - 切换到指定阶段（云/框架/贴片三参数预设）
 * /dyson cloud &lt;数量&gt;             - 直接设置戴森云数量（0-50000）
 * /dyson frame &lt;数量&gt;             - 直接设置框架数量（0-500000）
 * /dyson paste &lt;数量&gt;             - 直接设置贴片数量（0-2000000）
 * /dyson reset                    - 重置为未开始（清空全部队伍）
 * </pre>
 */
public class CommandDysonSphere extends CommandBase {

    private static final UUID DEBUG_TEAM = new UUID(0L, 1L);

    @Override
    public String getCommandName() {
        return "dyson";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dyson <stage|cloud|frame|paste|reset>";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "stage", "cloud", "frame", "paste", "reset");
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

        UUID teamId;
        String teamName;
        if (sender instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) sender;
            UUID resolved = OrundumEnergyService.getTeamIdForUser(player.getUniqueID());
            teamId = resolved != null ? resolved : player.getUniqueID();
            teamName = player.getDisplayName();
        } else {
            teamId = DEBUG_TEAM;
            teamName = "DEBUG";
        }

        DysonTeamProgress team = data.getTeam(teamId);
        int newCloud = team == null ? 0 : team.cloudCount;
        int newFrame = team == null ? 0 : team.frameCount;
        int newPaste = team == null ? 0 : team.pasteCount;

        String sub = args[0];
        switch (sub) {
            case "stage": {
                if (args.length < 2) {
                    sendError(sender, "用法: /dyson stage <1-5>");
                    return;
                }
                int stageArg;
                try {
                    stageArg = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sendError(sender, "阶段必须是 1-5 的整数。");
                    return;
                }
                if (stageArg < 1 || stageArg > 5) {
                    sendError(sender, "阶段必须是 1-5 的整数。");
                    return;
                }
                switch (stageArg) {
                    case 1:
                        newCloud = 10_000;
                        newFrame = 0;
                        newPaste = 0;
                        break;
                    case 2:
                        newCloud = 20_000;
                        newFrame = DysonSphereState.FRAME_MIN;
                        newPaste = 50_000;
                        break;
                    case 3:
                        newCloud = DysonSphereState.CLOUD_LEVEL_3;
                        newFrame = DysonSphereState.FRAME_STAGE_2;
                        newPaste = 300_000;
                        break;
                    case 4:
                        newCloud = 5_000;
                        newFrame = DysonSphereState.FRAME_STAGE_3;
                        newPaste = 600_000;
                        break;
                    default:
                        newCloud = 0;
                        newFrame = DysonSphereState.FRAME_COMPLETE;
                        newPaste = DysonSphereState.PASTE_COMPLETE;
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
            case "paste": {
                if (args.length < 2) {
                    sendError(sender, "用法: /dyson paste <数量>");
                    return;
                }
                try {
                    newPaste = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sendError(sender, "数量必须是整数。");
                    return;
                }
                newPaste = Math.max(0, Math.min(DysonSphereState.PASTE_COMPLETE, newPaste));
                break;
            }
            case "reset":
                DysonSphereSystem.resetAll(sender.getEntityWorld());
                sendInfo(sender, "戴森球状态已重置（全部队伍与完工状态清空）。");
                return;
            default:
                sendUsage(sender);
                return;
        }

        DysonSphereSystem.setTeamCounters(sender.getEntityWorld(), teamId, teamName, newCloud, newFrame, newPaste);
        DysonSphereWorldData updated = DysonSphereWorldData.get(sender.getEntityWorld());
        if (updated != null) {
            sendInfo(sender, "戴森球状态已更新（领先者）: 云=" + updated.getCloudCount()
                + " 框架=" + updated.getFrameCount()
                + " 贴片=" + updated.getPasteCount()
                + " 阶段=" + updated.getStage()
                + (updated.getOwnerName().isEmpty() ? "" : " 领先=" + updated.getOwnerName()));
        }
    }

    private void sendUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(
            EnumChatFormatting.YELLOW
                + "用法: /dyson stage <1-5> | /dyson cloud <数量> | /dyson frame <数量> | /dyson paste <数量> | /dyson reset"));
    }

    private void sendInfo(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + msg));
    }

    private void sendError(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + msg));
    }
}
