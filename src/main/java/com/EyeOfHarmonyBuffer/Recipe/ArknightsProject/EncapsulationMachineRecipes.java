package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.EncapsulationMachine;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class EncapsulationMachineRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingLingJian.get(5),
                GTCMItemList.YuanShiFenMo.get(10)
            )
            .itemOutputs(
                GTCMItemList.DiRongGuDiDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingLingJian.get(5),
                GTCMItemList.TongHuaGuanMuFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.GongYeBaoZhaWu.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TieZhiLingJian.get(10),
                GTCMItemList.YuanShiFenMo.get(15)
            )
            .itemOutputs(
                GTCMItemList.ZhongRongGuDiDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GangZhiLingJian.get(10),
                GTCMItemList.ZhiMiYuanShiFenMo.get(15)
            )
            .itemOutputs(
                GTCMItemList.GaoRongGuDiDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.XiRang.get(5),
                GTCMItemList.ZhiMiYuanShiFenMo.get(15)
            )
            .itemOutputs(
                GTCMItemList.DiRongXiRangDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.RangJing.get(5),
                GTCMItemList.ZhiMiYuanShiFenMo.get(20)
            )
            .itemOutputs(
                GTCMItemList.ZhongRongWuLingDianChi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TieZhiLingJian.get(10),
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 5, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.YaZhenZhenJi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongLingJian.get(10),
                new ItemStack(GTCMItemList.ChiTongFluidBottle.getItem(), 5, EnumBottleFluid.YA_ZHEN_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.YouZhiYaZhenZhenJi.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongLingJian.get(10),
                new ItemStack(GTCMItemList.LanTieFluidBottle.getItem(), 5, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.JinCaoRuanYin.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongLingJian.get(10),
                new ItemStack(GTCMItemList.ChiTongFluidBottle.getItem(), 5, EnumBottleFluid.JIN_CAO_RONG_YE.meta)
            )
            .itemOutputs(
                GTCMItemList.YouZhiJinCaoRuanYin.get(1)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongNaiYaPing.get(1),
                GTCMItemList.XiRang.get(1)
            )
            .itemOutputs(
                GTCMItemList.FenLiXin.get(2)
            )
            .eut(50000)
            .duration(10 * SECONDS)
            .addTo(EncapsulationMachine);
    }
}
