package com.EyeOfHarmonyBuffer.Mixins.LargeFusion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import goodgenerator.blocks.tileEntity.*;

@Mixin(
    value = { MTELargeFusionComputer5.class, MTELargeFusionComputer4.class, MTELargeFusionComputer3.class,
        MTELargeFusionComputer2.class, MTELargeFusionComputer1.class },
    remap = false)
public class LargeFusionPara {

    @Inject(method = "getMaxPara", at = @At("RETURN"), cancellable = true)
    private void onGetMaxPara(CallbackInfoReturnable<Integer> cir) {

        if (MainConfig.LargeFusionParaMixin) {
            cir.setReturnValue(MainConfig.LargeFusionPara);
        }
    }
}
