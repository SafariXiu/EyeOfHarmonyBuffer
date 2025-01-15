package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import kubatech.api.implementations.KubaTechGTMultiBlockBase;
import kubatech.tileentity.gregtech.multiblock.MTEDEFusionCrafter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEDEFusionCrafter.class, remap = false)
public abstract class DEFusionCrafterMixin extends KubaTechGTMultiBlockBase<MTEDEFusionCrafter> implements ISurvivalConstructable {

    protected DEFusionCrafterMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int mTierCasing = 0;

    @Inject(
        method = "createProcessingLogic",
        at = @At("RETURN"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.DEFusionCrafterEnable){
            CustomProcessingLogic customLogic = new CustomProcessingLogic();
            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
