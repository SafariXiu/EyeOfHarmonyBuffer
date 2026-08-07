package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.SeparatingUnit;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class SeparatingUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .itemOutputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidOutputs(
                Materials.Water.getFluid(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .itemOutputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 1, EnumBottleFluid.YE_HUA_ZHONG_XI_RANG.meta)
            )
            .itemOutputs(
                GTCMItemList.LanTiePing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidHeavyXiranite.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ZiJingZhiFluidBottle.getItem(), 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .itemOutputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidOutputs(
                Materials.Water.getFluid(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ZiJingZhiFluidBottle.getItem(), 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ZiJingZhiFluidBottle.getItem(), 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ZiJingZhiFluidBottle.getItem(), 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .itemOutputs(
                GTCMItemList.ZiJingZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GangZhiFluidBottle.getItem(), 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .itemOutputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidOutputs(
                Materials.Water.getFluid(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GangZhiFluidBottle.getItem(), 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GangZhiFluidBottle.getItem(), 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GangZhiFluidBottle.getItem(), 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .itemOutputs(
                GTCMItemList.GangZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GaoJingFluidBottle.getItem(), 1, EnumBottleFluid.QING_SHUI.meta)
            )
            .itemOutputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidOutputs(
                Materials.Water.getFluid(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GaoJingFluidBottle.getItem(), 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GaoJingFluidBottle.getItem(), 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.GaoJingFluidBottle.getItem(), 1, EnumBottleFluid.YE_HUA_XI_RANG.meta)
            )
            .itemOutputs(
                GTCMItemList.GaoJingZhiPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.LiquidXiranite.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongFluidBottle.getItem(), 1, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.YaZhenSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongFluidBottle.getItem(), 1, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.JinCaoSolution.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.QI_TAI_ZHUO_TONG.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.PyrroliteGas.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.QI_TAI_HE_TONG.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.HetoniteGas.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.QI_TAI_CHI_TONG.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.CupriumGas.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.ZHONG_XI_RANG_QI.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.HeavyXiragen.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.XI_RANG_QI.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Xiragen.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.SUAN_QI.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Acridgen.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.SHUI_ZHENG_QI.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Aquagen.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(GTCMItemList.ChiTongGasTank.getItem(), 1, EnumBottleFluid.DUO_QI.meta)
            )
            .itemOutputs(
                GTCMItemList.ChiTongNaiYaPing.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Inergen.getFluidOrGas(1000)
            )
            .eut(20000)
            .duration(10 * SECONDS)
            .addTo(SeparatingUnit);
    }
}
