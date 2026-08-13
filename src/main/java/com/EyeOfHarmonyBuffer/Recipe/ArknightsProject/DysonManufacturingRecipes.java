package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.DysonManufacturing;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;

import gregtech.api.enums.GTValues;

/** 戴森组件制造配方。实际 Orundum 成本见 {@link com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.DysonMachineConfig}，
 *  配方 eut 仅作 NEI 展示占位（50 亿/t 超出 int 上限，无法直接写在配方里）。 */
public class DysonManufacturingRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(GTCMItemList.YuanShi.get(64))
            .itemOutputs(GTCMItemList.DysonCloudComponent.get(64))
            .eut(1_000_000_000)
            .duration(30 * SECONDS)
            .addTo(DysonManufacturing);

        GTValues.RA.stdBuilder()
            .itemInputs(GTCMItemList.XiRang.get(64))
            .itemOutputs(GTCMItemList.DysonFrameComponent.get(512))
            .eut(1_000_000_000)
            .duration(30 * SECONDS)
            .addTo(DysonManufacturing);
    }
}
