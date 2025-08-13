package com.EyeOfHarmonyBuffer.Mixins.Mask;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.extensions.ArrayExt;
import gtnhlanth.api.recipe.LanthanidesRecipeMaps;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.tileentity.MTETargetChamber;
import gtnhlanth.common.tileentity.recipe.beamline.TargetChamberMetadata;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

import static gtnhlanth.api.recipe.LanthanidesRecipeMaps.TARGET_CHAMBER_METADATA;

@Mixin(value = MTETargetChamber.class, remap = false)
public abstract class TargetChamberMixin extends MTEEnhancedMultiBlockBase<MTETargetChamber> implements ISurvivalConstructable {

    protected TargetChamberMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    protected abstract ArrayList<ItemStack> getFocusItemStack();

    @Shadow
    private GTRecipe lastRecipe;

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true)
    private void injectCustomCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        // 1. 获取输入物品
        ArrayList<ItemStack> tItems = getStoredInputs();
        ArrayList<ItemStack> tFocusItemArray = getFocusItemStack();
        ArrayList<ItemStack> tItemsWithFocusItem = new ArrayList<>(tItems);

        if (tFocusItemArray != null && !tFocusItemArray.isEmpty()) {
            ItemStack focus = tFocusItemArray.get(0).copy();
            focus.setItemDamage(0);
            tItemsWithFocusItem.add(focus);
        }

        ItemStack[] tItemsWithFocusItemArray = tItemsWithFocusItem.toArray(new ItemStack[0]);
        long tVoltageActual = GTValues.VP[(int) getInputVoltageTier()];

        // 2. 使用假粒子束（绕过粒子检测）
        BeamInformation fakeBeam = new BeamInformation(0, 9999, 9999, 9999);

        // 3. 查找配方
        GTRecipe tRecipe = LanthanidesRecipeMaps.targetChamberRecipes.findRecipeQuery()
            .items(tItemsWithFocusItemArray)
            .voltage(tVoltageActual)
            .filter(recipe -> {
                TargetChamberMetadata metadata = recipe.getMetadata(TARGET_CHAMBER_METADATA);
                return metadata != null;
            })
            .cachedRecipe(lastRecipe)
            .find();

        if (tRecipe == null) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }

        // 4. 设置批次数（自由控制）
        int batchAmount = 64;
        int progressTime = 20;

        mMaxProgresstime = progressTime;
        mEUt = -((int) tVoltageActual);
        lastRecipe = tRecipe;

        // 5. 设置输出
        ItemStack[] outputs = ArrayExt.copyItemsIfNonEmpty(tRecipe.mOutputs);
        for (ItemStack stack : outputs) {
            stack.stackSize *= batchAmount;
        }
        mOutputItems = outputs;

        // 6. 消耗输入，但不处理焦点耐久
        tRecipe.consumeInput(batchAmount, GTValues.emptyFluidStackArray, tItemsWithFocusItemArray);

        // 7. 更新状态
        updateSlots();
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;

        // 8. 设置返回值，取消原方法执行
        cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }
}
