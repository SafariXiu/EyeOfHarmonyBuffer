package com.EyeOfHarmonyBuffer.client.rbmk;

/**
 * 单个堆芯通道的数据模型（一个通道 = 一个 250mm 栅格位）。
 * 目前为模拟占位数据（温度/棒位按通道坐标确定性伪随机，随位置稳定）：
 * 机器接入后通过 RbmkCoreData.setChannelProvider 注入实时数据。
 */
public class RbmkChannel {

    public static final int GRAPHITE = 0;
    public static final int FUEL = 1;
    public static final int CONTROL_ROD = 2;
    public static final int SHORT_ROD = 3;
    public static final int AUTO_ROD = 4;
    public static final int LAR_ROD = 5;

    /** 通道类型（同 RbmkCoreData 图例：0-5）。 */
    public final int type;

    /** 通道温度（°C）。 */
    private final double temperature;

    /** 控制棒插入深度（0-100%）。无控制棒的通道为 -1。 */
    private final double rodDepth;

    public RbmkChannel(int type, long seed) {
        this.type = type;
        // 用 seed（坐标）做确定性伪随机：同一通道每次读都稳定
        long h = seed * 2654435761L;
        h ^= h >>> 13;
        double r = (h & 0xFFFF) / 65535.0;
        switch (type) {
            case FUEL:
                temperature = 600 + 500 * r;   // 燃料：600-1100°C
                rodDepth = -1;
                break;
            case CONTROL_ROD:
            case SHORT_ROD:
            case AUTO_ROD:
            case LAR_ROD:
                temperature = 280 + 220 * r;  // 冷却剂附近：280-500°C
                rodDepth = 10 + 90 * r;       // 0-100%
                break;
            case GRAPHITE:
            default:
                temperature = 650 + 200 * r;  // 石墨砌体：650-850°C
                rodDepth = -1;
                break;
        }
    }

    /** 通道温度（°C）。 */
    public double getTemperature() {
        return temperature;
    }

    /** 控制棒插入深度（0-100%）；无控制棒返回 -1。 */
    public double getRodDepth() {
        return rodDepth;
    }

    /** 是否为控制棒类通道（R/S/A/L）。 */
    public boolean hasRod() {
        return type >= CONTROL_ROD && type <= LAR_ROD;
    }
}
