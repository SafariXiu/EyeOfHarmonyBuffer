package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.entity.Entity;

/**
 * 全息屏跨屏共享状态（准星悬停信息）。由 HoloInteraction 每 tick 更新。
 * 各屏自己的业务状态（棒位/AZ-5/激活态等）都在各自的 HoloScreen 实例里，不在此共享。
 */
public class HoloState {

    /** 准星是否命中某块全息屏。 */
    public static boolean hovering = false;

    /** 命中屏的局部坐标（px/py，屏自身坐标系）。 */
    public static int hoverX = 0;
    public static int hoverY = 0;

    /** 当前准星命中的全息屏实体，未命中为 null。 */
    public static Entity hoveredEntity = null;
}
