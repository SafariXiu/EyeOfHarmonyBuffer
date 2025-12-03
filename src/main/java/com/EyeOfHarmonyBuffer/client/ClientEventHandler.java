package com.EyeOfHarmonyBuffer.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class ClientEventHandler {

    private static boolean itemTableUploaded = false;

    @SubscribeEvent
    public void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player == null) return;
        if (!event.player.worldObj.isRemote) return;

        EntityPlayer clientPlayer = Minecraft.getMinecraft().thePlayer;
        if (event.player != clientPlayer) return;

        if (itemTableUploaded) return;

        System.out.println("[EOH] onClientPlayerTick: triggering scanAndUpload once for " + clientPlayer.getCommandSenderName());
        ClientItemTableUploader.scanAndUpload();
        itemTableUploaded = true;
    }
}
