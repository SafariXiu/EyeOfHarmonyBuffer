package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyGas {

    private EyeOfHarmonyRecipe getCurrentRecipe(MTEEyeOfHarmony instance) {
        try {
            Field recipeField = MTEEyeOfHarmony.class.getDeclaredField("currentRecipe");
            recipeField.setAccessible(true);
            return (EyeOfHarmonyRecipe) recipeField.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Inject(method = "getStellarPlasmaStored", at = @At("HEAD"), cancellable = true)
    private void returnStellarPlasmaFromRecipe(CallbackInfoReturnable<Long> cir) {

        if (!MainConfig.GasInPut) {
            return;
        }

        MTEEyeOfHarmony instance = (MTEEyeOfHarmony) (Object) this;
        EyeOfHarmonyRecipe recipe = getCurrentRecipe(instance);

        if (recipe != null) {
            EyeOfHarmonyAccessor accessor = (EyeOfHarmonyAccessor) instance;
            long parallelAmount = accessor.getParallelAmount();
            long requiredStellarPlasma = (long) (recipe.getHeliumRequirement() * (12.4 / 1_000_000f) * parallelAmount);
            cir.setReturnValue(requiredStellarPlasma);
        }
    }

    @Inject(method = "getHydrogenStored", at = @At("HEAD"), cancellable = true)
    private void returnHydrogenFromRecipe(CallbackInfoReturnable<Long> cir) {

        if (!MainConfig.GasInPut) {
            return;
        }

        MTEEyeOfHarmony instance = (MTEEyeOfHarmony) (Object) this;
        EyeOfHarmonyRecipe recipe = getCurrentRecipe(instance);

        if (recipe != null) {
            long requiredHydrogen = recipe.getHydrogenRequirement();
            cir.setReturnValue(requiredHydrogen);
        }
    }

    @Inject(method = "getHeliumStored", at = @At("HEAD"), cancellable = true)
    private void returnHeliumFromRecipe(CallbackInfoReturnable<Long> cir) {

        if (!MainConfig.GasInPut) {
            return;
        }

        MTEEyeOfHarmony instance = (MTEEyeOfHarmony) (Object) this;
        EyeOfHarmonyRecipe recipe = getCurrentRecipe(instance);

        if (recipe != null) {
            long requiredHelium = recipe.getHeliumRequirement();
            cir.setReturnValue(requiredHelium);
        }
    }
}
