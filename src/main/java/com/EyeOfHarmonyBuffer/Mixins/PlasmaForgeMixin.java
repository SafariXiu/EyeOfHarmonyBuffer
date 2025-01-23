package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPlasmaForge.class, remap = false)
public abstract class PlasmaForgeMixin extends MTEExtendedPowerMultiBlockBase<MTEPlasmaForge> implements ISurvivalConstructable {

    protected PlasmaForgeMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(
        method = "createProcessingLogic",
        at = @At("RETURN"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.DTPFEnable){
            CustomProcessingLogic customLogic = new CustomProcessingLogic();
            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
