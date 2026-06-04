package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.utils.OverdomainRenderHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import org.lwjgl.opengl.GL11;

public class OverdomainFogHandler {

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        EntityPlayer player = (EntityPlayer) event.entity;
        if (!OverdomainRenderHelper.isPlayerInsidePortal(player)) return;

        float r = 0.45F;
        float g = 0.00F;
        float b = 0.05F;

        event.red   = r;
        event.green = g;
        event.blue  = b;
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        EntityPlayer player = (EntityPlayer) event.entity;
        if (!OverdomainRenderHelper.isPlayerInsidePortal(player)) return;

        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP2);

        event.density = 0.20F;
        event.setCanceled(true);
    }
}
