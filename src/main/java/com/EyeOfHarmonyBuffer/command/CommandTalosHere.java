package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomePicker;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class CommandTalosHere extends CommandBase {

    @Override
    public String getCommandName() {
        return "talos_here";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos_here";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new WrongUsageException("该指令只能由玩家执行。");
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.getEntityWorld();

        int blockX = MathHelper.floor_double(player.posX);
        int blockY = MathHelper.floor_double(player.posY);
        int blockZ = MathHelper.floor_double(player.posZ);

        int worldSeedInt = TalosSeed.of(world);

        // 旧轨面板已删除（世界只有 V2 链）：本指令只输出 V2 信息面板。
        showV2Info(sender, world, blockX, blockY, blockZ, worldSeedInt);
    }
    /**
     * V2 轨信息面板（X1 阶段2）：类型场 + 群系 + 块高。旧轨字段（superId/板块）在 V2 世界无意义，不再显示。
     */
    private void showV2Info(ICommandSender sender, World world,
                            int blockX, int blockY, int blockZ, int worldSeedInt) {
        OrographyField.OroSample o = OrographyField.sample(blockX, blockZ, worldSeedInt);
        String dimName = getDimensionName(world);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[Talos] 维度: %s (id=%d), 坐标: (%d, %d, %d), worldSeedInt: %d",
            dimName, world.provider.dimensionId, blockX, blockY, blockZ, worldSeedInt
        )));
        if (!o.isLand) {
            double depth = V2TerrainGen.seaDepthBlocks(blockX, blockZ, worldSeedInt);
            sender.addChatMessage(new ChatComponentText(String.format(
                "[V2] 海洋 (L1 噪声海陆)  水深≈%.1f  biome=%s",
                depth, biomeName(world, blockX, blockZ)
            )));
            return;
        }
        String kind = kindLabel(o.kind);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] 陆地 kind=%s(%d)  elevation01=%.2f  relief01=%.2f  beltMask01=%.2f",
            kind, o.kind, o.elevation01, o.relief01, o.beltMask01
        )));
        // 高度链：**与生产同一个入口** V2TerrainGen.composeColumn。
        // 这里曾经少算 mountainDetail（也没软封顶），却打印"→ 合成高度"和"(雪线以上)"——
        // 显示值比世界里的列顶低 10~40 blocks，而"(雪线以上)"几乎永远为假。
        V2TerrainGen.Column col = V2TerrainGen.composeColumn(
            blockX, blockZ, worldSeedInt, LandformField.SEA_LEVEL, o,
            V2TerrainGen.MC_WORLD_HEIGHT - 2);
        // 立刻拷进局部变量：composeColumn 返回**每线程复用对象**（与生产同一条纪律）。
        final double plain = col.plain, base = col.base, mtnComp = col.mtnComp;
        final double w = col.auth, up = col.uplift, detail = col.detailStrength;
        final double hCap = col.hCapped, hDetail = col.hDetail;
        final int topY = col.h;
        final boolean snow = col.snow;
        final double snowY = V2TerrainGen.snowLineY(blockZ);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] coastDist=%.0f 块(陆侧负)  biome=%s", o.coastDist, biomeName(world, blockX, blockZ)
        )));
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] 高度分解: plain=%.1f  base=%.1f  mtnComp=%.1f", plain, base, mtnComp
        )));
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] 山层: w=%.2f  uplift=%.1f  细节强度=%.2f  → 合成高度=%.1f（封顶前 %.1f）  列顶 y=%d  雪线=%.1f  %s",
            w, up, detail, hCap, hDetail, topY, snowY, snow ? "(雪线以上)" : ""
        )));
    }

    private static String kindLabel(int kind) {
        switch (kind) {
            case OrographyField.KIND_HILL:     return "丘陵";
            case OrographyField.KIND_PLATEAU:  return "台地";
            case OrographyField.KIND_MOUNTAIN: return "山地";
            case OrographyField.KIND_PEAK:     return "峰";
            default:                           return "低地";
        }
    }

    private static String biomeName(World world, int x, int z) {
        try {
            net.minecraft.world.biome.BiomeGenBase b = world.getBiomeGenForCoords(x, z);
            return b == null ? "?" : b.biomeName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private String getDimensionName(World world) {
        try {
            return world.provider.getDimensionName();
        } catch (Throwable t) {
            return "Dim" + world.provider.dimensionId;
        }
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}
