package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.common.render.IMTERenderer;
import gregtech.common.tileentities.machines.multi.MTEIndustrialCuttingMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEIndustrialCuttingMachine.class,remap = false)
public abstract class IndustrialCuttingMachineMixin extends MTEExtendedPowerMultiBlockBase<MTEIndustrialCuttingMachine> implements ISurvivalConstructable, IMTERenderer {

    public IndustrialCuttingMachineMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    private void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.IndustrialCuttingMachineEnable){
            CustomProcessingLogic customProcessingLogic = new CustomProcessingLogic();
            cir.setReturnValue(customProcessingLogic);
            cir.cancel();
        }
    }
}
