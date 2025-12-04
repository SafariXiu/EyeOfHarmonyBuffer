package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.client.ClientItemTableUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.io.File;

public class CommandExportItems extends CommandBase {

    @Override
    public String getCommandName() {
        return "eoh_export_items";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/eoh_export_items -- export NEI display items to CSV";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        File gameDir = mc.mcDataDir;
        File outFile = new File(gameDir, "eoh_nei_items.csv");

        ClientItemTableUploader.exportNeiItemsToCsv(outFile);
        sender.addChatMessage(new ChatComponentText("Exported NEI items to " + outFile.getAbsolutePath()));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
