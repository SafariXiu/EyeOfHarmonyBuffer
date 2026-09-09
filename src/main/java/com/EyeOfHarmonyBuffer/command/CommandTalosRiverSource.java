package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

public class CommandTalosRiverSource extends CommandBase {

    @Override
    public String getCommandName() {
        return "talosRiverSource";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosRiverSource - 传送到当前超级大陆上最近的一个河流源头（基于新河网，Debug 用）";
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
                "当前位置 superId=0（海洋或未定义区域），无法在超级大陆河网中查找河流源头。"
            ));
            return;
        }

        List<TalosRiverSystem.RiverEndpoint> endpoints =
            TalosRiverSystem.listEndpointsOnCurrentSupercontinent(
                px, pz,
                worldSeedInt,
                EnumSet.of(TalosRiverSystem.EndpointKind.SOURCE)
            );

        if (endpoints.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在当前超级大陆 (superId=%d) 未找到任何标记了源头的河流 edge。",
                    superId
                )
            ));
            return;
        }

        double bestDistSq = Double.POSITIVE_INFINITY;
        TalosRiverSystem.RiverEndpoint best = null;

        double sxPlayer = player.posX;
        double szPlayer = player.posZ;

        for (TalosRiverSystem.RiverEndpoint ep : endpoints) {
            double dx = ep.x - sxPlayer;
            double dz = ep.z - szPlayer;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = ep;
            }
        }

        if (best == null) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在当前超级大陆 (superId=%d) 找到了源头端点列表，但未能选出最近项（逻辑错误）。",
                    superId
                )
            ));
            return;
        }

        double dist = Math.sqrt(bestDistSq);

        if (!Double.isFinite(best.x) || !Double.isFinite(best.z)) {
            sender.addChatMessage(new ChatComponentText(
                "[TalosRiver] 目标坐标无效（非有限值），已取消传送。"
            ));
            return;
        }

        int blockX = (int) Math.floor(best.x);
        int blockZ = (int) Math.floor(best.z);
        int y = world.getTopSolidOrLiquidBlock(blockX, blockZ);
        if (y <= 0) {
            y = 64;
        }
        // 钳制到合法玩家高度（最高 255），避免山顶列 y+2 越界被踢 "Illegal position"
        y = Math.min(y, 253);
        double py = y + 2.0;

        player.setPositionAndUpdate(best.x + 0.5, py, best.z + 0.5);

        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosRiver] 跳转到最近河流源头: superId=%d, riverId=%d, level=%d, dist=%.1f, pos=(%.1f, %.1f)",
                best.superId,
                best.riverId,
                best.riverLevel,
                dist,
                best.x, best.z
            )
        ));
    }
}

