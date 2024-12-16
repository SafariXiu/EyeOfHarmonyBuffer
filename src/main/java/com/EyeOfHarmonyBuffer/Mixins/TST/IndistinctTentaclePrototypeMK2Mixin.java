package com.EyeOfHarmonyBuffer.Mixins.TST;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.IndistinctTentaclePrototypeMK2Accessor;
import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2;
import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.ModularizedMachineLogic.MultiExecutionCoreMachineSupportAllModuleBase;
import gregtech.api.util.GTRecipe;
import net.minecraftforge.fluids.FluidStack;
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
    private void lockFuelDiscount(@Nonnull GTRecipe recipe, CallbackInfoReturnable<GTRecipe> cir) {
        if(MainConfig.DTPFMK2Enable){
            GTRecipe tRecipe = recipe.copy();
            FluidStack[] validFuels = IndistinctTentaclePrototypeMK2Accessor.getValidFuels();
            for (int i = 0; i < tRecipe.mFluidInputs.length; ++i) {
                FluidStack inputFluid = tRecipe.mFluidInputs[i];
                for (FluidStack fuel : validFuels) {
                    if (inputFluid.isFluidEqual(fuel)) {
                        double fixedFuelMultiplier = MainConfig.DTPFMK2FuelRelief;
                        tRecipe.mFluidInputs[i] = inputFluid.copy();
                        tRecipe.mFluidInputs[i].amount = (int) Math.round(inputFluid.amount * fixedFuelMultiplier);
                        cir.setReturnValue(tRecipe);
                        return;
                    }
                }
            }
        }
        cir.setReturnValue(recipe);
    }
}
