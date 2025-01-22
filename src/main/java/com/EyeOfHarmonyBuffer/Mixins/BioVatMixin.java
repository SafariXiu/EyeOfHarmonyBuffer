package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import bartworks.common.configs.Configuration;
import bartworks.common.tileentities.multis.MTEBioVat;

@Mixin(value = MTEBioVat.class, remap = false)
public class BioVatMixin {

    @Inject(method = "calcMod", at = @At("RETURN"), cancellable = true)
    private void injectCalcMod(double x, CallbackInfoReturnable<Integer> cir) {

        if (MainConfig.BioVatTrue) {
            double z = Configuration.Multiblocks.bioVatMaxParallelBonus;

            cir.setReturnValue((int) z);
        }
    }
}
