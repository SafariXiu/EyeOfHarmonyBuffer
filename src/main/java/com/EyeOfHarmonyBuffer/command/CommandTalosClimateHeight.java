package com.EyeOfHarmonyBuffer.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

public class CommandTalosClimateHeight extends CommandBase {

    @Override
    public String getCommandName() {
        return "talosclimate";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosclimate [x] [z]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ChunkCoordinates origin = sender.getPlayerCoordinates();
        if (origin == null) {
            origin = new ChunkCoordinates(0, 0, 0);
        }

        int x = origin.posX;
        int z = origin.posZ;

        if (args.length == 1 || args.length == 2) {
            x = parseInt(sender, args[0]);
        }
        if (args.length == 2) {
            z = parseInt(sender, args[1]);
        }
        if (args.length > 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        World world = sender.getEntityWorld();
        TalosClimateSample sample = TalosClimateDiagnostics.sample(world, x, z);

        sender.addChatMessage(new ChatComponentText(sample.formatForChat()));
    }
}
