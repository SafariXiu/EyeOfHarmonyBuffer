package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /talosRiverBody [type] [index]
 *
 * 传送到当前超级大陆上的水体（湖 / 湿地 / 穿河湖 / 牛轭湖）。
 * type: all|lake|wetland|through|oxbow（默认 all）
 * index: 按玩家距离从近到远的序号，默认 1 = 最近。
 */
public class CommandTalosRiverBody extends CommandBase {

    private static final String[] TYPE_NAMES = {
        "all", "lake", "wetland", "through", "oxbow"
    };

    @Override
    public String getCommandName() {
        return "talosRiverBody";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosRiverBody [type] [index] - 传送到水体"
            + "（type: all|lake|wetland|through|oxbow，默认 all；"
            + "index 为按距离从近到远的序号，默认 1=最近）";
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

        String typeArg = "all";
        if (args.length >= 1) {
            typeArg = args[0].toLowerCase();
        }

        RiverBodyType filter = parseType(typeArg);
        if (filter == null && !typeArg.equals("all")) {
            sender.addChatMessage(new ChatComponentText(
                "类型参数无效: " + args[0] + "（应为 all|lake|wetland|through|oxbow）"
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
                "当前位置 superId=0（海洋或未定义区域），无法查找水体。"
            ));
            return;
        }

        List<TalosRiverSystem.RiverBodyInfo> bodies =
            TalosRiverSystem.listBodiesOnCurrentSupercontinent(
                px, pz, worldSeedInt
            );

        List<TalosRiverSystem.RiverBodyInfo> filtered =
            new ArrayList<TalosRiverSystem.RiverBodyInfo>();
        for (TalosRiverSystem.RiverBodyInfo b : bodies) {
            if (filter == null || b.type == filter) {
                filtered.add(b);
            }
        }

        if (filtered.isEmpty()) {
            String typeName = typeLabel(filter);
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在当前超级大陆 (superId=%d) 未找到 %s水体。",
                    superId, typeName
                )
            ));
            return;
        }

        if (index > filtered.size()) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "序号 %d 超出范围：%s水体共有 %d 个（按距离从近到远排序，1 为最近）。",
                    index, typeLabel(filter), filtered.size()
                )
            ));
            return;
        }

        final double sxPlayer = player.posX;
        final double szPlayer = player.posZ;

        Collections.sort(filtered, (a, b) -> Double.compare(
            distSq(a, sxPlayer, szPlayer),
            distSq(b, sxPlayer, szPlayer)
        ));

        TalosRiverSystem.RiverBodyInfo target = filtered.get(index - 1);
        double dist = Math.sqrt(distSq(target, sxPlayer, szPlayer));

        if (!Double.isFinite(target.centerX) || !Double.isFinite(target.centerZ)) {
            sender.addChatMessage(new ChatComponentText(
                "[TalosRiver] 目标坐标无效（非有限值），已取消传送。"
            ));
            return;
        }

        int blockX = (int) Math.floor(target.centerX);
        int blockZ = (int) Math.floor(target.centerZ);
        int y = world.getTopSolidOrLiquidBlock(blockX, blockZ);
        if (y <= 0) {
            y = 64;
        }
        // 钳制到合法玩家高度（最高 255），避免山顶列 y+2 越界被踢 "Illegal position"
        y = Math.min(y, 253);

        player.setPositionAndUpdate(target.centerX + 0.5, y + 2.0, target.centerZ + 0.5);

        String attach = target.parentRiverId >= 0
            ? "挂河#" + target.parentRiverId : "独立";
        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosRiver] 跳转到%s #%d/%d: bodyId=%d (%s), 半轴=%.0f×%.0f, "
                    + "深度=%.1f, dist=%.1f, pos=(%.1f, %.1f)",
                typeLabel(target.type),
                index,
                filtered.size(),
                target.bodyId,
                attach,
                target.radiusX,
                target.radiusZ,
                target.maxDepthBlocks,
                dist,
                target.centerX,
                target.centerZ
            )
        ));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, TYPE_NAMES);
        }
        return null;
    }

    private static RiverBodyType parseType(String s) {
        switch (s) {
            case "lake":
                return RiverBodyType.LAKE;
            case "wetland":
                return RiverBodyType.WETLAND;
            case "through":
            case "throughlake":
                return RiverBodyType.THROUGH_LAKE;
            case "oxbow":
            case "oxbowlake":
                return RiverBodyType.OXBOW_LAKE;
            default:
                return null;
        }
    }

    private static String typeLabel(RiverBodyType type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case LAKE:         return "湖";
            case WETLAND:      return "湿地";
            case THROUGH_LAKE: return "穿河湖";
            case OXBOW_LAKE:   return "牛轭湖";
            default:           return type.name();
        }
    }

    private static double distSq(TalosRiverSystem.RiverBodyInfo b,
                                 double sx, double sz) {
        double dx = b.centerX - sx;
        double dz = b.centerZ - sz;
        return dx * dx + dz * dz;
    }
}
