package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.SimpleStringSpecialFormatter;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import com.EyeOfHarmonyBuffer.common.RecipeMap.SubstanceReshapingDeviceFrontend;
import com.EyeOfHarmonyBuffer.common.RecipeMap.BlueDogDeviceFrontend;

public class RecipeMaps {

    public static final RecipeMap<RecipeMapBackend> SubstanceReshapingDevice = RecipeMapBuilder
        .of("eohb.recipe.SubstanceReshapingDevice")
        .maxIO(4, 16, 4, 16)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .neiSpecialInfoFormatter(new SimpleStringSpecialFormatter("SubstanceReshapingDeviceRecipes"))
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .frontend(SubstanceReshapingDeviceFrontend::new)
        .build();

    public static final RecipeMap<RecipeMapBackend> BlueDogMachine = RecipeMapBuilder
        .of("eohb.recipe.BlueDogFountain")
        .maxIO(0,0,1,16)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(BlueDogDeviceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .build();

    public static final RecipeMap<RecipeMapBackend> BlueDogMachineMax = RecipeMapBuilder
        .of("eohb.recipe.BlueDogFountainMAX")
        .maxIO(0,0,1,16)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(BlueDogDeviceFrontend::new)
        .disableRegisterNEI()
        .build();
}
