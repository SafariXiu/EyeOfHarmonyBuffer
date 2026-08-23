package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockRBMKRod;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.world.IBlockAccess;

/**
 * RBMK 通道渲染辅助（纯客户端）。
 * <p>
 * 通道结构判定已下沉到公共类 {@link BlockRBMKRod#channelBottom}（服务端物理代码亦可使用），
 * 这里只做渲染侧的薄封装。
 */
@SideOnly(Side.CLIENT)
public final class RbmkFuelChannelHelper {

    private RbmkFuelChannelHelper() {
    }

    /** @return 若 (x,y,z) 属于一个已成型通道，返回该通道基座（底部）的 Y；否则返回 -1。 */
    public static int channelBottom(IBlockAccess world, int x, int y, int z) {
        return BlockRBMKRod.channelBottom(world, x, y, z);
    }

    /** 是否为合法的通道顶部（普通燃料管 / 各类控制棒）。 */
    public static boolean isChannelTop(BlockRBMKRod.Role role) {
        return BlockRBMKRod.isChannelTop(role);
    }
}
