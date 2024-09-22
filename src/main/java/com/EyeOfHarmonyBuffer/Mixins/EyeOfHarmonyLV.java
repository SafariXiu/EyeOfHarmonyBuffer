package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;

import gregtech.api.recipe.check.CheckRecipeResult;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyLV {

    @Inject(method = "processRecipe", at = @At("HEAD"), cancellable = true)
    private void injectSpacetimeCompressionFieldMetadata(CallbackInfoReturnable<CheckRecipeResult> cir) {

        try {
            Field field = GT_MetaTileEntity_EM_EyeOfHarmony.class.getDeclaredField("spacetimeCompressionFieldMetadata");
            field.setAccessible(true);

            field.setInt(this, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

}
