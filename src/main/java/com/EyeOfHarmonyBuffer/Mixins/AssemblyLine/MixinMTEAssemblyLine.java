package com.EyeOfHarmonyBuffer.Mixins.AssemblyLine;

import com.EyeOfHarmonyBuffer.utils.RecipeAssemblyLineAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.*;
import gregtech.common.tileentities.machines.multi.MTEAssemblyLine;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Map;

import static gregtech.GTMod.GT_FML_LOGGER;

@Mixin(value = MTEAssemblyLine.class, remap = false)
public abstract class MixinMTEAssemblyLine extends MTEExtendedPowerMultiBlockBase<MTEAssemblyLine> implements ISurvivalConstructable {
    protected MixinMTEAssemblyLine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    public abstract ArrayList<ItemStack> getDataItems(int state);

    /**
     * @author
     * @reason
     */
    @Overwrite
    public CheckRecipeResult checkProcessing() {
        if (GTValues.D1) {
            GT_FML_LOGGER.info("Start ALine recipe check");
        }
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_DATA_STICKS;

        ArrayList<ItemStack> tDataStickList = getDataItems(2);
        if (tDataStickList.isEmpty()) {
            return result; // 没有数据棒，直接返回
        }
        if (GTValues.D1) {
            GT_FML_LOGGER.info("Stick accepted, " + tDataStickList.size() + " Data Sticks found");
        }

        int[] tStacks = new int[0];
        FluidStack[] tFluids = new FluidStack[0];
        long averageVoltage = getAverageInputVoltage();
        long maxAmp = getMaxInputAmps();
        Map<GTUtility.ItemId, ItemStack> inputsFromME = getStoredInputsFromME();
        Map<Fluid, FluidStack> fluidsFromME = getStoredFluidsFromME();

        int maxParallel = 10; // 默认并行量为 1

        for (ItemStack tDataStick : tDataStickList) {
            AssemblyLineUtils.LookupResult tLookupResult = AssemblyLineUtils
                .findAssemblyLineRecipeFromDataStick(tDataStick, false);

            if (tLookupResult.getType() == AssemblyLineUtils.LookupResultType.INVALID_STICK) {
                result = CheckRecipeResultRegistry.NO_RECIPE;
                continue; // 跳过无效数据棒
            }

            GTRecipe.RecipeAssemblyLine tRecipe = tLookupResult.getRecipe();
            if (tLookupResult.getType() != AssemblyLineUtils.LookupResultType.VALID_STACK_AND_VALID_HASH) {
                tRecipe = AssemblyLineUtils.processDataStick(tDataStick);
                if (tRecipe == null) {
                    result = CheckRecipeResultRegistry.NO_RECIPE;
                    continue; // 跳过无效配方
                }
            }

            // 检查输入物品和流体是否足够
            if (mInputBusses.size() < tRecipe.mInputs.length || mInputHatches.size() < tRecipe.mFluidInputs.length) {
                if (GTValues.D1) {
                    GT_FML_LOGGER.info(
                        "Not enough sources: Need ({}, {}), has ({}, {})",
                        mInputBusses.size(),
                        tRecipe.mInputs.length,
                        mInputHatches.size(),
                        tRecipe.mFluidInputs.length
                    );
                }
                result = CheckRecipeResultRegistry.NO_RECIPE;
                continue; // 跳过不符合输入条件的配方
            }

            // 设置 lastRecipeInputs
            if (tRecipe != null && tRecipe.mInputs != null) {
                try {
                    RecipeAssemblyLineAccessor.setLastRecipeInputs(tRecipe.mInputs);
                    System.out.println("[EYEBUF] Initialized lastRecipeInputs in checkProcessing.");
                } catch (Exception e) {
                    e.printStackTrace();
                    return CheckRecipeResultRegistry.INTERNAL_ERROR;
                }
            }

            // **计算最大并行量**
            maxParallel = 1;

            if (maxParallel <= 0) {
                // 如果计算结果为 0，跳过该配方
                result = CheckRecipeResultRegistry.NO_RECIPE;
                continue;
            }

            if (GTValues.D1) {
                GT_FML_LOGGER.info("Max Parallel Calculated: {}", maxParallel);
            }

            // **获取物品消耗量数组**
            tStacks = GTRecipe.RecipeAssemblyLine.getItemConsumptionAmountArray(mInputBusses, tRecipe);
            tFluids = tRecipe.mFluidInputs; // 流体直接从配方中获取

            if (tStacks == null || tStacks.length == 0) {
                result = CheckRecipeResultRegistry.NO_RECIPE;
                continue; // 跳过没有物品消耗的配方
            }

            // 强制锁定耗时和耗电
            lEUt = 0;  // 功率为 0
            mMaxProgresstime = 100;  // 耗时为 100 ticks，即 5 秒

            if (GTValues.D1) {
                GT_FML_LOGGER.info("Recipe found and processing time/power locked.");
            }

            result = CheckRecipeResultRegistry.SUCCESSFUL;
            mOutputItems = new ItemStack[] { tRecipe.mOutput.copy() };
            break;
        }

        if (!result.wasSuccessful()) {
            return result; // 没有找到有效配方，直接返回
        }

        // 检查物品消耗和并行量是否合法
        if (tStacks.length == 0 || maxParallel <= 0) {
            return CheckRecipeResultRegistry.INTERNAL_ERROR;
        }

        if (GTValues.D1) {
            GT_FML_LOGGER.info("All checked start consuming inputs");
        }

        try {
            // **消耗物品和流体**
            GTRecipe.RecipeAssemblyLine.consumeInputItems(mInputBusses, maxParallel, tStacks, inputsFromME);
            GTRecipe.RecipeAssemblyLine.consumeInputFluids(mInputHatches, maxParallel, tFluids, fluidsFromME);

            // 更新输入总线与输入仓的状态
            for (MTEHatchInputBus inputBus : mInputBusses) {
                if (inputBus != null) {
                    inputBus.updateSlots(); // 刷新物品输入总线
                }
            }
            for (MTEHatchInput inputHatch : mInputHatches) {
                if (inputHatch != null) {
                    inputHatch.updateSlots(); // 刷新流体输入仓
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return CheckRecipeResultRegistry.INTERNAL_ERROR;
        }

// 其他状态更新逻辑
        if (this.lEUt > 0) {
            this.lEUt = -this.lEUt;
        }
        this.mEfficiency = (10000 - (getIdealStatus() - getRepairStatus()) * 1000);
        this.mEfficiencyIncrease = 10000;
        updateSlots();

        if (GTValues.D1) {
            GT_FML_LOGGER.info("Recipe successful");
        }
        return result;
    }
}
