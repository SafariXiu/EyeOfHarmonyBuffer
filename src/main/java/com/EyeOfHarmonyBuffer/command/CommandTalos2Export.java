package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.Talos2MapExporter;
import com.EyeOfHarmonyBuffer.space.talos.Talos2NoiseConfig;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import com.EyeOfHarmonyBuffer.space.talos.Talos2Hooks;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.Locale;

public class CommandTalos2Export extends CommandBase {

    @Override
    public String getCommandName() {
        return "talos2export";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos2export <distZone|isLand|c01|macroBiome|biome>"
            + " <sizeBlocks> <stepBlocks> <fileName>"
            + " [coastRadius] [visRadius] [tilePixels] [macroPreset]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        final String mode       = args[0];
        final int sizeBlocks    = parseIntBounded(sender, args[1], 512, 2_000_000);
        final int stepBlocks    = parseIntBounded(sender, args[2], 1, 4096);
        final String fileName   = args[3];

        final int coastRadius   = (args.length >= 5) ? parseIntBounded(sender, args[4], 16, 4096) : 192;
        final int visRadius     = (args.length >= 6) ? parseIntBounded(sender, args[5], 16, 4096) : 160;
        final int tilePixels    = (args.length >= 7) ? parseIntBounded(sender, args[6], 4, 256)   : 24;
        final String macroPreset = (args.length >= 8 && !args[7].isEmpty())
            ? args[7].toLowerCase(Locale.ROOT)
            : Talos2NoiseConfig.getActiveMacroPreset();

        final MacroBiomeField.MacroBiomeConfig macroConfig =
            Talos2NoiseConfig.resolveMacroPreset(macroPreset);
        if (macroConfig == null) {
            sender.addChatMessage(new ChatComponentText("[Talos2] Unknown macro preset: " + macroPreset));
            return;
        }

        World world = sender.getEntityWorld();
        Talos2Hooks.HookData hook = Talos2Hooks.resolve(world);

        if (hook == null) {
            sender.addChatMessage(new ChatComponentText("[Talos2] Not in Talos2 world or continentNoise unavailable."));
            return;
        }

        sender.addChatMessage(new ChatComponentText(
            "[Talos2] Export started: mode=" + mode
                + " size=" + sizeBlocks
                + " step=" + stepBlocks
                + " file=" + fileName
                + " preset=" + macroPreset
        ));

        new Thread(() ->
            Talos2NoiseConfig.withMacroPreset(macroPreset, () ->
                runExport(sender, hook, macroConfig, mode, sizeBlocks, stepBlocks,
                    fileName, coastRadius, visRadius, tilePixels)
            ),
            "Talos2ExportThread"
        ).start();
    }

    private void runExport(ICommandSender sender,
                           Talos2Hooks.HookData hook,
                           MacroBiomeField.MacroBiomeConfig macroConfig,
                           String mode,
                           int sizeBlocks,
                           int stepBlocks,
                           String fileName,
                           int coastRadius,
                           int visRadius,
                           int tilePixels) {

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        int centerX = MathHelper.floor_double(player.posX);
        int centerZ = MathHelper.floor_double(player.posZ);

        try {
            switch (mode.toLowerCase(Locale.ROOT)) {
                case "distzone" ->
                    Talos2MapExporter.exportDistZoneMap(
                        hook,hook.world, hook.seed, fileName,
                        sizeBlocks, stepBlocks, coastRadius,
                        visRadius, tilePixels, true, centerX,
                        centerZ, macroConfig);

                case "island" ->
                    Talos2MapExporter.exportIsLandMap(
                        hook,hook.world, hook.seed,
                        fileName, sizeBlocks, stepBlocks,
                        coastRadius, tilePixels, true,
                        centerX, centerZ);

                case "c01" ->
                    Talos2MapExporter.exportContinentC01Map(
                        hook.world, hook.seed,
                        fileName, sizeBlocks, stepBlocks,
                        tilePixels, true,
                        centerX, centerZ);

                case "macrobiome" ->
                    Talos2MapExporter.exportMacroBiomeMap(
                        hook,hook.world, hook.seed, fileName,
                        sizeBlocks, stepBlocks, tilePixels,
                        true, centerX,
                        centerZ, macroConfig);

                case "biome" ->
                    Talos2MapExporter.exportFinalBiomeMap(
                        hook,hook.world, hook.seed, fileName,
                        sizeBlocks, stepBlocks, tilePixels,
                        true, centerX,
                        centerZ, macroConfig);

                default ->
                    sender.addChatMessage(new ChatComponentText("[Talos2] Unknown mode: " + mode));
            }

            sender.addChatMessage(new ChatComponentText("[Talos2] Export finished: " + fileName));

        } catch (IOException e) {
            sender.addChatMessage(new ChatComponentText("[Talos2] Export failed: " + e.getMessage()));
            e.printStackTrace();
        } catch (Throwable t) {
            sender.addChatMessage(new ChatComponentText("[Talos2] Export crashed: " + t));
            t.printStackTrace();
        }
    }
}
