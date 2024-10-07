package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;


@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyGas {

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;getStellarPlasmaStored()J"))
    private long redirectGetStellarPlasmaStored(MTEEyeOfHarmony instance) {
        return Long.MAX_VALUE; // 返回一个极大的值，确保总是通过检查
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;getHydrogenStored()J"))
    private long redirectGetHydrogenStored(MTEEyeOfHarmony instance) {
        return Long.MAX_VALUE; // 返回一个极大的值，确保总是通过检查
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;getHeliumStored()J"))
    private long redirectGetHeliumStored(MTEEyeOfHarmony instance) {
        return Long.MAX_VALUE; // 返回一个极大的值，确保总是通过检查
    }
}
