package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

/**
 * 机载全息屏的最终渲染实例：结构检查时（服务端）由 HoloPanelConfig 计算好，
 * 经 IMTERenderer 渲染通道同步给客户端，客户端拿到直接绘制（不做任何朝向数学）。
 *
 * <p>字段：世界坐标中心 (cx,cy,cz) + 朝向基向量 right/up/normal（单位向量，
 * 与 {@code HoloMath.Frame} 对应，HoloRender 直接使用）。
 */
public class HoloPanelInstance {

    /** 屏中心世界坐标（已含方块中心 0.5 偏移与三轴推动）。 */
    public double cx, cy, cz;
    /** 朝向基：right（屏右）、up（屏上）、normal（屏法向，指向观察者）。 */
    public float rx, ry, rz;
    public float ux, uy, uz;
    public float nx, ny, nz;

    public HoloPanelInstance() {
    }

    public HoloPanelInstance(double cx, double cy, double cz,
                             float rx, float ry, float rz,
                             float ux, float uy, float uz,
                             float nx, float ny, float nz) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.ux = ux;
        this.uy = uy;
        this.uz = uz;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    /** 内容是否完全相同（用于服务端快照版本比对）。 */
    public boolean sameAs(HoloPanelInstance o) {
        if (o == null) {
            return false;
        }
        return cx == o.cx && cy == o.cy && cz == o.cz
            && rx == o.rx && ry == o.ry && rz == o.rz
            && ux == o.ux && uy == o.uy && uz == o.uz
            && nx == o.nx && ny == o.ny && nz == o.nz;
    }
}
