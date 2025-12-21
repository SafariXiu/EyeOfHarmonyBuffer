package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.Talos2MapExporter;
import com.EyeOfHarmonyBuffer.utils.Talos2Hooks;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.io.IOException;

public class CommandTalos2Export extends CommandBase {

    @Override
    public String getCommandName() {
        return "talos2export";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos2export <distZone|isLand|c01> <sizeBlocks> <stepBlocks> <fileName> [coastRadius] [visRadius]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 4) throw new WrongUsageException(getCommandUsage(sender));

        final String mode = args[0];
        final int sizeBlocks = parseIntBounded(sender, args[1], 512, 2_000_000);
        final int stepBlocks = parseIntBounded(sender, args[2], 1, 4096);
        final String fileName = args[3];

        final int coastRadius = (args.length >= 5) ? parseIntBounded(sender, args[4], 16, 4096) : 192;
        final int visRadius   = (args.length >= 6) ? parseIntBounded(sender, args[5], 16, 4096) : 160;

        World world = sender.getEntityWorld();

        final Talos2Hooks.HookData hook = Talos2Hooks.resolve(world);
        if (hook == null) {
            sender.addChatMessage(new ChatComponentText("[Talos2] Not in Talos2 world or continentNoise unavailable."));
            return;
        }

        sender.addChatMessage(new ChatComponentText(
            "[Talos2] Export started: mode=" + mode + " size=" + sizeBlocks + " step=" + stepBlocks + " file=" + fileName
        ));

        new Thread(() -> {
            try {
                if ("distZone".equalsIgnoreCase(mode)) {
                    Talos2MapExporter.exportDistZoneMap(
                        hook.world, hook.continentNoise, hook.seed,
                        fileName,
                        sizeBlocks, stepBlocks,
                        coastRadius,
                        visRadius,
                        24,
                        true
                    );
                } else if ("isLand".equalsIgnoreCase(mode)) {
                    Talos2MapExporter.exportIsLandMap(
                        hook.world, hook.continentNoise, hook.seed,
                        fileName,
                        sizeBlocks, stepBlocks,
                        coastRadius,
                        24,
                        true
                    );
                } else if ("c01".equalsIgnoreCase(mode)) {
                    Talos2MapExporter.exportContinentC01Map(
                        hook.world, hook.continentNoise, hook.seed,
                        fileName,
                        sizeBlocks, stepBlocks,
                        24,
                        true
                    );
                } else {
                    sender.addChatMessage(new ChatComponentText("[Talos2] Unknown mode: " + mode));
                    return;
                }

                sender.addChatMessage(new ChatComponentText("[Talos2] Export finished: " + fileName));
            } catch (IOException e) {
                sender.addChatMessage(new ChatComponentText("[Talos2] Export failed: " + e.getMessage()));
                e.printStackTrace();
            } catch (Throwable t) {
                sender.addChatMessage(new ChatComponentText("[Talos2] Export crashed: " + t));
                t.printStackTrace();
            }
        }, "Talos2ExportThread").start();
    }
}
