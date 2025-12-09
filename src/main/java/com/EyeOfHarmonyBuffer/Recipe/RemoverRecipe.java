package com.EyeOfHarmonyBuffer.Recipe;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.utils.RecipeRemover;
import goodgenerator.items.GGMaterial;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.recipe.RecipeMaps;

public class RemoverRecipe {

    public static void run(){

        RecipeRemover.builder()
            .backend(RecipeMaps.formingPressRecipes.getBackend())
            .itemInputs(
                WerkstoffLoader.Tiberium.get(OrePrefixes.plate, 1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Silicon, 8)
            )
            .itemOutputs(
                GGMaterial.orundum.get(OrePrefixes.plate, 1)
            )
            .remove();
    }
}
