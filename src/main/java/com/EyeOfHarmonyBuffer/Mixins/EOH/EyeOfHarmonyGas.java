package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.lang.reflect.Field;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.EyeOfHarmonyAccessor;

import com.github.technus.tectech.recipe.EyeOfHarmonyRecipe;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonyGas {

    private EyeOfHarmonyRecipe getCurrentRecipe(GT_MetaTileEntity_EM_EyeOfHarmony instance) {
        try {
            Field recipeField = GT_MetaTileEntity_EM_EyeOfHarmony.class.getDeclaredField("currentRecipe");
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

        GT_MetaTileEntity_EM_EyeOfHarmony instance = (GT_MetaTileEntity_EM_EyeOfHarmony) (Object) this;
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

        GT_MetaTileEntity_EM_EyeOfHarmony instance = (GT_MetaTileEntity_EM_EyeOfHarmony) (Object) this;
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

        GT_MetaTileEntity_EM_EyeOfHarmony instance = (GT_MetaTileEntity_EM_EyeOfHarmony) (Object) this;
        EyeOfHarmonyRecipe recipe = getCurrentRecipe(instance);

        if (recipe != null) {
            long requiredHelium = recipe.getHeliumRequirement();
            cir.setReturnValue(requiredHelium);
        }
    }
}
