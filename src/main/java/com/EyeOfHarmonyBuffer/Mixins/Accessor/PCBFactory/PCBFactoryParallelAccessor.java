package com.EyeOfHarmonyBuffer.Mixins.Accessor.PCBFactory;

import gregtech.common.tileentities.machines.multi.MTEPCBFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MTEPCBFactory.class, remap = false)
public interface PCBFactoryParallelAccessor {

    @Accessor("mMaxParallel")
    void setMaxParallel(int maxParallel);

}
