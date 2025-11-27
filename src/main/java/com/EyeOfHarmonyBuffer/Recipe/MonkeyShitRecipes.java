package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.FoodHelper;
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
import static gregtech.api.enums.Mods.Forestry;
import static gregtech.api.enums.Mods.IndustrialCraft2;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class MonkeyShitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        List<ItemStack> foods = new ArrayList<>(FoodHelper.getAllFoods());
        if (foods.isEmpty()) return;

        foods.removeIf(stack -> !GTUtility.isStackValid(stack));

        ItemStack[] outputs = new ItemStack[]{
            getModItem(Forestry.ID, "fertilizerCompound", 1024),
            getModItem(Forestry.ID, "soil", 1024, 0),
            new ItemStack(Blocks.dirt, 1024),
            getModItem(IndustrialCraft2.ID, "itemFertilizer", 1024)
        };

        FluidStack[] fluidInputs  = new FluidStack[0];
        FluidStack[] fluidOutputs = new FluidStack[]{
            new FluidStack(GTPPFluids.PoopJuice,           1_000_000),
            new FluidStack(GTPPFluids.FertileManureSlurry, 1_000_000)
        };

        int duration = 5 * SECONDS;
        int eut      = 0;
        int special  = 1;

        for (ItemStack food : foods) {
            ItemStack input = food.copy();
            input.stackSize = 1;

            GTRecipe recipe = new GTRecipe(
                 false,
                 new ItemStack[]{ input },
                 outputs,
                 null,
                 null,
                 fluidInputs,
                 fluidOutputs,
                 duration,
                 eut,
                 special
            );

            MonkeyShit.add(recipe);
        }
    }
}
