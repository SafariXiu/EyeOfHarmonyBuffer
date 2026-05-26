package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.Grinder;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class GrinderRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhiMiLanTieFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.GaoJingFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhiMiYuanShiFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TanFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhiMiTanFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JingTiWaiKeFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhiMiJingTiFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QiaoHuaFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.XiMoQiaoHuaFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GanShiFenMo.get(2),
                GTCMItemList.ShaYeFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.XiMoGanShiFenMo.get(1)
            )
            .eut(50000)
            .duration(5 * SECONDS)
            .addTo(Grinder);
    }
}
