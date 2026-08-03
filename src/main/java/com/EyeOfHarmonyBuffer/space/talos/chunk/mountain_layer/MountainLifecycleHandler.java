package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer;

import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.api.TalosMountainSystem;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Unload;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 山地层世界生命周期：
 *   - WorldEvent.Load   -> 创建状态 + 启动后台预构建；
 *   - WorldEvent.Unload -> 停止线程 + 释放缓存；
 *   - WorldTickEvent    -> 预构建中心跟随玩家。
 */
public final class MountainLifecycleHandler {

    @SubscribeEvent
    public void onWorldLoad(Load event) {
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider instanceof WorldProviderTalos2) {
            TalosMountainSystem.onWorldLoad(event.world);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(Unload event) {
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider instanceof WorldProviderTalos2) {
            TalosMountainSystem.onWorldUnload(event.world);
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        if (event.world.provider instanceof WorldProviderTalos2) {
            TalosMountainSystem.onWorldTick(event.world);
        }
    }
}
