package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandTalosContinent extends CommandBase {

    @Override
    public String getCommandName() {
        return "talosContinent";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosContinent <list|main|sub> [index]"
            + " - 操作当前 80k 超级格：list 列出所有大陆，main 传送到主大陆中心，"
            + "sub [index] 传送到第 index 个次级大陆（按距离排序，默认 1=最近）";
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

        String mode = args[0].toLowerCase();
        if (!mode.equals("list") && !mode.equals("main") && !mode.equals("sub")) {
            sender.addChatMessage(new ChatComponentText(
                "模式无效: " + args[0] + "（应为 list / main / sub）"
            ));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        int worldSeedInt = TalosLandMask.getWorldSeedInt(world);

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        int[] superCell = TalosLandMask.getSuperCellXZAt(px, pz);
        int superCellX = superCell[0];
        int superCellZ = superCell[1];

        List<TalosLandMask.SupercellContinentInfo> continents =
            TalosLandMask.listSupercellContinents(superCellX, superCellZ, worldSeedInt);

        if (continents.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "当前超级格 (superCell=%d, %d) 内没有找到任何大陆（数据异常）。",
                    superCellX, superCellZ
                )
            ));
            return;
        }

        if (mode.equals("list")) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "[TalosContinent] 超级格 (%d, %d) 内共有 %d 个大陆:",
                    superCellX, superCellZ, continents.size()
                )
            ));
            for (TalosLandMask.SupercellContinentInfo c : continents) {
                sender.addChatMessage(new ChatComponentText(
                    String.format(
                        "  - %s: superId=%d, center=(%d, %d)",
                        c.isMain ? "主大陆" : "次级大陆",
                        c.superId,
                        c.centerX, c.centerZ
                    )
                ));
            }
            return;
        }

        TalosLandMask.SupercellContinentInfo target = null;

        if (mode.equals("main")) {
            for (TalosLandMask.SupercellContinentInfo c : continents) {
                if (c.isMain) {
                    target = c;
                    break;
                }
            }
            if (target == null) {
                sender.addChatMessage(new ChatComponentText(
                    "当前超级格内没有主大陆（数据异常，主大陆应当永远存在）。"
                ));
                return;
            }
        } else {
            List<TalosLandMask.SupercellContinentInfo> subs =
                new ArrayList<TalosLandMask.SupercellContinentInfo>();
            for (TalosLandMask.SupercellContinentInfo c : continents) {
                if (!c.isMain) {
                    subs.add(c);
                }
            }

            if (subs.isEmpty()) {
                sender.addChatMessage(new ChatComponentText(
                    String.format(
                        "当前超级格 (superCell=%d, %d) 内没有次级大陆。",
                        superCellX, superCellZ
                    )
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

            if (index > subs.size()) {
                sender.addChatMessage(new ChatComponentText(
                    String.format(
                        "序号 %d 超出范围：当前超级格共有 %d 个次级大陆（按距离排序）。",
                        index, subs.size()
                    )
                ));
                return;
            }

            final double sx = player.posX;
            final double sz = player.posZ;
            Collections.sort(subs, (a, b) -> Double.compare(
                distSq(a, sx, sz), distSq(b, sx, sz)
            ));
            target = subs.get(index - 1);
        }

        int y = world.getTopSolidOrLiquidBlock(target.centerX, target.centerZ);
        if (y <= 0) {
            y = 64;
        }

        player.setPositionAndUpdate(
            target.centerX + 0.5, y + 2.0, target.centerZ + 0.5
        );

        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosContinent] 跳转到%s中心: superId=%d, center=(%d, ~%d, %d)",
                target.isMain ? "主大陆" : "次级大陆",
                target.superId,
                target.centerX, y + 2, target.centerZ
            )
        ));
    }

    private static double distSq(TalosLandMask.SupercellContinentInfo c,
                                 double sx, double sz) {
        double dx = c.centerX - sx;
        double dz = c.centerZ - sz;
        return dx * dx + dz * dz;
    }
}
