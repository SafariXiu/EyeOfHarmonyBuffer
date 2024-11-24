package com.EyeOfHarmonyBuffer.Mixins.EOH;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonySuccessRateControl {

    @Inject(method = "recipeChanceCalculator", at = @At("HEAD"), cancellable = true)
    private void onRecipeChanceCalculator(CallbackInfoReturnable<Double> cir) {

        if (!MainConfig.EOHSuccessRateControls) {
            return;
        }

        double customChance = MainConfig.RecipeChance;
        cir.setReturnValue(customChance);
    }
}
