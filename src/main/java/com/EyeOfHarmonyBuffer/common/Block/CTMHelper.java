package com.EyeOfHarmonyBuffer.common.Block;

import net.minecraft.world.IBlockAccess;

/**
 * 连接纹理（CTM）公共工具：完整 47 格 CTM。
 *
 * <p>核心为 {@link #getNeighborBits} + {@link #NEIGHBOR_MAP}：
 * 8 方向邻接组合（0~255 位图）直接映射到 47 张连接贴图（_conn_0..46，mcpatcher/Angelica
 * 标准布局，含角吸收），不再使用旧的 16 格 4 方向连接掩码。
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
     * 从 8 位邻接组合中提取 4 向正交掩码（贴图系：U=1,D=2,L=4,R=8）。
     * 位布局见 {@link #NEIGHBOR_MAP}。
     */
    public static int getOrthoFromBits(int bits) {
        return ((bits & 64) != 0 ? 1 : 0)   // up
             | ((bits & 4) != 0 ? 2 : 0)    // down
             | ((bits & 1) != 0 ? 4 : 0)    // left
             | ((bits & 16) != 0 ? 8 : 0);  // right
    }

    /**
     * 从 8 位邻接组合中提取 4 个对角位（TL=1,TR=2,BL=4,BR=8，贴图系）。
     * 仅当两条相邻边与该对角都连接时置位（角吸收前提）。
     * 位布局见 {@link #NEIGHBOR_MAP}。
     */
    public static int getCornersFromBits(int bits) {
        int tl = (bits & 64) != 0 && (bits & 1) != 0 && (bits & 128) != 0 ? 1 : 0;
        int tr = (bits & 64) != 0 && (bits & 16) != 0 && (bits & 32) != 0 ? 2 : 0;
        int bl = (bits & 4) != 0 && (bits & 1) != 0 && (bits & 2) != 0 ? 4 : 0;
        int br = (bits & 4) != 0 && (bits & 16) != 0 && (bits & 8) != 0 ? 8 : 0;
        return tl | tr | bl | br;
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
     * 位布局与偏移完全对照 Angelica {@code BlockOrientation.NEIGHBOR_OFFSET}：
     * <pre>
     *  7 6 5
     *  0 * 4
     *  1 2 3
     * </pre>
     * bit0=左, bit1=左下, bit2=下, bit3=右下, bit4=右, bit5=右上, bit6=上, bit7=左上。
     * 面偏移（含底面 180° 旋转、北/东面水平镜像）：
     *   BOTTOM: L=EAST +X, D=SOUTH +Z, R=WEST -X, U=NORTH -Z
     *   TOP   : L=WEST -X, D=SOUTH +Z, R=EAST +X, U=NORTH -Z
     *   NORTH : L=EAST +X, D=BOTTOM -Y, R=WEST -X, U=TOP +Y
     *   SOUTH : L=WEST -X, D=BOTTOM -Y, R=EAST +X, U=TOP +Y
     *   WEST  : L=NORTH -Z, D=BOTTOM -Y, R=SOUTH +Z, U=TOP +Y
     *   EAST  : L=SOUTH +Z, D=BOTTOM -Y, R=NORTH -Z, U=TOP +Y
     *
     * @param side    Forge 侧面序数（0=底,1=顶,2=北,3=南,4=西,5=东）
     * @param checker 连接判断
     * @return 8 位邻接掩码
     */
    public static int getNeighborBits(IBlockAccess world, int x, int y, int z, int side,
        ConnectionChecker checker) {
        // 8 邻偏移表 [面][方向 bit][坐标]，顺序 [左,左下,下,右下,右,右上,上,左上]
        final int[][][] OFFSET = new int[][][] {
            // BOTTOM(0)：与 TOP 相同（vanilla renderFaceYNeg 默认 UV：U 随 +X、V 随 +Z，
            // 即贴图 上=-Z 左=-X —— 与顶面完全一致，无旋转无镜像）
            { {-1,0,0}, {-1,0,1}, {0,0,1}, {1,0,1}, {1,0,0}, {1,0,-1}, {0,0,-1}, {-1,0,-1} },
            // TOP(1): L=-X U=-Z R=+X D=+Z
            { {-1,0,0}, {-1,0,1}, {0,0,1}, {1,0,1}, {1,0,0}, {1,0,-1}, {0,0,-1}, {-1,0,-1} },
            // NORTH(2): L=+X U=+Y R=-X D=-Y
            { {1,0,0}, {1,-1,0}, {0,-1,0}, {-1,-1,0}, {-1,0,0}, {-1,1,0}, {0,1,0}, {1,1,0} },
            // SOUTH(3): L=-X U=+Y R=+X D=-Y
            { {-1,0,0}, {-1,-1,0}, {0,-1,0}, {1,-1,0}, {1,0,0}, {1,1,0}, {0,1,0}, {-1,1,0} },
            // WEST(4): L=-Z U=+Y R=+Z D=-Y
            { {0,0,-1}, {0,-1,-1}, {0,-1,0}, {0,-1,1}, {0,0,1}, {0,1,1}, {0,1,0}, {0,1,-1} },
            // EAST(5): L=+Z U=+Y R=-Z D=-Y
            { {0,0,1}, {0,-1,1}, {0,-1,0}, {0,-1,-1}, {0,0,-1}, {0,1,-1}, {0,1,0}, {0,1,1} },
        };
        if (side < 0 || side > 5) {
            return 0;
        }
        int bits = 0;
        int[][] off = OFFSET[side];
        for (int b = 0; b < 8; b++) {
            if (checker.isConnected(world, x + off[b][0], y + off[b][1], z + off[b][2])) {
                bits |= (1 << b);
            }
        }
        return bits;
    }
}
