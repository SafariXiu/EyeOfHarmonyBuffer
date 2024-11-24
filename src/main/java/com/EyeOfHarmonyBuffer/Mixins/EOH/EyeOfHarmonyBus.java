package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.util.List;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonyBus {

    @Inject(method = "checkMachine_EM", at = @At(value = "RETURN"), cancellable = true)
    private void injectModifyReturn(CallbackInfoReturnable<Boolean> cir) {
        List<?> mInputBusses = ((GT_MetaTileEntity_EM_EyeOfHarmony) (Object) this).mInputBusses;

        if (MainConfig.EOHinputBusMe) {
            if (mInputBusses != null && !mInputBusses.isEmpty()) {
                cir.setReturnValue(true);
            }
        }
    }
}
