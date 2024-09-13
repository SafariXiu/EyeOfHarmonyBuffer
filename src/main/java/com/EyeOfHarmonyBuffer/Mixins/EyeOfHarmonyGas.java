package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.EyeOfHarmonyBuffer.Config;
import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonyGas {

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/technus/tectech/thing/metaTileEntity/multi/GT_MetaTileEntity_EM_EyeOfHarmony;getStellarPlasmaStored()J"))
    private long redirectGetStellarPlasmaStored(GT_MetaTileEntity_EM_EyeOfHarmony instance) {
        return Long.MAX_VALUE; // 返回一个极大的值，确保总是通过检查
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/technus/tectech/thing/metaTileEntity/multi/GT_MetaTileEntity_EM_EyeOfHarmony;getHydrogenStored()J"))
    private long redirectGetHydrogenStored(GT_MetaTileEntity_EM_EyeOfHarmony instance) {
            return Long.MAX_VALUE; // 返回一个极大的值，确保总是通过检查
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/technus/tectech/thing/metaTileEntity/multi/GT_MetaTileEntity_EM_EyeOfHarmony;getHeliumStored()J"))
    private long redirectGetHeliumStored(GT_MetaTileEntity_EM_EyeOfHarmony instance) {
            return Long.MAX_VALUE; // 返回一个极大的值，确保总是通过检查
    }
}
