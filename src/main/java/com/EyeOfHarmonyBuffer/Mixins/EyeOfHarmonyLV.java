package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.api.recipe.check.CheckRecipeResult;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyLV {

    @Inject(method = "processRecipe", at = @At("HEAD"), cancellable = true)
    private void injectSpacetimeCompressionFieldMetadata(CallbackInfoReturnable<CheckRecipeResult> cir) {

        if (!MainConfig.EOHLV) {
            return;
        }

        try {
            Field field = MTEEyeOfHarmony.class.getDeclaredField("spacetimeCompressionFieldMetadata");
            field.setAccessible(true);

            field.setInt(this, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

}
