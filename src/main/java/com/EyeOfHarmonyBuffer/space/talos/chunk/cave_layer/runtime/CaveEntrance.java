package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/**
 * 地表入口：从真实洞穴节点（基座）向上延伸的一条通道到地表开口。
 *
 * 入口不再是「贴近地表的独立节点」，而是：
 *   - 基座 = 某个真实洞穴网络节点（backbone / normal），位于洞穴带内；
 *   - 通道 = 从基座向上延伸到地表开口的通道（垂直或斜向）；
 *   - 基座本身是网络节点 → 入口 100% 连通、无缝隙、不会被塌方填死。
 *
 * 类型：
 *   - TYPE_FUNNEL   锥形开阔洞口（地表大洞，向下收窄，直连基座）；
 *   - TYPE_RAMP     倾斜坡道隧道（地表斜洞，从基座斜向延伸上来）；
 *   - TYPE_SHAFT    加宽竖井（垂直大井，直连基座）；
 *   - TYPE_SINKHOLE 天坑（较小垂直入口，独立类型）。
 *
 * x / z = 地表开口列（装饰碎石环、/talcave TP 用它）；
 * y     = 基座深度（通道底部 = 真实节点的 y，雕刻时从 y 一路挖到该列地表）；
 * baseX / baseZ = 基座节点水平位置（垂直类型与 x/z 相同；坡道时地表开口偏移）。
 * dirX / dirZ 仅坡道使用（基座 → 地表开口的水平方向，单位向量）。
 * 地表高度是地形链产物，通道顶由雕刻 pass 按实际列地表开凿。
 */
public final class CaveEntrance {

    public static final int TYPE_FUNNEL = 0;
    public static final int TYPE_RAMP = 1;
    public static final int TYPE_SHAFT = 2;
    public static final int TYPE_SINKHOLE = 3;

    /** 地表开口列 x / z。 */
    public final int x;
    public final int z;
    /** 基座深度（通道底部 y = 真实洞穴节点 y）。 */
    public final int y;
    /** 基座节点水平位置（垂直类型与 x/z 相同；坡道时地表开口沿 dir 偏移）。 */
    public final int baseX;
    public final int baseZ;
    /** 地表开口处的高度（地形链 surfaceD 取整；坡道轴线顶端用，雕刻时与列顶一致）。 */
    public final int surfaceY;
    public final int radius;
    public final int type;
    /** 坡道的基座 → 地表开口方向（单位向量）；其它类型为 0。 */
    public final double dirX;
    public final double dirZ;

    public CaveEntrance(int x, int z, int y, int radius, boolean sinkhole) {
        this(x, z, y, radius, sinkhole ? TYPE_SINKHOLE : TYPE_FUNNEL, 0.0, 0.0);
    }

    public CaveEntrance(int x, int z, int y, int radius,
                        int type, double dirX, double dirZ) {
        this(x, z, y, x, z, 0, radius, type, dirX, dirZ);
    }

    public CaveEntrance(int x, int z, int y,
                        int baseX, int baseZ,
                        int radius, int type, double dirX, double dirZ) {
        this(x, z, y, baseX, baseZ, 0, radius, type, dirX, dirZ);
    }

    public CaveEntrance(int x, int z, int y,
                        int baseX, int baseZ, int surfaceY,
                        int radius, int type, double dirX, double dirZ) {
        this.x = x;
        this.z = z;
        this.y = y;
        this.baseX = baseX;
        this.baseZ = baseZ;
        this.surfaceY = surfaceY;
        this.radius = radius;
        this.type = type;
        this.dirX = dirX;
        this.dirZ = dirZ;
    }

    public boolean isSinkhole() {
        return type == TYPE_SINKHOLE;
    }

    /** 人类可读类型名（调试 / 指令用）。 */
    public static String typeName(int type) {
        switch (type) {
            case TYPE_FUNNEL:
                return "锥形漏斗";
            case TYPE_RAMP:
                return "倾斜坡道";
            case TYPE_SHAFT:
                return "加宽竖井";
            case TYPE_SINKHOLE:
                return "天坑";
            default:
                return "未知";
        }
    }
}
