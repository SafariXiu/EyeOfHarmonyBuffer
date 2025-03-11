package com.EyeOfHarmonyBuffer.Recipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import com.EyeOfHarmonyBuffer.common.RecipeMap.CoreDrillFrontend;

public class RecipeMaps {

    public static final RecipeMap<RecipeMapBackend> CoreDrill = RecipeMapBuilder
        .of("eohb.recipe.coreDrill")
        .maxIO(2,4,2,16)
        .neiRecipeBackgroundSize(170, 160)
        .disableOptimize()
        .useCustomFilterForNEI()
        .frontend(CoreDrillFrontend::new)
        .build();
}
