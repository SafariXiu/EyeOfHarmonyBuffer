package com.EyeOfHarmonyBuffer.Recipe.utils;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTRecipe;

import java.util.Collection;

public final class RecipeMapMigrator {

    private RecipeMapMigrator() {}

    public static void copyAllRecipesWithFullChance(
        RecipeMap<RecipeMapBackend> oldMap,
        RecipeMap<RecipeMapBackend> newMap
    ) {
        RecipeMapBackend oldBackend = oldMap.getBackend();
        RecipeMapBackend newBackend = newMap.getBackend();

        Collection<GTRecipe> oldRecipes = oldBackend.getAllRecipes();

        for (GTRecipe oldRecipe : oldRecipes) {
            GTRecipe newRecipe = oldRecipe.copy();

            newRecipe.mChances = null;

            newRecipe.setRecipeCategory(null);

            newBackend.compileRecipe(newRecipe);
        }
    }
}
