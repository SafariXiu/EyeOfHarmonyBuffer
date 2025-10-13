package com.EyeOfHarmonyBuffer.Events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class ClientJoinWorldHandler {

    private boolean shown = false;

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote && event.entity instanceof EntityClientPlayerMP && !shown) {
            Minecraft mc = Minecraft.getMinecraft();

            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin00));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01));

            shown = true;
        }
    }
}
