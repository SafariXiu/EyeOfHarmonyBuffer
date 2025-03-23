package com.EyeOfHarmonyBuffer.Recipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import com.EyeOfHarmonyBuffer.common.RecipeMap.CoreDrillFrontend;

public class RecipeMaps {

    public static final RecipeMap<RecipeMapBackend> SubstanceReshapingDevice = RecipeMapBuilder
        .of("eohb.recipe.SubstanceReshapingDevice")
        .maxIO(4,16,4,16)
        .neiRecipeBackgroundSize(170, 185)
        .disableOptimize()
        .useCustomFilterForNEI()
        .frontend(CoreDrillFrontend::new)
        .build();
}
