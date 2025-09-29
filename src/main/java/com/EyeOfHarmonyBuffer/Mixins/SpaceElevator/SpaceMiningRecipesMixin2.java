package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTUtility;
import gtnhintergalactic.recipe.IG_RecipeAdder;
import gtnhintergalactic.recipe.SpaceMiningRecipes;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.*;

@Mixin(value = SpaceMiningRecipes.class,remap = false)
public abstract class SpaceMiningRecipesMixin2 {

    @Shadow @Final private static ItemStack[] MINING_DRILLS;
    @Shadow @Final private static ItemStack[] MINING_RODS;
    @Shadow @Final private static ItemStack[] MINING_DRONES;


    /**
     * @author EyeOfHarmonyBuffer
     * @reason 重写太空钻机配方逻辑
     */
    @Overwrite
    private static void addRecipesToDrones(
        String asteroidName,
        ItemStack[] aItemInputs, FluidStack[] aFluidInputs,
        int[] aChances, Materials[] ores, OrePrefixes orePrefixes,
        int minSize, int maxSize, int minDistance, int maxDistance,
        int computationRequiredPerSec, int minModuleTier, int duration,
        int EUt, int startDroneTier, int endDroneTier, int recipeWeight) {

        ItemStack[] tItemInputs;
        if (aItemInputs == null) {
            tItemInputs = new ItemStack[3];
        } else {
            tItemInputs = new ItemStack[aItemInputs.length + 3];
            System.arraycopy(aItemInputs, 0, tItemInputs, 3, aItemInputs.length);
        }

        for (int i = startDroneTier; i <= endDroneTier; i++) {
            tItemInputs[0] = MINING_DRONES[i];

            if (MINING_DRILLS != null && i < MINING_DRILLS.length && MINING_DRILLS[i] != null) {
                tItemInputs[1] = MINING_DRILLS[i];
            } else {
                tItemInputs[1] = null;
            }

            if (MINING_RODS != null && i < MINING_RODS.length && MINING_RODS[i] != null) {
                tItemInputs[2] = MINING_RODS[i];
            } else {
                tItemInputs[2] = null;
            }

            IG_RecipeAdder.addSpaceMiningRecipe(
                asteroidName,
                tItemInputs,
                aFluidInputs,
                aChances,
                ores, orePrefixes,
                minSize + (int) GTUtility.powInt(2, i - startDroneTier) - 1,
                maxSize + (int) GTUtility.powInt(2, i - startDroneTier) - 1,
                minDistance,
                maxDistance,
                computationRequiredPerSec,
                minModuleTier,
                (int) Math.ceil(duration / Math.sqrt(i - startDroneTier + 1)),
                (int) Math.ceil(EUt * Math.sqrt(i - startDroneTier + 1)),
                recipeWeight);
        }
    }
}
