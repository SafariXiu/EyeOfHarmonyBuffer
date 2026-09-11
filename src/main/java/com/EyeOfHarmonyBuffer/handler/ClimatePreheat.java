package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.MountainLayerV2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2BiomeField;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

/**
 * 世界加载预热：Talos2（维度 14001）加载时后台线程预求解 V2 的四层，
 * 避免首区块生成时被一次窗口构建卡住（实测 5.5~7.6 s/窗口，见 P203）。
 * 失败/未完成时首次查询仍会兜底同步求解。
 *
 * **只在 V2 地形轨开启时才预热**（terrainV2Enabled）。默认轨（V1）根本不消费这四层，
 * 以前无条件预热等于每次世界加载白烧 4 层后台求解。
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
        // 这里曾经有个 "V2 轨没开就不预热" 的提前返回。旧轨已删除 ⇒ 地形链唯一，
        // 这四层在任何世界里都是被消费的，预热条件只剩"是不是 Talos 维度"。
        final int seed = (int) (world.getSeed() & 0x7FFFFFFFL);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                // 山层（每格 50km×200km 离线求解抬升 + 侵蚀，250m 网格）
                MountainLayerV2.ensure(seed);
                // 地貌场（250m 网格，唯一权威：地形与群系共用）
                LandformField.ensure(seed);
                // 气候场（一次窗口构建实测 5.5~7.6 s）
                RelaxedClimate.ensure(seed);
                // 群系 LUT（250m 网格 + 平滑，依赖气候场）
                V2BiomeField.ensure(seed);
            }
        }, "EOHB-ClimatePreheat");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
