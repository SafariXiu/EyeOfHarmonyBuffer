package com.EyeOfHarmonyBuffer.Mixins.PHM;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reobf.proghatches.gt.metatileentity.StorageOutputBus;

@Mixin(value = StorageOutputBus.class, remap = false)
public abstract class StorageOutputBusMixin {

    @Inject(method = "getCacheCapacity", at = @At("RETURN"), cancellable = true)
    private void injectGetCacheCapacity(CallbackInfoReturnable<Long> cir) {
        if (MainConfig.StorageOutputBusEnable) {
            cir.setReturnValue(Long.MAX_VALUE);
        }
    }
}
