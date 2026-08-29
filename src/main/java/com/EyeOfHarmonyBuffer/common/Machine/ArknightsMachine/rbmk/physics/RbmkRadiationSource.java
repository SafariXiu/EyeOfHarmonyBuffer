package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * 一个辐射源（一次爆炸）。存于维度 WorldSavedData，爆炸时写入。
 * 默认不随时间衰减；未来"石棺封堆"后接入衰减函数（覆写 attenuation）。
 */
public class RbmkRadiationSource {

    public final double x, y, z;
    /** 爆炸时刻（世界总时间 tick） */
    public final long explosionTime;

    public RbmkRadiationSource(double x, double y, double z, long explosionTime) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.explosionTime = explosionTime;
    }

    /**
     * 衰减因子（0~1，乘在强度上）。默认不衰减。
     * 后续石棺封堆后：返回一个随时间递减的值（如 exp(-k·(now-explosionTime))）。
     */
    public double attenuation(long currentTime) {
        return 1.0;
    }
}
