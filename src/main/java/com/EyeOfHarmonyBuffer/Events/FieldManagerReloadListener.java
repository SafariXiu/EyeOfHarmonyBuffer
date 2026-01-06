package com.EyeOfHarmonyBuffer.Events;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.TalosFieldContextBootstrap;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;

public final class FieldManagerReloadListener {

    private final World world;
    private FieldContext fieldContext;

    public FieldManagerReloadListener(World world, FieldContext initialContext) {
        this.world = world;
        this.fieldContext = initialContext;
    }

    @SubscribeEvent
    public void onConfigReload(ConfigReloadedEvent event) {
        this.fieldContext = TalosFieldContextBootstrap.create(world);
    }

    public FieldContext getFieldContext() {
        return fieldContext;
    }
}
