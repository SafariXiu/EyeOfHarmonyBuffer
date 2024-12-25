package com.EyeOfHarmonyBuffer.Mixins;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.MTEAssemblyLine;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.Map;

import static gregtech.GTMod.GT_FML_LOGGER;

@Mixin(value = MTEAssemblyLine.class, remap = false)
public abstract class AssemblyLineMixin extends MTEExtendedPowerMultiBlockBase<MTEAssemblyLine> implements ISurvivalConstructable {

    protected AssemblyLineMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 覆写装配线检测逻辑
     */
    @Overwrite
    public CheckRecipeResult checkProcessing() {
        if (GTValues.D1) {
            GT_FML_LOGGER.info("Start ALine recipe check");
        }
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_DATA_STICKS;

        int currentParallel = 1;

        ArrayList<ItemStack> tDataStickList = ((MTEAssemblyLine) (Object) this).getDataItems(2);
        if (tDataStickList.isEmpty()) {
            return result;
        }
        if (GTValues.D1) {
            GT_FML_LOGGER.info("Stick accepted, " + tDataStickList.size() + " Data Sticks found");
        }

        int[] tStacks = new int[0];
        FluidStack[] tFluids = new FluidStack[0];
        long averageVoltage = getAverageInputVoltage();
        int maxParallel = 1;
        long maxAmp = getMaxInputAmps();
        Map<GTUtility.ItemId, ItemStack> inputsFromME = getStoredInputsFromME();
        Map<Fluid, FluidStack> fluidsFromME = getStoredFluidsFromME();

        for (ItemStack tDataStick : tDataStickList) {
            AssemblyLineUtils.LookupResult tLookupResult = AssemblyLineUtils
                .findAssemblyLineRecipeFromDataStick(tDataStick, false);

            if (tLookupResult.getType() == AssemblyLineUtils.LookupResultType.INVALID_STICK) {
                result = CheckRecipeResultRegistry.NO_RECIPE;
                continue;
            }

            GTRecipe.RecipeAssemblyLine tRecipe = tLookupResult.getRecipe();
            if (tLookupResult.getType() != AssemblyLineUtils.LookupResultType.VALID_STACK_AND_VALID_HASH) {
                tRecipe = AssemblyLineUtils.processDataStick(tDataStick);
                if (tRecipe == null) {
                    result = CheckRecipeResultRegistry.NO_RECIPE;
                    continue;
                }
            }

            if (tRecipe.mEUt > averageVoltage * 4) {
                result = CheckRecipeResultRegistry.insufficientPower(tRecipe.mEUt);
                continue;
            }

            if (tRecipe.mEUt > maxAmp * averageVoltage) {
                result = CheckRecipeResultRegistry.insufficientPower(tRecipe.mEUt);
                continue;
            }

            if (tRecipe.mFluidInputs.length > 0) {
                currentParallel = (int) GTRecipe.RecipeAssemblyLine.maxParallelCalculatedByInputFluids(
                    mInputHatches,
                    maxParallel,
                    tRecipe.mFluidInputs,
                    fluidsFromME
                );

                if (currentParallel <= 0) {
                    result = CheckRecipeResultRegistry.NO_RECIPE;
                    continue;
                }
                tFluids = tRecipe.mFluidInputs;
            }

            GTRecipe.RecipeAssemblyLine.consumeInputItems(
                mInputBusses,
                currentParallel,
                tStacks,
                inputsFromME
            );

            if (this.lEUt > 0) {
                this.lEUt = -this.lEUt;
            }
            this.mEfficiency = (10000 - (getIdealStatus() - getRepairStatus()) * 1000);
            this.mEfficiencyIncrease = 10000;
            updateSlots();
            if (GTValues.D1) {
                GT_FML_LOGGER.info("Recipe successful");
            }
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        return result;
    }
}
