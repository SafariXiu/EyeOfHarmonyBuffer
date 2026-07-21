package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.GearingUnit;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class GearingUnitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKe.get(5),
                GTCMItemList.ZiJingXianWei.get(5)
            )
            .itemOutputs(
                GTCMItemList.ZiJingZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKe.get(10),
                GTCMItemList.LanTieKuai.get(10)
            )
            .itemOutputs(
                GTCMItemList.LanTieZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKe.get(10),
                GTCMItemList.GaoJingXianWei.get(10)
            )
            .itemOutputs(
                GTCMItemList.GaoJingZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKe.get(10),
                GTCMItemList.XiRang.get(10)
            )
            .itemOutputs(
                GTCMItemList.XiRangZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongLingJian.get(10),
                GTCMItemList.XiRang.get(10)
            )
            .itemOutputs(
                GTCMItemList.ChiTongZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HeTongLingJian.get(2),
                GTCMItemList.ZhongXiRang.get(2)
            )
            .itemOutputs(
                GTCMItemList.HeTongZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuoTongLingJian.get(1),
                GTCMItemList.ZhongXiRang.get(2)
            )
            .itemOutputs(
                GTCMItemList.ZhuoTongZhuangBeiYuanJian.get(1)
            )
            .eut(10000)
            .duration(10 * SECONDS)
            .addTo(GearingUnit);
    }
}
