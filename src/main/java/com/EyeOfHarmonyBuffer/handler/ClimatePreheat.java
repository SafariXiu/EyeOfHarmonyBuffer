package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

/**
 * 世界加载预热：Talos2（维度 14001）加载时后台线程预求解气候场，
 * 避免首区块生成时被 ~5s 的离线松弛卡住。失败/未完成时首次查询仍会兜底同步求解。
 */
public class ClimatePreheat {

    /** Talos2 维度 ID（与 space.RegisterDimensions 一致）。 */
    private static final int DIM_TALOS2 = 14001;

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.world;
        if (world == null || world.isRemote || world.provider == null) {
            return;
        }
        if (world.provider.dimensionId != DIM_TALOS2) {
            return;
        }
        final int seed = (int) (world.getSeed() & 0x7FFFFFFFL);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                RelaxedClimate.ensure(seed);
            }
        }, "EOHB-ClimatePreheat");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
