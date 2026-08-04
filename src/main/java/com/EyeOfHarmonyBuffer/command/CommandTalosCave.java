package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.api.TalosCaveSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosTerrainHeights;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * /talcave [tp|tpchamber] [index] - 洞穴系统调试指令。
 * 只经 TalosCaveSystem api / TalosTerrainHeights 获取信息。
 */
public class CommandTalosCave extends CommandBase {

    @Override
    public String getCommandName() {
        return "talcave";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talcave [tp|tpchamber] [index] - 查看洞穴状态 / 传送到最近的入口或大厅";
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
        int seed = TalosLandMask.getWorldSeedInt(world);
        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        if (args.length >= 1 && args[0].equalsIgnoreCase("tp")) {
            tpToEntrance(player, world, seed, px, pz, args, false);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("tpchamber")) {
            tpToEntrance(player, world, seed, px, pz, args, true);
            return;
        }

        List<String> lines = TalosCaveSystem.debugSummary(px, pz, seed);
        for (String line : lines) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void tpToEntrance(EntityPlayerMP player, World world, int seed,
                              int px, int pz, String[] args, boolean chamber) {
        int index = 1;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                player.addChatMessage(new ChatComponentText(
                    "序号参数无效: " + args[1] + "（1 为最近）"
                ));
                return;
            }
            if (index < 1) {
                player.addChatMessage(new ChatComponentText("序号从 1 开始。"));
                return;
            }
        }

        final double sx = player.posX;
        final double sz = player.posZ;

        if (chamber) {
            List<CaveChamber> chams = TalosCaveSystem.debugChambersNear(
                px, pz, seed, 2
            );
            if (chams.isEmpty()) {
                player.addChatMessage(new ChatComponentText(
                    "周围 5×5 单元内没有大厅。"
                ));
                return;
            }
            List<CaveChamber> sorted = new ArrayList<CaveChamber>(chams);
            java.util.Collections.sort(sorted, (a, b) -> Double.compare(
                distSq(a.cx, a.cz, sx, sz), distSq(b.cx, b.cz, sx, sz)
            ));
            if (index > sorted.size()) {
                player.addChatMessage(new ChatComponentText(
                    "序号 " + index + " 超出范围：附近大厅共 "
                        + sorted.size() + " 个。"
                ));
                return;
            }
            CaveChamber c = sorted.get(index - 1);
            player.setPositionAndUpdate(c.cx, c.cy + 1.0, c.cz);
            player.addChatMessage(new ChatComponentText(String.format(
                "[TALCAVE] 跳转到大厅 #%d/%d: pos=(%.0f,%.0f) 中心Y=%.0f 半轴=%.0f×%.0f×%.0f",
                index, sorted.size(), c.cx, c.cz, c.cy, c.rx, c.ry, c.rz
            )));
            return;
        }

        List<CaveEntrance> ents = TalosCaveSystem.debugEntrancesNear(
            px, pz, seed, 2
        );
        if (ents.isEmpty()) {
            player.addChatMessage(new ChatComponentText(
                "周围 5×5 单元内没有入口（洞穴系统可能未启用）。"
            ));
            return;
        }
        List<CaveEntrance> sorted = new ArrayList<CaveEntrance>(ents);
        java.util.Collections.sort(sorted, (a, b) -> Double.compare(
            distSq(a, sx, sz), distSq(b, sx, sz)
        ));

        if (index > sorted.size()) {
            player.addChatMessage(new ChatComponentText(
                "序号 " + index + " 超出范围：附近入口共 " + sorted.size() + " 个。"
            ));
            return;
        }
        CaveEntrance e = sorted.get(index - 1);

        double surface = TalosTerrainHeights.sample(
            e.x, e.z, seed, 64, world.getActualHeight()
        ).surfaceD;
        double y = Math.round(surface) + 2.0;
        player.setPositionAndUpdate(e.x + 0.5, y, e.z + 0.5);
        player.addChatMessage(new ChatComponentText(String.format(
            "[TALCAVE] 跳转到%s #%d/%d: pos=(%d,%d) 井底Y=%d 地表≈%.0f",
            e.sinkhole ? "天坑" : "入口",
            index, sorted.size(), e.x, e.z, e.y, surface
        )));
    }

    private static double distSq(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    private static double distSq(CaveEntrance e, double sx, double sz) {
        return distSq(e.x, e.z, sx, sz);
    }
}
