package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockRBMKRod;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

/**
 * RBMK 燃料通道成型判定（纯客户端，渲染用）。
 * <p>
 * 结构（自上而下，共 8 格）：
 * <pre>
 *   y+7  普通燃料管（FUEL_CHANNEL_TOP）
 *   y+1~y+6  反应堆内部石墨管道（FUEL_CHANNEL_PIPE）x6
 *   y    燃料管底座（FUEL_CHANNEL_BASE）
 * </pre>
 * 全部按序摆放即视为成型；成型后整根渲染 FuelTube 模型，方块本身不替换。
 */
@SideOnly(Side.CLIENT)
public final class RbmkFuelChannelHelper {

    private RbmkFuelChannelHelper() {
    }

    /**
     * @return 若 (x,y,z) 属于一个已成型燃料通道，返回该通道基座（底部）的 Y；否则返回 -1。
     */
    public static int channelBottom(IBlockAccess world, int x, int y, int z) {
        BlockRBMKRod.Role role = roleOf(world, x, y, z);
        if (role == null) {
            return -1;
        }
        int candidate;
        switch (role) {
            case FUEL_CHANNEL_BASE:
                candidate = y;
                break;
            case FUEL_CHANNEL_TOP:
                candidate = y - 7;
                break;
            case FUEL_CHANNEL_PIPE: {
                int yy = y;
                while (yy > 0 && roleOf(world, x, yy - 1, z) == BlockRBMKRod.Role.FUEL_CHANNEL_PIPE) {
                    yy--;
                }
                candidate = yy - 1; // 最下方石墨管道再往下一格，必须是底座
                break;
            }
            default:
                return -1;
        }
        return isChannel(world, x, candidate, z) ? candidate : -1;
    }

    private static boolean isChannel(IBlockAccess world, int x, int by, int z) {
        if (roleOf(world, x, by, z) != BlockRBMKRod.Role.FUEL_CHANNEL_BASE) {
            return false;
        }
        for (int i = 1; i <= 6; i++) {
            if (roleOf(world, x, by + i, z) != BlockRBMKRod.Role.FUEL_CHANNEL_PIPE) {
                return false;
            }
        }
        return roleOf(world, x, by + 7, z) == BlockRBMKRod.Role.FUEL_CHANNEL_TOP;
    }

    private static BlockRBMKRod.Role roleOf(IBlockAccess world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        return (b instanceof BlockRBMKRod) ? ((BlockRBMKRod) b).getRole() : null;
    }
}
