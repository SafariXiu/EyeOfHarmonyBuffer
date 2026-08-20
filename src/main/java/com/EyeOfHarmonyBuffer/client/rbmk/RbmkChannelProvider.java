package com.EyeOfHarmonyBuffer.client.rbmk;

/**
 * 通道实时数据源（机器接入点）。
 * 反应堆机器接入后实现本接口，通过 RbmkCoreData.setChannelProvider 注入，
 * 大屏/面板即可显示真实温度、棒位等数据。
 */
public interface RbmkChannelProvider {

    /** 返回 (row, col) 通道的数据；越界或未知返回 null。 */
    RbmkChannel channel(int row, int col);
}
