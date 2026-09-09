package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;
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
                // 山层（400k×200k 环面离线求解 + 侵蚀，~1-3s）
                MountainLayerV2.ensure(seed);
                // 地貌场（1km，唯一权威：地形与群系共用）
                LandformField.ensure(seed);
                // 气候场（~5s 松弛求解）
                RelaxedClimate.ensure(seed);
                // 群系 LUT（1km 网格 + 平滑，依赖气候场，~0.3s）
                V2BiomeField.ensure(seed);
            }
        }, "EOHB-ClimatePreheat");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
