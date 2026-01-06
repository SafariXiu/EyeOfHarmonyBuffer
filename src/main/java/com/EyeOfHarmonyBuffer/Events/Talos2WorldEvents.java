package com.EyeOfHarmonyBuffer.Events;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public final class Talos2WorldEvents {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Talos2WorldEvents());
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.world;
        if (world == null) return;

        int dim = world.provider.dimensionId;

        if (world.getChunkProvider() instanceof ChunkProviderTalos2) {
            Talos2Hooks.unregister(dim);
        }
    }
}
