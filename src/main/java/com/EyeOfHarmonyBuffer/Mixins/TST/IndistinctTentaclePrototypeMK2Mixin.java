package com.EyeOfHarmonyBuffer.Mixins.TST;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2;
import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.ModularizedMachineLogic.MultiExecutionCoreMachineSupportAllModuleBase;
import gregtech.api.util.GTRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2.class, remap = false)
public abstract class IndistinctTentaclePrototypeMK2Mixin extends MultiExecutionCoreMachineSupportAllModuleBase<MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2> {

    public IndistinctTentaclePrototypeMK2Mixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(
        method = "recipeAfterDiscount",
        at = @At("HEAD"),
        cancellable = true
    )
    protected void injectRecipeAfterDiscount(@Nonnull GTRecipe recipe, CallbackInfoReturnable<GTRecipe> cir) {
        if (MainConfig.DTPFMK2Enable) {
            GTRecipe tRecipe = recipe.copy();
            cir.setReturnValue(tRecipe);
        }
    }
}
