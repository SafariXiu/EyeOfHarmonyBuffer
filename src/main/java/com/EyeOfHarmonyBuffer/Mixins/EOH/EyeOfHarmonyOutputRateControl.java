package com.EyeOfHarmonyBuffer.Mixins.EOH;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonyOutputRateControl {

    @Inject(method = "recipeYieldCalculator", at = @At("HEAD"), cancellable = true)
    private void onRecipeYieldCalculator(CallbackInfoReturnable<Double> cir) {

        if (!MainConfig.EOHOutputRateControl) {
            return;
        }

        System.out.println("产出率修改成功");
        double customYield = MainConfig.RecipeYield;
        cir.setReturnValue(customYield);
    }

}
