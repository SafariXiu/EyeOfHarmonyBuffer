package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
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
            "landWeight: %.3f, coastWeight: %.3f, edgeWeight: %.3f, shelfWeight: %.3f",
            landWeight, coastWeight, edgeWeight, shelfWeight
        );

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
        sender.addChatMessage(new ChatComponentText(msgSuper));
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
