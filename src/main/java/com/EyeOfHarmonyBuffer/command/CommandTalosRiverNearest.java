package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class CommandTalosRiverNearest extends CommandBase {

    @Override
    public String getCommandName() {
        return "talosRiverNearest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosRiverNearest - 传送到当前超级大陆最近的河流点（基于新河网，Debug 用）";
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

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        int worldSeedInt = TalosRiverSystem.getWorldSeedInt(world);
        LegacyV2Note.note(sender);

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        int superId = TalosLandMask.getSuperId(px, pz, worldSeedInt);
        if (superId == 0) {
            sender.addChatMessage(new ChatComponentText(
                "当前位置 superId=0（海洋或未定义区域），无法在超级大陆河网中查找最近河流。"
            ));
            return;
        }

        TalosRiverSystem.DebugNearestRiverInfo info =
            TalosRiverSystem.debugFindNearestRiverOnSuper(px, pz, worldSeedInt);

        if (info == null || !info.hasRiver) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在当前超级大陆 (superId=%d) 附近未找到河流（可能该模板无河或距离过远）。",
                    superId
                )
            ));
            return;
        }

        double rx = info.nearestX;
        double rz = info.nearestZ;

        if (!Double.isFinite(rx) || !Double.isFinite(rz)) {
            sender.addChatMessage(new ChatComponentText(
                "[TalosRiver] 目标坐标无效（非有限值），已取消传送。"
            ));
            return;
        }

        int blockX = (int) Math.floor(rx);
        int blockZ = (int) Math.floor(rz);
        int y = world.getTopSolidOrLiquidBlock(blockX, blockZ);
        if (y <= 0) {
            y = 64;
        }
        // 钳制到合法玩家高度（最高 255），避免山顶列 y+2 越界被踢 "Illegal position"
        y = Math.min(y, 253);

        double ry = y + 2.0;

        player.setPositionAndUpdate(rx + 0.5, ry, rz + 0.5);

        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosRiver] 跳转到最近河流: superId=%d, riverId=%d, level=%d, dist=%.1f, pos=(%.1f, %.1f)",
                superId,
                info.riverId,
                info.riverLevel,
                info.distance,
                rx, rz
            )
        ));
    }
}
