package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;

public class WirelessTickHandler {

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        World world = event.world;
        if (world == null || world.isRemote) {
            return;
        }

        WirelessComputeManager.getInstance().serverTick(world);
    }
}
