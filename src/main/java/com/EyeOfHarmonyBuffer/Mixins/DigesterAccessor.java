package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import gregtech.api.enums.HeatingCoilLevel;
import gtnhlanth.common.tileentity.MTEDigester;

@Mixin(value = MTEDigester.class, remap = false)
public interface DigesterAccessor {

    @Accessor("heatLevel")
    HeatingCoilLevel getHeatLevel();

}
