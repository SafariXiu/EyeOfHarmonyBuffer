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

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.PyrroliteGas.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.QI_TAI_ZHUO_TONG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.QI_TAI_HE_TONG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.QI_TAI_CHI_TONG.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.ZHONG_XI_RANG_QI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.XI_RANG_QI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.Acridgen.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.SUAN_QI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.Aquagen.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.SHUI_ZHENG_QI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidInputs(
                EOHBMaterialPool.Inergen.getFluidOrGas(1000)
            )
            .itemOutputs(
                new ItemStack(ItemLoader.ChiTongGasTank, 1, EnumBottleFluid.DUO_QI.meta)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(FillingUnit);
    }
}
