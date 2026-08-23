package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockCasingsDysonFlow;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockCasingsEOH;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import net.minecraft.item.ItemStack;

/**
 * EOHB 机械方块（舱室方块）统一注册与取用。
 * 机器结构请从这里拿方块，对标 GT 的 GregTechAPI.sBlockCasings*。
 */
public final class EOHBMachineBlocks {

    public static BlockCasingsEOH sBlockCasingsEOH;

    /** 戴森系列导流外壳：meta 0 = 核心信息导流，meta 1 = 发射中心能量流转 */
    public static BlockCasingsDysonFlow sBlockCasingsDysonFlow;

    private EOHBMachineBlocks() {
    }

    public static void registerCasings() {
        sBlockCasingsEOH = new BlockCasingsEOH();

        GTCMItemList.XiRangWaiKeCasing.set(
            new ItemStack(sBlockCasingsEOH, 1, BlockCasingsEOH.META_XIRANG_WAIKE)
        );

        GTCMItemList.ZhongXiRangWaiKeCasing.set(
            new ItemStack(sBlockCasingsEOH, 1, BlockCasingsEOH.META_ZHONG_XIRANG_WAIKE)
        );

        sBlockCasingsDysonFlow = new BlockCasingsDysonFlow();

        GTCMItemList.DysonCoreInfoFlowCasing.set(
            new ItemStack(sBlockCasingsDysonFlow, 1, BlockCasingsDysonFlow.META_DYSON_FLOW)
        );

        GTCMItemList.DysonLaunchCenterEnergyFlowCasing.set(
            new ItemStack(sBlockCasingsDysonFlow, 1, BlockCasingsDysonFlow.META_DYSON_LAUNCH_FLOW)
        );
    }
}
