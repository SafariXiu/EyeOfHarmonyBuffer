package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.fluids.GTPPFluids;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.MonkeyShit;
import static com.EyeOfHarmonyBuffer.utils.FoodHelper.getAllFoods;
import static com.EyeOfHarmonyBuffer.utils.FoodHelper.getHunger;
import static gregtech.api.enums.Mods.Forestry;
import static gregtech.api.enums.Mods.IndustrialCraft2;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class MonkeyShitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        List<ItemStack> foods = new ArrayList<>(getAllFoods());
        if (foods.isEmpty()) return;

        foods.removeIf(stack -> !GTUtility.isStackValid(stack));

        final double BASE_LEGS = 5.0;

        final ItemStack[] baseOutputs = new ItemStack[]{
            getModItem(Forestry.ID, "fertilizerCompound", 1024),
            getModItem(Forestry.ID, "soil", 1024, 0),
            new ItemStack(Blocks.dirt, 1024),
            getModItem(IndustrialCraft2.ID, "itemFertilizer", 1024)
        };

        final FluidStack[] baseFluidOutputs = new FluidStack[]{
            new FluidStack(GTPPFluids.PoopJuice, 1_000_000),
            new FluidStack(GTPPFluids.FertileManureSlurry, 1_000_000)
        };

        final FluidStack[] fluidInputs  = new FluidStack[0];

        final int duration = 5 * SECONDS;
        final int eut = 0;
        final int special = 1;

        for (ItemStack food : foods) {
            if (!GTUtility.isStackValid(food))
                continue;

            int hunger = getHunger(food);
            if (hunger <= 0)
                continue;

            double legs = hunger / 2.0;
            double deltaLegs = legs - BASE_LEGS;

            double scale;
            int inputCount;

            if (deltaLegs > 0) {
                scale = Math.pow(2.0, deltaLegs);
                inputCount = 1;
            } else if (deltaLegs < 0) {
                double missingLegs = -deltaLegs;
                scale = 1.0 / Math.pow(2.0, missingLegs);
                inputCount = (int) Math.floor(1.0 + missingLegs);
                if (inputCount < 1)
                    inputCount = 1;
            } else {
                scale = 1.0;
                inputCount = 1;
            }

            ItemStack input = food.copy();
            input.stackSize = inputCount;

            ItemStack[] recipeInputs = new ItemStack[]{ input };

            ItemStack[] recipeOutputs = new ItemStack[baseOutputs.length];
            for (int i = 0; i < baseOutputs.length; i++) {
                ItemStack base = baseOutputs[i];
                if (base == null) {
                    recipeOutputs[i] = null;
                    continue;
                }
                ItemStack out = base.copy();
                double rawAmount = out.stackSize * scale;
                long newSize = (long) Math.ceil(rawAmount);
                if (newSize < 1)
                    newSize = 1;
                if (newSize > Integer.MAX_VALUE)
                    newSize = Integer.MAX_VALUE;
                out.stackSize = (int) newSize;
                recipeOutputs[i] = out;
            }

            FluidStack[] recipeFluidOutputs = new FluidStack[baseFluidOutputs.length];
            for (int i = 0; i < baseFluidOutputs.length; i++) {
                FluidStack base = baseFluidOutputs[i];
                if (base == null) {
                    recipeFluidOutputs[i] = null;
                    continue;
                }
                FluidStack out = base.copy();
                double rawAmount = out.amount * scale;
                long newAmount = (long) Math.ceil(rawAmount);
                if (newAmount < 1)
                    newAmount = 1;
                if (newAmount > Integer.MAX_VALUE)
                    newAmount = Integer.MAX_VALUE;
                out.amount = (int) newAmount;
                recipeFluidOutputs[i] = out;
            }

            GTRecipe recipe = new GTRecipe(
                false,
                recipeInputs,
                recipeOutputs,
                null,
                null,
                fluidInputs,
                recipeFluidOutputs,
                duration,
                eut,
                special
            );

            MonkeyShit.add(recipe);
        }
    }
}
