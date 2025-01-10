package com.EyeOfHarmonyBuffer.Mixins.TST;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2;
import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.ModularizedMachineLogic.MultiExecutionCoreMachineSupportAllModuleBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2.class, remap = false)
public abstract class IndistinctTentaclePrototypeMK2Mixin extends MultiExecutionCoreMachineSupportAllModuleBase<MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2> {

    public IndistinctTentaclePrototypeMK2Mixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Redirect(method = "recipeAfterDiscount",
        at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;max(DD)D"))
    private double redirectFuelCostMultiplier(double value1, double value2) {
        if(MainConfig.DTPFMK2Enable){
            return 1.0;
        }
        return Math.max(value1, value2);
    }

}
