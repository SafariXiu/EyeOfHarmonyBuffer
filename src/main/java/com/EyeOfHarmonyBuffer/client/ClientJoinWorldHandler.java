package com.EyeOfHarmonyBuffer.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
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

            sendConfigLink(mc, EOHB_Client_PlayerJoin02, "/openconfig main.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin03, "/openconfig items.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin04, "/openconfig fluids.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin05, "/openconfig MachineLoaderConfig.cfg");

            shown = true;
        }
    }

    private void sendConfigLink(Minecraft mc, String displayText, String command) {
        ChatComponentText clickable = new ChatComponentText(displayText);
        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        style.setUnderlined(true);
        clickable.setChatStyle(style);
        mc.thePlayer.addChatMessage(clickable);
    }
}
