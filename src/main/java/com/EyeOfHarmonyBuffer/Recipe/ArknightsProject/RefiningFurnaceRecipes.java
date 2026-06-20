package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.RefiningFurnace;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class RefiningFurnaceRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiKuang.get(1)
            )
            .itemOutputs(
                GTCMItemList.JingTiWaiKe.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieKuai.get(1)
            )
            .itemOutputs(
                GTCMItemList.GangKuai.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZiJingKuang.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZiJingXianWei.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiMiJingTiFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.MiZhiJingTi.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiMiLanTieFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.GangKuai.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaoJingFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.GaoJingXianWei.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiMiTanFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.WenDingTanKuai.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhiMiYuanShiFenMo.get(1)
            )
            .itemOutputs(
                GTCMItemList.ZhiMiJingTiFenMo.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QiaoHua.get(1)
            )
            .itemOutputs(
                GTCMItemList.TanKuai.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LanTieKuang.get(1)
            )
            .itemOutputs(
                GTCMItemList.LanTieKuai.get(1)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YaZhen.get(1)
            )
            .itemOutputs(
                GTCMItemList.TanKuai.get(2)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ChiTongKuang.get(1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                GTCMItemList.ChiTongKuai.get(1)
            )
            .fluidOutputs(
                EOHBMaterialPool.Sewage.getFluidOrGas(1000)
            )
            .eut(5000)
            .duration(10 * SECONDS)
            .addTo(RefiningFurnace);
    }
}
