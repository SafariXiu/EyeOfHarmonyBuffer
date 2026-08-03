package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandTalosRiverConfluence extends CommandBase {

    @Override
    public String getCommandName() {
        return "talosRiverConfluence";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosRiverConfluence <level> [index] - 传送到指定级别河流的汇入点"
            + "（0=主河入海口, 1=一级支流汇入点, 2=二级支流汇入点；"
            + "index 为按玩家距离从近到远的序号，默认 1=最近）";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("必须由玩家在服务端执行。"));
            return;
        }

        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText("用法: " + getCommandUsage(sender)));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.addChatMessage(new ChatComponentText(
                "级别参数无效: " + args[0] + "（应为 0=主河, 1=一级支流, 2=二级支流）"
            ));
            return;
        }

        if (level < 0 || level > 2) {
            sender.addChatMessage(new ChatComponentText(
                "级别参数无效: " + level + "（应为 0=主河, 1=一级支流, 2=二级支流）"
            ));
            return;
        }

        int index = 1;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText(
                    "序号参数无效: " + args[1] + "（应为正整数，1 为最近）"
                ));
                return;
            }
            if (index < 1) {
                sender.addChatMessage(new ChatComponentText(
                    "序号参数无效: " + index + "（从 1 开始，1 为最近）"
                ));
                return;
            }
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        int worldSeedInt = TalosRiverSystem.getWorldSeedInt(world);

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        int superId = TalosLandMask.getSuperId(px, pz, worldSeedInt);
        if (superId == 0) {
            sender.addChatMessage(new ChatComponentText(
                "当前位置 superId=0（海洋或未定义区域），无法在超级大陆河网中查找汇入点。"
            ));
            return;
        }

        List<TalosRiverSystem.RiverConfluence> confluences =
            TalosRiverSystem.listConfluencesOnCurrentSupercontinent(
                px, pz,
                worldSeedInt,
                level
            );

        if (confluences.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在当前超级大陆 (superId=%d) 未找到 %d 级河流的汇入点。",
                    superId, level
                )
            ));
            return;
        }

        if (index > confluences.size()) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "序号 %d 超出范围：当前 %d 级河流共有 %d 个汇入点（按距离从近到远排序，1 为最近）。",
                    index, level, confluences.size()
                )
            ));
            return;
        }

        final double sxPlayer = player.posX;
        final double szPlayer = player.posZ;

        List<TalosRiverSystem.RiverConfluence> sorted =
            new ArrayList<TalosRiverSystem.RiverConfluence>(confluences);
        Collections.sort(sorted, (a, b) -> Double.compare(
            distSq(a, sxPlayer, szPlayer),
            distSq(b, sxPlayer, szPlayer)
        ));

        TalosRiverSystem.RiverConfluence target = sorted.get(index - 1);
        double dist = Math.sqrt(distSq(target, sxPlayer, szPlayer));

        int blockX = (int) Math.floor(target.x);
        int blockZ = (int) Math.floor(target.z);
        int y = world.getTopSolidOrLiquidBlock(blockX, blockZ);
        if (y <= 0) {
            y = 64;
        }

        player.setPositionAndUpdate(target.x + 0.5, y + 2.0, target.z + 0.5);

        String kind;
        if (level == 0) {
            kind = "主河入海口";
        } else if (target.fromParent) {
            kind = "下游分流入海支流的分叉点";
        } else {
            kind = "支流汇入点";
        }

        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosRiver] 跳转到 %d 级%s #%d/%d: riverId=%d, parentId=%d, dist=%.1f, pos=(%.1f, %.1f)",
                level,
                kind,
                index,
                sorted.size(),
                target.riverId,
                target.parentRiverId,
                dist,
                target.x, target.z
            )
        ));
    }

    private static double distSq(TalosRiverSystem.RiverConfluence c,
                                 double sx, double sz) {
        double dx = c.x - sx;
        double dz = c.z - sz;
        return dx * dx + dz * dz;
    }
}
