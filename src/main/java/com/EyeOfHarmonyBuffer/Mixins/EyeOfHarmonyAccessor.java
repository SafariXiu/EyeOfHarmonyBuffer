package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public interface EyeOfHarmonyAccessor {

    @Accessor("parallelAmount")
    long getParallelAmount();

    @Accessor("parallelAmount")
    void setParallelAmount(long parallelAmount);

}
