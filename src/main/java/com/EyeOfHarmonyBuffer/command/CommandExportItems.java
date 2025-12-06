package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.client.ClientItemTableUploader;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

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
        ClientItemTableUploader.exportNeiItemsToDefaultCsv();
        sender.addChatMessage(new ChatComponentText(
            "Exported NEI items to config/EyeOfHarmonyBuffer/eoh_nei_items.csv. " +
                "Copy this file to your server config/EyeOfHarmonyBuffer directory."
        ));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
