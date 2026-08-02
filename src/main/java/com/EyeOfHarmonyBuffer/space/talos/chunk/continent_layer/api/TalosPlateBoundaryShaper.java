package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.PlateBoundaryState;

/**
 * 板块边界地形塑形（海陆分布层 API）。
 *
 * 目前实现分离带的「裂谷悬崖」风格化，沿强度轴分段：
 *   - 强度 &lt; 0.197：外侧原始地形，不受影响；
 *   - 0.197 ~ 0.2：外崖面——从原始高度下落到崖缘，下落带锯齿噪声与两层岩架，
 *     靠近崖缘一侧最陡（现实悬崖的观感）；
 *   - 0.2 ~ 0.45：崖缘平台——崖唇上卷 + 参差崖线，向内缘缓坡过渡；
 *   - 0.45 ~ 0.75：倒石堆——凸曲线从崖脚落到谷底，带碎石起伏；
 *   - 0.75 ~ 1：谷底，小幅起伏。
 *
 * 所有噪声均为按世界坐标 + 种子计算的确定性值噪声，跨区块可复现、无随机状态。
 */
public final class TalosPlateBoundaryShaper {

    private TalosPlateBoundaryShaper() {}

    /**
     * 裂谷高度塑形。
     *
     * @param height       当前基础高度（海岸塑形之后）
     * @param seaLevel     海平面 Y
     * @param isLand       是否为陆地（海洋不塑形）
     * @param state        板块边界状态（仅 DIVERGENT 生效）
     * @param strength     板块边界强度 [0,1]
     * @param worldX       世界方块坐标 X（用于确定性崖面噪声）
     * @param worldZ       世界方块坐标 Z
     * @param worldSeedInt 世界种子 int
     */
    public static double applyRiftShaping(double height, int seaLevel,
                                          boolean isLand,
                                          PlateBoundaryState state,
                                          double strength,
                                          int worldX, int worldZ,
                                          int worldSeedInt) {
        if (!isLand || state != PlateBoundaryState.DIVERGENT
            || strength <= 0.0) {
            return height;
        }

        double rimStrength = TectonicConfig.PLATE_BOUNDARY_MIN_STRENGTH;
        double talusStrength = TectonicConfig.RIFT_TALUS_START_STRENGTH;
        double floorStrength = TectonicConfig.RIFT_FLOOR_START_STRENGTH;

        double edgeY = seaLevel + TectonicConfig.RIFT_EDGE_ABOVE_SEA;
        double floorY = seaLevel - TectonicConfig.RIFT_FLOOR_BELOW_SEA;

        // 三档确定性噪声：崖线主锯齿 / 崖面凹凸 / 细节
        double nRim = valueNoise(worldX, worldZ, worldSeedInt,
            TectonicConfig.RIFT_NOISE_SCALE_RIM, 0x1B873593);
        double nFace = valueNoise(worldX, worldZ, worldSeedInt,
            TectonicConfig.RIFT_NOISE_SCALE_FACE, 0x2D1F3A9C);
        double nDetail = valueNoise(worldX, worldZ, worldSeedInt,
            TectonicConfig.RIFT_NOISE_SCALE_DETAIL, 0x4B6E7F8A);

        // 崖缘基准：边缘平台 + 上卷崖唇 + 崖线锯齿
        double rimY = edgeY + TectonicConfig.RIFT_RIM_UPLIFT
            + nRim * TectonicConfig.RIFT_RIM_NOISE_AMP;
        // 倒石堆顶部（崖脚）
        double talusTop = floorY + TectonicConfig.RIFT_TALUS_HEIGHT;

        double target;
        if (strength < rimStrength) {
            // 外崖面：t=0 原样地形，t=1 崖缘；下落集中在靠崖缘一侧
            double t = TectonicMath.clamp(
                (strength - TectonicConfig.RIFT_CLIFF_START_STRENGTH)
                    / (rimStrength - TectonicConfig.RIFT_CLIFF_START_STRENGTH),
                0.0, 1.0);
            double drop = Math.pow(t, 1.5);
            target = height + (rimY - height) * drop;

            // 崖面凹凸：下落中段最强，两端收敛到 0，保证与外侧 / 崖缘连续
            target += (nFace * TectonicConfig.RIFT_CLIFF_NOISE_AMP
                + nDetail * 1.5) * Math.sin(drop * Math.PI);

            // 两层岩架：下落途中短暂停留，形成台阶
            target += TectonicConfig.RIFT_LEDGE_HEIGHT
                * (ledgePulse(drop, 0.45) + ledgePulse(drop, 0.75));
        } else if (strength < talusStrength) {
            // 崖缘平台 + 内缘缓坡：rimY → talusTop
            double q = (strength - rimStrength)
                / (talusStrength - rimStrength);
            double slope = smoothstep(0.0, 1.0, q);
            target = rimY + (talusTop - rimY) * slope;
            // 细节中段最强、两端为 0，保持连续
            target += nDetail * 1.5 * q * (1.0 - q);
        } else if (strength < floorStrength) {
            // 倒石堆：凸曲线（先陡后缓）talusTop → floorY
            double q = (strength - talusStrength)
                / (floorStrength - talusStrength);
            double drop = Math.pow(q, 0.65);
            target = talusTop + (floorY - talusTop) * drop;
            // 碎石起伏：两端收敛，中段最强
            target += nDetail * TectonicConfig.RIFT_TALUS_NOISE_AMP
                * q * (1.0 - q);
        } else {
            // 谷底：小幅起伏
            double q = (strength - floorStrength)
                / (1.0 - floorStrength);
            target = floorY + nDetail * TectonicConfig.RIFT_FLOOR_NOISE_AMP * q
                + nRim * 0.6 * q;
        }

        return height + (target - height) * TectonicConfig.RIFT_BLEND;
    }

    /** 岩架脉冲：drop 接近 center 时返回 0~1，其余为 0。 */
    private static double ledgePulse(double drop, double center) {
        double d = Math.abs(drop - center);
        double w = 0.07;
        if (d >= w) {
            return 0.0;
        }
        double x = 1.0 - d / w;
        return x * x;
    }

    /** 确定性值噪声，返回 [-1,1]；无分配、无随机状态。 */
    private static double valueNoise(int worldX, int worldZ, int seed,
                                     double scale, int salt) {
        double sx = worldX / scale;
        double sz = worldZ / scale;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double fx = sx - x0;
        double fz = sz - z0;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fz * fz * (3.0 - 2.0 * fz);

        double n00 = corner(x0, z0, seed, salt);
        double n10 = corner(x0 + 1, z0, seed, salt);
        double n01 = corner(x0, z0 + 1, seed, salt);
        double n11 = corner(x0 + 1, z0 + 1, seed, salt);
        double a = n00 + (n10 - n00) * u;
        double b = n01 + (n11 - n01) * u;
        return a + (b - a) * v;
    }

    /** 格点角值：[-1,1]。 */
    private static double corner(int cx, int cz, int seed, int salt) {
        long h = 0x9E3779B97F4A7C15L;
        h = mixInt(h, cx);
        h = mixInt(h, cz);
        h = mixInt(h, seed);
        h = mixInt(h, salt);
        long m = TectonicMath.mix64(h) >>> (64 - 53);
        return m / (double) (1L << 53) * 2.0 - 1.0;
    }

    private static long mixInt(long h, int v) {
        h ^= TectonicMath.mix64((v & 0xFFFFFFFFL) + 0x9E3779B97F4A7C15L);
        return TectonicMath.mix64(h);
    }

    private static double smoothstep(double a, double b, double x) {
        double t = (x - a) / (b - a);
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
