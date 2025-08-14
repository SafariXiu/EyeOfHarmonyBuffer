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

        ArrayList<ItemStack> tItems = getStoredInputs();
        ArrayList<ItemStack> tFocusItemArray = getFocusItemStack();
        ArrayList<ItemStack> tItemsWithFocusItem = new ArrayList<>(tItems);

        if (tFocusItemArray != null && !tFocusItemArray.isEmpty()) {
            ItemStack focus = tFocusItemArray.get(0).copy();
            focus.setItemDamage(0);
            tItemsWithFocusItem.add(focus);
        }

        ItemStack[] inputs = tItemsWithFocusItem.toArray(new ItemStack[0]);

        long voltage = GTValues.VP[(int) getInputVoltageTier()];

        GTRecipe recipe = LanthanidesRecipeMaps.targetChamberRecipes.findRecipeQuery()
            .items(inputs)
            .voltage(voltage)
            .filter(r -> r.getMetadata(TARGET_CHAMBER_METADATA) != null)
            .cachedRecipe(lastRecipe)
            .find();

        if (recipe == null) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }

        int desiredBatchAmount = 2_050_781;
        double maxParallel = recipe.maxParallelCalculatedByInputs(desiredBatchAmount, GTValues.emptyFluidStackArray, inputs);
        int batchAmount = (int) Math.min(desiredBatchAmount, maxParallel);

        if (batchAmount <= 0) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }

        int progressTime = 20;
        mMaxProgresstime = progressTime;
        mEUt = -(int) voltage;
        lastRecipe = recipe;

        ItemStack[] outputs = ArrayExt.copyItemsIfNonEmpty(recipe.mOutputs);
        for (ItemStack stack : outputs) {
            stack.stackSize *= batchAmount;
        }
        mOutputItems = outputs;

        recipe.consumeInput(batchAmount, GTValues.emptyFluidStackArray, inputs);

        updateSlots();
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;

        cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }
}
