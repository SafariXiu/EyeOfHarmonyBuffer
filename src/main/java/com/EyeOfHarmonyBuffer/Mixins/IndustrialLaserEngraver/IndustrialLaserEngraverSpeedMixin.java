package com.EyeOfHarmonyBuffer.Mixins.IndustrialLaserEngraver;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(targets = "gregtech.common.tileentities.machines.multi.MTEIndustrialLaserEngraver$1",remap = false)
public class IndustrialLaserEngraverSpeedMixin {

    @Inject(method = "validateRecipe", at = @At("HEAD"), cancellable = true)
    private void injectValidateRecipe(@Nonnull GTRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if(MainConfig.IndustrialLaserEngraverOverclockEnabled){
            recipe.mDuration = 128;
            recipe.mEUt = 0;

            cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
        }
    }
}
