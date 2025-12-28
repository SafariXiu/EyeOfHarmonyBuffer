package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.Talos2ClimateService;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2ClimateSampler;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.List;

public class CommandTalosClimate extends CommandBase {

    @Override
    public String getCommandName() {
        return "talos2climate";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos2climate [x] [z]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        World world = getWorld(sender);

        double defaultX = sender.getPlayerCoordinates().posX;
        double defaultZ = sender.getPlayerCoordinates().posZ;

        double x = (args.length >= 1) ? parseCoord(sender, defaultX, args[0]) : defaultX;
        double z = (args.length >= 2) ? parseCoord(sender, defaultZ, args[1]) : defaultZ;

        Talos2ClimateSampler sampler = Talos2ClimateService.get(world);
        Talos2ClimateSampler.ClimateSample sample = sampler.sample((int) Math.floor(x), (int) Math.floor(z));

        String msg = String.format(
            "[Talos2] x=%.1f z=%.1f | temp=%.3f hum=%.3f continental=%.3f macroId=%d",
            x, z, sample.temperature, sample.humidity, sample.continentalness, sample.primaryMacroId);

        sender.addChatMessage(new ChatComponentText(msg));
    }

    private double parseCoord(ICommandSender sender, double base, String token) {
        boolean relative = token.startsWith("~");
        double offset = 0.0;

        if (relative && token.length() > 1) {
            offset = parseDouble(token.substring(1));
        } else if (!relative) {
            return parseDouble(token);
        }

        return relative ? base + offset : offset;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new NumberInvalidException("commands.generic.num.invalid", value);
        }
    }

    private World getWorld(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) sender).worldObj;
        }
        return MinecraftServer.getServer().getEntityWorld();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return (args.length == 1 || args.length == 2)
            ? getListOfStringsMatchingLastWord(args, "~")
            : null;
    }
}
