package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

public final class MacroSiteQueryResult {

    private final MacroSite primary;
    private final MacroSite secondary;
    private final double primaryDistance;
    private final double secondaryDistance;

    public MacroSiteQueryResult(MacroSite primary,
                                MacroSite secondary,
                                double primaryDistance,
                                double secondaryDistance) {

        this.primary = primary;
        this.secondary = secondary;
        this.primaryDistance = primaryDistance;
        this.secondaryDistance = secondaryDistance;
    }

    public MacroSite primary() {
        return primary;
    }

    public MacroSite secondary() {
        return secondary;
    }

    public double primaryDistance() {
        return primaryDistance;
    }

    public double secondaryDistance() {
        return secondaryDistance;
    }

    public double edgeMetric() {
        return (secondaryDistance == Double.POSITIVE_INFINITY)
            ? Double.POSITIVE_INFINITY
            : secondaryDistance - primaryDistance;
    }
}
