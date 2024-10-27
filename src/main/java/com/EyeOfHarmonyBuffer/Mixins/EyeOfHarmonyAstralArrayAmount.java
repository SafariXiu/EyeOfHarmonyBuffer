package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyAstralArrayAmount {

    @ModifyConstant(method = "processRecipe", constant = @Constant(longValue = 8637L))
    private long modifyAstralArrayLimit(long original) {
        System.out.println("狠狠地注入");
        return 100000L;
    }
}
