package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.api.TalosCaveSystem;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * 洞穴层世界生命周期（组合根层）：
 *   - WorldEvent.Load   -> 预热状态；
 *   - WorldEvent.Unload -> 释放节点缓存。
 */
public final class CaveLifecycleHandler {

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider instanceof WorldProviderTalos2) {
            TalosCaveSystem.onWorldLoad(event.world);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider instanceof WorldProviderTalos2) {
            TalosCaveSystem.onWorldUnload(event.world);
        }
    }
}
