package com.EyeOfHarmonyBuffer.Mixins;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.api.util.GTRecipe;
import gtnhlanth.common.tileentity.MTEDissolutionTank;

@Mixin(value = MTEDissolutionTank.class, remap = false)
public class DissolutionTankMixin {

    @Inject(method = "checkRatio", at = @At("HEAD"), cancellable = true)
    private void injectCheckRatio(GTRecipe tRecipe, List<FluidStack> tFluidInputs,
        CallbackInfoReturnable<Boolean> cir) {
        if (MainConfig.DisTankTrue) {
            cir.setReturnValue(true);
        }
    }
}
