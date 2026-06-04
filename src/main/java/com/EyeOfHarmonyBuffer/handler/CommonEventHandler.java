package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.overdomain.entity.OverdomainErosionData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

public class CommonEventHandler {

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity instanceof EntityLivingBase) {
            OverdomainErosionData.register((EntityLivingBase) event.entity);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase living = event.entityLiving;
        OverdomainErosionData data = OverdomainErosionData.get(living);
        if (data != null) {
            data.onEntityUpdate();
        }
    }
}
