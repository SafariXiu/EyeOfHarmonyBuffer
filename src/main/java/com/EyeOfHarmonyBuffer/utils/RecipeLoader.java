package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.Recipe.AssemblerRecipes;
import com.EyeOfHarmonyBuffer.Recipe.SpaceAssemblerRecipes;

public class RecipeLoader {

    public static void loadRecipes() {

        IRecipePool[] recipePools = new IRecipePool[] {
            new AssemblerRecipes(),
            new SpaceAssemblerRecipes()
        };
        for (IRecipePool recipePool : recipePools) {
            recipePool.loadRecipes();
        }
    }
}
