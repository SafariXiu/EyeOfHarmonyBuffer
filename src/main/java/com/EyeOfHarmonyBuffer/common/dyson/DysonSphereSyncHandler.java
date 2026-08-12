package com.EyeOfHarmonyBuffer.common.dyson;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;

/** 玩家登录时把当前戴森球状态同步过去，避免新客户端看不到已建成的进度。 */
public class DysonSphereSyncHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DysonSphereSystem.syncToPlayer((EntityPlayerMP) event.player);
        }
    }
}
