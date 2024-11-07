package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.api.logic.ProcessingLogic;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEMassFabricator;

@Mixin(value = MTEMassFabricator.class, remap = false)
public class UUMixin {

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void modifyEnergyConsumption(CallbackInfoReturnable<ProcessingLogic> ci) {
        if (MainConfig.UUMixin) {
            ProcessingLogic logic = ci.getReturnValue();
            logic.setEuModifier(0.0F);
            ci.setReturnValue(logic);
        }
    }

    @Inject(method = "getMaxParallelRecipes", at = @At("RETURN"), cancellable = true)
    private void modifyMaxParallelRecipes(CallbackInfoReturnable<Integer> ci) {
        if (MainConfig.UUMixin) {
            ci.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
