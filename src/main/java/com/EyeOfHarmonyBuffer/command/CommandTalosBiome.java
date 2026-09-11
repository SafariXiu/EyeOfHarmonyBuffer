package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomePicker;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
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

    /**
     * 搜索半径上限。**这是旧环面世界的遗留值**（当时 x 半周 200k、z 半周 100k ⇒ 200k 即"全球"），
     * 现值与任何世界周期都无关。值不变，仅补注说明；改它只影响本命令的输出，不影响世界生成。
     */
    private static final int MAX_RADIUS = 200000;

    /** 本命令的固定扫描域（blocks）——同样是旧环面遗留，见上方 MAX_RADIUS 说明。 */
    private static final int SCAN_X_SPAN = 400_000;
    private static final int SCAN_Z_SPAN = 200_000;

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
            + " - 搜索并传送到最近的指定群系（V2 轨默认全球搜索，半径上限 200000；旧轨默认 30000）";
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

        // 世界只有一条链 ⇒ 搜索永远是 LUT 查表（8 万次、瞬时、覆盖全球），默认半径直接取全球。
        // 这里曾经按 terrainV2Enabled 在 MAX_RADIUS / DEFAULT_RADIUS 之间二选一。
        int radius = MAX_RADIUS;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText(
                    "搜索半径无效: " + args[1] + "（应为正整数；V2 轨默认全球，旧轨默认 30000）"
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

        int worldSeedInt = TalosSeed.of(world);   // 世界种子派生唯一入口

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        int bestX = px;
        int bestZ = pz;
        double bestDistSq = Double.POSITIVE_INFINITY;
        boolean found = false;

        {
            // 直接在群系 LUT（250m 网格）上找最近命中格。
            // 注意：V2BiomeField.NX/NZ 是**单个瓦片**（100km × 50km = 400×200 格），
            // 而本命令要扫的是下面这张固定的旧环面区域（400k × 200k），所以按 SCAN_*_SPAN 循环，
            // 不是按瓦片尺寸。⚠️ 世界现在是 X 无限 / Z 不重复，这个扫描域与 wrapDistSq 的环绕
            // 都是旧环面的遗留；本命令只用于开发排查，改它会改变命令输出（不影响世界生成）。
            final int half = V2BiomeField.CELL / 2;
            final int periodNZ = SCAN_Z_SPAN / V2BiomeField.CELL;
            final int periodNX = SCAN_X_SPAN / V2BiomeField.CELL;
            for (int cz = 0; cz < periodNZ; cz++) {
                int wz = cz * V2BiomeField.CELL + half;
                for (int cx = 0; cx < periodNX; cx++) {
                    int wx = cx * V2BiomeField.CELL + half;
                    if (V2BiomePicker.biomeAt(wx, wz, worldSeedInt) != target) {
                        continue;
                    }
                    double d = wrapDistSq(px, pz, wx, wz);
                    if (d <= (double) radius * radius && d < bestDistSq) {
                        bestDistSq = d;
                        bestX = wx;
                        bestZ = wz;
                        found = true;
                    }
                }
            }
            // 在命中格内精扫（±REFINE_HALF，步长 REFINE_STEP）
            if (found) {
                int cx = bestX, cz = bestZ;
                bestDistSq = Double.POSITIVE_INFINITY;
                for (int dz = -REFINE_HALF; dz <= REFINE_HALF; dz += REFINE_STEP) {
                    for (int dx = -REFINE_HALF; dx <= REFINE_HALF; dx += REFINE_STEP) {
                        int x = cx + dx;
                        int z = cz + dz;
                        if (V2BiomePicker.biomeAt(x, z, worldSeedInt) != target) {
                            continue;
                        }
                        double d = wrapDistSq(px, pz, x, z);
                        if (d <= (double) radius * radius && d < bestDistSq) {
                            bestDistSq = d;
                            bestX = x;
                            bestZ = z;
                        }
                    }
                }
            }
        }

        if (!found) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "在半径 %d 格内未找到群系 \"%s\"。可以加大搜索半径重试（最大 %d）。",
                    radius, target.biomeName, MAX_RADIUS
                )
            ));
            return;
        }

        int y = world.getTopSolidOrLiquidBlock(bestX, bestZ);
        if (y <= 0) {
            y = 64;
        }
        // 钳制到合法玩家高度（最高 255），避免山顶列 y+2 越界被踢 "Illegal position"
        y = Math.min(y, 253);

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

    /**
     * 距离平方：**X 无限（不折叠）**、**Z 是 200k 纬度循环（折叠取最短）**。
     * 世界沿 X 无边界、沿 Z 循环，因此只有 Z 需要绕回。
     */
    private static double wrapDistSq(int px, int pz, int x, int z) {
        double dx = (double) x - px;
        int dz = Math.abs(z - pz) % 200000;
        if (dz > 100000) {
            dz = 200000 - dz;
        }
        return dx * dx + (double) dz * dz;
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

    /**
     * 粗扫用的群系查询：走世界群系管理器（= {@link V2BiomePicker} 那一条链）。
     * 旧轨的"旧宏气候"分支已随旧轨删除 —— 本命令不可能再读到第二套群系口径。
     */
    private static BiomeGenBase biomeAt(World world, int x, int z) {
        return world.getBiomeGenForCoords(x, z);
    }
}
