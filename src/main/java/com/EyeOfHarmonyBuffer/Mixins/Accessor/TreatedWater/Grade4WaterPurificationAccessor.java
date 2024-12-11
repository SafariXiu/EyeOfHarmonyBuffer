package com.EyeOfHarmonyBuffer.Mixins.Accessor.TreatedWater;

import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitPhAdjustment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MTEPurificationUnitPhAdjustment.class,remap = false)
public interface Grade4WaterPurificationAccessor {

    @Accessor("currentpHValue")
    float getCurrentpHValue();

    @Accessor("currentpHValue")
    void setCurrentpHValue(float value);
}
