package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonySuccessRateControl {

    @Inject(method = "recipeChanceCalculator", at = @At("HEAD"), cancellable = true)
    private void onRecipeChanceCalculator(CallbackInfoReturnable<Double> cir) {
        double customChance = MainConfig.RecipeChance;
        cir.setReturnValue(customChance);
    }
}
