package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.Talos2MapExporter;
import com.EyeOfHarmonyBuffer.space.talos.Talos2NoiseConfig;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
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
        return "";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

    }
}
