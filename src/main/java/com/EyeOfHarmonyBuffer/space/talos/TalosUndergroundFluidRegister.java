package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import gregtech.api.objects.GTUODimensionList;
import gregtech.api.util.GTConfig;
import gregtech.GTMod;

/**
 * 塔罗斯维度的 GT 地下流体田注册（电子探矿仪可扫到的虚拟流体）。
 * 写入 GT 的 UndergroundFluids.cfg（undergroundfluid 分类）并重新解析，
 * 之后 UndergroundOil 会在对应维度按区块生成可抽取的流体田。
 * 放在服务器启动阶段执行，确保 BartWorks 的气体流体已注册完成。
 */
public final class TalosUndergroundFluidRegister {

    private TalosUndergroundFluidRegister() {}

    public static void register() {
        GTUODimensionList uo = GTMod.proxy.mUndergroundOil;
        String dimKey = Integer.toString(RegisterDimensions.ID_TALOS2_DIM);

        // 用运行时实际的流体注册名，避免语言环境导致的名字漂移。
        String fluidName = EOHBMaterialPool.HighEnergyGas.getFluidOrGas(1)
            .getFluid()
            .getName();

        // 维度名/流体名/最小量/最大量/权重/单次衰减量
        uo.SetConfigValues("talos", dimKey, "highenergygas", fluidName, 0, 400, 100, 5);
        uo.save();
        // 重新解析配置，把刚写入的塔罗斯流体田挂进运行时维度表。
        uo.getConfig(GTConfig.undergroundFluidsFile, "undergroundfluid");
    }
}
