package com.EyeOfHarmonyBuffer.Recipe;

import net.minecraftforge.fluids.FluidRegistry;
import org.apache.commons.lang3.tuple.Pair;

import static gtnhintergalactic.recipe.SpacePumpingRecipes.RECIPES;

public class EOHBSpacePumpingRecipes {

    public static void addPumpingRecipes(){

        RECIPES.put(Pair.of(2,2), FluidRegistry.getFluidStack("liquiddna",20000000));
    }
}
