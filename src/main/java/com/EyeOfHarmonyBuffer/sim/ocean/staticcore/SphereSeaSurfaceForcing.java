package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-04（A0/A1）：球面网格的强迫坐标映射。
 *
 * <p>球面网格的 z 是【纬度轴】（0 = 北极，20,000 km = 南极），而大气/辐射/降水的
 * 函数都吃【世界 z】。直接用球面的 z 会被 {@link WorldContract#latOf(int)} 解释成
 * 三角波纬度（0→N→0→S→0），高纬会错位一整段。
 *
 * <p>本装饰器把球面 z 映射回北极半球的世界 z（{@link WorldContract#zOfLat(double)}），
 * 所以球面上的每个格点拿到的是【它自己纬度】的大气强迫。
 */
public final class SphereSeaSurfaceForcing implements SeaSurfaceForcing {

    private final SeaSurfaceForcing d;
    private final double heightM;

    public SphereSeaSurfaceForcing(SeaSurfaceForcing delegate, double heightM) {
        this.d = delegate;
        this.heightM = heightM;
    }

    private int zw(int z) {
        double latDeg = 90.0 - 180.0 * ((double) z / heightM);
        return WorldContract.zOfLat(latDeg);
    }

    @Override public double windStressX(int x, int z, double theta) { return d.windStressX(x, zw(z), theta); }
    @Override public double windStressZ(int x, int z, double theta) { return d.windStressZ(x, zw(z), theta); }
    @Override public double netHeatFluxWm2(int x, int z, double theta, double sst) { return d.netHeatFluxWm2(x, zw(z), theta, sst); }
    @Override public double netHeatFluxWm2(int x, int z, double theta, double sst, double ice) { return d.netHeatFluxWm2(x, zw(z), theta, sst, ice); }
    @Override public double shortwaveWm2(int x, int z, double theta, double sst) { return d.shortwaveWm2(x, zw(z), theta, sst); }
    @Override public double freshwaterFluxKgM2S(int x, int z, double theta, double sst) { return d.freshwaterFluxKgM2S(x, zw(z), theta, sst); }
}
