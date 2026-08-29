package com.EyeOfHarmonyBuffer.Events;

import com.EyeOfHarmonyBuffer.common.misc.OrundumLinkNetworkData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;

public class OrundumLinkEventHandler {

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;

        OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
        if (data != null) {
            data.tick(world);
        }
    }
}
