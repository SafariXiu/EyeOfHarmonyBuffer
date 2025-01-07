package com.EyeOfHarmonyBuffer.Mixins;

import bartworks.common.tileentities.multis.MTECircuitAssemblyLine;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MTECircuitAssemblyLine.class, remap = false)
public abstract class CircuitAssemblyLineMixin extends MTEEnhancedMultiBlockBase<MTECircuitAssemblyLine>
    implements ISurvivalConstructable {

    protected CircuitAssemblyLineMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "setupProcessingLogic",at = @At("HEAD"))
    private void setupProcessingLogic(ProcessingLogic aProcessingLogic, CallbackInfo ci) {
        if(MainConfig.CircuitAssemblyLineEnable){
            aProcessingLogic.setMaxParallel(Integer.MAX_VALUE);
            aProcessingLogic.setEuModifier(0);
        }
    }
}
