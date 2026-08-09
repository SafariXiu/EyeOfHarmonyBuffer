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
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Pulverizer;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.PurificationUnit;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.RefiningFurnace;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.SeparatingUnit;
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
            .eut(TierEU.RECIPE_LuV)
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
            .eut(TierEU.RECIPE_UHV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        // 装置：破损装置修复（主配方）
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

        // 装置：独立生产（不需要破损装置）
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

        // 全新装置
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuangZhi.get(1),
                GTCMItemList.GuYuanYanZu.get(4),
                ItemList.Electric_Motor_HV.get(1),
                ItemList.Electric_Piston_HV.get(1),
                ItemList.Sensor_HV.get(1),
                NHItemList.CircuitLuV.get(1)
            )
            .fluidInputs(
                Materials.RubberSilicone.getFluid(2000)
            )
            .itemOutputs(
                GTCMItemList.QuanXinZhuangZhi.get(1)
            )
            .eut(TierEU.RECIPE_LuV)
            .duration(45 * SECONDS)
            .addTo(assemblerRecipes);

        // 改量装置
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QuanXinZhuangZhi.get(1),
                GTCMItemList.GuYuanYanZu.get(4),
                ItemList.Electric_Motor_ZPM.get(1),
                ItemList.Robot_Arm_ZPM.get(1),
                ItemList.Field_Generator_ZPM.get(1),
                NHItemList.CircuitUHV.get(1)
            )
            .fluidInputs(
                Materials.RubberSilicone.getFluid(4000)
            )
            .itemOutputs(
                GTCMItemList.GaiLiangZhuangZhi.get(1)
            )
            .eut(TierEU.RECIPE_UHV)
            .duration(60 * SECONDS)
            .addTo(assemblerRecipes);

        // 糖汁：甘蔗压榨（粉碎机）
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(Items.reeds, 8)
            )
            .fluidOutputs(
                EOHBMaterialPool.TangZhi.getFluidOrGas(4000)
            )
            .eut(5000)
            .duration(30 * SECONDS)
            .addTo(Pulverizer);

        // 代糖：碳粉 + 乙酸合成甜味剂（精炼炉）
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
            .eut(5000)
            .duration(30 * SECONDS)
            .addTo(RefiningFurnace);

        // 糖：糖汁净化结晶（净化单元）
        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.TangZhi.getFluidOrGas(4000),
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.Tang.get(2)
            )
            .eut(50000)
            .duration(40 * SECONDS)
            .specialValue(GasEnvRecipeFlags.NONE)
            .setNEIDesc(EOHB_Environment_NONE)
            .addTo(PurificationUnit);

        // 糖蜜：糖 + 代糖浓缩糖浆
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.Tang.get(2),
                GTCMItemList.DaiTang.get(2)
            )
            .fluidInputs(
                Materials.Water.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.TangMi.getFluidOrGas(3000)
            )
            .eut(TierEU.RECIPE_UHV)
            .duration(45 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        // 糖组：糖蜜灌装成型（灌装单元）
        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.TangMi.getFluidOrGas(3000)
            )
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Carbon, 8)
            )
            .itemOutputs(
                GTCMItemList.TangZu.get(1)
            )
            .eut(20000)
            .duration(45 * SECONDS)
            .addTo(FillingUnit);

        // 复合糖浆：糖组 + 酮凝集组复合液
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TangZu.get(2),
                GTCMItemList.TongNingJiZu.get(1)
            )
            .fluidInputs(
                Materials.Ethanol.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FuHeTangJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_UIV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        // 糖聚块：复合糖浆真空结晶
        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.FuHeTangJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.TangJuKuai.get(1)
            )
            .eut(TierEU.RECIPE_UIV)
            .duration(60 * SECONDS)
            .addTo(vacuumFreezerRecipes);

        // 双酮：乙酸脱羧合成
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

        // 酮基溶液：双酮溶于乙醇（分离单元）
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
            .eut(20000)
            .duration(30 * SECONDS)
            .addTo(SeparatingUnit);

        // 酮凝集：酮基溶液封装胶体化（封装机）
        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.TongJiRongYe.getFluidOrGas(2000),
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.TongNingJi.get(1)
            )
            .eut(50000)
            .duration(30 * SECONDS)
            .addTo(EncapsulationMachine);

        // 酮基聚合物：酮凝集聚合（LCR）
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
            .eut(TierEU.RECIPE_UHV)
            .duration(45 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        // 酮凝集组：酮基聚合物封装（封装机）
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
            .duration(45 * SECONDS)
            .addTo(EncapsulationMachine);

        // 复合酮浆：酮凝集组 + 糖组复合液
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongNingJiZu.get(2),
                GTCMItemList.TangZu.get(1)
            )
            .fluidInputs(
                Materials.Acetone.getFluid(2000)
            )
            .fluidOutputs(
                EOHBMaterialPool.FuHeTongJiang.getFluidOrGas(4000)
            )
            .eut(TierEU.RECIPE_UIV)
            .duration(60 * SECONDS)
            .addTo(multiblockChemicalReactorRecipes);

        // 酮阵列：复合酮浆真空结晶
        GTValues.RA.stdBuilder()
            .fluidInputs(
                EOHBMaterialPool.FuHeTongJiang.getFluidOrGas(4000)
            )
            .itemOutputs(
                GTCMItemList.TongZhenLie.get(1)
            )
            .eut(TierEU.RECIPE_UIV)
            .duration(60 * SECONDS)
            .addTo(vacuumFreezerRecipes);

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
