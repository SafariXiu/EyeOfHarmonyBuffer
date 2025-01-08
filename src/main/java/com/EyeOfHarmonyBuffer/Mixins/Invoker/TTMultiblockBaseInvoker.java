package com.EyeOfHarmonyBuffer.Mixins.Invoker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

@Mixin(value = TTMultiblockBase.class, remap = false)
public interface TTMultiblockBaseInvoker {

    @Invoker("getAvailableData_EM")
    long invokeGetAvailableData_EM();
}
