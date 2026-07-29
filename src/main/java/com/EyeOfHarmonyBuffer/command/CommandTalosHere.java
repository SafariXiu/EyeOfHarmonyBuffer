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

        int superId = TalosLandMask.getSuperId(blockX, blockZ, worldSeedInt);
        int plateId = TalosLandMask.getPlateId(blockX, blockZ, worldSeedInt);
        double landWeight = TalosLandMask.getLandWeight(blockX, blockZ, worldSeedInt);
        double coastWeight = TalosLandMask.getCoastWeight(blockX, blockZ, worldSeedInt);
        double EdgeWeight = TalosLandMask.getEdgeWeight(blockX, blockZ, worldSeedInt);

        boolean isLand = TalosLandMask.isLandCheap(blockX, blockZ, worldSeedInt);

        String dimName = getDimensionName(world);
        String msgHeader = String.format(
            "[Talos] 维度: %s (id=%d), 坐标: (%d, %d, %d)",
            dimName, world.provider.dimensionId, blockX, blockY, blockZ
        );

        String msgBody = String.format(
            "超级大陆ID: %d, 板块ID: %d, isLand: %s, landWeight: %.3f, coastWeight: %.3f, EdgeWeight: %.3f",
            superId, plateId, isLand ? "true" : "false", landWeight, coastWeight, EdgeWeight
        );

        sender.addChatMessage(new ChatComponentText(msgHeader));
        sender.addChatMessage(new ChatComponentText(msgBody));
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
