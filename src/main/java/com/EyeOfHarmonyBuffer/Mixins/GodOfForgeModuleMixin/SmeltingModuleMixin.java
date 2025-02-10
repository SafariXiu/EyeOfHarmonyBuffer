package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import gregtech.api.logic.ProcessingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTESmeltingModule;

@Mixin(value = MTESmeltingModule.class,remap = false)
public abstract class SmeltingModuleMixin extends MTEBaseModule {

    public SmeltingModuleMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    private void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir){
        if(MainConfig.SmeltingModuleEnable){
            CustomProcessingLogic customProcessingLogic = new CustomProcessingLogic();
            cir.setReturnValue(customProcessingLogic);
            cir.cancel();
        }
    }
}
