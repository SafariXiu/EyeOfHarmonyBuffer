package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public interface EyeOfHarmonyAccessor {

    @Accessor("parallelAmount")
    long getParallelAmount();

    @Accessor("parallelAmount")
    void setParallelAmount(long parallelAmount);

}
