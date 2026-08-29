package com.EyeOfHarmonyBuffer.client.holo;

/**
 * 世界 3D 模型展示（机器绑定面板的同款锚点/朝向机制，但渲染的不是 2D 屏而是真 3D 模型）。
 * <p>
 * 由 {@link HoloRender#renderModel3D} 在模型局部系调用：模型原点 = 面板锚点，
 * 局部坐标轴与屏一致（x=right、y=向下、z=法向朝观察侧）。调用方已设置矩阵
 * （translate + 朝向 + scale，含自转）与 GL 状态保险（pushAttrib/popAttrib）。
 * <p>
 * 实现类在 draw 内部自行完成：世界 3D 几何绘制、自转动画、数据源读取。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public interface HoloModel3D {

    /**
     * 在世界中绘制模型（调用方已套好模型局部矩阵与 GL 保险）。
     *
     * @param ticks 动画时钟（世界 tick，用于自转等动画）
     * @param vx vy vz  观察者在模型局部系（x=right、y=向下、z=法向朝观察者）中的单位方向。
     *                  若渲染器内部有自转（glRotatef），需用自转补偿得到顶点系视线方向。
     * @param scale 模型整体缩放倍率（1 = 默认尺寸；面板配置 modelScale 透传）
     * @param opacity 模型不透明度（0~1；面板配置 modelOpacity 透传）
     */
    void draw(double ticks, float vx, float vy, float vz, float scale, float opacity);
}
