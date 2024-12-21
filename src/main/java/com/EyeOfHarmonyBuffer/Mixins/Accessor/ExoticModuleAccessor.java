package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

@Mixin(value = MTEExoticModule.class, remap = false)
public interface ExoticModuleAccessor {

    @Accessor("NUMBER_OF_INPUTS")
    static int getNumberOfInputs() {
        throw new AssertionError();
    }
}
