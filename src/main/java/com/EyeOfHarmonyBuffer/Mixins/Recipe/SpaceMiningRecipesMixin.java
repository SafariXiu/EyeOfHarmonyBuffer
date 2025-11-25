package com.EyeOfHarmonyBuffer.Mixins.Recipe;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTRecipeBuilder;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhintergalactic.recipe.IG_RecipeAdder;
import gtnhintergalactic.recipe.SpaceMiningData;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(value = IG_RecipeAdder.class, remap = false)
public abstract class SpaceMiningRecipesMixin {

    @Inject(
        method = "addSpaceMiningRecipe(Ljava/lang/String;[Lnet/minecraft/item/ItemStack;[Lnet/minecraftforge/fluids/FluidStack;[I[Lnet/minecraft/item/ItemStack;IIIIIIIII)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void igspace$overrideAddSpaceMiningRecipe(
        String asteroidName, ItemStack[] aItemInputs, FluidStack[] aFluidInputs, int[] aChances, ItemStack[] aItemOutputs, int minSize, int maxSize, int minDistance, int maxDistance, int computationRequiredPerSec, int minModuleTier, int duration, int EUt, int recipeWeight, CallbackInfoReturnable<Boolean> cir
    ) {
        if(MainConfig.SpaceMiningRecipesEnable){
            if (aItemInputs != null && aItemInputs.length >= 3) {
                aItemInputs = stripDrillAndRod(aItemInputs);
            }

            if ((aItemInputs == null && aFluidInputs == null) || aItemOutputs == null) {
                cir.setReturnValue(false);
                return;
            }
            if (minDistance > maxDistance || minSize > maxSize) {
                cir.setReturnValue(false);
                return;
            }
            if (recipeWeight <= 0) {
                GTLog.err.println(
                    "Weight of mining recipe for main material " + aItemOutputs[0].getUnlocalizedName() + " is 0");
            }
            if (aChances != null) {
                if (aChances.length < aItemOutputs.length) {
                    cir.setReturnValue(false);
                    return;
                } else if (aChances.length > aItemOutputs.length) {
                    GTLog.err.println(
                        "Chances and outputs of mining recipe for main material "
                            + aItemOutputs[0].getUnlocalizedName()
                            + " have different length!");
                }
                if (Arrays.stream(aChances).sum() != 10000) {
                    GTLog.err.println(
                        "Sum of chances in mining recipe for main material "
                            + aItemOutputs[0].getUnlocalizedName()
                            + " is not 100%! This will lead to no issue but might be unintentional");
                }
            } else {
                aChances = new int[aItemOutputs.length];
                Arrays.fill(aChances, 10000);
            }

            SpaceMiningData miningData = new SpaceMiningData(
                asteroidName,
                minDistance,
                maxDistance,
                minSize,
                maxSize,
                computationRequiredPerSec,
                recipeWeight);

            GTRecipeBuilder builder = GTValues.RA.stdBuilder();

            if (aItemInputs != null) {
                builder.itemInputs(aItemInputs);
            }
            if (aFluidInputs != null) {
                builder.fluidInputs(aFluidInputs);
            }

            builder.itemOutputs(aItemOutputs)
                .outputChances(aChances)
                .metadata(IGRecipeMaps.MODULE_TIER, minModuleTier)
                .metadata(IGRecipeMaps.SPACE_MINING_DATA, miningData)
                .duration(duration)
                .eut(EUt)
                .addTo(IGRecipeMaps.spaceMiningRecipes);

            cir.setReturnValue(true);
        }
    }

    private static ItemStack[] stripDrillAndRod(ItemStack[] original) {
        if (original == null) return null;
        if (original.length < 3) return original;

        int extraCount = original.length - 3;
        ItemStack[] trimmed = new ItemStack[1 + extraCount];

        trimmed[0] = original[0];

        if (extraCount > 0) {
            System.arraycopy(original, 3, trimmed, 1, extraCount);
        }

        return trimmed;
    }
}
