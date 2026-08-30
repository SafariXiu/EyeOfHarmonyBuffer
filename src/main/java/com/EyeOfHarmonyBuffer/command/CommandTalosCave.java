package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.api.TalosCaveSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChamber;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveEntrance;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveNode;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosTerrainHeights;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * /talcave [tp|tpchamber|tphall|node] - 洞穴系统调试指令。
 * 只经 TalosCaveSystem api / TalosTerrainHeights 获取信息。
 */
public class CommandTalosCave extends CommandBase {

    @Override
    public String getCommandName() {
        return "talcave";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talcave [tp|tpchamber|tphall|node <type>] [index] - 查看洞穴状态 / 传送到最近的入口、大厅、洞厅或指定节点";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender,
                                                String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                "tp", "tpchamber", "tphall", "node", "probe");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("node")) {
            return getListOfStringsMatchingLastWord(args,
                "all", "entrance", "sinkhole", "chamber", "backbone",
                "normal", "hall", "megahall", "aquifer",
                "aquiferfull", "aquiferhalf", "aquiferdead");
        }
        if (args.length == 2
            && (args[0].equalsIgnoreCase("tp")
                || args[0].equalsIgnoreCase("tpchamber"))) {
            return getListOfStringsMatchingLastWord(args,
                "funnel", "ramp", "shaft", "sinkhole");
        }
        return null;
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("tphall")) {
            tpToMegaHall(player, world, seed, px, pz, args);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("node")) {
            tpToNode(player, seed, px, pz, args);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("probe")) {
            probeColumn(player, world, seed, args);
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
        Integer typeFilter = null;
        if (args.length >= 2) {
            for (int i = 1; i < args.length; i++) {
                Integer t = parseEntranceType(args[i]);
                if (t != null) {
                    typeFilter = t;
                    continue;
                }
                try {
                    index = Integer.parseInt(args[i]);
                } catch (NumberFormatException ex) {
                    player.addChatMessage(new ChatComponentText(
                        "参数无效: " + args[i]
                            + "（类型: funnel|ramp|shaft|sinkhole，序号: 1 为最近）"
                    ));
                    return;
                }
                if (index < 1) {
                    player.addChatMessage(new ChatComponentText("序号从 1 开始。"));
                    return;
                }
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

        // 扫描半径：天坑稀有，用更大的范围；其余类型 3×3 单元。
        int scanCells = (typeFilter != null
            && typeFilter.intValue() == CaveEntrance.TYPE_SINKHOLE) ? 4 : 3;
        List<CaveEntrance> ents = TalosCaveSystem.debugEntrancesNear(
            px, pz, seed, scanCells
        );
        if (typeFilter != null) {
            List<CaveEntrance> filtered = new ArrayList<CaveEntrance>();
            for (CaveEntrance e : ents) {
                if (e.type == typeFilter.intValue()) {
                    filtered.add(e);
                }
            }
            ents = filtered;
        }
        if (ents.isEmpty()) {
            player.addChatMessage(new ChatComponentText(
                "周围 " + scanCells + "×" + scanCells + " 单元内没有"
                    + (typeFilter != null
                        ? CaveEntrance.typeName(typeFilter.intValue()) : "")
                    + "入口（洞穴系统可能未启用）。"
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
            "[TALCAVE] 跳转到%s #%d/%d: pos=(%d,%d) 洞口底Y=%d 地表≈%.0f",
            CaveEntrance.typeName(e.type),
            index, sorted.size(), e.x, e.z, e.y, surface
        )));
    }

    private void tpToMegaHall(EntityPlayerMP player, World world, int seed,
                              int px, int pz, String[] args) {
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

        List<CaveMegaHall> halls = TalosCaveSystem.findMegaHallsNear(
            px, pz, seed, 64
        );
        if (halls.isEmpty()) {
            player.addChatMessage(new ChatComponentText(
                "扫描 65×65 个超级格（约 26 万格范围）未找到洞厅。"
                    + "洞厅只在高海拔群系（宏包带底 > 100）生成，且极稀有。"
            ));
            return;
        }
        List<CaveMegaHall> sorted = new ArrayList<CaveMegaHall>(halls);
        java.util.Collections.sort(sorted, (a, b) -> Double.compare(
            distSq(a.cx, a.cz, player.posX, player.posZ),
            distSq(b.cx, b.cz, player.posX, player.posZ)
        ));
        if (index > sorted.size()) {
            player.addChatMessage(new ChatComponentText(
                "序号 " + index + " 超出范围：附近洞厅共 " + sorted.size() + " 个。"
            ));
            return;
        }
        CaveMegaHall hall = sorted.get(index - 1);
        // 洞厅 TP 直接绑定湖心岛：落点 = 岛心 (islandX, islandZ)，站在岛顶上方。
        // 湖心岛是「房间 1 内离所有墙最远的点」+ 中心平地（islandFlatRadius），
        // 既好找岛，也方便后续在上面放建筑。
        double tx = hall.islandCenterX();
        double tz = hall.islandCenterZ();
        if (hall.isPillarColumn((int) tx, (int) tz)) {
            boolean found = false;
            for (int dz = -3; dz <= 3 && !found; dz++) {
                for (int dx = -3; dx <= 3 && !found; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (!hall.isPillarColumn((int) tx + dx, (int) tz + dz)) {
                        tx += dx;
                        tz += dz;
                        found = true;
                    }
                }
            }
        }
        player.setPositionAndUpdate(tx + 0.5, hall.islandTop() + 1.0, tz + 0.5);
        double dist = Math.sqrt(distSq(
            tx, tz, player.posX, player.posZ));
        player.addChatMessage(new ChatComponentText(String.format(
            "[TALCAVE] 跳转到洞厅 #%d/%d（湖心岛）: 距离≈%.0f 岛心=(%.0f,%.0f) "
                + "岛顶Y=%d 半径=%.0f×%.0f×%.0f",
            index, sorted.size(), dist, tx, tz, hall.islandTop(),
            hall.rx, hall.ry, hall.rz
        )));
    }

    private void tpToNode(EntityPlayerMP player, int seed,
                          int px, int pz, String[] args) {
        String type = args.length >= 2 ? args[1] : "all";
        int index = 1;
        if (args.length >= 3) {
            try {
                index = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                player.addChatMessage(new ChatComponentText(
                    "序号参数无效: " + args[2] + "（1 为最近）"
                ));
                return;
            }
            if (index < 1) {
                player.addChatMessage(new ChatComponentText("序号从 1 开始。"));
                return;
            }
        }

        Set<Integer> kinds = parseNodeKinds(type);
        if (!type.equalsIgnoreCase("all") && kinds.isEmpty()) {
            player.addChatMessage(new ChatComponentText(
                "未知节点类型: " + type
                    + "（all|entrance|sinkhole|chamber|backbone|normal|"
                    + "hall|aquifer|aquiferfull|aquiferhalf|aquiferdead）"
            ));
            return;
        }

        List<CaveNode> nodes = TalosCaveSystem.debugNodesNear(
            px, pz, seed, 8, kinds
        );
        if (nodes.isEmpty()) {
            player.addChatMessage(new ChatComponentText(
                "扫描 17×17 个单元内没有匹配的节点。"
            ));
            return;
        }
        List<CaveNode> sorted = new ArrayList<CaveNode>(nodes);
        java.util.Collections.sort(sorted, (a, b) -> Double.compare(
            distSq(a.x, a.z, player.posX, player.posZ),
            distSq(b.x, b.z, player.posX, player.posZ)
        ));
        if (index > sorted.size()) {
            player.addChatMessage(new ChatComponentText(
                "序号 " + index + " 超出范围：匹配节点共 " + sorted.size() + " 个。"
            ));
            return;
        }
        CaveNode n = sorted.get(index - 1);
        player.setPositionAndUpdate(n.x + 0.5, n.y + 1.0, n.z + 0.5);
        double dist = Math.sqrt(distSq(
            n.x, n.z, player.posX, player.posZ));
        player.addChatMessage(new ChatComponentText(String.format(
            "[TALCAVE] 跳转到%s #%d/%d: 距离≈%.0f pos=(%.0f,%.0f) Y=%.0f",
            nodeKindName(n.kind), index, sorted.size(),
            dist, n.x, n.z, n.y
        )));
    }

    /** 解析入口类型名（funnel/ramp/shaft/sinkhole），未知返回 null。 */
    private static Integer parseEntranceType(String s) {
        if (s.equalsIgnoreCase("funnel")) {
            return Integer.valueOf(CaveEntrance.TYPE_FUNNEL);
        }
        if (s.equalsIgnoreCase("ramp")) {
            return Integer.valueOf(CaveEntrance.TYPE_RAMP);
        }
        if (s.equalsIgnoreCase("shaft")) {
            return Integer.valueOf(CaveEntrance.TYPE_SHAFT);
        }
        if (s.equalsIgnoreCase("sinkhole")) {
            return Integer.valueOf(CaveEntrance.TYPE_SINKHOLE);
        }
        return null;
    }

    private static Set<Integer> parseNodeKinds(String type) {
        Set<Integer> s = new HashSet<Integer>();
        if (type.equalsIgnoreCase("entrance")) {
            s.add(CaveNode.KIND_ENTRANCE);
            s.add(CaveNode.KIND_SINKHOLE);
        } else if (type.equalsIgnoreCase("sinkhole")) {
            s.add(CaveNode.KIND_SINKHOLE);
        } else if (type.equalsIgnoreCase("chamber")) {
            s.add(CaveNode.KIND_CHAMBER);
        } else if (type.equalsIgnoreCase("backbone")) {
            s.add(CaveNode.KIND_BACKBONE);
        } else if (type.equalsIgnoreCase("normal")) {
            s.add(CaveNode.KIND_NORMAL);
        } else if (type.equalsIgnoreCase("hall")
            || type.equalsIgnoreCase("megahall")) {
            s.add(CaveNode.KIND_MEGA_HALL);
        } else if (type.equalsIgnoreCase("aquifer")) {
            s.add(CaveNode.KIND_AQUIFER_FULL);
            s.add(CaveNode.KIND_AQUIFER_HALF);
            s.add(CaveNode.KIND_AQUIFER_DEAD);
        } else if (type.equalsIgnoreCase("aquiferfull")
            || type.equalsIgnoreCase("full")) {
            s.add(CaveNode.KIND_AQUIFER_FULL);
        } else if (type.equalsIgnoreCase("aquiferhalf")
            || type.equalsIgnoreCase("half")) {
            s.add(CaveNode.KIND_AQUIFER_HALF);
        } else if (type.equalsIgnoreCase("aquiferdead")
            || type.equalsIgnoreCase("dead")) {
            s.add(CaveNode.KIND_AQUIFER_DEAD);
        }
        return s;
    }

    private static String nodeKindName(int kind) {
        switch (kind) {
            case CaveNode.KIND_NORMAL:
                return "普通节点";
            case CaveNode.KIND_BACKBONE:
                return "骨干节点";
            case CaveNode.KIND_CHAMBER:
                return "大厅";
            case CaveNode.KIND_ENTRANCE:
                return "入口";
            case CaveNode.KIND_SINKHOLE:
                return "天坑";
            case CaveNode.KIND_MEGA_HALL:
                return "洞厅";
            case CaveNode.KIND_AQUIFER_FULL:
                return "全水节点";
            case CaveNode.KIND_AQUIFER_HALF:
                return "半水节点";
            case CaveNode.KIND_AQUIFER_DEAD:
                return "尽头节点";
            default:
                return "未知节点";
        }
    }

    private static double distSq(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    private static double distSq(CaveEntrance e, double sx, double sz) {
        return distSq(e.x, e.z, sx, sz);
    }

    /**
     * /talcave probe [x z] - 诊断入口列：打印地形采样、入口节点、雕刻门控条件，
     * 以及实际世界方块（从地表到入口深度哪些是空气）。
     * 不传坐标则探查最近的入口；传坐标则探查指定列附近最近的入口。
     */
    private void probeColumn(EntityPlayerMP player, World world, int seed,
                             String[] args) {
        int tx, tz;
        if (args.length >= 3) {
            try {
                tx = Integer.parseInt(args[1]);
                tz = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                player.addChatMessage(new ChatComponentText(
                    "坐标无效: " + args[1] + " " + args[2]));
                return;
            }
        } else {
            tx = (int) Math.floor(player.posX);
            tz = (int) Math.floor(player.posZ);
        }

        // 1) 地形采样（雕刻门控条件）
        TalosTerrainHeights.TerrainHeightSample ts =
            TalosTerrainHeights.sample(tx, tz, seed, 64, world.getActualHeight());
        player.addChatMessage(new ChatComponentText(String.format(
            "[PROBE] pos=(%d,%d) 地表≈%.0f isLand=%b riverMask=%.2f body=%s waterLevel=%s",
            tx, tz, ts.surfaceD, ts.isLand, ts.riverMask,
            ts.body == null ? "null" : ts.body.toString(),
            ts.waterLevel == Double.NEGATIVE_INFINITY ? "-inf" : String.format("%.0f", ts.waterLevel)
        )));

        // 2) 入口（从真实洞穴节点延伸的通道）
        int cellX = Math.floorDiv(tx, 256);
        int cellZ = Math.floorDiv(tz, 256);
        CaveEntrance nearest = null;
        double nearestD = Double.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                CaveEntrance e = TalosCaveSystem.debugEntranceAt(
                    cellX + dx, cellZ + dz, seed);
                if (e == null) continue;
                double d = Math.sqrt(
                    (e.x - tx) * (e.x - tx) + (e.z - tz) * (e.z - tz));
                if (d < nearestD) {
                    nearestD = d;
                    nearest = e;
                }
            }
        }
        if (nearest == null) {
            player.addChatMessage(new ChatComponentText(
                "[PROBE] 附近 3×3 单元内没有入口。"));
            return;
        }
        int ex = nearest.x;
        int ez = nearest.z;
        int eY = nearest.y; // 基座深度（通道底部）
        player.addChatMessage(new ChatComponentText(String.format(
            "[PROBE] 最近入口 @(%d,%d) 基座Y=%d 地表开口高=%d type=%s dist=%.0f",
            ex, ez, eY, nearest.surfaceY,
            CaveEntrance.typeName(nearest.type), nearestD
        )));

        // 3) 雕刻门控检查（与 CaveCarver.carveColumn 相同的逻辑）
        int topSolidY = (int) Math.round(ts.surfaceD);
        boolean gateSkip = topSolidY < (int) (ts.waterLevel) + 1;
        player.addChatMessage(new ChatComponentText(String.format(
            "[PROBE] 雕刻门控: topSolidY≈%d, waterSurfaceY≈%d, %s",
            topSolidY,
            ts.waterLevel == Double.NEGATIVE_INFINITY ? 0 : (int) ts.waterLevel,
            gateSkip ? "会跳过（列顶低于水面）" : "不跳过（应雕刻）"
        )));

        // 4) 实际世界方块：从 eY 到地表
        StringBuilder sb = new StringBuilder("[PROBE] 方块 y90→地表: ");
        for (int y = Math.max(1, eY); y <= Math.min(topSolidY, eY + 16); y++) {
            net.minecraft.block.Block b = world.getBlock(ex, y, ez);
            String name = (b == null || b == net.minecraft.init.Blocks.air)
                ? "空" : (b == net.minecraft.init.Blocks.water
                    || b == net.minecraft.init.Blocks.flowing_water) ? "水" : "石";
            sb.append(y).append(":").append(name).append(" ");
        }
        player.addChatMessage(new ChatComponentText(sb.toString()));

        // 5) 该区块 data.entrances 是否包含入口
        int chunkX = ex >> 4;
        int chunkZ = ez >> 4;
        CaveChunkData data = TalosCaveSystem.dataForChunk(chunkX, chunkZ, seed);
        if (data == null) {
            player.addChatMessage(new ChatComponentText(
                "[PROBE] 该区块 caveData 为 null（洞穴数据未生成！）"));
            return;
        }
        boolean inData = false;
        for (CaveEntrance e : data.entrances) {
            if (e.x == ex && e.z == ez) {
                inData = true;
                break;
            }
        }
        player.addChatMessage(new ChatComponentText(String.format(
            "[PROBE] 该区块 caveData.entrances=%d, 入口在数据中: %s",
            data.entrances.size(), inData ? "是" : "否（异常！）"
        )));
    }

}
