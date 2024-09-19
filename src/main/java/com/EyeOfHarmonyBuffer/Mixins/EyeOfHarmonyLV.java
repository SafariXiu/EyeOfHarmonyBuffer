package com.EyeOfHarmonyBuffer.Mixins;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyLV {

    @Shadow
    public abstract String[] getInfoData();

    @Inject(method = "recipeProcessTimeCalculator",at = @At("HEAD"))
    private void SpacetimeCompressionField(long recipeTime, long recipeSpacetimeCasingRequired, CallbackInfoReturnable<Integer> cir){
        try {
            Field field = this.getClass().getDeclaredField("spacetimeCompressionFieldMetadata");
            field.setAccessible(true);

            field.set(this, 8L);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
