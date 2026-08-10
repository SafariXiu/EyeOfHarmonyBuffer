package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.RecipeMap.MonkeyShitFrontend;
import com.EyeOfHarmonyBuffer.common.byproduct.ByproductFrontend;
import com.EyeOfHarmonyBuffer.utils.FixedStringSpecialFormatter;
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
        .maxIO(4,4,4,4)
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
        .maxIO(1, 1, 1, 0)
        .neiRecipeBackgroundSize(170,185)
        .useCustomFilterForNEI()
        .frontend(RefiningFurnaceFrontend::new)
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20,20)
        .logoPos(152,63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.GaoJingZhiPing.get(1)))
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
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.DiRongGuDiDianChi.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> FillingUnit = RecipeMapBuilder
        .of(EOHB_Recipe_FillingUnit)
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
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.LanTiePing.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> ForgeOfTheSky = RecipeMapBuilder
        .of(EOHB_Recipe_ForgeOfTheSky)
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
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.XiRang.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> PurificationUnit = RecipeMapBuilder
        .of(EOHB_Recipe_PurificationUnit)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.HeTongRongYe.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> ReactorCrucible = RecipeMapBuilder
        .of(EOHB_Recipe_ReactorCrucible)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.RangJing.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> FluidPumpMK1 = RecipeMapBuilder
        .of(EOHB_Recipe_FluidPumpMK1)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.QingShui.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> FluidPumpMK2 = RecipeMapBuilder
        .of(EOHB_Recipe_FluidPumpMK2)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ChenJiSuan.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> ElectricTypeTwoMiningMachine = RecipeMapBuilder
        .of(EOHB_Recipe_ElectricTypeTwoMiningMachine)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.LanTieKuang.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> HighDensityEnergyFluidGenerator = RecipeMapBuilder
        .of(EOHB_Recipe_HighDensityEnergyFluidGenerator)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()

        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.HighDensityEnergyFluidGenerator.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> IsotopeInfusionReactor = RecipeMapBuilder
        .of(EOHB_Recipe_IsotopeInfusionReactor)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.YuanShiTongWeiSu_Alpha.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> GasDiffuser = RecipeMapBuilder
        .of(EOHB_Recipe_GasDiffuser)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .neiSpecialInfoFormatter(new FixedStringSpecialFormatter("GT5U.gui.text.recipe.GasDiffuserInfo"))
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.SuanQi.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> Fluid_GasTransmutingUnit = RecipeMapBuilder
        .of(EOHB_Recipe_Fluid_GasTransmutingUnit)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .neiSpecialInfoFormatter(new FixedStringSpecialFormatter("GT5U.gui.text.recipe.Fluid_GasTransmutingUnitInfo"))
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.XiRangQi.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> Solid_GasTransmutingUnit = RecipeMapBuilder
        .of(EOHB_Recipe_Solid_GasTransmutingUnit)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .neiSpecialInfoFormatter(new FixedStringSpecialFormatter("GT5U.gui.text.recipe.Solid_GasTransmutingUnit"))
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ZhuoTongKuai.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> GasReactorGlobe = RecipeMapBuilder
        .of(EOHB_Recipe_GasReactorGlobe)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.QiTaiZhuoTong.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> HydroMiningRig = RecipeMapBuilder
        .of(EOHB_Recipe_HydroMiningRig)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ChiTongKuang.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> GasExtractor = RecipeMapBuilder
        .of(EOHB_Recipe_GasExtractor)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.DuoQi.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> SeparatingUnit = RecipeMapBuilder
        .of(EOHB_Recipe_SeparatingUnit)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ChiTongNaiYaPing.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> GearingUnit = RecipeMapBuilder
        .of(EOHB_Recipe_GearingUnit)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.ZiJingZhuangBeiYuanJian.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> LargeForce_ContainedProliferationMine = RecipeMapBuilder
        .of(EOHB_Recipe_LargeForce_ContainedProliferationMine)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.LargeForce_ContainedProliferationMine.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> InternalizedUniverseComputingEngine = RecipeMapBuilder
        .of(EOHB_Recipe_InternalizedUniverseComputingEngine)
        .maxIO(4, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new GeneralFrontend(uiProps, neiProps,
                4,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.InternalizedUniverseComputingEngine.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> XirangAssembler = RecipeMapBuilder
        .of(EOHB_Recipe_XirangAssembler)
        .maxIO(16, 4, 4, 4)
        .neiRecipeBackgroundSize(170, 185)
        .useCustomFilterForNEI()
        .frontend((uiProps, neiProps) ->
            new ByproductFrontend(uiProps, neiProps,
                16,
                4
            )
        )
        .logo(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
        .logoSize(20, 20)
        .logoPos(152, 63)
        .neiHandlerInfo(builder -> builder.setDisplayStack(GTCMItemList.JingTiDianLu.get(1)))
        .build();
}
