package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

@Mixin(value = TTMultiblockBase.class, remap = false)
public interface TTMultiblockBaseAccessor {

    @Accessor("eEnergyMulti")
    ArrayList<MTEHatchEnergyMulti> getEnergyMulti();

    @Accessor("eDynamoMulti")
    ArrayList<?> getEDynamoMulti();

}
