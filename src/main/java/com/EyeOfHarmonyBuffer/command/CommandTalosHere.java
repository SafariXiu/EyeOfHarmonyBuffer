package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;
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

        int worldSeedInt = TalosLandMask.getWorldSeedInt(world);

        if (V2TerrainConfigSection.terrainV2Enabled) {
            showV2Info(sender, world, blockX, blockY, blockZ, worldSeedInt);
            return;
        }

        TalosLandMask.Sample sample = TalosLandMask.sampleFull(blockX, blockZ, worldSeedInt);
        if (sample == null) {
            String dimName = getDimensionName(world);
            String msgHeader = String.format(
                "[Talos] 维度: %s (id=%d), 坐标: (%d, %d, %d)",
                dimName, world.provider.dimensionId, blockX, blockY, blockZ
            );
            sender.addChatMessage(new ChatComponentText(msgHeader));
            sender.addChatMessage(new ChatComponentText(
                "[Talos] 无法获取采样结果（sampleFull 返回 null）"
            ));
            return;
        }

        boolean isLand = sample.isLand;
        int plateId = sample.plateId;
        int superId = sample.superId;

        double landWeight  = sample.landWeight;
        double coastWeight = sample.coastWeight;
        double edgeWeight  = sample.edgeWeight;
        double shelfWeight = sample.shelfWeight;
        double plateBoundaryWeight = sample.plateBoundaryWeight;
        String plateBoundaryState = String.valueOf(sample.plateBoundaryState);
        double plateCompression = sample.plateCompression;

        StringBuilder mix = new StringBuilder();
        if (sample.plateBoundaryInfluences != null) {
            for (int i = 0; i < sample.plateBoundaryInfluences.length; i++) {
                if (i > 0) {
                    mix.append(", ");
                }
                mix.append(sample.plateBoundaryInfluences[i].state)
                   .append("(")
                   .append(String.format("%.3f", sample.plateBoundaryInfluences[i].strength))
                   .append(")");
            }
        }
        if (mix.length() == 0) {
            mix.append("无");
        }

        int[] superCenter = TalosLandMask.getSuperCenterXZAt(blockX, blockZ, worldSeedInt);
        double superBaseRadius = TalosLandMask.getSuperBaseRadius(superId, worldSeedInt);

        String dimName = getDimensionName(world);

        String msgHeader = String.format(
            "[Talos] 维度: %s (id=%d), 坐标: (%d, %d, %d), worldSeedInt: %d",
            dimName, world.provider.dimensionId, blockX, blockY, blockZ, worldSeedInt
        );

        String mainSubLabel = "";
        if (superId != 0) {
            mainSubLabel = TalosLandMask.isMainSupercontinent(superId)
                ? "主大陆" : "次级大陆";
        }

        String msgIds = String.format(
            "超级大陆ID: %d (%s), 板块ID: %d, isLand: %s",
            superId,
            mainSubLabel.isEmpty() ? "无" : mainSubLabel,
            plateId,
            isLand ? "true" : "false"
        );

        String msgWeights = String.format(
            "landWeight: %.3f, coastWeight: %.3f, edgeWeight: %.3f, shelfWeight: %.3f, plateBoundary: %s(%.3f), 挤压度: %+.3f",
            landWeight, coastWeight, edgeWeight, shelfWeight,
            plateBoundaryState, plateBoundaryWeight, plateCompression
        );
        String msgMix = "板块混合: " + mix;

        String msgSuper;
        if (superId == 0 || superCenter == null) {
            msgSuper = "当前位置不在任何超级大陆内 (superId=0)";
        } else {
            msgSuper = String.format(
                "超级大陆中心: (%d, %d), baseRadius: %.1f",
                superCenter[0], superCenter[1], superBaseRadius
            );
        }

        sender.addChatMessage(new ChatComponentText(msgHeader));
        sender.addChatMessage(new ChatComponentText(msgIds));
        sender.addChatMessage(new ChatComponentText(msgWeights));
        sender.addChatMessage(new ChatComponentText(msgMix));
        sender.addChatMessage(new ChatComponentText(msgSuper));
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
        double base = V2TerrainGen.landBaseHeight(blockX, blockZ, worldSeedInt, 64, o);
        double plain = V2TerrainGen.landPlainHeight(blockX, blockZ, worldSeedInt, 64, o);
        double mtnComp = Math.max(0.0, base - plain);
        double w = MountainLayerV2.auth(blockX, blockZ, worldSeedInt);
        double up = MountainLayerV2.uplift(blockX, blockZ, worldSeedInt);
        double h = plain + (1.0 - w) * mtnComp + w * up;
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] coastDist=%.0f 块(陆侧负)  biome=%s", o.coastDist, biomeName(world, blockX, blockZ)
        )));
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] 高度分解: plain=%.1f  base=%.1f  mtnComp=%.1f", plain, base, mtnComp
        )));
        sender.addChatMessage(new ChatComponentText(String.format(
            "[V2] 山层: w=%.2f  uplift=%.1f  → 合成高度=%.1f  雪线=%.1f  %s",
            w, up, h, V2TerrainGen.snowLineY(blockZ),
            h >= V2TerrainGen.snowLineY(blockZ) ? "(雪线以上)" : ""
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
