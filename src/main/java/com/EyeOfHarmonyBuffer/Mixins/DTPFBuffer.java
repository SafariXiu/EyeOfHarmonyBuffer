package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gregtech.common.tileentities.machines.multi.GT_MetaTileEntity_PlasmaForge;

@Mixin(value = GT_MetaTileEntity_PlasmaForge.class, remap = false)
public abstract class DTPFBuffer {

    @Shadow
    private double discount;

    @Inject(method = "recalculateDiscount", at = @At("HEAD"), cancellable = true)
    private void LockDiscount(CallbackInfo ci) {
        this.discount = 1.0;

        ci.cancel();
    }

}
