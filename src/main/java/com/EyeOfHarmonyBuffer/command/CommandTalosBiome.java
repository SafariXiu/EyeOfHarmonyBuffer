package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandTalosBiome extends CommandBase {

    private static final int DEFAULT_RADIUS = 30000;
    private static final int MAX_RADIUS = 100000;

    /** 粗扫步长：先在整个搜索范围内找最近的命中点。 */
    private static final int COARSE_STEP = 512;
    /** 细扫步长与范围：在粗扫命中点附近精确找最近命中格。 */
    private static final int REFINE_STEP = 32;
    private static final int REFINE_HALF = 1024;

    @Override
    public String getCommandName() {
        return "talosBiome";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosBiome <群系名> [搜索半径]"
            + " - 搜索并传送到最近的指定群系（默认半径 30000 格，最大 100000）";
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

        String input = normalize(args[0]);
        if (input.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("群系名无效。"));
            return;
        }

        int radius = DEFAULT_RADIUS;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText(
                    "搜索半径无效: " + args[1] + "（应为正整数，默认 30000）"
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

        List<BiomeGenBase> matches = resolveBiomes(input);
        if (matches.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(
                "未找到群系 \"" + args[0] + "\"。可用群系: " + availableBiomeNames()
            ));
            return;
        }

        BiomeGenBase target = matches.get(0);
        if (matches.size() > 1) {
            StringBuilder sb = new StringBuilder("群系名存在歧义，匹配到: ");
            for (BiomeGenBase b : matches) {
                sb.append(b.biomeName).append(" / ");
            }
            sender.addChatMessage(new ChatComponentText(sb.toString()));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;

        int worldSeedInt = TalosMacroClimate.getWorldSeedInt(world);

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        // 第一阶段：粗扫
        int bestX = px;
        int bestZ = pz;
        double bestDistSq = Double.POSITIVE_INFINITY;
        boolean found = false;

        for (int dz = -radius; dz <= radius; dz += COARSE_STEP) {
            for (int dx = -radius; dx <= radius; dx += COARSE_STEP) {
                int x = px + dx;
                int z = pz + dz;
                if (TalosMacroClimate.getBiome(x, z, worldSeedInt) == target) {
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
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在半径 %d 格内未找到群系 \"%s\"。可以加大搜索半径重试。",
                    radius, target.biomeName
                )
            ));
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
                if (TalosMacroClimate.getBiome(x, z, worldSeedInt) == target) {
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
            // 理论上不会发生（粗扫命中点在细扫窗口内），防御性处理
            sender.addChatMessage(new ChatComponentText("细扫阶段未找到命中点（逻辑异常）。"));
            return;
        }

        int y = world.getTopSolidOrLiquidBlock(bestX, bestZ);
        if (y <= 0) {
            y = 64;
        }

        player.setPositionAndUpdate(bestX + 0.5, y + 2.0, bestZ + 0.5);

        double dist = Math.sqrt(bestDistSq);
        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosBiome] 跳转到最近群系 \"%s\": dist=%.1f, pos=(%d, ~%d, %d)",
                target.biomeName,
                dist,
                bestX, y + 2, bestZ
            )
        ));
    }

    /** 归一化：小写、空白/下划线统一为下划线、去掉 talos_ 前缀。 */
    private static String normalize(String s) {
        String t = s.trim().toLowerCase().replaceAll("[\\s_]+", "_");
        if (t.startsWith("talos_")) {
            t = t.substring("talos_".length());
        }
        return t;
    }

    /** 从 TalosBiomes 反射收集与输入匹配的群系（支持字段名或 biomeName）。 */
    private static List<BiomeGenBase> resolveBiomes(String input) {
        List<BiomeGenBase> out = new ArrayList<BiomeGenBase>();

        for (Field f : TalosBiomes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (!BiomeGenBase.class.isAssignableFrom(f.getType())) {
                continue;
            }

            BiomeGenBase b;
            try {
                b = (BiomeGenBase) f.get(null);
            } catch (IllegalAccessException ex) {
                continue;
            }
            if (b == null) {
                continue;
            }

            String fieldNorm = normalize(f.getName());
            String nameNorm = normalize(b.biomeName);
            if (fieldNorm.equals(input) || nameNorm.equals(input)) {
                if (!out.contains(b)) {
                    out.add(b);
                }
            }
        }

        return out;
    }

    private static String availableBiomeNames() {
        List<String> names = new ArrayList<String>();

        for (Field f : TalosBiomes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (!BiomeGenBase.class.isAssignableFrom(f.getType())) {
                continue;
            }
            names.add(normalize(f.getName()));
        }

        Collections.sort(names);
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(n);
        }
        return sb.toString();
    }
}
