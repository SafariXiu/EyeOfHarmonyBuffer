package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 搜索并传送到最近的指定类型板块边界。
 *
 * 用法：/talosBoundary <类型> [强度] [搜索半径]
 *   类型：CONVERGENT(挤压) / DIVERGENT(分离) / TRANSFORM(走滑) / INACTIVE(静止)
 *   强度：0~1（例如 0.2 = 边界带边缘），省略则匹配任意强度
 *   半径：默认 30000 格，最大 100000
 */
public class CommandTalosBoundary extends CommandBase {

    private static final int DEFAULT_RADIUS = 30000;
    private static final int MAX_RADIUS = 100000;

    /** 粗扫步长：先在整个搜索范围内找最近的命中点。 */
    private static final int COARSE_STEP = 512;
    /** 细扫步长与范围：在粗扫命中点附近精确找最近命中格。 */
    private static final int REFINE_STEP = 32;
    private static final int REFINE_HALF = 1024;

    /** 强度匹配容差（例如指定 0.2 时接受 0.17~0.23）。 */
    private static final double STRENGTH_TOLERANCE = 0.03;

    @Override
    public String getCommandName() {
        return "talosBoundary";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosBoundary <CONVERGENT|DIVERGENT|TRANSFORM|INACTIVE> [强度] [搜索半径]"
            + " - 搜索并传送到最近的指定类型板块边界（强度 0~1，如 0.2）";
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

        PlateBoundaryState state = parseState(args[0]);
        if (state == null) {
            sender.addChatMessage(new ChatComponentText(
                "板块边界类型无效: " + args[0]
                    + "（应为 CONVERGENT/挤压, DIVERGENT/分离, TRANSFORM/走滑, INACTIVE/静止）"
            ));
            return;
        }

        double targetStrength = -1.0;
        boolean hasStrength = false;
        if (args.length >= 2) {
            try {
                targetStrength = Double.parseDouble(args[1]);
                if (targetStrength < 0.0 || targetStrength > 1.0) {
                    throw new NumberFormatException();
                }
                hasStrength = true;
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText(
                    "强度无效: " + args[1] + "（应为 0~1 的小数，例如 0.2）"
                ));
                return;
            }
        }

        int radius = DEFAULT_RADIUS;
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText(
                    "搜索半径无效: " + args[2] + "（应为正整数，默认 30000）"
                ));
                return;
            }
            if (radius < 1000) {
                radius = 1000;
            }
            if (radius > MAX_RADIUS) {
                radius = MAX_RADIUS;
            }
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;
        int worldSeedInt = TalosLandMask.getWorldSeedInt(world);

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        // 第一阶段：粗扫，找最近的命中点
        int bestX = px;
        int bestZ = pz;
        double bestDistSq = Double.POSITIVE_INFINITY;
        boolean found = false;

        for (int dz = -radius; dz <= radius; dz += COARSE_STEP) {
            for (int dx = -radius; dx <= radius; dx += COARSE_STEP) {
                int x = px + dx;
                int z = pz + dz;
                if (matches(x, z, worldSeedInt, state, targetStrength, hasStrength)) {
                    double d = (double) dx * dx + (double) dz * dz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        bestX = x;
                        bestZ = z;
                        found = true;
                    }
                }
            }
        }

        if (!found) {
            sender.addChatMessage(new ChatComponentText(String.format(
                "在半径 %d 格内未找到类型 %s 的板块边界%s。可以加大搜索半径重试。",
                radius,
                state,
                hasStrength ? String.format("（强度≈%.2f）", targetStrength) : ""
            )));
            return;
        }

        // 第二阶段：在粗扫命中点附近细扫，找精确的最近命中格
        bestDistSq = Double.POSITIVE_INFINITY;
        found = false;

        for (int dz = -REFINE_HALF; dz <= REFINE_HALF; dz += REFINE_STEP) {
            for (int dx = -REFINE_HALF; dx <= REFINE_HALF; dx += REFINE_STEP) {
                int x = bestX + dx;
                int z = bestZ + dz;
                if (x < px - radius || x > px + radius
                    || z < pz - radius || z > pz + radius) {
                    continue;
                }
                if (matches(x, z, worldSeedInt, state, targetStrength, hasStrength)) {
                    double ddx = (double) x - px;
                    double ddz = (double) z - pz;
                    double d = ddx * ddx + ddz * ddz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        bestX = x;
                        bestZ = z;
                        found = true;
                    }
                }
            }
        }

        if (!found) {
            sender.addChatMessage(new ChatComponentText("细扫阶段未找到命中点（逻辑异常）。"));
            return;
        }

        TalosLandMask.Sample sample =
            TalosLandMask.sampleFull(bestX, bestZ, worldSeedInt);
        double actualStrength =
            (sample != null) ? sample.plateBoundaryWeight : 0.0;

        int y = world.getTopSolidOrLiquidBlock(bestX, bestZ);
        if (y <= 0) {
            y = 64;
        }
        // 钳制到合法玩家高度（最高 255），避免山顶列 y+2 越界被踢 "Illegal position"
        y = Math.min(y, 253);

        player.setPositionAndUpdate(bestX + 0.5, y + 2.0, bestZ + 0.5);

        double dist = Math.sqrt(bestDistSq);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[TalosBoundary] 跳转到最近%s边界: 类型=%s, 强度=%.3f, dist=%.1f, pos=(%d, ~%d, %d)",
            hasStrength ? String.format("（强度≈%.2f）", targetStrength) : "",
            state,
            actualStrength,
            dist,
            bestX, y + 2, bestZ
        )));
    }

    /** 判断点是否命中：陆地 + 指定状态 + （可选）强度接近目标值。 */
    private static boolean matches(int x, int z, int worldSeedInt,
                                   PlateBoundaryState state,
                                   double targetStrength, boolean hasStrength) {
        TalosLandMask.Sample s = TalosLandMask.sampleFull(x, z, worldSeedInt);
        if (s == null || !s.isLand || s.plateBoundaryState != state) {
            return false;
        }
        if (hasStrength) {
            return Math.abs(s.plateBoundaryWeight - targetStrength)
                <= STRENGTH_TOLERANCE;
        }
        return true;
    }

    /** 解析类型：支持英文枚举名与中文别名。 */
    private static PlateBoundaryState parseState(String arg) {
        String t = arg.trim().toLowerCase();
        if (t.equals("convergent") || t.equals("挤压")) {
            return PlateBoundaryState.CONVERGENT;
        }
        if (t.equals("divergent") || t.equals("分离")) {
            return PlateBoundaryState.DIVERGENT;
        }
        if (t.equals("transform") || t.equals("走滑")) {
            return PlateBoundaryState.TRANSFORM;
        }
        if (t.equals("inactive") || t.equals("静止")) {
            return PlateBoundaryState.INACTIVE;
        }
        return null;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> types = Arrays.asList(
                "CONVERGENT", "DIVERGENT", "TRANSFORM", "INACTIVE"
            );
            List<String> out = new ArrayList<String>();
            for (String s : types) {
                if (s.toLowerCase().startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        return null;
    }
}
