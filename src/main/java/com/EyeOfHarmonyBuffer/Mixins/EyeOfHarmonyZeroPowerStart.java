package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tectech.recipe.EyeOfHarmonyRecipe;

@Mixin(value = EyeOfHarmonyRecipe.class, remap = false)
public class EyeOfHarmonyZeroPowerStart {

    @Inject(method = "getEUStartCost", at = @At("RETURN"), cancellable = true)
    private void modifyGetEUStartCost(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(0L);
    }
}