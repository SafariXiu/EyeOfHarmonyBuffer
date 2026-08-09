package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import bartworks.system.material.WerkstoffLoader;
import com.dreammaster.item.NHItemList;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBGTMaterials;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvRecipeFlags;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.EncapsulationMachine;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.FillingUnit;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Fluid_GasTransmutingUnit;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.GasReactorGlobe;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Pulverizer;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.PurificationUnit;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.ReactorCrucible;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.RefiningFurnace;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Environment_NONE;
import static gregtech.api.recipe.RecipeMaps.*;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

public class ArknightsRecipesLoad implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Magnesium.getDust(32),
                WerkstoffLoader.TantalumCarbideHafniumCarbideMixture.get(OrePrefixes.dust, 16),
                Materials.Europium.getDust(16)
            )
            .itemOutputs(
                EOHBMaterialPool.Hoyomixium.get(OrePrefixes.dust, 64)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(325 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTCMItemList.YuanShiFuelRod1.get(2),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stick, 4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod2.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(5),
                GTCMItemList.YuanShiFuelRod2.get(2),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stick, 4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod4.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(4),
                GTCMItemList.YuanShiFuelRod1.get(4),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stickLong, 6)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod4.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiDepletedFuelRod1.get(8)
            )
            .itemOutputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1),
                GTCMItemList.FuelRod_empty1.get(8)
            )
            .outputChances(
                200,
                10000
            )
            .fluidOutputs(
                EOHBMaterialPool.ContaminatedOrundumSlurry.getFluidOrGas(100)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(512 * SECONDS)
            .addTo(centrifugeRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiDepletedFuelRod2.get(4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1),
                GTCMItemList.FuelRod_empty1.get(8)
            )
            .outputChances(
                200,
                10000
            )
            .fluidOutputs(
                EOHBMaterialPool.ContaminatedOrundumSlurry.getFluidOrGas(100)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(512 * SECONDS)
            .addTo(centrifugeRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiDepletedFuelRod4.get(2)
            )
            .itemOutputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1),
                GTCMItemList.FuelRod_empty1.get(8)
            )
            .outputChances(
                200,
                10000
            )
            .fluidOutputs(
                EOHBMaterialPool.ContaminatedOrundumSlurry.getFluidOrGas(100)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(512 * SECONDS)
            .addTo(centrifugeRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, EOHBGTMaterials.YiTie, 1)
            )
            .itemOutputs(
                GTCMItemList.YiTie.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(50 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, EOHBGTMaterials.ZhuanZhiYan, 32)
            )
            .fluidInputs(
                Materials.Water.getFluid(64000)
            )
            .itemOutputs(
                GTCMItemList.ZhuanZhiYanZu.get(16)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(60 * SECONDS)
            .addTo(autoclaveRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, EOHBGTMaterials.QingMengKuang, 8)
            )
            .itemOutputs(
                GTCMItemList.QingMengKuang.get(1)
            )
            .fluidInputs(
                Materials.SulfuricAcid.getFluid(2000)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(50 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTieZu.get(1)
            )
            .fluidOutputs(
                EOHBGTMaterials.YiTie.getMolten(144)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(2 * SECONDS)
            .addTo(fluidExtractionRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuanZhiYanZu.get(1)
            )
            .fluidOutputs(
                EOHBGTMaterials.ZhuanZhiYan.getMolten(144)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(2 * SECONDS)
            .addTo(fluidExtractionRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QingMengKuang.get(1)
            )
            .fluidOutputs(
                EOHBGTMaterials.QingMengKuang.getMolten(144)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(2 * SECONDS)
            .addTo(fluidExtractionRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.DiChunYuanShiFenMo.get(16),
                Materials.Stone.getDust(48)
            )
            .itemOutputs(
                GTCMItemList.GuYuanYan.get(64)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanYan.get(64)
            )
            .itemOutputs(
                GTCMItemList.GuYuanYan.get(4)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GuYuanYan.get(4)
            )
            .fluidInputs(
                Materials.Epoxid.getMolten(2000)
            )
            .itemOutputs(
                GTCMItemList.GuYuanYanZu.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GuYuanYanZu.get(4)
            )
            .fluidInputs(
                Materials.HydrofluoricAcid.getFluid(4000)
            )
            .itemOutputs(
                GTCMItemList.TiChunYuanYan.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.PoSunZhuangZhi.get(1),
                GTCMItemList.GuYuanYan.get(3),
                ItemList.Electric_Motor_LV.get(1),
                NHItemList.CircuitLV.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhuangZhi.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GuYuanYan.get(4),
                ItemList.Electric_Motor_MV.get(1),
                ItemList.Electric_Pump_MV.get(1),
                NHItemList.CircuitLV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 2)
            )
            .itemOutputs(
                GTCMItemList.ZhuangZhi.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(45 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuangZhi.get(1),
                GTCMItemList.GuYuanYanZu.get(4),
                ItemList.Electric_Motor_HV.get(1),
                ItemList.Electric_Piston_HV.get(1),
                ItemList.Sensor_HV.get(1),
                NHItemList.CircuitEV.get(1)
            )
            .fluidInputs(
                Materials.RubberSilicone.getFluid(2000)
            )
            .itemOutputs(
                GTCMItemList.QuanXinZhuangZhi.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(45 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QuanXinZhuangZhi.get(1),
                GTCMItemList.GuYuanYanZu.get(4),
                ItemList.Electric_Motor_IV.get(1),
                ItemList.Robot_Arm_IV.get(1),
                ItemList.Field_Generator_IV.get(1),
                NHItemList.CircuitIV.get(1)
            )
            .fluidInputs(
                Materials.RubberSilicone.getFluid(4000)
            )
            .itemOutputs(
                GTCMItemList.GaiLiangZhuangZhi.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(Items.reeds, 8)
            )
            .fluidOutputs(
                EOHBMaterialPool.TangZhi.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(extractorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 16)
            )
            .fluidInputs(
                Materials.AceticAcid.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.DaiTang.get(4)
            )
            .fluidOutputs(
                Materials.CarbonDioxide.getGas(500)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.TangZhi.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.Tang.get(2)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(40 * SECONDS)
            .addTo(vacuumFreezerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.Tang.get(2)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.TangRongYe.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.TangRongYe.getFluidOrGas(3000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FaJiaoTangJiang.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(fermentingRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.FaJiaoTangJiang.getFluidOrGas(3000)
            )
            .itemOutputs(
                GTCMItemList.TangZu.get(1)
            )
            .fluidOutputs(
                Materials.Ethanol.getFluid(1000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(distillationTowerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TangZu.get(2),
                GTCMItemList.TongNingJiZu.get(1),
                GTCMItemList.DaiTang.get(2)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FuHeTangJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.FuHeTangJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.TangJuKuai.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(vacuumFreezerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 2)
            )
            .fluidInputs(
                Materials.AceticAcid.getFluid(2000)
            )
            .itemOutputs(
                GTCMItemList.ShuangTong.get(1)
            )
            .fluidOutputs(
                Materials.CarbonDioxide.getGas(500)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShuangTong.get(2)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.TongJiRongYe.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.TongJiRongYe.getFluidOrGas(2000),
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.TongNingJi.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongNingJi.get(4),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 4)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000)
            )
            .itemOutputs(
                EOHBMaterialPool.TongJiJuHeWu.get(OrePrefixes.dust, 4)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(45 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.TongJiJuHeWu.get(OrePrefixes.dust, 4)
            )
            .fluidInputs(
                Materials.Acetone.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.TongNingJiZu.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongNingJiZu.get(2),
                GTCMItemList.TangZu.get(1)
            )
            .fluidInputs(
                Materials.Acetone.getFluid(2000),
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.TongZhenLie.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Ethanol.getFluid(1000),
                Materials.AceticAcid.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.ZhiYuanLiao.get(2)
            )
            .fluidOutputs(
                Materials.Water.getFluid(1000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiYuanLiao.get(2)
            )
            .fluidInputs(
                Materials.AceticAcid.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.JuSuanZhi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JuSuanZhi.get(2),
                GTCMItemList.ZhiYuanLiao.get(2)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JuZhiRongJiang.getFluidOrGas(3000),
                Materials.Water.getFluid(500)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(45 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.JuZhiRongJiang.getFluidOrGas(3000)
            )
            .itemOutputs(
                GTCMItemList.JuSuanZhiZu.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JuSuanZhiZu.get(2),
                GTCMItemList.TangZu.get(1)
            )
            .fluidInputs(
                Materials.AceticAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FuHeJuZhiJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 2)
            )
            .fluidInputs(
                EOHBMaterialPool.FuHeJuZhiJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.JuSuanZhiKuai.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTieSuiPian.get(4)
            )
            .fluidInputs(
                Materials.SulfuricAcid.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.YiTie.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTie.get(4)
            )
            .fluidInputs(
                Materials.SulfuricAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.YiTieJiang.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(45 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.YiTieJiang.getFluidOrGas(3000)
            )
            .itemOutputs(
                GTCMItemList.YiTieZu.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTieZu.get(2),
                GTCMItemList.TongNingJiZu.get(1)
            )
            .fluidInputs(
                Materials.SulfuricAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FuHeYiTieJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.FuHeYiTieJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.YiTieKuai.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(vacuumFreezerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Platinum, 2),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Iridium, 1)
            )
            .itemOutputs(
                EOHBMaterialPool.BoYiFen.get(OrePrefixes.dust, 3)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.BoYiFen.get(OrePrefixes.dust, 3),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Titanium, 4)
            )
            .fluidInputs(
                Materials.HydrochloricAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.DianJiJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.DianJiJiang.getFluidOrGas(4000),
                Materials.HydrofluoricAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingZhiDianJiJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Titanium, 2)
            )
            .fluidInputs(
                EOHBMaterialPool.JingZhiDianJiJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.DianJiDanYuan.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKe.get(2),
                GTCMItemList.YuanShiFenMo.get(4)
            )
            .fluidInputs(
                Materials.HydrofluoricAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingTiJiang.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Circuit_Silicon_Wafer.get(2)
            )
            .fluidInputs(
                EOHBMaterialPool.JingTiJiang.getFluidOrGas(2000),
                Materials.HydrofluoricAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.ShiKeJingTiJiang.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Platinum, 1)
            )
            .fluidInputs(
                EOHBMaterialPool.ShiKeJingTiJiang.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.DuBoJingTiJiang.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.DuBoJingTiJiang.getFluidOrGas(2000),
                Materials.RubberSilicone.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.JingTiYuanJian.get(2)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTie.get(2),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Titanium, 2),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tungsten, 2),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Platinum, 1)
            )
            .itemOutputs(
                EOHBMaterialPool.ChiHeJinPeiLiao.get(OrePrefixes.dust, 4)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.ChiHeJinPeiLiao.get(OrePrefixes.dust, 4)
            )
            .fluidInputs(
                Materials.HydrofluoricAcid.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.ChiHeJinJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.ChiHeJinJiang.getFluidOrGas(4000),
                Materials.Nitrogen.getGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingLianChiHeJinJiang.getFluidOrGas(4000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTie.get(4)
            )
            .fluidInputs(
                EOHBMaterialPool.JingLianChiHeJinJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.ChiHeJin.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Acetylene.getGas(2000),
                Materials.Oxygen.getGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HighEnergyGas.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(GasReactorGlobe);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HighEnergyGas.getFluidOrGas(2000),
                Materials.Nitrogen.getGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.WenDingGaoNengQiTi.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(GasReactorGlobe);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.WenDingGaoNengQiTi.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.GaoNengYeTi.getFluidOrGas(2000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(Fluid_GasTransmutingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.GaoNengYeTi.getFluidOrGas(1000)
            )
            .itemInputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .itemOutputs(
                GTCMItemList.YeHuaGaoNengQiTi.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Benzene.getFluid(2000),
                Materials.Ethylene.getGas(2000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HuanTingDanTi.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiFenMo.get(4)
            )
            .fluidInputs(
                EOHBMaterialPool.HuanTingDanTi.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.HuanTingJuHeWu.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.HuanTingJuHeWu.getFluidOrGas(2000)
            )
            .itemOutputs(
                GTCMItemList.HuanTingJuZhi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiYuanLiao.get(2)
            )
            .fluidInputs(
                EOHBMaterialPool.TongJiRongYe.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.NingJiaoQianTi.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.NingJiaoQianTi.getFluidOrGas(2000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JiaoLianNingJiao.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.JiaoLianNingJiao.getFluidOrGas(2000)
            )
            .itemOutputs(
                GTCMItemList.NingJiao.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShuangTong.get(2)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.NiuZhuanChunJiang.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.NiuZhuanChunJiang.getFluidOrGas(3000),
                Materials.Water.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.ChunHuaNiuZhuanChunJiang.getFluidOrGas(2500)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.ChunHuaNiuZhuanChunJiang.getFluidOrGas(2500)
            )
            .itemOutputs(
                GTCMItemList.NiuZhuanChun.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(vacuumFreezerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiYuanLiao.get(4)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000),
                Materials.Water.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.ChuanTongRongJi.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.ChuanTongRongJi.getFluidOrGas(4000),
                Materials.Benzene.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.GaiXingRongJi.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 2)
            )
            .fluidInputs(
                EOHBMaterialPool.GaiXingRongJi.getFluidOrGas(4000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.NaiSuanJianRongJi.getFluidOrGas(4000)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.NaiSuanJianRongJi.getFluidOrGas(4000)
            )
            .itemInputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .itemOutputs(
                GTCMItemList.BanZiRanRongJi.get(2)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Glycerol.getFluid(1000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.RuHuaJi.getFluidOrGas(1000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiYuanLiao.get(4)
            )
            .fluidInputs(
                EOHBMaterialPool.RuHuaJi.getFluidOrGas(1000),
                Materials.Water.getFluid(4000)
            )
            .itemOutputs(
                GTCMItemList.QieXiaoYuanYe.get(4)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QieXiaoYuanYe.get(4)
            )
            .fluidInputs(
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingZhiQieXiaoYe.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.JingZhiQieXiaoYe.getFluidOrGas(2000)
            )
            .itemInputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .itemOutputs(
                GTCMItemList.HuaHeQieXiaoYe.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 4)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.ActivatedCarbon, 4)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.ActivatedCarbon, 4),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Silicon, 4)
            )
            .fluidInputs(
                Materials.Water.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.XiFuJiang.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.XiFuJiang.getFluidOrGas(2000),
                EOHBMaterialPool.TongJiRongYe.getFluidOrGas(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.NingJieJiang.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.NingJieJiang.getFluidOrGas(2000)
            )
            .itemOutputs(
                GTCMItemList.LeiNingJieHe.get(2)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(vacuumFreezerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Aluminiumoxide, 4)
            )
            .itemOutputs(
                EOHBMaterialPool.GangYuFen.get(OrePrefixes.dust, 2)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .metadata(COIL_HEAT, 3000)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.GangYuFen.get(OrePrefixes.dust, 2),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Borax, 2),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Graphite, 2)
            )
            .fluidInputs(
                Materials.Water.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.YanMoJiang.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.YanMoJiang.getFluidOrGas(3000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingZhiYanMoJiang.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Graphite, 2)
            )
            .fluidInputs(
                EOHBMaterialPool.JingZhiYanMoJiang.getFluidOrGas(3000)
            )
            .itemOutputs(
                GTCMItemList.YanMoShi.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QingMengKuang.get(2)
            )
            .itemOutputs(
                EOHBMaterialPool.MengKuangJingFen.get(OrePrefixes.dust, 4)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                EOHBMaterialPool.MengKuangJingFen.get(OrePrefixes.dust, 4),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Manganese, 2)
            )
            .fluidInputs(
                Materials.Water.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.KuangWuJiang.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.KuangWuJiang.getFluidOrGas(2000),
                Materials.SulfuricAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingZhiKuangWuJiang.getFluidOrGas(2000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongNingJiZu.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.JingZhiKuangWuJiang.getFluidOrGas(2000),
                Materials.Acetone.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.RMA70_12.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiFenMo.get(4)
            )
            .fluidInputs(
                EOHBMaterialPool.JuZhiRongJiang.getFluidOrGas(2000),
                EOHBMaterialPool.HuanTingJuHeWu.getFluidOrGas(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FangSiYe.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.FangSiYe.getFluidOrGas(3000),
                Materials.AceticAcid.getFluid(1000)
            )
            .fluidOutputs(
                EOHBMaterialPool.JingZhiFangSiYe.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.JingZhiFangSiYe.getFluidOrGas(3000)
            )
            .fluidOutputs(
                EOHBMaterialPool.NongSuoFangSiYe.getFluidOrGas(2500)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.NongSuoFangSiYe.getFluidOrGas(2500),
                Materials.SulfuricAcid.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.HeSuXianWei.get(3)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(ReactorCrucible);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiKuang.get(8),
                new ItemStack(Blocks.stone, 1)
            )
            .itemOutputs(
                GTCMItemList.YuanShiBlock.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);
    }
}
