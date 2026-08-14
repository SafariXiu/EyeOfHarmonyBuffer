package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.DysonManufacturing;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Recipe_DysonManufacturing_Tooltip_00;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.DysonMachineConfig;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;

import gregtech.api.enums.GTValues;

/**
 * 戴森组件制造配方：与源石发电机同款的「假配方」模式——真实成本不走配方（制造模块按
 * {@link DysonMachineConfig} 的每 tick Orundum 常量 × 时长 × 并行结算），
 * 这里的 eut 固定为 0，真实数值通过 {@code setNEIDesc} 展示在 NEI 上
 * （框架 50 亿/t 超出配方 int 上限，因此必须用字符串描述）。
 */
public class DysonManufacturingRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        // 云组件：64 源石 → 64 云组件，基准 30 秒，每 tick 10 亿 Orundum
        GTValues.RA.stdBuilder()
            .itemInputs(GTCMItemList.YuanShi.get(64))
            .itemOutputs(GTCMItemList.DysonCloudComponent.get(64))
            .eut(0)
            .duration(30 * SECONDS)
            .setNEIDesc(EOHB_Recipe_DysonManufacturing_Tooltip_00 + DysonMachineConfig.CLOUD_COMPONENT_ORUNDUM_PER_TICK)
            .addTo(DysonManufacturing);

        // 框架组件：64 息壤 → 512 框架组件，基准 30 秒，每 tick 50 亿 Orundum
        GTValues.RA.stdBuilder()
            .itemInputs(GTCMItemList.XiRang.get(64))
            .itemOutputs(GTCMItemList.DysonFrameComponent.get(512))
            .eut(0)
            .duration(30 * SECONDS)
            .setNEIDesc(EOHB_Recipe_DysonManufacturing_Tooltip_00 + DysonMachineConfig.FRAME_COMPONENT_ORUNDUM_PER_TICK)
            .addTo(DysonManufacturing);
    }
}
