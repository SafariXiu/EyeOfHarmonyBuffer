package com.EyeOfHarmonyBuffer.Mixins.FuelFactory;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import goodgenerator.blocks.tileEntity.MTEFuelRefineFactory;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.logic.ProcessingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEFuelRefineFactory.class,remap = false)
public abstract class FuelRefineFactoryMixin extends MTETooltipMultiBlockBaseEM implements IConstructable, ISurvivalConstructable {

    protected FuelRefineFactoryMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(
        method = "createProcessingLogic",
        at = @At("RETURN"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.FuelRefineFactoryEnable){
            CustomProcessingLogic customLogic = new CustomProcessingLogic();
            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
