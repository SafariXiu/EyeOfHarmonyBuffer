package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.entity.Entity;

/**
 * 全息面板共享状态（PoC）。
 * 控件区域定义在 RbmkHoloPanel 的控件里。
 */
public class RbmkHoloState {

    public static double rodPos = 62;
    public static boolean az5Pressed = false;

    /** 面板是否已激活（左键点击后）。未激活时控件不响应，避免误触。 */
    public static boolean activated = false;

    /** 准星悬停信息（每 tick 更新）。 */
    public static boolean hovering = false;
    public static int hoverX = 0;
    public static int hoverY = 0;

    /** 当前准星命中的全息屏实体（viewType 0 控制面板 / 1 堆芯大屏），未命中为 null。 */
    public static Entity hoveredEntity = null;
}
