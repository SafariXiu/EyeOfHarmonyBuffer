package com.EyeOfHarmonyBuffer.Mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyBus {

    @Inject(method = "checkMachine_EM", at = @At(value = "RETURN"), cancellable = true)
    private void injectModifyReturn(CallbackInfoReturnable<Boolean> cir) {
        List<?> mInputBusses = ((MTEEyeOfHarmony) (Object) this).mInputBusses;

        if (MainConfig.EOHinputBusMe) {
            if (mInputBusses != null && !mInputBusses.isEmpty()) {
                cir.setReturnValue(true);
            }
        }
    }
}
