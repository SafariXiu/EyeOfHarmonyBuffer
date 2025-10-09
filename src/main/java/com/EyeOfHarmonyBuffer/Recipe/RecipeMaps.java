package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.SimpleStringSpecialFormatter;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import com.EyeOfHarmonyBuffer.common.RecipeMap.SubstanceReshapingDeviceFrontend;

public class RecipeMaps {

    public static final RecipeMap<RecipeMapBackend> SubstanceReshapingDevice = RecipeMapBuilder
        .of("eohb.recipe.SubstanceReshapingDevice")
        .maxIO(4,16,4,16)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .neiSpecialInfoFormatter(new SimpleStringSpecialFormatter("SubstanceReshapingDeviceRecipes"))
        .frontend(SubstanceReshapingDeviceFrontend::new)
        .build();

    public static final RecipeMap<RecipeMapBackend> BlueDogMachine = RecipeMapBuilder
        .of("eohb.recipe.BlueDogFountain")
        .maxIO(4,16,4,16)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(SubstanceReshapingDeviceFrontend::new)
        .build();

    public static final RecipeMap<RecipeMapBackend> BlueDogMachineMax = RecipeMapBuilder
        .of("eohb.recipe.BlueDogFountainMAX")
        .maxIO(4,16,4,16)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(SubstanceReshapingDeviceFrontend::new)
        .build();
}
