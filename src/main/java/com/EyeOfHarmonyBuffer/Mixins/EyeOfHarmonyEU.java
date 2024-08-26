package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;
import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.technus.tectech.recipe.EyeOfHarmonyRecipe;
import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;

import gregtech.api.recipe.check.CheckRecipeResult;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonyEU {

    @Inject(method = "processRecipe", at = @At("RETURN"), cancellable = true)
    private void modifyEnergyOutput(EyeOfHarmonyRecipe recipeObject, CallbackInfoReturnable<CheckRecipeResult> cir) {

        // 使用一个50位的大数值
        BigInteger constantOutputEU = new BigInteger("12345678901234567890123456789012345678901234567890");

        try {
            // 通过反射访问私有字段 outputEU_BigInt
            Field outputEUField = GT_MetaTileEntity_EM_EyeOfHarmony.class.getDeclaredField("outputEU_BigInt");
            outputEUField.setAccessible(true);
            outputEUField.set(this, constantOutputEU);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
