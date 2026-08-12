package com.EyeOfHarmonyBuffer.common.dyson;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * 每 MC 天（24,000 tick）触发一次戴森球每日结算：
 * 先贴片（128 云 = 1 贴片）、后掉落（10~64 云）、再完工判定。
 */
public class DysonSphereDailyHandler {

    private static final long TICKS_PER_DAY = 24_000L;

    private final Map<Integer, Long> lastSettledDay = new HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (world == null || world.isRemote) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (world.provider.dimensionId != RegisterDimensions.ID_TALOS2_DIM) {
            return;
        }

        long day = world.getWorldTime() / TICKS_PER_DAY;
        Long last = lastSettledDay.get(world.provider.dimensionId);
        if (last != null && day <= last) {
            return;
        }
        lastSettledDay.put(world.provider.dimensionId, day);

        DysonSphereSystem.settleDaily(world);
    }
}
