package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * F2 3D 盆地域：几何 + 开边界契约（新，默认无消费者）。
 *
 * <p>不提供 solver；只判断域是否具备合法边界。
 */
public final class BasinDomain {

    public final BasinGeometry geometry;
    public final OpenBoundaryProvider boundary;

    public BasinDomain(BasinGeometry geometry, OpenBoundaryProvider boundary) {
        this.geometry = geometry;
        this.boundary = boundary;
    }

    /** 闭合盆地可直接算；开边界必须提供 provider。 */
    public boolean ready() {
        if (geometry == null || !geometry.valid) return false;
        return geometry.closed || boundary != null;
    }

    public int openEdges() {
        return geometry == null ? 0 : geometry.openEdges;
    }
}
