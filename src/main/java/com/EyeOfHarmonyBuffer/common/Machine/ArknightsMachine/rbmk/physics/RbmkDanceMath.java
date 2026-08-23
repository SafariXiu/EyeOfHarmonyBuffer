package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

/**
 * RBMK 跳舞（控制棒/燃料棒被高压顶得乱窜）的确定性数学。
 * <p>
 * 服务端物理与客户端渲染用<b>同一套 (seed, 坐标, 时间)</b> 计算，天然同步：
 * 服务端只把"种子 + 窗口开始 tick + 舞者坐标 + 基准位"广播一次（每 5 秒一个窗口），
 * 两端各自按公式推出每帧棒位，几乎零流量、且客户端动画无缝平滑。
 */
public final class RbmkDanceMath {

    /** 单根棒最大上下摆动幅度（格）。 */
    public static final double MAX_AMPLITUDE = 4.0D;

    private RbmkDanceMath() {
    }

    /** 确定性散列：seed + 世界坐标 -> [0,1) 伪随机数（派生每根棒的相位/频率/幅度）。 */
    public static double hash01(long seed, int x, int y, int z, long salt) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L + x;
        h = h * 0x9E3779B97F4A7C15L + y;
        h = h * 0x9E3779B97F4A7C15L + z;
        h = h * 0x9E3779B97F4A7C15L + salt;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFFFFFFFFL) / (double) 0x1000000000000L;
    }

    /**
     * 该棒在给定时刻的跳舞偏移（格，正数向上）。平滑、确定性、无缝。
     * 叠加两个非整数倍频率的正弦，观感不规则（乱窜）但数学上稳定。
     *
     * @param seed     窗口种子
     * @param x,y,z    通道基座世界坐标
     * @param seconds  距窗口开始的时间（秒）
     */
    public static double danceOffset(long seed, int x, int y, int z, double seconds) {
        double rAmp = hash01(seed, x, y, z, 17);
        double rF1 = hash01(seed, x, y, z, 31);
        double rF2 = hash01(seed, x, y, z, 47);
        double rP1 = hash01(seed, x, y, z, 61);
        double rP2 = hash01(seed, x, y, z, 73);

        double amp = 0.5D + rAmp * (MAX_AMPLITUDE - 0.5D);      // 0.5 ~ 4.0 格
        double f1 = 0.8D + rF1 * 1.2D;                          // 0.8 ~ 2.0 Hz
        double f2 = f1 * (2.0D + rF2);                          // 高频、非整数倍
        double ph1 = rP1 * Math.PI * 2.0D;
        double ph2 = rP2 * Math.PI * 2.0D;

        double s = 0.65D * Math.sin(seconds * f1 * Math.PI * 2.0D + ph1)
                 + 0.35D * Math.sin(seconds * f2 * Math.PI * 2.0D + ph2);
        return amp * s;
    }
}
