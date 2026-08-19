package com.EyeOfHarmonyBuffer.client.rbmk;

/** 全息面板共享状态（PoC：单面板）。控件区域定义在 RbmkHoloPanel 的控件里。 */
public class RbmkHoloState {

    public static double rodPos = 62;
    public static boolean az5Pressed = false;

    /** 准星悬停信息（每 tick 更新）。 */
    public static boolean hovering = false;
    public static int hoverX = 0;
    public static int hoverY = 0;
}
