package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import gregtech.api.logic.ProcessingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEMoltenModule;

@Mixin(value = MTEMoltenModule.class, remap = false)
public abstract class MoltenModuleMixin extends MTEBaseModule {

    public MoltenModuleMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    public void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.MoltenModuleEnable){
            CustomProcessingLogic customLogic = new CustomProcessingLogic();
            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
