package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;
import java.util.Locale;

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

        int xi = MathHelper.floor_double(x);
        int zi = MathHelper.floor_double(z);

        BiomeGenBase biome = world.getBiomeGenForCoords(xi, zi);
        float temp = biome.temperature;
        float humid = biome.rainfall;

        MacroBiomeField.SampleDual macroSample = null;
        Talos2Hooks.HookData hook = Talos2Hooks.resolve(world);
        if (hook != null && hook.macroField != null) {
            macroSample = hook.macroField.sampleDual(xi, zi);
        }

        MacroBiome primary = (macroSample != null) ? macroSample.primary : null;
        MacroBiome secondary = (macroSample != null) ? macroSample.secondary : null;
        double weight = (macroSample != null) ? macroSample.primaryWeight : 0.0D;
        int macroId = (primary != null) ? primary.getId() : -1;

        String msg = String.format(Locale.ROOT,
            "[Talos2] x=%.1f z=%.1f | biome=%s temp=%.3f hum=%.3f macro=%s/%s weight=%.2f macroId=%d",
            x, z,
            biome.biomeName,
            temp,
            humid,
            primary != null ? primary.name() : "none",
            secondary != null ? secondary.name() : "none",
            weight,
            macroId
        );

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
