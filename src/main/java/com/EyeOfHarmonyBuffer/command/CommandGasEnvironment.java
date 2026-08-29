package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class CommandGasEnvironment extends CommandBase {

    @Override
    public String getCommandName() {
        return "gasenv";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gasenv [x y z]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (world == null) {
            sender.addChatMessage(new ChatComponentText("§c无法获取世界对象。"));
            return;
        }

        int x, y, z;

        if (args.length == 0) {
            if (!(sender instanceof EntityPlayer)) {
                throw new WrongUsageException("命令方必须是玩家，或指定坐标：/gasenv <x> <y> <z>");
            }
            EntityPlayer player = (EntityPlayer) sender;
            x = (int) Math.floor(player.posX);
            y = (int) Math.floor(player.posY);
            z = (int) Math.floor(player.posZ);
        } else if (args.length == 3) {
            x = parseIntBounded(sender, args[0], -30000000, 30000000);
            y = parseIntBounded(sender, args[1], 0, 255);
            z = parseIntBounded(sender, args[2], -30000000, 30000000);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        GasEnvironmentType type = GasEnvironmentHelper.getEnvironmentAt(world, x, y, z);

        String envName = type == null ? "NONE" : type.name();

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        sender.addChatMessage(new ChatComponentText(
            String.format("§a[GasEnv] 坐标 (%d, %d, %d) 所在区块 (%d, %d) 的环境为：§e%s",
                x, y, z, chunkX, chunkZ, envName)
        ));
    }
}
