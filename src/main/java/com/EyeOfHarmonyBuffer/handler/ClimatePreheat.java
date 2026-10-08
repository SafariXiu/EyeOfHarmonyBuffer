package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
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
        final int seed = com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed.of(world);   // ★ 统一入口
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                // ★★★★★★★ 2026-10-08：原此处调 MountainLayerV2.ensure(seed) —— 旧山脉层已【物理删除】。
                //   山脉改由海陆分布层派生（OrographyField.orogeny01 = 窄脊线带 × 板块汇聚度），
                //   那是**逐点纯函数**，没有需要预热的离线网格。
                // 地貌场（250m 网格，唯一权威：地形与群系共用）
                LandformField.ensure(seed);
                // ★★ 2026-09-18 退役（用户裁决「必须顶死一套」）★★
                //   原此处调 RelaxedClimate.ensure(seed) —— **旧栈**（LAT_CYCLE = 1,000,000，
                //   赤道→极点 500 km；一次窗口构建实测 5.5~7.6 s、237 MB）。
                //
                //   但自 SimClimate 接线后，**生产气候路径只有 SimClimate 一套**：
                //     ClimateCoords.sample:77  ->  if (SimClimate.ENABLED) SimClimate.sample(...)
                //     ClimateCoords.sample:84-126（旧实现）在 ENABLED=true 下**一行都不走**
                //     群系链 = V2BiomeSelect -> ClimateCoords.sample -> SimClimate
                //     V2BiomeField / V2BiomePicker 对 RelaxedClimate **零代码引用**（已 grep 确认）
                //     RelaxedClimate 的其余消费者只有：GlobalClimate（仅 CommandTalosMap 用）
                //     与 TalosContract（自检类）—— 都不在生产路径上
                //   ⇒ 旧栈在**任何**生产路径上都没有消费者，这次预热是纯白烧。
                //
                //   ⚠ 原注释写「这四层在任何世界里都是被消费的」—— 那句话对 RelaxedClimate
                //     已经**不成立**（它是从「旧轨已删除 ⇒ 地形链唯一」推出来的，但气候链
                //     并不是那四层里的一层）。这正是「两套口径并存」留下的伤疤。
                //
                // RelxCn.ensure(seed);   // 已退役
                // 群系 LUT（250m 网格 + 平滑，依赖气候场）
                V2BiomeField.ensure(seed);
            }
        }, "EOHB-ClimatePreheat");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
