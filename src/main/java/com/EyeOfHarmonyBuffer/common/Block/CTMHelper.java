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

    /**
     * 47 格全连接 CTM 的邻接位相对布局（与 Angelica/mcpatcher 的 neighborMap 一致）：
     * <pre>
     *  128  64  32        &lt;- 左上 上 右上
     *    1   *  16        &lt;- 左      右
     *    2   4   8        &lt;- 左下 下 右下
     * </pre>
     * 下标 = 8 位组合（0~255），值为 47 tile 索引（0~46，对应 RBMK_Schema_E_conn_&lt;n&gt;.png）。
     */
    public static final int[] NEIGHBOR_MAP = new int[] {
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        16, 18, 16, 18, 6, 46, 6, 21, 16, 18, 16, 18, 28, 9, 28, 22,
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        37, 40, 37, 40, 30, 8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        16, 42, 16, 42, 6, 20, 6, 10, 16, 42, 16, 42, 28, 35, 28, 44,
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38, 25, 33, 25, 26 };

    /**
     * 计算 (x,y,z) 处方块 side 面在贴图上的 8 方向邻接位组合（0~255）。
     * 位布局见 {@link #NEIGHBOR_MAP}（面镜像规则与 {@link #getConnectionMask} 一致）。
     *
     * @param side    Forge 侧面序数（0=底,1=顶,2=北,3=南,4=西,5=东）
     * @param checker 连接判断
     * @return 8 位邻接掩码（bit0=左, bit1=左下, bit2=下, bit3=右下, bit4=右, bit5=右上, bit6=上, bit7=左上）
     */
    public static int getNeighborBits(IBlockAccess world, int x, int y, int z, int side,
        ConnectionChecker checker) {
        boolean up = checker.isConnected(world, x, y + 1, z);
        boolean down = checker.isConnected(world, x, y - 1, z);
        boolean north = checker.isConnected(world, x, y, z - 1);
        boolean south = checker.isConnected(world, x, y, z + 1);
        boolean west = checker.isConnected(world, x - 1, y, z);
        boolean east = checker.isConnected(world, x + 1, y, z);
        boolean uL, uR, dL, dR; // 贴图系四个对角
        switch (side) {
            case 0: // 底面：贴图 左=西 右=东 上=北 下=南
            case 1: // 顶面：不镜像，与底面一致
                uL = checker.isConnected(world, x - 1, y, z - 1);
                uR = checker.isConnected(world, x + 1, y, z - 1);
                dL = checker.isConnected(world, x - 1, y, z + 1);
                dR = checker.isConnected(world, x + 1, y, z + 1);
                break;
            case 2: // 北面（水平镜像）：左=东 右=西 上=上 下=下
                uL = checker.isConnected(world, x + 1, y + 1, z);
                uR = checker.isConnected(world, x - 1, y + 1, z);
                dL = checker.isConnected(world, x + 1, y - 1, z);
                dR = checker.isConnected(world, x - 1, y - 1, z);
                break;
            case 3: // 南面：左=西 右=东 上=上 下=下
                uL = checker.isConnected(world, x - 1, y + 1, z);
                uR = checker.isConnected(world, x + 1, y + 1, z);
                dL = checker.isConnected(world, x - 1, y - 1, z);
                dR = checker.isConnected(world, x + 1, y - 1, z);
                break;
            case 4: // 西面：左=北 右=南 上=上 下=下
                uL = checker.isConnected(world, x, y + 1, z - 1);
                uR = checker.isConnected(world, x, y + 1, z + 1);
                dL = checker.isConnected(world, x, y - 1, z - 1);
                dR = checker.isConnected(world, x, y - 1, z + 1);
                break;
            default: // 东面：左=南 右=北 上=上 下=下
                uL = checker.isConnected(world, x, y + 1, z + 1);
                uR = checker.isConnected(world, x, y + 1, z - 1);
                dL = checker.isConnected(world, x, y - 1, z + 1);
                dR = checker.isConnected(world, x, y - 1, z - 1);
                break;
        }
        return (west ? 1 : 0) | (dL ? 2 : 0) | (down ? 4 : 0) | (dR ? 8 : 0)
            | (east ? 16 : 0) | (uR ? 32 : 0) | (up ? 64 : 0) | (uL ? 128 : 0);
    }
}
