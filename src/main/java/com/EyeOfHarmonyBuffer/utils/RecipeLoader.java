package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.Recipe.AssemblerRecipes;
import com.EyeOfHarmonyBuffer.Recipe.CoreDrillRecipe;
import com.EyeOfHarmonyBuffer.Recipe.MachineBlockRecipes;
import com.EyeOfHarmonyBuffer.Recipe.SpaceAssemblerRecipes;

public class RecipeLoader {

    public static void loadRecipes() {

        IRecipePool[] recipePools = new IRecipePool[] {
            new AssemblerRecipes(),
            new SpaceAssemblerRecipes(),
            new MachineBlockRecipes(),
            new CoreDrillRecipe(),
        };
        for (IRecipePool recipePool : recipePools) {
            recipePool.loadRecipes();
        }
    }
}
