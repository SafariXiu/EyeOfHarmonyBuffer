package com.EyeOfHarmonyBuffer.Mixins.Mask;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.tileentity.MTETargetChamber;
import gtnhlanth.common.tileentity.recipe.beamline.BeamlineRecipeAdder2;
import gtnhlanth.common.tileentity.recipe.beamline.RecipeTC;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = MTETargetChamber.class, remap = false)
public abstract class TargetChamberMixin extends MTEEnhancedMultiBlockBase<MTETargetChamber> implements ISurvivalConstructable {

    protected TargetChamberMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private ItemStack getFocusItemStack() {
        return null;
    }

    @Inject(method = "checkRecipe", at = @At("HEAD"), cancellable = true)
    private void bypassChecks(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if(MainConfig.TargetChamberEnable){
            MTETargetChamber chamber = (MTETargetChamber) (Object) this;

            ArrayList<ItemStack> tItems = chamber.getStoredInputs();
            ItemStack tFocusItem = getFocusItemStack();

            ItemStack tFocusItemZeroDamage = null;
            if (tFocusItem != null) {
                tFocusItemZeroDamage = tFocusItem.copy();
                tFocusItemZeroDamage.setItemDamage(0);
            }

            ArrayList<ItemStack> tItemsWithFocusItem = new ArrayList<>();
            tItemsWithFocusItem.add(tFocusItemZeroDamage);
            tItemsWithFocusItem.addAll(tItems);
            ItemStack[] tItemsWithFocusItemArray = tItemsWithFocusItem.toArray(new ItemStack[0]);

            RecipeTC tRecipe = (RecipeTC) BeamlineRecipeAdder2.instance.TargetChamberRecipes
                .findRecipeQuery()
                .items(tItemsWithFocusItemArray)
                .voltage(chamber.getMaxInputVoltage())
                .find();

            if (tRecipe != null) {
                int batchAmount;

                if (MainConfig.TargetChamberParallelEnable) {
                    batchAmount = Math.min(MainConfig.TargetChamberParallel, 1000000);
                } else {
                    if (mProgresstime <= 0) {
                        cir.setReturnValue(false);
                        return;
                    }

                    batchAmount = (int) Math.round(1.0 / mProgresstime);

                    double maxParallel = tRecipe
                        .maxParallelCalculatedByInputs(batchAmount, new FluidStack[] {}, tItemsWithFocusItemArray);

                    if (maxParallel < 1) {
                        cir.setReturnValue(false);
                        return;
                    }

                    if (batchAmount > maxParallel) batchAmount = (int) maxParallel;
                }

                tRecipe.consumeInput(batchAmount, new FluidStack[] {}, tItemsWithFocusItemArray);

                ItemStack[] itemOutputArray = GTUtility.copyItemArray(tRecipe.mOutputs);
                for (ItemStack stack : itemOutputArray) {
                    stack.stackSize *= batchAmount;
                }

                chamber.mMaxProgresstime = 100;
                chamber.mEfficiency = 10000;
                chamber.mEfficiencyIncrease = 10000;
                chamber.mEUt = 0;

                chamber.mOutputItems = itemOutputArray;
                chamber.updateSlots();

                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
        }
    }
}
