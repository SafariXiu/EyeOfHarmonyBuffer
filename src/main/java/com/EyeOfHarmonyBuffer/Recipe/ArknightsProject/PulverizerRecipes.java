package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Pulverizer;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class PulverizerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ShaYe.get(1)
            )
            .itemOutputs(
                GTCMItemList.ShaYeFenMo.get(3)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GanShi.get(1)
            )
            .itemOutputs(
                GTCMItemList.GanShiFenMo.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YaZhen.get(1)
            )
            .itemOutputs(
                GTCMItemList.YaZhenFenMo.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QiaoHua.get(1)
            )
            .itemOutputs(
                GTCMItemList.QiaoHuaFenMo.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinCao.get(1)
            )
            .itemOutputs(
                GTCMItemList.JinCaoFenMo.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKe.get(1)
            )
            .itemOutputs(
                GTCMItemList.JingTiWaiKeFenMo.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiKuang.get(1)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFenMo.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TanKuai.get(1)
            )
            .itemOutputs(
                GTCMItemList.TanFenMo.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingXianWei.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZiJingFenMo.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieKuai.get(1)
            )
            .itemOutputs(
                GTCMItemList.LanTieFenMo.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongHuaGuanMu.get(1)
            )
            .itemOutputs(
                GTCMItemList.TongHuaGuanMuFenMo.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(Pulverizer);
    }
}
