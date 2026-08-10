package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockCasingsEOH;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import net.minecraft.item.ItemStack;

/**
 * EOHB 机械方块（舱室方块）统一注册与取用。
 * 机器结构请从这里拿方块，对标 GT 的 GregTechAPI.sBlockCasings*。
 */
public final class EOHBMachineBlocks {

    public static BlockCasingsEOH sBlockCasingsEOH;

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
    }
}
