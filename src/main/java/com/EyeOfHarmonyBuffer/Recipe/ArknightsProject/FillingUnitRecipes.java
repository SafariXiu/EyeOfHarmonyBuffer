package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.common.item.ItemLoader;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.FillingUnit;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class FillingUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingZhiPing.get(5),
                GTCMItemList.GanShiFenMo.get(5)
            )
            .itemOutputs(
                GTCMItemList.GanShiGuanTou.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingZhiPing.get(5),
                GTCMItemList.QiaoHuaFenMo.get(5)
            )
            .itemOutputs(
                GTCMItemList.QiaoYuJiaoNang.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(10),
                GTCMItemList.QiaoHuaFenMo.get(10)
            )
            .itemOutputs(
                GTCMItemList.YouZhiQiaoYuJiaoNang.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(10),
                GTCMItemList.GanShiFenMo.get(10)
            )
            .itemOutputs(
                GTCMItemList.YouZhiGanShiGuanTou.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiPing.get(10),
                GTCMItemList.XiMoQiaoHuaFenMo.get(10)
            )
            .itemOutputs(
                GTCMItemList.JingXuanQiaoYuJiaoNang.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiPing.get(10),
                GTCMItemList.XiMoGanShiFenMo.get(10)
            )
            .itemOutputs(
                GTCMItemList.JingXuanGanShiGuanTou.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.LanTieFluidBottle, 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.LanTieFluidBottle, 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.LanTieFluidBottle, 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.LanTieFluidBottle, 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.LiquidHeavyXiranite.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.LanTieFluidBottle, 1, EnumBottleFluid.YE_HUA_ZHONG_XI_RANG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ZiJingZhiFluidBottle, 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ZiJingZhiFluidBottle, 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ZiJingZhiFluidBottle, 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ZiJingZhiFluidBottle, 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GangZhiFluidBottle, 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GangZhiFluidBottle, 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GangZhiFluidBottle, 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GangZhiFluidBottle, 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GaoJingFluidBottle, 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GaoJingFluidBottle, 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GaoJingFluidBottle, 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.GaoJingFluidBottle, 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongFluidBottle, 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongFluidBottle, 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.FuelRod_empty1.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.HighEnergyOrundumSolvent.getFluidOrGas(16000)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod1.get(1)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);
    }
}
