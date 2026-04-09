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
        return "/talosRiverNeare - 传送到当前板块最近的河流点（Debug 用）";
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

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        int plateId = TalosLandMask.getPlateId(px, pz, worldSeedInt);
        if (plateId == 0) {
            sender.addChatMessage(new ChatComponentText(
                String.format("当前位置 plateId=0（可能是海洋或未定义区域），无法查找河流。")
            ));
            return;
        }

        TalosRiverSystem.DebugNearestRiverInfo info =
            TalosRiverSystem.debugFindNearestRiver(px, pz, worldSeedInt);

        if (!info.hasRiver) {
            sender.addChatMessage(new ChatComponentText(
                String.format("当前板块 %d 未生成主河。", plateId)
            ));
            return;
        }

        double rx = info.nearestX;
        double rz = info.nearestZ;

        int blockX = (int) Math.floor(rx);
        int blockZ = (int) Math.floor(rz);
        int y = world.getTopSolidOrLiquidBlock(blockX, blockZ);
        if (y <= 0) {
            y = 64;
        }

        double ry = y + 2.0;

        player.setPositionAndUpdate(rx + 0.5, ry, rz + 0.5);

        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosRiver] 跳转到最近河流: plateId=%d, riverId=%d, level=%d, dist=%.1f, pos=(%.1f, %.1f)",
                info.plateId, info.riverId, info.riverLevel, info.distance, rx, rz
            )
        ));
    }
}
