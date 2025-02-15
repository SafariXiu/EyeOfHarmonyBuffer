package com.EyeOfHarmonyBuffer.Mixins.OthTech;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.newmaa.othtech.machine.OTEWoodenFusionReactor;
import com.newmaa.othtech.machine.machineclass.OTH_MultiMachineBase;
import gregtech.api.logic.ProcessingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OTEWoodenFusionReactor.class, remap = false)
public abstract class WoodenFusionReactorMixin extends OTH_MultiMachineBase<OTEWoodenFusionReactor> {

    public WoodenFusionReactorMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    public void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.WoodenFusionReactorEnable){
            CustomProcessingLogic customProcessingLogic = new CustomProcessingLogic();
            cir.setReturnValue(customProcessingLogic);
            cir.cancel();
        }
    }
}
