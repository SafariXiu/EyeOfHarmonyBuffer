package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/** 把 tile 内大气强迫按 x 周期折叠，保证相邻/平移 tile 同解。 */
public final class PeriodicSeaSurfaceForcing implements SeaSurfaceForcing {

    private final SeaSurfaceForcing delegate;
    private final int period;

    public PeriodicSeaSurfaceForcing(SeaSurfaceForcing delegate) {
        this.delegate = delegate;
        this.period = (int) Math.round(OceanGrid.WIDTH_M);
    }

    private int wrap(int x) {
        int v = x % period;
        return v < 0 ? v + period : v;
    }

    @Override public double windStressX(int x, int z, double theta) {
        return delegate.windStressX(wrap(x), z, theta);
    }
    @Override public double windStressZ(int x, int z, double theta) {
        return delegate.windStressZ(wrap(x), z, theta);
    }
    @Override public double netHeatFluxWm2(int x, int z, double theta, double sst) {
        return delegate.netHeatFluxWm2(wrap(x), z, theta, sst);
    }
    /**
     * ★ 2026-11-02 修复：装饰器原来**没有覆盖**带冰覆盖率的 5 参重载 ⟹ 接口默认实现退化为
     * 无冰调用 ⟹ 冰反照率在这一层被静默丢掉。冰覆盖率必须原样透传。
     */
    @Override public double netHeatFluxWm2(int x, int z, double theta, double sst, double iceFraction) {
        return delegate.netHeatFluxWm2(wrap(x), z, theta, sst, iceFraction);
    }
    /**
     * ★ 2026-11-02 修复：{@code shortwaveWm2} 在接口里默认返回 0，而本类**没有覆盖**它 ⟹
     * 3D 静态解里 {@code f.shortwaveWm2(...)} 恒为 0（Diag3D 的 {@code qsw0=0.0} 就是证据）。
     * 后果：{@code Qsw(z)} 的穿透分配整条链失效，太阳辐射被当成"表层净通量"一次性塞进 k=0。
     * 这里必须原样透传。
     */
    @Override public double shortwaveWm2(int x, int z, double theta, double sst) {
        return delegate.shortwaveWm2(wrap(x), z, theta, sst);
    }
    @Override public double freshwaterFluxKgM2S(int x, int z, double theta, double sst) {
        return delegate.freshwaterFluxKgM2S(wrap(x), z, theta, sst);
    }
}
