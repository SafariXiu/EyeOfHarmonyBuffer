package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.entity.Arknights.EntityIndustrialExplosive;
import cpw.mods.fml.common.registry.EntityRegistry;

public class EntityLoader {

    private static int nextEntityId = 0;

    public static void registerEntities() {
        registerEntity(
            EntityIndustrialExplosive.class,
            "IndustrialExplosive",
            64,
            10,
            true
        );
    }

    private static void registerEntity(Class entityClass,
                                       String name,
                                       int trackingRange,
                                       int updateFrequency,
                                       boolean sendsVelocityUpdates) {

        EntityRegistry.registerModEntity(
            entityClass,
            name,
            nextEntityId++,
            EyeOfHarmonyBuffer.instance,
            trackingRange,
            updateFrequency,
            sendsVelocityUpdates
        );
    }
}
