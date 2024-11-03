package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

@Mixin(value = MTEPlasmaForge.class, remap = false)
public abstract class DTPFBuffer {

    @Shadow
    private double discount;

    @Inject(method = "recalculateDiscount", at = @At("HEAD"), cancellable = true)
    private void LockDiscount(CallbackInfo ci) {

        if(!MainConfig.DTPFOpen) {
            this.discount = MainConfig.discount;
            ci.cancel();
        }

    }

}
