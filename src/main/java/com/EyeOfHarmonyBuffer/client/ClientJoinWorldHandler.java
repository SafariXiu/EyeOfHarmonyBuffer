package com.EyeOfHarmonyBuffer.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class ClientJoinWorldHandler {

    private boolean shown = false;
    private int tickDelay = 0;
    private boolean waiting = false;

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote && event.entity instanceof EntityClientPlayerMP && !shown) {
            tickDelay = 40;
            waiting = true;
            shown = true;

            FMLCommonHandler.instance().bus().register(this);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!waiting) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        waiting = false;
        FMLCommonHandler.instance().bus().unregister(this);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Text_SeparatingLine));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin00));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01_1));

            sendConfigLink(mc, EOHB_Client_PlayerJoin02, "/openEOHBconfig main.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin03, "/openEOHBconfig items.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin04, "/openEOHBconfig fluids.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin05, "/openEOHBconfig MachineLoaderConfig.cfg");
            sendConfigLink(mc, EOHB_Client_PlayerJoin06, "/openEOHBconfig folder");

            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin07));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin01_1));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Client_PlayerJoin07_2));
            mc.thePlayer.addChatMessage(new ChatComponentText(EOHB_Text_SeparatingLine));
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
