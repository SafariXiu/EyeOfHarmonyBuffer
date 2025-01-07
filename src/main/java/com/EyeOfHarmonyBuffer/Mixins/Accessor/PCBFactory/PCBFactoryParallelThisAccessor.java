package com.EyeOfHarmonyBuffer.Mixins.Accessor.PCBFactory;

import gregtech.common.tileentities.machines.multi.MTEPCBFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTEPCBFactory$1",remap = false)
public interface PCBFactoryParallelThisAccessor {

    @Accessor("this$0")
    MTEPCBFactory getOuterInstance();

}
