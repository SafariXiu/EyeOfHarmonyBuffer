package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.api.TalosMountainSystem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;

/**
 * /talmountain - 山地系统调试指令。
 * 只经 TalosMountainSystem api 获取调试信息，不直接接触 runtime 内部。
 */
public class CommandTalosMountain extends CommandBase {

    @Override
    public String getCommandName() {
        return "talmountain";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talmountain - 查看当前位置的山地系统状态";
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

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;
        int worldSeedInt = TalosLandMask.getWorldSeedInt(world);
        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        List<String> lines = TalosMountainSystem.debugSummary(
            px, pz, worldSeedInt
        );
        for (String line : lines) {
            sender.addChatMessage(new ChatComponentText(line));
        }

        BiomeGenBase biome = world.getBiomeGenForCoords(px, pz);
        sender.addChatMessage(new ChatComponentText(
            "  biome=" + (biome != null ? biome.biomeName : "null")
        ));
    }
}
