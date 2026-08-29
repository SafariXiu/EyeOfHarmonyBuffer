package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * 辐射防护来源。护甲/物品实现本接口，可注册到防护系统（护甲位穿戴检测）。
 * 高等级装备保护对应等级及以下（6 级装备护 6/5/4/3/2，进 7 级区仍无效）。
 */
public interface RadiationProtection {

    /** 返回该来源提供的防护等级（0 = 无防护） */
    int protectionLevel();
}
