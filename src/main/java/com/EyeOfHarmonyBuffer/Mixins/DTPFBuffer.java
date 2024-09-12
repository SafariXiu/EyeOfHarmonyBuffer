package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;

import javax.annotation.Nonnull;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.util.GT_OverclockCalculator;
import gregtech.api.util.GT_Recipe;
import gregtech.common.tileentities.machines.multi.GT_MetaTileEntity_PlasmaForge;

@Mixin(value = GT_MetaTileEntity_PlasmaForge.class, remap = false)
public abstract class DTPFBuffer {

    @Shadow
    private double discount;
    @Shadow
    private boolean convergence;
    @Shadow
    private GT_OverclockCalculator overclockCalculator;

    @Shadow
    @Final
    private static FluidStack[] valid_fuels;

    @Shadow
    @Final
    private static double maximum_discount;

    @Shadow
    protected abstract void calculateCatalystIncrease(GT_Recipe recipe, int index, boolean withoutCatalyst);

    private static double getMaximumDiscount() {
        try {
            Field field = ProcessingLogic.class.getDeclaredField("maximum_discount");
            field.setAccessible(true);
            return (double) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return 0.5d;
        }
    }

    @Inject(method = "recipeAfterAdjustments", at = @At("HEAD"), cancellable = true)
    private void modifyRecipeAfterAdjustments(@Nonnull GT_Recipe recipe, CallbackInfoReturnable<GT_Recipe> cir) {
        GT_Recipe gtRecipe = recipe.copy();
        boolean adjusted = false;

        for (int i = 0; i < recipe.mFluidInputs.length; i++) {
            for (FluidStack fuel : valid_fuels) {
                if (gtRecipe.mFluidInputs[i].isFluidEqual(fuel)) {
                    gtRecipe.mFluidInputs[i].amount = gtRecipe.mFluidInputs[i].amount;

                    adjusted = true;
                    break;
                }
            }
        }

        if (!adjusted && discount == maximum_discount
            && convergence
            && overclockCalculator != null
            && overclockCalculator.getCalculationStatus()) {
            calculateCatalystIncrease(gtRecipe, 0, true);
        }
        cir.setReturnValue(gtRecipe);
    }
}
