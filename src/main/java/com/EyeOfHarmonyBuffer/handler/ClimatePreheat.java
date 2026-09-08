package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

/**
 * 世界加载预热：Talos2（维度 14001）加载时后台线程预求解气候场，
 * 避免首区块生成时被 ~5s 的离线松弛卡住。失败/未完成时首次查询仍会兜底同步求解。
 */
public class ClimatePreheat {


    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.world;
        if (world == null || world.isRemote || world.provider == null) {
            return;
        }
        if (world.provider.dimensionId != RegisterDimensions.ID_TALOS2_DIM) {
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
