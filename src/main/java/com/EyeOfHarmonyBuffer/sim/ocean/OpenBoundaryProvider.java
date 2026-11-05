package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * F2 开边界数据接口（新，默认无实现）。
 *
 * <p>3D 盆地海洋的 bbox 通常四边开；边界值必须由 L1 粗层/嵌套提供。
 * <p>原始 z；不回卷；theta 为季节相位。
 */
public interface OpenBoundaryProvider {

    double temperatureK(int x, int z, double theta);

    double salinityPsu(int x, int z, double theta);

    double uMs(int x, int z, double theta);

    double vMs(int x, int z, double theta);

    double sshM(int x, int z, double theta);
}
