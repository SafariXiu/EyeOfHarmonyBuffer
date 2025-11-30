package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.RecipeMap.MonkeyShitFrontend;
import com.EyeOfHarmonyBuffer.utils.SimpleStringSpecialFormatter;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import com.EyeOfHarmonyBuffer.common.RecipeMap.SubstanceReshapingDeviceFrontend;
import com.EyeOfHarmonyBuffer.common.RecipeMap.BlueDogDeviceFrontend;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class RecipeMaps {

    public static final RecipeMap<RecipeMapBackend> SubstanceReshapingDevice = RecipeMapBuilder
        .of(EOHB_Recipe_SubstanceReshapingDevice)
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
        .of(EOHB_Recipe_BlueDogFountain)
        .maxIO(0,0,1,16)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(BlueDogDeviceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ChengDuHeart.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> BlueDogMachineMax = RecipeMapBuilder
        .of(EOHB_Recipe_BlueDogFountainMAX)
        .maxIO(0,0,1,16)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(BlueDogDeviceFrontend::new)
        .disableRegisterNEI()
        .build();

    public static final RecipeMap<RecipeMapBackend> MonkeyShit = RecipeMapBuilder
        .of(EOHB_Recipe_MonkeyShit)
        .maxIO(4,4,4,4)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(MonkeyShitFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .build();
}
