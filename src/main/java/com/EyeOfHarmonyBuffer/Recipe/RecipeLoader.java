package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;

public class RecipeLoader {

    public static void loadRecipes() {

        IRecipePool[] recipePools = new IRecipePool[] {
            new AssemblerRecipes(),
            new SpaceAssemblerRecipes(),
            new MachineBlockRecipes(),
            new SubstanceReshapingDeviceRecipes(),
            new BlueDogMachineRecipes(),
            new MaterialsRecipes(),
        };
        for (IRecipePool recipePool : recipePools) {
            recipePool.loadRecipes();
        }
    }
}
