package com.EyeOfHarmonyBuffer.common.dyson;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;

/**
 * 每 MC 天触发两次戴森球结算：
 * 0:00 完整结算（贴片 + 掉落 + 完工判定），12:00 只做贴片转化（需点亮贴片转化节点）。
 * 结算水印持久化在 {@link DysonSphereWorldData}，服务器重启不会重复结算。
 */
public class DysonSphereDailyHandler {

    private static final long TICKS_PER_DAY = 24_000L;
    private static final long TICKS_PER_HALF_DAY = TICKS_PER_DAY / 2;

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

        long worldTime = world.getWorldTime();
        long day = worldTime / TICKS_PER_DAY;
        int half = (worldTime % TICKS_PER_DAY) >= TICKS_PER_HALF_DAY ? 1 : 0;
        long index = day * 2 + half;

        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || !data.tryClaimSettlement(index)) {
            return;
        }

        DysonSphereSystem.settleDaily(world, half == 1);
    }
}
