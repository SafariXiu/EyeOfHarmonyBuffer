package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.Recipe.*;

public class RecipeLoader {

    public static void loadRecipes() {

        IRecipePool[] recipePools = new IRecipePool[] {
            new AssemblerRecipes(),
            new SpaceAssemblerRecipes(),
            new MachineBlockRecipes(),
            new SubstanceReshapingDeviceRecipes(),
            new BlueDogMachineRecipes(),
        };
        for (IRecipePool recipePool : recipePools) {
            recipePool.loadRecipes();
        }
    }
}
