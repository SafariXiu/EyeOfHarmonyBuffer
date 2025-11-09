package com.EyeOfHarmonyBuffer.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

public class EOHBChatUtils {

    public static ChatComponentText createConfigLink(String displayText, String command) {
        ChatComponentText clickable = new ChatComponentText(displayText);
        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        style.setUnderlined(true);
        clickable.setChatStyle(style);
        return clickable;
    }

    public static void sendConfigLink(Minecraft mc, String displayText, String command) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(createConfigLink(displayText, command));
        }
    }
}
