package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyWorkTime {

    @Inject(method = "recipeProcessTimeCalculator", at = @At("HEAD"), cancellable = true)
    private void recipeProcessTimeCalculator(long recipeTime, long recipeSpacetimeCasingRequired,
        CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.EOHWorkTime){
            cir.setReturnValue(MainConfig.EOHtime);
        }
    }
}
