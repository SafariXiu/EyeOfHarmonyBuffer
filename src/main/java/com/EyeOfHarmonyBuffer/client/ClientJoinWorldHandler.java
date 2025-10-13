package com.EyeOfHarmonyBuffer.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

public class ClientJoinWorldHandler {

    private boolean shown = false;

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote && event.entity instanceof EntityClientPlayerMP && !shown) {
            Minecraft mc = Minecraft.getMinecraft();

            mc.thePlayer.addChatMessage(new ChatComponentText("§a欢迎使用 EyeOfHarmonyBuffer！"));
            mc.thePlayer.addChatMessage(new ChatComponentText("§e你可以通过以下链接打开配置文件："));

            sendConfigLink(mc, "§b[主配置文件 main.cfg]", "/openconfig main.cfg");
            sendConfigLink(mc, "§b[物品配置 items.cfg]", "/openconfig items.cfg");
            sendConfigLink(mc, "§b[流体配置 fluids.cfg]", "/openconfig fluids.cfg");
            sendConfigLink(mc, "§b[机器加载配置 MachineLoaderConfig.cfg]", "/openconfig MachineLoaderConfig.cfg");

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
