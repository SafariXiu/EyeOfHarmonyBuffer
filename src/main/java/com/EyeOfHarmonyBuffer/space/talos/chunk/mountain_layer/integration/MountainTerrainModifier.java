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
 */
public final class MountainTerrainModifier {

    private MountainTerrainModifier() {}

    public static double applyMountainUplift(double baseHeight,
                                             int seaLevel,
                                             double elevation01,
                                             double mask01,
                                             int beltKind) {
        if (mask01 <= 0.0 || beltKind <= 0) {
            return baseHeight;
        }
        double valley = valleyForKind(beltKind);
        double peak = peakForKind(beltKind);
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

    /** 山带类型的谷底高度（blocks，海平面 64 之上）。 */
    private static double valleyForKind(int kind) {
        switch (kind) {
            case 3: // PEAK
                return 90.0;
            case 2: // MOUNTAINS
                return 78.0;
            default: // 1 = HIGHLAND
                return 68.0;
        }
    }

    /** 山带类型的峰顶高度（blocks）。 */
    private static double peakForKind(int kind) {
        switch (kind) {
            case 3:
                return 252.0;
            case 2:
                return 240.0;
            default:
                return 216.0;
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double smoothstep01(double v) {
        double t = clamp01(v);
        return t * t * (3.0 - 2.0 * t);
    }
}
