package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.recipe.EyeOfHarmonyRecipe;

@Mixin(value = EyeOfHarmonyRecipe.class, remap = false)
public class EyeOfHarmonyZeroPowerStart {

    @Inject(method = "getEUStartCost", at = @At("RETURN"), cancellable = true)
    private void modifyGetEUStartCost(CallbackInfoReturnable<Long> cir) {

        if (MainConfig.EOHZeroPowerStart) {
            cir.setReturnValue(0L);
        }
    }
}
