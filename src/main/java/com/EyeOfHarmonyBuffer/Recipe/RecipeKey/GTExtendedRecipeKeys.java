package com.EyeOfHarmonyBuffer.Recipe.RecipeKey;

import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.nei.RecipeDisplayInfo;

import javax.annotation.Nullable;

public final class GTExtendedRecipeKeys {

    private GTExtendedRecipeKeys() {}

    public static final RecipeMetadataKey<Integer> NEI_OUTPUT_MIN =
        new RecipeMetadataKey<Integer>(Integer.class, "nei_output_min") {
            @Override
            public void drawInfo(RecipeDisplayInfo recipeInfo, @Nullable Object value) {
            }
        };

    public static final RecipeMetadataKey<Integer> NEI_OUTPUT_MAX =
        new RecipeMetadataKey<Integer>(Integer.class, "nei_output_max") {
            @Override
            public void drawInfo(RecipeDisplayInfo recipeInfo, @Nullable Object value) {
            }
        };
}
