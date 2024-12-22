package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2.class, remap = false)
public interface IndistinctTentaclePrototypeMK2Accessor {

    @Accessor("valid_fuels")
    static FluidStack[] getValidFuels() {
        throw new AssertionError();
    }
}
