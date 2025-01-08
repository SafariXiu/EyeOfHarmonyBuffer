package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.Recipe.AssemblerRecipes;

public class RecipeLoader {

    public static void loadRecipes() {

        IRecipePool[] recipePools = new IRecipePool[] {
            new AssemblerRecipes()
        };
        for (IRecipePool recipePool : recipePools) {
            recipePool.loadRecipes();
        }
    }
}
