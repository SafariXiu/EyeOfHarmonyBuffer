package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.Recipe.utils.RecipeMapMigrator;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;

import static bartworks.API.recipe.BartWorksRecipeMaps.bioLabRecipes;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.NewBioLab;

public class NewBioLabRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {
        RecipeMapMigrator.copyAllRecipesWithFullChance(
            bioLabRecipes,
            NewBioLab
        );
    }
}
