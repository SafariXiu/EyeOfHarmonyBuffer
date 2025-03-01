package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIndustrialCuttingMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEIndustrialCuttingMachine.class,remap = false)
public abstract class IndustrialCuttingMachineMixin extends GTPPMultiBlockBase<MTEIndustrialCuttingMachine>
    implements ISurvivalConstructable {

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
