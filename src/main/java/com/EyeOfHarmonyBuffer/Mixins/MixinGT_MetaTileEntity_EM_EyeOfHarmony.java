package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config;
import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class MixinGT_MetaTileEntity_EM_EyeOfHarmony {

    @Inject(method = "recipeChanceCalculator", at = @At("HEAD"), cancellable = true)
    private void onRecipeChanceCalculator(CallbackInfoReturnable<Double> cir) {
        double customChance = Config.RecipeChance;
        cir.setReturnValue(customChance);
    }

    @Inject(method = "recipeYieldCalculator", at = @At("HEAD"), cancellable = true)
    private void onRecipeYieldCalculator(CallbackInfoReturnable<Double> cir) {
        double customYield = Config.RecipeYield;
        cir.setReturnValue(customYield);
    }
}