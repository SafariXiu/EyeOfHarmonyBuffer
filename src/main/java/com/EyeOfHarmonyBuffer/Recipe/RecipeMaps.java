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
import com.EyeOfHarmonyBuffer.common.RecipeMap.RangeOutputFrontend;
import com.EyeOfHarmonyBuffer.common.RecipeMap.PlanterOutputFrontend;
import com.EyeOfHarmonyBuffer.common.RecipeMap.RefiningFurnaceFrontend;
import com.EyeOfHarmonyBuffer.common.RecipeMap.GrinderFrontend;
import com.EyeOfHarmonyBuffer.common.RecipeMap.GeneralFrontend;

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

    public static final RecipeMap<RecipeMapBackend> OrundumDynamo = RecipeMapBuilder
        .of(EOHB_Recipe_OrundumDynamo)
        .maxIO(4,4,4,4)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RangeOutputFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.YuanShi.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> ElectricTypeOneMiningMachine = RecipeMapBuilder
        .of(EOHB_Recipe_ElectricTypeOneMiningMachine)
        .maxIO(1,4,0,0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RangeOutputFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.YuanShiKuang.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> Planter = RecipeMapBuilder
        .of(EOHB_Recipe_Planter)
        .maxIO(1, 1, 1, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(PlanterOutputFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ShaYe.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> SeedCollectingMachine = RecipeMapBuilder
        .of(EOHB_Recipe_SeedCollectingMachine)
        .maxIO(1, 1, 1, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(PlanterOutputFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ShaYeZhongZi.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> RefiningFurnace = RecipeMapBuilder
        .of(EOHB_Recipe_RefiningFurnace)
        .maxIO(1, 1, 1, 1)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RefiningFurnaceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.JingTiWaiKe.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> Pulverizer = RecipeMapBuilder
        .of(EOHB_Recipe_Pulverizer)
        .maxIO(1, 1, 0, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RefiningFurnaceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ShaYeFenMo.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> AccessoriesMachine = RecipeMapBuilder
        .of(EOHB_Recipe_AccessoriesMachine)
        .maxIO(1, 1, 0, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RefiningFurnaceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.GaoJingLingJian.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> ShapingMachine = RecipeMapBuilder
        .of(EOHB_Recipe_ShapingMachine)
        .maxIO(1, 1, 0, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RefiningFurnaceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.GaoJingLingJian.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> Grinder = RecipeMapBuilder
        .of(EOHB_Recipe_Grinder)
        .maxIO(2, 1, 0, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(GrinderFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ZhiMiJingTiFenMo.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> EncapsulationMachine = RecipeMapBuilder
        .of(EOHB_Recipe_EncapsulationMachine)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.DiRongLiangDianChi.get(1)))
        .build();
}
