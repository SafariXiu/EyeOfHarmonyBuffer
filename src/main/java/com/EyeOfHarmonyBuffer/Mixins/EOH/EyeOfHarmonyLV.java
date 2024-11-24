package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.lang.reflect.Field;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.api.recipe.check.CheckRecipeResult;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyLV {

    @Inject(method = "processRecipe", at = @At("HEAD"), cancellable = true)
    private void injectSpacetimeCompressionFieldMetadata(CallbackInfoReturnable<CheckRecipeResult> cir) {

        if (!MainConfig.EOHLV) {
            return;
        }

        try {
            Field field = GT_MetaTileEntity_EM_EyeOfHarmony.class.getDeclaredField("spacetimeCompressionFieldMetadata");
            field.setAccessible(true);

            field.setInt(this, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

}
