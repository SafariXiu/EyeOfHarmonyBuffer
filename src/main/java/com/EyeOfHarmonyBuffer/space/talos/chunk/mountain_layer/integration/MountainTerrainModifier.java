package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.integration;

/**
 * 山脉地形修饰器：把 DLA 结构高度叠加到基础地形上。
 *
 * v3 做法：山带内部「全权接管」成真实山脉——
 *   ridgeH = 谷底 + (峰顶 - 谷底) × elevation01
 *   h = lerp(base, ridgeH, mask01)
 * 山脊顶到峰顶、山间谷地落到谷底（真实起伏），带边缘按单元格蒙版
 * （已拉宽模糊）平滑混合回基础地形。不再使用 v1 的「基础地形上的加法」：
 * 山地宏包高度带本身已顶到 96~256，加法没有余量，看不出效果。
 *
 * v4：峰/谷高度按「山带类型」决定（1=HIGHLAND 2=MOUNTAINS 3=PEAK），
 * 不再依赖所在宏群系——构造带常常横穿非山地宏包，按宏包门控会导致
 * 整条带被跳过、地形全平。
 *
 * v5：峰顶通过 MountainHeightProfile 与 Y 轴上限解耦——
 * 256 高度下行为不变，未来突破 Y 轴后峰顶自动按比例抬升。
 */
public final class MountainTerrainModifier {

    /** V2 轨 DLA 脊顶相对局部基高的最大抬升（blocks）；超出部分由峰核锥承担。 */
    public static final double DLA_RELIEF_MAX = 55.0;

    private MountainTerrainModifier() {}

    public static double applyMountainUplift(double baseHeight,
                                             int seaLevel,
                                             double elevation01,
                                             double mask01,
                                             int beltKind,
                                             MountainHeightProfile profile) {
        return applyMountainUplift(baseHeight, seaLevel, elevation01, mask01,
            beltKind, profile, Double.POSITIVE_INFINITY);
    }

    /**
     * v6（D33）：峰顶上限可被外部场压住——V2 轨传入峰核场的目标高度 T，
     * 保证 DLA 脊顶不越过峰核（否则 DLA 252 会把峰核比下去，Alpine 就不在最高点）。
     */
    public static double applyMountainUplift(double baseHeight,
                                             int seaLevel,
                                             double elevation01,
                                             double mask01,
                                             int beltKind,
                                             MountainHeightProfile profile,
                                             double peakCap) {
        if (mask01 <= 0.0 || beltKind <= 0) {
            return baseHeight;
        }
        double valley = profile.valleyForKind(beltKind);
        double peak = profile.peakForKind(beltKind);
        if (peakCap != Double.POSITIVE_INFINITY) {
            // V2 轨（D33）：DLA 峰顶不得越过峰核目标 T，也不得无限抬高——
            // 相对局部基高封顶，保证"最高点"永远由峰核占据（旧轨 peakCap=+∞，行为不变）。
            peak = Math.min(peak, baseHeight + DLA_RELIEF_MAX);
            peak = Math.min(peak, peakCap);
        }
        if (valley <= 0.0) {
            return baseHeight;
        }
        // 轻微抬升中高值（0.85 次方），让主脊更接近峰顶
        double elev = Math.pow(clamp01(elevation01), 0.85);
        double ridgeH = valley + (peak - valley) * elev;
        // 边缘混合用 smoothstep：过渡更柔和，避免硬切/凸出
        double t = smoothstep01(mask01);
        return baseHeight + (ridgeH - baseHeight) * t;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double smoothstep01(double v) {
        double t = clamp01(v);
        return t * t * (3.0 - 2.0 * t);
    }
}
