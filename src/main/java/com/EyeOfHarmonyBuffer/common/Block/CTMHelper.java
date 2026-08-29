package com.EyeOfHarmonyBuffer.common.Block;

import net.minecraft.world.IBlockAccess;

/**
 * 连接纹理（CTM）公共工具：4 方向连接掩码计算 + 面方向到贴图边的映射。
 *
 * <p>掩码位定义（相对贴图本身）：
 * bit0 = 上（贴图顶部边）已连接
 * bit1 = 下（贴图底部边）已连接
 * bit2 = 左（贴图左边）已连接
 * bit3 = 右（贴图右边）已连接
 *
 * <p>面的世界方向 -&gt; 贴图边 的映射由 RenderBlocks 各面 UV 顶点顺序推导得出
 * （注意 vanilla 的北面(side=2)和东面(side=5)贴图是水平镜像的）。
 */
public final class CTMHelper {

    private CTMHelper() {
    }

    /** 连接判断回调：world 中 (x,y,z) 处是否为"同类可连接"方块 */
    public interface ConnectionChecker {

        boolean isConnected(IBlockAccess world, int x, int y, int z);
    }

    /**
     * 计算 (x,y,z) 处方块 side 面在贴图上的 4 方向连接掩码（0~15）。
     *
     * @param side    Forge 侧面序数（0=底,1=顶,2=北,3=南,4=西,5=东）
     * @param checker 连接判断
     */
    public static int getConnectionMask(IBlockAccess world, int x, int y, int z, int side,
        ConnectionChecker checker) {
        boolean north = checker.isConnected(world, x, y, z - 1); // -Z
        boolean south = checker.isConnected(world, x, y, z + 1); // +Z
        boolean west = checker.isConnected(world, x - 1, y, z);  // -X
        boolean east = checker.isConnected(world, x + 1, y, z);  // +X
        boolean up = checker.isConnected(world, x, y + 1, z);    // +Y
        boolean down = checker.isConnected(world, x, y - 1, z);  // -Y

        switch (side) {
            case 0: // 底面：贴图上=北(-Z) 下=南(+Z) 左=西(-X) 右=东(+X)
                return mask(north, south, west, east);
            case 1: // 顶面（不镜像，与底面一致）：贴图上=北(-Z) 下=南(+Z) 左=西(-X) 右=东(+X)
                return mask(north, south, west, east);
            case 2: // 北面（水平镜像）：左=东(+X) 右=西(-X)
                return mask(up, down, east, west);
            case 3: // 南面：左=西(-X) 右=东(+X)
                return mask(up, down, west, east);
            case 4: // 西面：左=北(-Z) 右=南(+Z)
                return mask(up, down, north, south);
            case 5: // 东面：左=南(+Z) 右=北(-Z)
                return mask(up, down, south, north);
            default:
                return 0;
        }
    }

    private static int mask(boolean up, boolean down, boolean left, boolean right) {
        return (up ? 1 : 0) | (down ? 2 : 0) | (left ? 4 : 0) | (right ? 8 : 0);
    }

    /**
     * 计算 (x,y,z) 处方块 side 面在贴图上的 4 个对角连接位（0~15）。
     *
     * <p>位定义（贴图方位）：bit0=左上, bit1=右上, bit2=左下, bit3=右下，
     * 与 {@link #getConnectionMask} 的上下左右映射同源（对角 = 上下 x 左右组合）。
     * 用于"角吸收"判定：角并入屏幕（变黑）仅在两条相邻边都连接且对角也存在方块时发生。
     */
    public static int getDiagonalMask(IBlockAccess world, int x, int y, int z, int side,
        ConnectionChecker checker) {
        boolean tl;
        boolean tr;
        boolean bl;
        boolean br;
        switch (side) {
            case 0: // 底面：贴图上=北(-Z) 左=西(-X)
            case 1: // 顶面：不镜像，与底面一致
                tl = checker.isConnected(world, x - 1, y, z - 1);
                tr = checker.isConnected(world, x + 1, y, z - 1);
                bl = checker.isConnected(world, x - 1, y, z + 1);
                br = checker.isConnected(world, x + 1, y, z + 1);
                break;
            case 2: // 北面（水平镜像）：左=东(+X) 右=西(-X)
                tl = checker.isConnected(world, x + 1, y + 1, z);
                tr = checker.isConnected(world, x - 1, y + 1, z);
                bl = checker.isConnected(world, x + 1, y - 1, z);
                br = checker.isConnected(world, x - 1, y - 1, z);
                break;
            case 3: // 南面：左=西(-X) 右=东(+X)
                tl = checker.isConnected(world, x - 1, y + 1, z);
                tr = checker.isConnected(world, x + 1, y + 1, z);
                bl = checker.isConnected(world, x - 1, y - 1, z);
                br = checker.isConnected(world, x + 1, y - 1, z);
                break;
            case 4: // 西面：左=北(-Z) 右=南(+Z)
                tl = checker.isConnected(world, x, y + 1, z - 1);
                tr = checker.isConnected(world, x, y + 1, z + 1);
                bl = checker.isConnected(world, x, y - 1, z - 1);
                br = checker.isConnected(world, x, y - 1, z + 1);
                break;
            default: // 东面：左=南(+Z) 右=北(-Z)
                tl = checker.isConnected(world, x, y + 1, z + 1);
                tr = checker.isConnected(world, x, y + 1, z - 1);
                bl = checker.isConnected(world, x, y - 1, z + 1);
                br = checker.isConnected(world, x, y - 1, z - 1);
                break;
        }
        return (tl ? 1 : 0) | (tr ? 2 : 0) | (bl ? 4 : 0) | (br ? 8 : 0);
    }
}
